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

/** Máquina de resfriamento: fornece Ice Cubes a quem clica com o botão direito. */
public class MachineIceEntity extends StationEntity {
	/** Ice Cubes entregues por uso. */
	public static final int ICE_PER_USE = 16;
	/** Tempo de resfriamento entre usos (60 ticks = 3 s). */
	public static final int COOLDOWN_TICKS = 60;

	private static final DetailedHitbox HITBOX = DetailedHitbox.load("machine_ice");

	public MachineIceEntity(EntityType<? extends MachineIceEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected DetailedHitbox hitbox() {
		return HITBOX;
	}

	@Override
	protected Item dropItem() {
		return ModItems.MACHINE_ICE;
	}

	@Override
	public void onUse(Player player, ServerLevel level) {
		if (this.cooldown > 0) {
			player.displayClientMessage(Component.translatable("message.sunmod.machine_cooling"), true);
			return;
		}
		ItemStack stack = new ItemStack(ModItems.ICE_CUBE, ICE_PER_USE);
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		this.cooldown = COOLDOWN_TICKS;
		level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
		level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 1.2, this.getZ(), 12, 0.6, 0.3, 0.3, 0.02);
	}
}
