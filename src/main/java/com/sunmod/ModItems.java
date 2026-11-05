package com.sunmod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	private static final ResourceKey<Item> SUN_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("sun_vanilla"));
	private static final ResourceKey<Item> ICE_CUBE_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("ice_cube"));
	private static final ResourceKey<Item> ICE_CUBE_UP_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("ice_cube_upgraded"));
	private static final ResourceKey<Item> MACHINE_ICE_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("machine_ice"));
	private static final ResourceKey<Item> UPGRADER_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("upgrader"));

	/** Item que coloca o Sol no mundo. */
	public static final Item SUN = Registry.register(BuiltInRegistries.ITEM, SUN_KEY,
			new SunItem(new Item.Properties().setId(SUN_KEY).stacksTo(16)));

	/** Item arremessável (segure o botão direito para carregar, solte para lançar). */
	public static final Item ICE_CUBE = Registry.register(BuiltInRegistries.ITEM, ICE_CUBE_KEY,
			new IceCubeItem(new Item.Properties().setId(ICE_CUBE_KEY).stacksTo(64)));

	/** Ice Cube melhorado pelo Upgrader (3x o dano). */
	public static final Item ICE_CUBE_UPGRADED = Registry.register(BuiltInRegistries.ITEM, ICE_CUBE_UP_KEY,
			new IceCubeItem(new Item.Properties().setId(ICE_CUBE_UP_KEY).stacksTo(64)));

	public static final Item MACHINE_ICE = Registry.register(BuiltInRegistries.ITEM, MACHINE_ICE_KEY,
			new StationItem(new Item.Properties().setId(MACHINE_ICE_KEY).stacksTo(16),
					level -> new MachineIceEntity(ModEntities.MACHINE_ICE, level)));

	public static final Item UPGRADER = Registry.register(BuiltInRegistries.ITEM, UPGRADER_KEY,
			new StationItem(new Item.Properties().setId(UPGRADER_KEY).stacksTo(16),
					level -> new UpgraderEntity(ModEntities.UPGRADER, level)));

	public static void register() {
		// Só carrega a classe.
	}

	private ModItems() {}
}
