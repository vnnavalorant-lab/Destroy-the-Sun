package com.sunmod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Gun Ice: tiros de gelo sem carregar, um atrás do outro enquanto o botão estiver apertado.
 * Tira 2x a vida do Sol que um Ice Cube e rende 2x mais dinheiro por acerto.
 */
public class GunIceItem extends AutoFireItem {
	public static final float DAMAGE = IceCubeProjectile.DAMAGE * 2;      // 10
	public static final int COIN_VALUE = MoneyCoinEntity.VALUE * 2;       // $6
	public static final int FIRE_INTERVAL_TICKS = 5;                      // 4 tiros por segundo
	public static final float BULLET_SPEED = 4.0F;
	public static final int BULLET_LIFE_TICKS = 100;                      // ~400 blocos de alcance

	public GunIceItem(Properties properties) {
		super(properties);
	}

	@Override
	protected int fireInterval() {
		return FIRE_INTERVAL_TICKS;
	}

	@Override
	protected String upgradeId() {
		return "ice_cannon";
	}

	@Override
	protected void fire(ServerLevel level, Player player, ItemStack weapon) {
		IceCubeProjectile bullet = new IceCubeProjectile(level, player, new ItemStack(ModItems.ICE_CUBE))
				.asBullet(DAMAGE * Upgrades.coolingMultiplier(player), COIN_VALUE, BULLET_LIFE_TICKS);
		bullet.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, BULLET_SPEED, 0.0F);
		level.addFreshEntity(bullet);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.7F, 1.6F);
	}
}
