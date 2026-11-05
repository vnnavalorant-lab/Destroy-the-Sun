package com.sunmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Upgrader: transforma os Ice Cubes da mão principal em Upgraded Ice Cubes (mais dano no Sol). */
public class UpgraderEntity extends StationEntity {
	/** Máximo de Ice Cubes melhorados por uso. */
	public static final int MAX_PER_USE = 16;
	public static final int COOLDOWN_TICKS = 10;

	private static final DetailedHitbox HITBOX = DetailedHitbox.load("upgrader");

	public UpgraderEntity(EntityType<? extends UpgraderEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected DetailedHitbox hitbox() {
		return HITBOX;
	}

	@Override
	protected Item dropItem() {
		return ModItems.UPGRADER;
	}

	@Override
	public void onUse(Player player, ServerLevel level) {
		if (this.cooldown > 0) {
			player.displayClientMessage(Component.translatable("message.sunmod.upgrader_cooling"), true);
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (!held.is(ModItems.ICE_CUBE)) {
			player.displayClientMessage(Component.translatable("message.sunmod.upgrader_hint"), true);
			return;
		}
		int amount = Math.min(held.getCount(), MAX_PER_USE);
		held.consume(amount, player);

		ItemStack upgraded = new ItemStack(ModItems.ICE_CUBE_UPGRADED, amount);
		if (!player.getInventory().add(upgraded)) {
			player.drop(upgraded, false);
		}
		this.cooldown = COOLDOWN_TICKS;
		level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(),
				SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.4F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 1.6, this.getZ(), 20, 0.5, 0.3, 0.3, 0.1);
	}
}
