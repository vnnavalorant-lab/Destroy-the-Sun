package com.sunmod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Item que coloca uma máquina (Machine Ice / Upgrader) no mundo, virada para o jogador. */
public class StationItem extends Item {
	@FunctionalInterface
	public interface Factory {
		StationEntity create(Level level);
	}

	private final Factory factory;

	public StationItem(Properties properties, Factory factory) {
		super(properties);
		this.factory = factory;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

		StationEntity station = this.factory.create(level);
		Vec3 at = Vec3.atBottomCenterOf(pos);
		double y = context.getClickedFace() == Direction.DOWN ? at.y - station.getBbHeight() : at.y;
		station.setPos(at.x, y, at.z);
		if (player != null) {
			station.setYRot(Math.round(player.getYRot() / 90.0F) * 90.0F); // frente do modelo vira para o jogador
		}
		level.addFreshEntity(station);
		level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.5F, 1.2F);

		if (player != null) {
			context.getItemInHand().consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}
}
