package com.sunmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;

public class SunMod implements ModInitializer {
	public static final String MOD_ID = "sunmod";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModAttachments.register();
		ModEntities.register();
		ModItems.register();

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
			entries.accept(ModItems.SUN);
			entries.accept(ModItems.MACHINE_ICE);
			entries.accept(ModItems.UPGRADER);
			entries.accept(ModItems.ICE_CUBE);
			entries.accept(ModItems.GUN_ICE);
			entries.accept(ModItems.LAZER_ICE);
			entries.accept(ModItems.GRAVITY_GUN);
		});

		// Botão direito em uma máquina: só vale se o olhar acertar o modelo (hitbox detalhada).
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(entity instanceof StationEntity station) || !station.isUsable() || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (hand != InteractionHand.MAIN_HAND || !station.isLookedAt(player)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel serverLevel) {
				station.onUse(player, serverLevel);
			}
			return InteractionResult.SUCCESS;
		});
	}
}
