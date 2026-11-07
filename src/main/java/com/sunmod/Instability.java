package com.sunmod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Evento de Instabilidade Solar (reproduz o do jogo original).
 *
 * <p>Fluxo: (1) {@link #start} avisa "O ar está ficando mais quente ao seu redor..." e espera {@link #WARNING_TICKS};
 * (2) {@link #begin} mostra o banner "Evento de Instabilidade Solar" e liga o evento: o Sol ganha a barreira hexagonal
 * (imune), o gelo fica infinito, caem fragmentos em chuva (cada um deixa uma poça de lava onde pousa) e aparecem
 * inimigos (Ghoul Solar e Olho Solar); (3) a barra "INSTABILITY PROGRESS" sobe ao estourar fragmentos e matar
 * inimigos; ao chegar em 100% {@link #finish} remove tudo, dá gemas e pede para escolher um benefício ({@link Perks}).
 *
 * <p>Há no máximo um evento por vez (estado estático). {@link #isActive()} e {@link #getProgressPercent()} valem nos
 * dois lados: o servidor grava e o cliente copia por {@link InstabilityStatePayload}.
 */
public final class Instability {
	public static final String ENEMY_TAG = "sunmod_enemy";

	/** Tempo entre o aviso e o começo (6 s). */
	public static final int WARNING_TICKS = 20 * 6;
	/** Se ninguém terminar em 5 minutos o evento acaba sem recompensa. */
	public static final int MAX_EVENT_TICKS = 20 * 60 * 5;
	/** Chance por segundo de começar sozinho (~1 a cada 15 min) com jogador perto do Sol. */
	public static final double CHANCE_PER_SECOND = 1.0 / 900.0;
	/** Jogadores a esta distância do Sol participam (inimigos, avisos, recompensa). */
	public static final double PLAYER_RANGE = 110.0;

	public static final int FRAGMENT_INTERVAL_TICKS = 50;
	public static final int MAX_FRAGMENTS = 4;
	public static final float FRAGMENT_PROGRESS = 8.0F;
	public static final int ENEMY_INTERVAL_TICKS = 80;
	public static final int MAX_ENEMIES = 6;
	public static final double ENEMY_HEALTH_PER_EVENT = 1.05;

	public static final double POOL_RADIUS = 4.5;
	public static final int POOL_TICKS = 20 * 8;
	public static final float POOL_DAMAGE = 2.0F;

	public static final int REWARD_GEMS = 6;
	/** Termina em até esta quantidade de segundos = +1 gema de bônus. */
	public static final int BONUS_SECONDS = 45;

	private record Pool(Vec3 pos, long end, FxEntity fx) {
	}

	// ---- estado compartilhado (servidor grava, cliente copia) ----
	private static volatile boolean active = false;
	private static volatile int progressPercent = 0;

	// ---- estado só do servidor ----
	private static SunEntity sun;
	private static boolean warning;
	private static long warnEnd;
	private static long startTick;
	private static float progress;
	private static int lastSent = -1;
	private static final List<Mob> ENEMIES = new ArrayList<>();
	private static final List<Pool> POOLS = new ArrayList<>();

	private Instability() {}

	public static boolean isActive() {
		return active;
	}

	/** O evento dá gelo infinito (as armas e o arremesso não gastam Ice Cubes). */
	public static boolean isInfiniteIce() {
		return active;
	}

	public static int getProgressPercent() {
		return progressPercent;
	}

	/** Cliente: copia o estado recebido do servidor. */
	public static void setClientState(boolean isActive, int percent) {
		active = isActive;
		progressPercent = percent;
	}

	/** Zera tudo (parada do servidor ou Sol removido). */
	public static void reset() {
		active = false;
		progressPercent = 0;
		sun = null;
		warning = false;
		ENEMIES.clear();
		POOLS.clear();
		progress = 0.0F;
		lastSent = -1;
	}

	// ------------------------------------------------------------------ início

	/** Começa o aviso do evento neste Sol. Devolve false se já houver um evento. */
	public static boolean start(ServerLevel level, SunEntity target) {
		if (sun != null) {
			return false;
		}
		sun = target;
		warning = true;
		warnEnd = level.getGameTime() + WARNING_TICKS;
		for (ServerPlayer p : nearby(level)) {
			p.displayClientMessage(Component.translatable("message.sunmod.instability_warning"), true);
		}
		level.playSound(null, target.getX(), target.getY() + SunEntity.RADIUS, target.getZ(),
				SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 8.0F, 0.5F);
		return true;
	}

	private static void begin(ServerLevel level, long now) {
		active = true;
		warning = false;
		startTick = now;
		progress = 0.0F;
		lastSent = -1;
		ENEMIES.clear();
		POOLS.clear();
		sun.setEventShield(true, now);

		Component title = Component.literal("⚠ ").append(Component.translatable("message.sunmod.instability_title"))
				.append(Component.literal(" ⚠")).withStyle(ChatFormatting.GOLD);
		Component subtitle = Component.translatable("message.sunmod.instability_subtitle",
				String.format(Locale.ROOT, "%.2f", enemyHealthMultiplier())).withStyle(ChatFormatting.WHITE);
		for (ServerPlayer p : nearby(level)) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
			p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			p.connection.send(new ClientboundSetTitleTextPacket(title));
		}
		level.playSound(null, sun.getX(), sun.getY() + SunEntity.RADIUS, sun.getZ(),
				SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 6.0F, 1.4F);
		sync(level);
	}

	private static double enemyHealthMultiplier() {
		return Math.pow(ENEMY_HEALTH_PER_EVENT, sun.getEventsCleared() + 1);
	}

	// ------------------------------------------------------------------ tick

	/** Chamado todo tick do servidor por cada Sol (só o Sol do evento age quando há evento). */
	public static void tick(ServerLevel level, SunEntity thisSun) {
		long now = level.getGameTime();
		if (sun == null) {
			if (now % 20L == 11L && level.getRandom().nextDouble() < CHANCE_PER_SECOND
					&& !thisSun.isShielded() && !nearby(level, thisSun).isEmpty()) {
				start(level, thisSun);
			}
			return;
		}
		if (sun != thisSun) {
			return;
		}
		if (sun.isRemoved()) {
			reset();
			return;
		}
		if (!active) {
			if (now >= warnEnd) {
				begin(level, now);
			}
			return;
		}
		tickActive(level, now);
	}

	private static void tickActive(ServerLevel level, long now) {
		List<ServerPlayer> players = nearby(level);
		long age = now - startTick;
		if (age > MAX_EVENT_TICKS || (players.isEmpty() && age > 20L * 30L)) {
			finish(level, now, false);
			return;
		}

		if (!players.isEmpty()) {
			if (age % FRAGMENT_INTERVAL_TICKS == 0L
					&& level.getEntitiesOfClass(SolarFragmentEntity.class, sun.getBoundingBox().inflate(200.0)).size() < MAX_FRAGMENTS) {
				SolarFragmentEntity.launch(level, sun, players.get(level.getRandom().nextInt(players.size())));
			}
			if (age % ENEMY_INTERVAL_TICKS == 20L && ENEMIES.size() < MAX_ENEMIES) {
				spawnEnemy(level, players.get(level.getRandom().nextInt(players.size())));
			}
		}

		ENEMIES.removeIf(m -> m.isRemoved() || !m.isAlive());
		if (now % 5L == 0L) {
			for (Mob m : ENEMIES) {
				SolarEnemies.updateName(m);
			}
		}
		tickPools(level, now);

		int percent = (int) Math.min(100.0F, progress);
		if (percent != lastSent) {
			sync(level);
		}
		if (progress >= 100.0F) {
			finish(level, now, true);
		}
	}

	private static void spawnEnemy(ServerLevel level, ServerPlayer target) {
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		double dist = 14.0 + level.getRandom().nextDouble() * 14.0;
		int x = Mth.floor(target.getX() + Math.cos(angle) * dist);
		int z = Mth.floor(target.getZ() + Math.sin(angle) * dist);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		boolean eye = level.getRandom().nextDouble() < 0.35;
		Mob mob = SolarEnemies.spawn(level, new Vec3(x + 0.5, y + (eye ? 2.0 : 0.0), z + 0.5), eye, enemyHealthMultiplier());
		if (mob != null) {
			ENEMIES.add(mob);
		}
	}

	/** Soma progresso (fragmento estourado, inimigo morto). Só conta durante o evento. */
	public static void addProgress(float amount) {
		if (active) {
			progress = Math.min(100.0F, progress + amount);
		}
	}

	// ------------------------------------------------------------------ poças de lava

	/** Um fragmento pousou: durante o evento deixa uma poça de lava que queima quem ficar em cima. */
	public static void onFragmentLanded(ServerLevel level, Vec3 landing) {
		if (active) {
			// a poça é um disco de lava desenhado no cliente (FxEntity) que some sozinho; aqui só fica o dano
			Vec3 ground = new Vec3(landing.x, landing.y - 0.5, landing.z);
			FxEntity fx = FxEntity.spawn(level, ground, FxEntity.LAVA_POOL, (float) POOL_RADIUS, POOL_TICKS, 0xFF8A1E);
			POOLS.add(new Pool(new Vec3(ground.x, ground.y + 0.15, ground.z), level.getGameTime() + POOL_TICKS, fx));
		}
	}

	private static void tickPools(ServerLevel level, long now) {
		Iterator<Pool> it = POOLS.iterator();
		while (it.hasNext()) {
			Pool pool = it.next();
			if (now >= pool.end()) {
				it.remove();
				continue;
			}
			Vec3 c = pool.pos();
			if (now % 10L == 0L) {
				for (ServerPlayer p : nearby(level)) {
					if (p.isCreative() || p.isSpectator()) {
						continue;
					}
					double dx = p.getX() - c.x, dz = p.getZ() - c.z;
					if (dx * dx + dz * dz <= POOL_RADIUS * POOL_RADIUS && Math.abs(p.getY() - c.y) < 2.5) {
						p.hurtServer(level, level.damageSources().inFire(), POOL_DAMAGE);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ fim

	private static void finish(ServerLevel level, long now, boolean success) {
		int seconds = (int) ((now - startTick) / 20L);
		List<ServerPlayer> players = nearby(level);

		active = false;
		warning = false;
		for (Mob m : ENEMIES) {
			m.discard();
		}
		ENEMIES.clear();
		for (Pool pool : POOLS) {
			pool.fx().discard();
		}
		POOLS.clear();
		for (SolarFragmentEntity f : level.getEntitiesOfClass(SolarFragmentEntity.class, sun.getBoundingBox().inflate(250.0))) {
			f.discard();
		}
		sun.setEventShield(false, now);
		if (success) {
			sun.addEventCleared();
		}
		progressPercent = success ? 100 : 0;
		progress = 0.0F;
		lastSent = -1;
		sync(level);
		progressPercent = 0;

		if (success) {
			int bonus = seconds <= BONUS_SECONDS ? 1 : 0;
			for (ServerPlayer p : players) {
				p.setAttached(ModAttachments.GEMS, p.getAttachedOrElse(ModAttachments.GEMS, 0) + REWARD_GEMS + bonus);
				Perks.markPending(p);
				ServerPlayNetworking.send(p, new InstabilityClearedPayload(seconds, REWARD_GEMS, bonus));
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
						SoundSource.PLAYERS, 1.0F, 1.0F);
			}
		}
		sun = null;
	}

	/** Comando de teste: termina já, com recompensa. */
	public static boolean forceClear(ServerLevel level) {
		if (!active || sun == null) {
			return false;
		}
		progress = 100.0F;
		return true;
	}

	// ------------------------------------------------------------------ rede / ajudas

	private static void sync(ServerLevel level) {
		int percent = (int) Math.min(100.0F, progress);
		lastSent = percent;
		int shown = active ? percent : progressPercent;
		progressPercent = shown;
		for (Player p : level.players()) {
			if (p instanceof ServerPlayer sp) {
				ServerPlayNetworking.send(sp, new InstabilityStatePayload(active, shown));
			}
		}
	}

	private static List<ServerPlayer> nearby(ServerLevel level) {
		return nearby(level, sun);
	}

	private static List<ServerPlayer> nearby(ServerLevel level, SunEntity of) {
		List<ServerPlayer> out = new ArrayList<>();
		if (of == null) {
			return out;
		}
		Vec3 center = of.position().add(0.0, SunEntity.RADIUS, 0.0);
		for (Player p : level.players()) {
			if (p instanceof ServerPlayer sp && !sp.isSpectator() && sp.position().distanceToSqr(center) < PLAYER_RANGE * PLAYER_RANGE) {
				out.add(sp);
			}
		}
		return out;
	}
}
