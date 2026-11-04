package com.sunmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Clique direito em um bloco para colocar o Sol (50x50x50) no mundo. */
public class SunItem extends Item {
	public SunItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		double y = pos.getY();
		if (context.getClickedFace() == Direction.DOWN) {
			y -= 49.0; // clicou no teto de algo: coloca o Sol abaixo
		}

		SunEntity sun = new SunEntity(ModEntities.SUN, level);
		sun.setPos(pos.getX() + 0.5, y, pos.getZ() + 0.5);
		level.addFreshEntity(sun);

		if (context.getPlayer() != null) {
			context.getItemInHand().consume(1, context.getPlayer());
		}
		return InteractionResult.SUCCESS;
	}
}
