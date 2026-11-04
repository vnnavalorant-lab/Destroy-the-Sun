package com.sunmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.resources.Identifier;

public class SunMod implements ModInitializer {
	public static final String MOD_ID = "sunmod";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModEntities.register();
		ModItems.register();

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
			entries.accept(ModItems.SUN);
			entries.accept(ModItems.ICE_CUBE);
		});
	}
}
