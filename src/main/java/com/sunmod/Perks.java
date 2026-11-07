package com.sunmod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Benefícios da tela "Escolher Benefício" (aparece ao concluir um Evento de Instabilidade). Cada escolha soma +1 na
 * pilha do benefício e dura para sempre (salvo no jogador, {@link ModAttachments#PERKS}).
 * <ul>
 *   <li>double_dose (Dose Dupla): ao coletar um fragmento, 25% de chance (por pilha) de ganhar um segundo power-up.</li>
 *   <li>power_echo (Eco de Poder): ao coletar um fragmento, +3 s em todos os power-ups ativos (por pilha).</li>
 *   <li>destruction_force (Força de Destruição): +20% de dano nos inimigos do evento (por pilha).</li>
 * </ul>
 */
public final class Perks {
	public static final String DOUBLE_DOSE = "double_dose";
	public static final String POWER_ECHO = "power_echo";
	public static final String DESTRUCTION_FORCE = "destruction_force";
	public static final List<String> ALL = List.of(DOUBLE_DOSE, POWER_ECHO, DESTRUCTION_FORCE);

	public static final double DOUBLE_DOSE_CHANCE = 0.25;
	public static final int ECHO_SECONDS = 3;
	public static final double DESTRUCTION_BONUS = 0.20;

	/** Jogadores que acabaram de concluir um evento e ainda podem escolher (só servidor). */
	private static final Set<UUID> PENDING = new HashSet<>();

	private Perks() {}

	public static int stacks(Player player, String id) {
		return player.getAttachedOrElse(ModAttachments.PERKS, Map.<String, Integer>of()).getOrDefault(id, 0);
	}

	public static void markPending(ServerPlayer player) {
		PENDING.add(player.getUUID());
	}

	public static void clearAllPending() {
		PENDING.clear();
	}

	/** Chamado quando o cliente escolhe um benefício; só vale uma vez por evento concluído. */
	public static void pick(ServerPlayer player, String id) {
		if (!ALL.contains(id) || !PENDING.remove(player.getUUID())) {
			return;
		}
		Map<String, Integer> next = new HashMap<>(player.getAttachedOrElse(ModAttachments.PERKS, Map.<String, Integer>of()));
		next.merge(id, 1, Integer::sum);
		player.setAttached(ModAttachments.PERKS, next);
	}
}
