package com.sunmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Impulsos de Poder: estourar um Fragmento Solar dá um power-up aleatório por {@link #DURATION_SECONDS} s.
 * <ul>
 *   <li>frequency (Aumento de Frequência): todas as armas operam {@link #FREQUENCY_BONUS} mais rápido.</li>
 *   <li>cooling_x2 (2X Resfriamento): o resfriamento (dano) causado ao Sol é dobrado.</li>
 * </ul>
 * Os benefícios {@link Perks} podem dar um segundo power-up (Dose Dupla) ou estender os ativos (Eco de Poder).
 * O estado fica só no servidor; o cliente recebe {@link PowerUpPayload} para o popup e a lista da HUD.
 */
public final class PowerUps {
	public static final String FREQUENCY = "frequency";
	public static final String COOLING_X2 = "cooling_x2";
	public static final List<String> ALL = List.of(FREQUENCY, COOLING_X2);

	public static final int DURATION_SECONDS = 12;
	public static final double FREQUENCY_BONUS = 0.75;   // +75% de cadência
	public static final float COOLING_MULTIPLIER = 2.0F; // dano x2

	/** jogador -> (id do power-up -> tick de jogo em que acaba) */
	private static final Map<UUID, Map<String, Long>> ACTIVE = new HashMap<>();

	private PowerUps() {}

	public static boolean isActive(Player player, String id) {
		Map<String, Long> map = ACTIVE.get(player.getUUID());
		Long end = map == null ? null : map.get(id);
		return end != null && player.level().getGameTime() < end;
	}

	public static void clear(ServerPlayer player) {
		ACTIVE.remove(player.getUUID());
	}

	public static void clearAll() {
		ACTIVE.clear();
	}

	/** Dá um power-up aleatório (mais um segundo, se o benefício Dose Dupla sortear) e aplica o Eco de Poder. */
	public static void grantRandom(ServerPlayer player) {
		var random = player.getRandom();
		give(player, ALL.get(random.nextInt(ALL.size())), DURATION_SECONDS * 20, true);

		int doseStacks = Perks.stacks(player, Perks.DOUBLE_DOSE);
		for (int i = 0; i < doseStacks; i++) {
			if (random.nextDouble() < Perks.DOUBLE_DOSE_CHANCE) {
				give(player, ALL.get(random.nextInt(ALL.size())), DURATION_SECONDS * 20, true);
				break; // um segundo no máximo por fragmento
			}
		}

		int echoStacks = Perks.stacks(player, Perks.POWER_ECHO);
		if (echoStacks > 0) {
			long now = player.level().getGameTime();
			Map<String, Long> map = ACTIVE.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
			for (Map.Entry<String, Long> e : map.entrySet()) {
				if (e.getValue() > now) {
					e.setValue(e.getValue() + 20L * Perks.ECHO_SECONDS * echoStacks);
					ServerPlayNetworking.send(player, new PowerUpPayload(e.getKey(), (int) (e.getValue() - now), false));
				}
			}
		}
	}

	/** Liga (ou renova) um power-up por {@code ticks} e avisa o cliente. */
	private static void give(ServerPlayer player, String id, int ticks, boolean popup) {
		long now = player.level().getGameTime();
		Map<String, Long> map = ACTIVE.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
		long end = Math.max(now, map.getOrDefault(id, 0L)) + ticks;
		map.put(id, end);
		ServerPlayNetworking.send(player, new PowerUpPayload(id, (int) (end - now), popup));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
	}
}
