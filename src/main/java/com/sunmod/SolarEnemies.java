package com.sunmod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Inimigos do Evento de Instabilidade. Em vez de modelos próprios usam mobs do Minecraft (sem precisar de mod no
 * cliente), marcados com {@link Instability#ENEMY_TAG}:
 * <ul>
 *   <li><b>Ghoul Solar</b>: Husk de armadura dourada que corre atrás do jogador.</li>
 *   <li><b>Olho Solar</b>: Blaze que flutua e lança bolas de fogo.</li>
 * </ul>
 * Nome e barra de vida ficam em cima da cabeça. Só as armas do mod os machucam bem (ignoram armadura): ver {@link #damage}.
 */
public final class SolarEnemies {
	public static final float GHOUL_HEALTH = 40.0F;
	public static final float EYE_HEALTH = 30.0F;
	public static final float GHOUL_PROGRESS = 7.0F;
	public static final float EYE_PROGRESS = 9.0F;
	public static final int COIN_COUNT = 3;
	public static final int COIN_VALUE = 30;
	private static final int BAR_SEGMENTS = 10;

	private SolarEnemies() {}

	/** Cria um inimigo em {@code pos}. {@code eye} = Olho Solar, senão Ghoul Solar. Devolve null se falhar. */
	public static Mob spawn(ServerLevel level, Vec3 pos, boolean eye, double healthMultiplier) {
		Mob mob = eye ? EntityType.BLAZE.create(level, EntitySpawnReason.EVENT)
				: EntityType.HUSK.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		mob.setPos(pos.x, pos.y, pos.z);
		mob.addTag(Instability.ENEMY_TAG);
		mob.setPersistenceRequired();
		double health = (eye ? EYE_HEALTH : GHOUL_HEALTH) * healthMultiplier;
		if (mob.getAttribute(Attributes.MAX_HEALTH) != null) {
			mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		}
		mob.setHealth((float) health);
		if (!eye) { // Ghoul: armadura dourada completa (visual), sem drop
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
			mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
			mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
			mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
					EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
				mob.setDropChance(slot, 0.0F);
			}
		}
		mob.setCustomNameVisible(true);
		updateName(mob);
		level.addFreshEntity(mob);
		return mob;
	}

	public static boolean isEnemy(net.minecraft.world.entity.Entity entity) {
		return entity.getTags().contains(Instability.ENEMY_TAG);
	}

	private static boolean isEye(Mob mob) {
		return mob.getType() == EntityType.BLAZE;
	}

	/** Nome branco + barra de vida vermelha (10 traços) em cima do inimigo. */
	public static void updateName(Mob mob) {
		float max = mob.getMaxHealth();
		int filled = max <= 0.0F ? 0 : Math.max(mob.getHealth() > 0.0F ? 1 : 0, Math.round(BAR_SEGMENTS * mob.getHealth() / max));
		filled = Math.min(BAR_SEGMENTS, filled);
		MutableComponent bar = Component.literal("|".repeat(filled)).withStyle(ChatFormatting.RED)
				.append(Component.literal("|".repeat(BAR_SEGMENTS - filled)).withStyle(ChatFormatting.DARK_GRAY));
		mob.setCustomName(Component.translatable(isEye(mob) ? "entity.sunmod.solar_eye" : "entity.sunmod.solar_ghoul")
				.withStyle(ChatFormatting.WHITE).append(Component.literal(" ")).append(bar));
	}

	/** Dano das armas do mod num inimigo: ignora armadura e leva o bônus do benefício Força de Destruição. */
	public static void damage(ServerLevel level, LivingEntity enemy, Player shooter, float amount) {
		float mult = (float) (1.0 + Perks.DESTRUCTION_BONUS * Perks.stacks(shooter, Perks.DESTRUCTION_FORCE));
		enemy.invulnerableTime = 0; // várias armas acertam em sequência rápida
		enemy.hurtServer(level, level.damageSources().indirectMagic(shooter, shooter), amount * mult);
	}

	/**
	 * Chamado quando um inimigo ia morrer (a morte normal é cancelada para não soltar drops): dá moedas e progresso a
	 * quem matou e remove o inimigo.
	 */
	public static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer sp ? sp : null;
		boolean eye = entity.getType() == EntityType.BLAZE;
		Vec3 at = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
		if (killer != null) {
			for (int i = 0; i < COIN_COUNT; i++) {
				MoneyCoinEntity.spawn(level, at.add((level.getRandom().nextDouble() - 0.5) * 1.5, 0.0,
						(level.getRandom().nextDouble() - 0.5) * 1.5), killer, COIN_VALUE);
			}
		}
		FxEntity.spawn(level, at, FxEntity.BLAST, 2.6F, 14, 0xFFB040); // bola de fogo no lugar da morte
		Instability.addProgress(eye ? EYE_PROGRESS : GHOUL_PROGRESS);
		entity.discard();
	}
}
