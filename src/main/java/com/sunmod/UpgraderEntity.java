package com.sunmod;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Upgrader: botão direito abre a GUI "Melhorias" (comprar melhorias com o dinheiro). Um soco remove e devolve o item. */
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
		return true;
	}

	@Override
	public void onUse(Player player, ServerLevel level) {
		if (player instanceof ServerPlayer serverPlayer) {
			ServerPlayNetworking.send(serverPlayer, new OpenUpgraderPayload());
		}
	}
}
