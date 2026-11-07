package com.sunmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Melhorias vendidas na GUI "Melhorias" do Upgrader.
 *
 * <p>Cada melhoria tem um nível (salvo no jogador, ver {@link ModAttachments#UPGRADES}) e um preço que sobe a cada
 * nível: {@code preço(nível) = âncora * crescimento^(nível - nívelDaÂncora)}, arredondado para múltiplo de 5.
 * Os preços "âncora" são os que aparecem no vídeo de referência; ajuste a vontade na lista abaixo.
 *
 * <p>Nomes e descrições vêm do lang: {@code upgrade.sunmod.<id>.name} e {@code upgrade.sunmod.<id>.desc}.
 * Os números das descrições ({@code %s}) saem das constantes abaixo e de {@link Abilities}, então o texto da GUI
 * sempre diz o que realmente acontece no jogo.
 *
 * <p>Onde cada efeito é aplicado:
 * <ul>
 *   <li>attack_speed: {@link AutoFireItem} (Gun Ice / Lazer Ice) e {@link IceCubeItem} (tempo de carga)</li>
 *   <li>cooling_power: dano de todas as armas e do cometa</li>
 *   <li>backpack_capacity: {@link IceCubeItem#maxFor(Player)} (Machine Ice, HUD e recarga)</li>
 *   <li>move_speed: {@link Abilities#tickPlayer} (modificador de atributo)</li>
 *   <li>ice_cube / ice_cannon / cryo_beam / comet_catcher: liberam Ice Cube, Gun Ice, Lazer Ice e Gravity Gun</li>
 *   <li>adrenaline / instant_reload / critical_offense: habilidades ativadas por tecla, ver {@link Abilities}</li>
 * </ul>
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
	 * @param icon        item do Minecraft que representa a melhoria na GUI
	 * @param startLevel  nível inicial (armas já dadas começam em 1/1)
	 * @param anchorLevel nível em que o preço vale {@code anchorPrice}
	 * @param growth      multiplicador do preço a cada nível
	 */
	public record Upgrade(String id, Category category, Item icon, int maxLevel, int startLevel, int anchorLevel, int anchorPrice, double growth) {
		/** Preço para comprar o PRÓXIMO nível, estando em {@code level}. */
		public int price(int level) {
			double raw = this.anchorPrice * Math.pow(this.growth, level - this.anchorLevel);
			return (int) (Math.round(raw / 5.0) * 5L);
		}

		public Component name() {
			return Component.translatable("upgrade.sunmod." + this.id + ".name");
		}

		public Component description() {
			return Component.translatable("upgrade.sunmod." + this.id + ".desc", descriptionArgs(this.id));
		}
	}

	/** Distância máxima (blocos) entre o jogador e um Upgrader para o servidor aceitar uma compra. */
	public static final double MAX_DISTANCE = 12.0;

	// Bônus por nível das estatísticas
	public static final double ATTACK_SPEED_PER_LEVEL = 0.08;  // +8% de cadência por nível
	public static final double COOLING_PER_LEVEL = 0.10;       // +10% de dano no Sol por nível
	public static final int BACKPACK_PER_LEVEL = 8;            // +8 Ice Cubes na mochila por nível
	public static final double MOVE_SPEED_PER_LEVEL = 0.04;    // +4% de velocidade por nível

	public static final List<Upgrade> ALL = List.of(
			// Estatísticas
			new Upgrade("attack_speed", Category.STATS, Items.CLOCK, 15, 0, 5, 170, 1.25),
			new Upgrade("cooling_power", Category.STATS, Items.BLUE_ICE, 15, 0, 9, 1170, 1.25),
			new Upgrade("backpack_capacity", Category.STATS, Items.BUNDLE, 12, 0, 3, 115, 1.30),
			new Upgrade("move_speed", Category.STATS, Items.DIAMOND_BOOTS, 10, 0, 0, 75, 1.35),
			// Armas
			new Upgrade("ice_cube", Category.WEAPONS, Items.ICE, 1, 1, 0, 0, 1.0),
			new Upgrade("ice_cannon", Category.WEAPONS, Items.CROSSBOW, 1, 1, 0, 0, 1.0),
			new Upgrade("cryo_beam", Category.WEAPONS, Items.BEACON, 1, 0, 0, 16500, 1.0),
			new Upgrade("comet_catcher", Category.WEAPONS, Items.FIRE_CHARGE, 1, 0, 0, 38000, 1.0),
			// Habilidades
			new Upgrade("adrenaline", Category.ABILITIES, Items.SUGAR, 4, 0, 0, 3150, 1.8),
			new Upgrade("instant_reload", Category.ABILITIES, Items.ENDER_PEARL, 3, 0, 0, 4500, 1.8),
			new Upgrade("critical_offense", Category.ABILITIES, Items.TARGET, 4, 0, 0, 9900, 1.8));

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

	/** A arma/habilidade está liberada? (nível >= 1; no criativo tudo vale, para facilitar testes). */
	public static boolean isUnlocked(Player player, String id) {
		return player.getAbilities().instabuild || getLevel(player, id) >= 1;
	}

	/** Multiplicador de cadência de tiro/carga: 1 + bônus por nível (+ Surto de Adrenalina, se ativo). */
	public static double attackSpeedMultiplier(Player player) {
		double mult = 1.0 + ATTACK_SPEED_PER_LEVEL * getLevel(player, "attack_speed");
		if (PowerUps.isActive(player, PowerUps.FREQUENCY)) {
			mult *= 1.0 + PowerUps.FREQUENCY_BONUS; // power-up Aumento de Frequência
		}
		return Abilities.isAdrenalineActive(player) ? mult * (1.0 + Abilities.ADRENALINE_ATTACK_BONUS) : mult;
	}

	/** Multiplicador do dano no Sol (resfriamento): 1 + bônus por nível. */
	public static float coolingMultiplier(Player player) {
		float mult = (float) (1.0 + COOLING_PER_LEVEL * getLevel(player, "cooling_power"));
		return PowerUps.isActive(player, PowerUps.COOLING_X2) ? mult * PowerUps.COOLING_MULTIPLIER : mult; // power-up 2X Resfriamento
	}

	/** Bônus permanente de velocidade de movimento (0,04 = +4%). */
	public static double moveSpeedBonus(Player player) {
		return MOVE_SPEED_PER_LEVEL * getLevel(player, "move_speed");
	}

	/** Valores ({@code %s}) usados nas descrições do lang, sempre iguais aos do jogo. */
	private static Object[] descriptionArgs(String id) {
		Upgrade up = byId(id);
		return switch (id) {
			case "attack_speed" -> new Object[] {percent(ATTACK_SPEED_PER_LEVEL)};
			case "cooling_power" -> new Object[] {percent(COOLING_PER_LEVEL)};
			case "backpack_capacity" -> new Object[] {BACKPACK_PER_LEVEL,
					IceCubeItem.MAX_IN_INVENTORY + BACKPACK_PER_LEVEL * up.maxLevel()};
			case "move_speed" -> new Object[] {percent(MOVE_SPEED_PER_LEVEL)};
			case "ice_cube" -> new Object[] {(int) IceCubeProjectile.DAMAGE};
			case "ice_cannon" -> new Object[] {(int) GunIceItem.DAMAGE, GunIceItem.COIN_VALUE};
			case "cryo_beam" -> new Object[] {(int) LazerIceItem.DAMAGE, LazerIceItem.COIN_VALUE};
			case "comet_catcher" -> new Object[] {(int) CometEntity.DAMAGE, CometEntity.COIN_VALUE};
			case "adrenaline" -> new Object[] {percent(Abilities.ADRENALINE_MOVE_BONUS),
					percent(Abilities.ADRENALINE_ATTACK_BONUS), Abilities.adrenalineSeconds(1),
					Abilities.ADRENALINE_SECONDS_PER_LEVEL};
			case "instant_reload" -> new Object[] {Abilities.reloadCooldownSeconds(1), Abilities.RELOAD_COOLDOWN_STEP_SECONDS};
			case "critical_offense" -> new Object[] {Abilities.weakPointCount(1), Abilities.WEAK_POINTS_PER_LEVEL,
					Abilities.WEAK_POINT_SECONDS, (int) Abilities.WEAK_POINT_DAMAGE_MULTIPLIER};
			default -> new Object[0];
		};
	}

	private static int percent(double fraction) {
		return (int) Math.round(fraction * 100.0);
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
