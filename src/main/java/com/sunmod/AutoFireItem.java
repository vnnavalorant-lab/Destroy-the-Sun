package com.sunmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Base das armas de tiro contínuo (Gun Ice e Lazer Ice): sem carregar, dispara de {@link #fireInterval()}
 * em {@link #fireInterval()} ticks enquanto o jogador segurar o botão direito, e para ao soltar.
 * Cada disparo gasta {@link #AMMO_PER_SHOT} Ice Cube(s) do inventário (no criativo não gasta).
 */
public abstract class AutoFireItem extends Item {
	/** Ice Cubes gastos por disparo. Coloque 0 para munição infinita. */
	public static final int AMMO_PER_SHOT = 1;

	private static final int USE_DURATION = 72000;

	protected AutoFireItem(Properties properties) {
		super(properties);
	}

	/** Ticks entre dois disparos. */
	protected abstract int fireInterval();

	/** Faz um disparo (só roda no servidor, com munição já descontada). */
	protected abstract void fire(ServerLevel level, Player player, ItemStack weapon);

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (AMMO_PER_SHOT > 0 && !IceCubeItem.hasAny(player)) {
			player.displayClientMessage(Component.translatable("message.sunmod.no_ammo"), true);
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return USE_DURATION;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(level instanceof ServerLevel serverLevel) || !(user instanceof Player player)) {
			return;
		}
		int elapsed = USE_DURATION - remainingUseTicks;
		if (elapsed % this.fireInterval() != 0) {
			return;
		}
		if (AMMO_PER_SHOT > 0) {
			for (int i = 0; i < AMMO_PER_SHOT; i++) {
				if (!IceCubeItem.consumeOne(player)) {
					player.displayClientMessage(Component.translatable("message.sunmod.no_ammo"), true);
					player.stopUsingItem();
					return;
				}
			}
		}
		this.fire(serverLevel, player, stack);
	}
}
