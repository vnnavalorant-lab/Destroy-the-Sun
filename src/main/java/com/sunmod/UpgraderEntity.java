package com.sunmod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Upgrader: por enquanto só decorativo (não faz nada ao clicar). Um soco remove e devolve o item. */
public class UpgraderEntity extends StationEntity {
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
	public boolean isUsable() {
		return false;
	}

	@Override
	public void onUse(Player player, ServerLevel level) {
		// Sem função (ainda).
	}
}
