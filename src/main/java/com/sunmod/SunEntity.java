package com.sunmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * O Sol: 50x50x50 blocos, fica parado no ar (sem gravidade, nunca se move) e
 * só recebe dano de um {@link IceCubeProjectile}. A vida é a "temperatura" mostrada na HUD
 * e cada dano faz nascer uma {@link MoneyCoinEntity} para o jogador que acertou.
 */
public class SunEntity extends Entity {
	/** Vida total do Sol (100x a original: eram 100). Cada Ice Cube causa {@link IceCubeProjectile#DAMAGE}. */
	public static final float MAX_HEALTH = 50000.0F;

	/** Temperatura (°C) mostrada na HUD com a vida cheia; cai junto com a vida. */
	public static final int MAX_TEMPERATURE = 144982;

	/**
	 * Raio da hitbox real: a esfera mais externa do modelo (o outline), 25 blocos.
	 * Entidades gigantes não são achadas pela busca normal de colisão do Minecraft (ela só procura
	 * perto da posição da entidade), por isso o projétil testa o Sol diretamente por esta lista.
	 */
	public static final double RADIUS = 25.0;

	/** A barreira nasce a cada 5 minutos (contados do início de uma barreira até o início da próxima)... */
	public static final int SHIELD_INTERVAL_TICKS = 5 * 60 * 20;

	/** ...e fica ativa por 2 minutos; depois some e o Sol volta a receber dano. */
	public static final int SHIELD_DURATION_TICKS = 2 * 60 * 20;

	/** Raio da barreira: um pouco maior que o outline do Sol (25), para envolvê-lo. */
	public static final double SHIELD_RADIUS = 27.0;

	/** Vida sincronizada com os clientes (a HUD Sun Temperature lê daqui). */
	private static final EntityDataAccessor<Float> DATA_HEALTH =
			SynchedEntityData.defineId(SunEntity.class, EntityDataSerializers.FLOAT);

	/**
	 * Tick de jogo em que a barreira termina (0 = sem barreira). Só muda na ativação/desativação,
	 * então quase não gera tráfego de rede; o cliente calcula o tempo restante sozinho (fade, piscar).
	 */
	private static final EntityDataAccessor<Long> DATA_SHIELD_END =
			SynchedEntityData.defineId(SunEntity.class, EntityDataSerializers.LONG);

	/** Tick de jogo em que a barreira atual foi ligada (o cliente usa para o fade-in; vale também no evento). */
	private static final EntityDataAccessor<Long> DATA_SHIELD_START =
			SynchedEntityData.defineId(SunEntity.class, EntityDataSerializers.LONG);

	/** Quantos Eventos de Instabilidade já foram concluídos neste Sol (a vida dos inimigos sobe 5% por evento). */
	private int eventsCleared = 0;

	/** Máximo de cometas orbitando ao mesmo tempo e raio (blocos) em que eles aparecem em volta do centro do Sol. */
	public static final int MAX_COMETS = 3;
	public static final double COMET_ORBIT_MIN = RADIUS + 14.0;
	public static final double COMET_ORBIT_MAX = RADIUS + 30.0;
	/** Só nascem cometas se houver um jogador a até esta distância do Sol. */
	private static final double COMET_PLAYER_RANGE = 200.0;
	/** Chance por segundo de nascer um cometa (quando há menos que o máximo): ~1 a cada 12 s. */
	private static final double COMET_CHANCE_PER_SECOND = 1.0 / 12.0;
	/** Fragmento Solar: chance por segundo (~1 a cada 40 s), jogador precisa estar a até 90 blocos e no máx. 2 ao mesmo tempo. */
	public static final double FRAGMENT_CHANCE_PER_SECOND = 1.0 / 40.0;
	private static final double FRAGMENT_PLAYER_RANGE = 90.0;
	private static final int MAX_FRAGMENTS = 2;
	/** Raio (blocos) de um ponto super fraco da Ofensiva Crítica. */
	public static final double WEAK_POINT_RADIUS = 4.0;

	/** Pontos super fracos ativos na superfície do Sol (Ofensiva Crítica). Só no servidor, não é salvo. */
	private final List<Vec3> weakPoints = new ArrayList<>();
	private final List<FxEntity> weakPointFx = new ArrayList<>();
	private long weakPointsUntil = 0L;
	private float weakPointMultiplier = 1.0F;

	/** Tick de jogo da próxima ativação da barreira (só no servidor; salvo no mundo). */
	private long nextShieldAt = 0L;

	private static final Set<SunEntity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

	/** Sóis carregados no servidor. */
	public static List<SunEntity> activeSuns() {
		synchronized (ACTIVE) {
			return new ArrayList<>(ACTIVE);
		}
	}

	public SunEntity(EntityType<? extends SunEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	public float getHealthValue() {
		return this.entityData.get(DATA_HEALTH);
	}

	private void setHealthValue(float value) {
		this.entityData.set(DATA_HEALTH, value);
	}

	/** Tick de jogo em que a barreira acaba (0 = inativa). */
	public long getShieldEnd() {
		return this.entityData.get(DATA_SHIELD_END);
	}

	/** A barreira está ativa agora? (o Sol não recebe dano enquanto estiver) */
	public boolean isShielded() {
		long end = this.getShieldEnd();
		return end != 0L && this.level().getGameTime() < end;
	}

	/** Raio da hitbox atual: a barreira (maior) enquanto ativa, senão o outline do Sol. */
	public double getHitRadius() {
		return this.isShielded() ? SHIELD_RADIUS : RADIUS;
	}

	/** O segmento from→to (mundo) toca a esfera do Sol (inclui começar dentro dela)? */
	public boolean hitsSegment(Vec3 from, Vec3 to) {
		double radius = this.getHitRadius();
		Vec3 center = this.position().add(0.0, RADIUS, 0.0);
		Vec3 d = to.subtract(from);
		double len2 = d.lengthSqr();
		double t = len2 > 1.0E-7 ? Math.max(0.0, Math.min(1.0, center.subtract(from).dot(d) / len2)) : 0.0;
		Vec3 closest = from.add(d.scale(t));
		return closest.distanceToSqr(center) <= radius * radius;
	}

	/**
	 * Primeiro ponto em que o raio from→to entra na esfera do Sol (ou {@code from}, se já começar dentro).
	 * Devolve null se o segmento não toca a esfera. Usado pelos tiros instantâneos (Lazer Ice).
	 */
	public Vec3 rayHit(Vec3 from, Vec3 to) {
		double radius = this.getHitRadius();
		Vec3 center = this.position().add(0.0, RADIUS, 0.0);
		Vec3 d = to.subtract(from);
		double a = d.lengthSqr();
		if (a < 1.0E-7) {
			return null;
		}
		Vec3 oc = from.subtract(center);
		double c = oc.lengthSqr() - radius * radius;
		if (c <= 0.0) {
			return from;
		}
		double b = oc.dot(d);
		double disc = b * b - a * c;
		if (disc < 0.0) {
			return null;
		}
		double t = (-b - Math.sqrt(disc)) / a;
		return (t < 0.0 || t > 1.0) ? null : from.add(d.scale(t));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_HEALTH, MAX_HEALTH);
		builder.define(DATA_SHIELD_END, 0L);
		builder.define(DATA_SHIELD_START, 0L);
	}

	public long getShieldStart() {
		return this.entityData.get(DATA_SHIELD_START);
	}

	public int getEventsCleared() {
		return this.eventsCleared;
	}

	public void addEventCleared() {
		this.eventsCleared++;
	}

	/**
	 * Evento de Instabilidade: liga/desliga a barreira (o Sol fica imune enquanto o evento durar). O fim é colocado
	 * bem longe; o evento chama de novo com {@code false} quando termina.
	 */
	public void setEventShield(boolean on, long now) {
		if (on) {
			this.entityData.set(DATA_SHIELD_START, now);
			this.entityData.set(DATA_SHIELD_END, now + 20L * 60L * 30L);
		} else {
			this.entityData.set(DATA_SHIELD_END, 0L);
			this.nextShieldAt = now + SHIELD_INTERVAL_TICKS; // recomeça o ciclo normal da barreira
			this.playSound(SoundEvents.GLASS_BREAK, 16.0F, 0.5F);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel) {
			synchronized (ACTIVE) {
				ACTIVE.add(this);
			}
		}
		// Garante que nada (água, empurrões, etc.) tire o Sol de onde foi colocado.
		this.setDeltaMovement(Vec3.ZERO);

		if (this.level() instanceof ServerLevel level) {
			this.tickShield(level);
			this.tickWeakPoints(level);
			this.tickComets(level);
			this.tickFragments(level);
			Instability.tick(level, this);
		}
	}

	/** De vez em quando o Sol solta um Fragmento Solar que cai perto de um jogador por perto. */
	private void tickFragments(ServerLevel level) {
		if (level.getGameTime() % 20L != 7L || level.getRandom().nextDouble() > FRAGMENT_CHANCE_PER_SECOND) {
			return;
		}
		Vec3 center = this.position().add(0.0, RADIUS, 0.0);
		List<Player> near = new ArrayList<>();
		for (Player p : level.players()) {
			if (!p.isSpectator() && p.position().distanceToSqr(center) < FRAGMENT_PLAYER_RANGE * FRAGMENT_PLAYER_RANGE) {
				near.add(p);
			}
		}
		if (near.isEmpty()
				|| level.getEntitiesOfClass(SolarFragmentEntity.class, this.getBoundingBox().inflate(150.0)).size() >= MAX_FRAGMENTS) {
			return;
		}
		SolarFragmentEntity.launch(level, this, near.get(level.getRandom().nextInt(near.size())));
	}

	/** Ofensiva Crítica: cria {@code count} pontos super fracos em lugares aleatórios da superfície do Sol. */
	public void activateWeakPoints(ServerLevel level, int count, int durationTicks, float multiplier) {
		this.weakPoints.clear();
		Vec3 center = this.position().add(0.0, RADIUS, 0.0);
		for (int i = 0; i < count; i++) {
			double y = level.getRandom().nextDouble() * 2.0 - 1.0;
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double ring = Math.sqrt(1.0 - y * y);
			this.weakPoints.add(center.add(Math.cos(angle) * ring * RADIUS, y * RADIUS, Math.sin(angle) * ring * RADIUS));
		}
		this.weakPointsUntil = level.getGameTime() + durationTicks;
		this.weakPointMultiplier = multiplier;
		for (FxEntity old : this.weakPointFx) { // troca os desenhos antigos pelos novos
			old.discard();
		}
		this.weakPointFx.clear();
		for (Vec3 p : this.weakPoints) { // uma mira contínua em cada ponto, enquanto durar
			this.weakPointFx.add(FxEntity.spawn(level, p, FxEntity.WEAK_POINT, (float) WEAK_POINT_RADIUS, durationTicks, 0xFF3A2A));
		}
	}

	/** O ponto {@code surface} (superfície do Sol) está dentro de algum ponto super fraco ativo? */
	private boolean isWeakPointHit(ServerLevel level, Vec3 surface) {
		if (this.weakPoints.isEmpty() || level.getGameTime() >= this.weakPointsUntil) {
			return false;
		}
		for (Vec3 p : this.weakPoints) {
			if (p.distanceToSqr(surface) <= WEAK_POINT_RADIUS * WEAK_POINT_RADIUS) {
				return true;
			}
		}
		return false;
	}

	/** Apaga os pontos super fracos quando o tempo acaba (o desenho é uma {@link FxEntity} que some sozinha). */
	private void tickWeakPoints(ServerLevel level) {
		if (!this.weakPoints.isEmpty() && level.getGameTime() >= this.weakPointsUntil) {
			this.weakPoints.clear();
		}
	}

	/** Faz nascer cometas em órbita (até {@link #MAX_COMETS}) enquanto houver jogador por perto. */
	private void tickComets(ServerLevel level) {
		if (level.getGameTime() % 20L != 0L || level.getRandom().nextDouble() > COMET_CHANCE_PER_SECOND) {
			return;
		}
		Vec3 center = this.position().add(0.0, RADIUS, 0.0);
		boolean playerNear = false;
		for (Player p : level.players()) {
			if (!p.isSpectator() && p.position().distanceToSqr(center) < COMET_PLAYER_RANGE * COMET_PLAYER_RANGE) {
				playerNear = true;
				break;
			}
		}
		if (!playerNear) {
			return;
		}
		int comets = level.getEntitiesOfClass(CometEntity.class, this.getBoundingBox().inflate(COMET_ORBIT_MAX)).size();
		if (comets >= MAX_COMETS) {
			return;
		}
		double y = (level.getRandom().nextDouble() * 2.0 - 1.0) * 0.6; // evita os polos
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		double ring = Math.sqrt(1.0 - y * y);
		double dist = COMET_ORBIT_MIN + level.getRandom().nextDouble() * (COMET_ORBIT_MAX - COMET_ORBIT_MIN);
		CometEntity comet = new CometEntity(ModEntities.COMET, level);
		comet.setPos(center.x + Math.cos(angle) * ring * dist, center.y + y * dist - 0.5, center.z + Math.sin(angle) * ring * dist);
		comet.orbit(this);
		level.addFreshEntity(comet);
	}

	/** Ciclo da barreira: liga a cada {@link #SHIELD_INTERVAL_TICKS}, desliga após {@link #SHIELD_DURATION_TICKS}. */
	private void tickShield(ServerLevel level) {
		long now = level.getGameTime();
		if (this.nextShieldAt <= 0L) {
			this.nextShieldAt = now + SHIELD_INTERVAL_TICKS; // Sol recém-colocado: 1ª barreira em 5 min
		}

		long end = this.getShieldEnd();
		if (end != 0L && now >= end) {
			this.entityData.set(DATA_SHIELD_END, 0L);
			end = 0L;
			this.playSound(SoundEvents.GLASS_BREAK, 16.0F, 0.5F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + RADIUS, this.getZ(),
					200, 20.0, 20.0, 20.0, 0.3);
		}

		if (end == 0L && now >= this.nextShieldAt) {
			this.entityData.set(DATA_SHIELD_START, now);
			this.entityData.set(DATA_SHIELD_END, now + SHIELD_DURATION_TICKS);
			this.nextShieldAt = now + SHIELD_INTERVAL_TICKS;
			this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 16.0F, 1.8F);
			level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + RADIUS, this.getZ(),
					200, 20.0, 20.0, 20.0, 0.1);
		}
	}

	/** Chamado quando um projétil bate na barreira: só faíscas (sem som e sem dano). */
	public void onShieldHit(ServerLevel level, Vec3 from) {
		Vec3 p = this.surfacePoint(from);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 30, 1.0, 1.0, 1.0, 0.3);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// Único dano aceito: o vindo do projétil Ice Cube (inclui os tiros da Gun Ice).
		if (!(source.getDirectEntity() instanceof IceCubeProjectile projectile)) {
			return false;
		}
		ServerPlayer shooter = source.getEntity() instanceof ServerPlayer player ? player : null;
		return this.applyWeaponDamage(level, shooter, projectile.position(), amount, projectile.getCoinValue());
	}

	/**
	 * Dano das armas (projétil ou raio): tira vida, faz efeitos e, se foi um jogador, nasce uma moeda
	 * com {@code coinValue} de dinheiro no ponto do acerto. Com a barreira ativa não faz nada.
	 *
	 * @return true se o dano foi aplicado
	 */
	public boolean applyWeaponDamage(ServerLevel level, ServerPlayer shooter, Vec3 hitPos, float amount, int coinValue) {
		// Com a barreira ativa o Sol é invulnerável.
		if (this.isShielded()) {
			return false;
		}
		// Ofensiva Crítica: acertar um ponto super fraco multiplica o dano.
		Vec3 surface = this.surfacePoint(hitPos);
		if (this.isWeakPointHit(level, surface)) {
			amount *= this.weakPointMultiplier;
			FxEntity.spawn(level, surface, FxEntity.BLAST, 3.0F, 12, 0xFFB040);
			level.playSound(null, surface.x, surface.y, surface.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		float remaining = this.getHealthValue() - amount;
		this.setHealthValue(Math.max(0.0F, remaining));

		// Armas de tiro rápido: menos partículas por acerto (para não pesar com 4-5 acertos por segundo).
		int particles = amount > IceCubeProjectile.DAMAGE * 1.5F ? 40 : 150;
		level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + RADIUS, this.getZ(),
				particles, 15.0, 15.0, 15.0, 0.05);

		// Dano causado por um jogador: nasce uma moeda na superfície do Sol, no ponto do acerto.
		if (shooter != null) {
			MoneyCoinEntity.spawn(level, surface, shooter, coinValue);
		}

		if (remaining <= 0.0F) {
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + RADIUS, this.getZ(),
					800, 20.0, 20.0, 20.0, 0.2);
			this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 16.0F, 0.5F);
			this.discard();
		}
		return true;
	}

	/** Ponto da superfície do Sol (ou da barreira, se ativa) mais próximo de {@code from} (ou o próprio ponto, se estiver dentro). */
	private Vec3 surfacePoint(Vec3 from) {
		double radius = this.getHitRadius();
		Vec3 center = this.position().add(0.0, RADIUS, 0.0);
		Vec3 offset = from.subtract(center);
		double len = offset.length();
		return len > radius ? center.add(offset.scale(radius / len)) : from;
	}

	/** Precisa ser "pickable" para que projéteis (e o cursor) possam acertá-lo. */
	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setHealthValue(input.getFloatOr("SunHealth", MAX_HEALTH));
		this.entityData.set(DATA_SHIELD_END, input.getLongOr("SunShieldEnd", 0L));
		this.nextShieldAt = input.getLongOr("SunNextShield", 0L);
		this.eventsCleared = input.getIntOr("SunEventsCleared", 0);
		this.entityData.set(DATA_SHIELD_START, input.getLongOr("SunShieldStart", 0L));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putFloat("SunHealth", this.getHealthValue());
		output.putLong("SunShieldEnd", this.getShieldEnd());
		output.putLong("SunNextShield", this.nextShieldAt);
		output.putInt("SunEventsCleared", this.eventsCleared);
		output.putLong("SunShieldStart", this.getShieldStart());
	}
}
