package com.sunmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Melhorias vendidas na GUI "Melhorias" do Upgrader.
 *
 * <p>Cada melhoria tem um nível (salvo no jogador, ver {@link ModAttachments#UPGRADES}) e um preço que sobe a cada
 * nível: {@code preço(nível) = âncora * crescimento^(nível - nívelDaÂncora)}, arredondado para múltiplo de 5.
 * Os preços "âncora" são os que aparecem no vídeo de referência; ajuste a vontade na lista abaixo.
 *
 * <p>Nomes e descrições vêm do lang: {@code upgrade.sunmod.<id>.name} e {@code upgrade.sunmod.<id>.desc}.
 * Os efeitos de jogo (ex.: atirar mais rápido) ainda NÃO estão ligados: use {@link #getLevel(Player, String)}
 * nos itens/entidades para aplicar cada um.
 */
public final class Upgrades {
	public enum Category {
		STATS("stats"), WEAPONS("weapons"), ABILITIES("abilities");

		public final String key;

		Category(String key) {
			this.key = key;
		}
	}

	/**
	 * @param startLevel  nível inicial (armas já dadas começam em 1/1)
	 * @param anchorLevel nível em que o preço vale {@code anchorPrice}
	 * @param growth      multiplicador do preço a cada nível
	 */
	public record Upgrade(String id, Category category, int maxLevel, int startLevel, int anchorLevel, int anchorPrice, double growth) {
		/** Preço para comprar o PRÓXIMO nível, estando em {@code level}. */
		public int price(int level) {
			double raw = this.anchorPrice * Math.pow(this.growth, level - this.anchorLevel);
			return (int) (Math.round(raw / 5.0) * 5L);
		}

		public Component name() {
			return Component.translatable("upgrade.sunmod." + this.id + ".name");
		}

		public Component description() {
			return Component.translatable("upgrade.sunmod." + this.id + ".desc");
		}
	}

	/** Distância máxima (blocos) entre o jogador e um Upgrader para o servidor aceitar uma compra. */
	public static final double MAX_DISTANCE = 12.0;

	public static final List<Upgrade> ALL = List.of(
			// Estatísticas
			new Upgrade("attack_speed", Category.STATS, 15, 0, 5, 170, 1.25),
			new Upgrade("cooling_power", Category.STATS, 15, 0, 9, 1170, 1.25),
			new Upgrade("backpack_capacity", Category.STATS, 12, 0, 3, 115, 1.30),
			new Upgrade("move_speed", Category.STATS, 10, 0, 0, 75, 1.35),
			// Armas
			new Upgrade("ice_cube", Category.WEAPONS, 1, 1, 0, 0, 1.0),
			new Upgrade("ice_cannon", Category.WEAPONS, 1, 1, 0, 0, 1.0),
			new Upgrade("cryo_beam", Category.WEAPONS, 1, 0, 0, 16500, 1.0),
			new Upgrade("comet_catcher", Category.WEAPONS, 1, 0, 0, 38000, 1.0),
			// Habilidades
			new Upgrade("adrenaline", Category.ABILITIES, 4, 0, 0, 3150, 1.8),
			new Upgrade("instant_reload", Category.ABILITIES, 3, 0, 0, 4500, 1.8),
			new Upgrade("critical_offense", Category.ABILITIES, 4, 0, 0, 9900, 1.8));

	public static List<Upgrade> of(Category category) {
		return ALL.stream().filter(u -> u.category() == category).toList();
	}

	public static Upgrade byId(String id) {
		for (Upgrade u : ALL) {
			if (u.id().equals(id)) {
				return u;
			}
		}
		return null;
	}

	/** Nível atual do jogador nessa melhoria (funciona no cliente e no servidor). */
	public static int getLevel(Player player, String id) {
		Upgrade up = byId(id);
		if (up == null) {
			return 0;
		}
		Map<String, Integer> levels = player.getAttachedOrElse(ModAttachments.UPGRADES, Map.of());
		return levels.getOrDefault(id, up.startLevel());
	}

	/** Chamado no servidor quando o cliente pede para comprar uma melhoria. Valida tudo de novo. */
	public static void tryBuy(ServerPlayer player, String id) {
		Upgrade up = byId(id);
		if (up == null || player.isSpectator()) {
			return;
		}
		boolean nearUpgrader = !player.level()
				.getEntitiesOfClass(UpgraderEntity.class, player.getBoundingBox().inflate(MAX_DISTANCE)).isEmpty();
		if (!nearUpgrader) {
			return;
		}

		int level = getLevel(player, id);
		if (level >= up.maxLevel()) {
			return;
		}
		int price = up.price(level);
		int money = player.getAttachedOrElse(ModAttachments.MONEY, 0);
		if (money < price) {
			player.displayClientMessage(Component.translatable("message.sunmod.not_enough_money"), true);
			return;
		}

		player.setAttached(ModAttachments.MONEY, money - price);
		// Sempre cria um mapa novo: só assim o anexo é marcado como alterado e sincronizado com o cliente.
		Map<String, Integer> levels = new HashMap<>(player.getAttachedOrElse(ModAttachments.UPGRADES, Map.of()));
		levels.put(id, level + 1);
		player.setAttached(ModAttachments.UPGRADES, levels);

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	private Upgrades() {}
}
