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
 *
 * <p>A cadência real é {@code 1 / fireInterval()} vezes {@link Upgrades#attackSpeedMultiplier} (melhoria Velocidade
 * de Ataque e Surto de Adrenalina), e a arma só funciona se a melhoria dela estiver comprada ({@link #upgradeId()}).
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

	/** Id da melhoria que libera esta arma (ver {@link Upgrades}). */
	protected abstract String upgradeId();

	/** Faz um disparo (só roda no servidor, com munição já descontada). */
	protected abstract void fire(ServerLevel level, Player player, ItemStack weapon);

	/** Chamado a CADA tick em que o botão está apertado (o Lazer Ice usa para manter o raio contínuo). */
	protected void holdTick(ServerLevel level, Player player, ItemStack weapon) {
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!Upgrades.isUnlocked(player, this.upgradeId())) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.sunmod.upgrade_locked",
						Upgrades.byId(this.upgradeId()).name()), true);
			}
			return InteractionResult.FAIL;
		}
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
		this.holdTick(serverLevel, player, stack);
		int elapsed = USE_DURATION - remainingUseTicks;
		// Cadência com bônus: dispara quando a contagem (ticks * disparos por tick) passa para o próximo inteiro.
		double perTick = Upgrades.attackSpeedMultiplier(player) / this.fireInterval();
		boolean shootNow = elapsed == 0 || Math.floor(elapsed * perTick) > Math.floor((elapsed - 1) * perTick);
		if (!shootNow) {
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
