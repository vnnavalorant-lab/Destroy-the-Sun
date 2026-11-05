package com.sunmod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Habilidades ativas da GUI Melhorias (Surto de Adrenalina, Recarga Instantânea e Ofensiva Crítica) e o bônus
 * permanente de velocidade de movimento. O cliente manda {@link UseAbilityPayload} ao apertar a tecla (Z, X, C por
 * padrão, configuráveis em Controles) e o servidor valida nível, recarga e Sol por perto.
 */
public final class Abilities {
	// ---- Surto de Adrenalina ----
	public static final double ADRENALINE_MOVE_BONUS = 0.30;    // +30% de velocidade de movimento
	public static final double ADRENALINE_ATTACK_BONUS = 0.50;  // +50% de cadência de ataque
	public static final int ADRENALINE_BASE_SECONDS = 4;
	public static final int ADRENALINE_SECONDS_PER_LEVEL = 2;   // duração = 4 + 2 * nível (6, 8, 10, 12 s)
	public static final int ADRENALINE_COOLDOWN_SECONDS = 45;

	// ---- Recarga Instantânea ----
	public static final int RELOAD_COOLDOWN_BASE_SECONDS = 150;
	public static final int RELOAD_COOLDOWN_STEP_SECONDS = 30;  // recarga = 150 - 30 * nível (120, 90, 60 s)

	// ---- Ofensiva Crítica ----
	public static final int WEAK_POINT_BASE_COUNT = 3;
	public static final int WEAK_POINTS_PER_LEVEL = 1;          // pontos = 3 + nível (4 a 7)
	public static final int WEAK_POINT_SECONDS = 20;
	public static final int WEAK_POINT_COOLDOWN_SECONDS = 60;
	public static final float WEAK_POINT_DAMAGE_MULTIPLIER = 3.0F;
	/** Distância máxima até o Sol para a habilidade valer. */
	public static final double SUN_SEARCH_RANGE = 400.0;

	private static final Identifier MOVE_MODIFIER_ID = SunMod.id("upgrade_move_speed");

	/** Tick de jogo em que o Surto de Adrenalina de cada jogador termina (só servidor). */
	private static final Map<UUID, Long> ADRENALINE_END = new HashMap<>();
	/** Tick de jogo em que cada habilidade volta a ficar disponível: chave = "uuid:id" (só servidor). */
	private static final Map<String, Long> READY_AT = new HashMap<>();

	private Abilities() {}

	public static int adrenalineSeconds(int level) {
		return ADRENALINE_BASE_SECONDS + ADRENALINE_SECONDS_PER_LEVEL * level;
	}

	public static int reloadCooldownSeconds(int level) {
		return RELOAD_COOLDOWN_BASE_SECONDS - RELOAD_COOLDOWN_STEP_SECONDS * level;
	}

	public static int weakPointCount(int level) {
		return WEAK_POINT_BASE_COUNT + WEAK_POINTS_PER_LEVEL * level;
	}

	/** O Surto de Adrenalina deste jogador está ativo? (só tem estado no servidor, que é onde o tiro é decidido) */
	public static boolean isAdrenalineActive(Player player) {
		Long end = ADRENALINE_END.get(player.getUUID());
		return end != null && player.level().getGameTime() < end;
	}

	/** Limpa recargas e o Surto de Adrenalina do jogador (usado pelo /sunmod reset). */
	public static void resetState(ServerPlayer player) {
		ADRENALINE_END.remove(player.getUUID());
		READY_AT.keySet().removeIf(k -> k.startsWith(player.getUUID() + ":"));
	}

	/** Chamado todo tick do servidor para cada jogador: mantém o modificador de velocidade de movimento em dia. */
	public static void tickPlayer(ServerPlayer player) {
		double bonus = Upgrades.moveSpeedBonus(player) + (isAdrenalineActive(player) ? ADRENALINE_MOVE_BONUS : 0.0);
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		AttributeModifier current = speed.getModifier(MOVE_MODIFIER_ID);
		if (bonus <= 0.0) {
			if (current != null) {
				speed.removeModifier(MOVE_MODIFIER_ID);
			}
			return;
		}
		if (current == null || Math.abs(current.amount() - bonus) > 1.0E-6) {
			speed.addOrReplacePermanentModifier(
					new AttributeModifier(MOVE_MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** Chamado no servidor quando o jogador aperta a tecla de uma habilidade. Valida tudo. */
	public static void activate(ServerPlayer player, String id) {
		if (!id.equals("adrenaline") && !id.equals("instant_reload") && !id.equals("critical_offense")) {
			return;
		}
		if (player.isSpectator()) {
			return;
		}
		Upgrades.Upgrade up = Upgrades.byId(id);
		int level = Upgrades.getLevel(player, id);
		if (up == null || (level < 1 && !player.getAbilities().instabuild)) {
			player.displayClientMessage(Component.translatable("message.sunmod.upgrade_locked", up != null ? up.name() : id), true);
			return;
		}
		level = Math.max(level, 1);

		ServerLevel serverLevel = (ServerLevel) player.level();
		long now = serverLevel.getGameTime();
		String key = player.getUUID() + ":" + id;
		long ready = READY_AT.getOrDefault(key, 0L);
		if (now < ready) {
			long seconds = (ready - now + 19) / 20;
			player.displayClientMessage(Component.translatable("message.sunmod.ability_cooldown", up.name(), seconds), true);
			return;
		}

		int cooldownSeconds;
		switch (id) {
			case "adrenaline" -> {
				ADRENALINE_END.put(player.getUUID(), now + 20L * adrenalineSeconds(level));
				cooldownSeconds = ADRENALINE_COOLDOWN_SECONDS;
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.8F);
			}
			case "instant_reload" -> {
				refillIce(player);
				cooldownSeconds = reloadCooldownSeconds(level);
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
			}
			default -> { // critical_offense
				SunEntity sun = nearestSun(serverLevel, player);
				if (sun == null) {
					player.displayClientMessage(Component.translatable("message.sunmod.no_sun"), true);
					return; // não gasta a recarga
				}
				sun.activateWeakPoints(serverLevel, weakPointCount(level), 20 * WEAK_POINT_SECONDS, WEAK_POINT_DAMAGE_MULTIPLIER);
				cooldownSeconds = WEAK_POINT_COOLDOWN_SECONDS;
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
			}
		}
		READY_AT.put(key, now + 20L * cooldownSeconds);
		player.displayClientMessage(Component.translatable("message.sunmod.ability_active", up.name()), true);
	}

	/** Enche o inventário de Ice Cubes até a capacidade atual da mochila. */
	private static void refillIce(ServerPlayer player) {
		int missing = IceCubeItem.maxFor(player) - MachineIceEntity.countIceCubes(player);
		while (missing > 0) {
			int n = Math.min(missing, 64);
			ItemStack stack = new ItemStack(ModItems.ICE_CUBE, n);
			player.getInventory().add(stack);
			int added = n - stack.getCount();
			if (added <= 0) {
				break; // inventário cheio
			}
			missing -= added;
		}
	}

	/** O Sol (carregado, não removido, no mesmo mundo) mais perto do jogador, dentro de {@link #SUN_SEARCH_RANGE}. */
	static SunEntity nearestSun(ServerLevel level, Player player) {
		SunEntity best = null;
		double bestDist = SUN_SEARCH_RANGE * SUN_SEARCH_RANGE;
		for (SunEntity sun : SunEntity.activeSuns()) {
			if (sun.isRemoved() || sun.level() != level) {
				continue;
			}
			double d = sun.position().add(0.0, SunEntity.RADIUS, 0.0).distanceToSqr(player.position());
			if (d < bestDist) {
				bestDist = d;
				best = sun;
			}
		}
		return best;
	}
}
