package com.sunmod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	private static final ResourceKey<Item> SUN_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("sun_vanilla"));
	private static final ResourceKey<Item> ICE_CUBE_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("ice_cube"));
	private static final ResourceKey<Item> MACHINE_ICE_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("machine_ice"));
	private static final ResourceKey<Item> GUN_ICE_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("gun_ice"));
	private static final ResourceKey<Item> LAZER_ICE_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("lazer_ice"));
	private static final ResourceKey<Item> GRAVITY_GUN_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("gravity_gun"));
	private static final ResourceKey<Item> SOLAR_SPARK_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("solar_spark"));
	private static final ResourceKey<Item> SOLAR_FRAGMENT_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("solar_fragment"));
	private static final ResourceKey<Item> UPGRADER_KEY = ResourceKey.create(Registries.ITEM, SunMod.id("upgrader"));

	/** Item que coloca o Sol no mundo. */
	public static final Item SUN = Registry.register(BuiltInRegistries.ITEM, SUN_KEY,
			new SunItem(new Item.Properties().setId(SUN_KEY).stacksTo(16)));

	/** Item arremessável (segure o botão direito para carregar, solte para lançar). */
	public static final Item ICE_CUBE = Registry.register(BuiltInRegistries.ITEM, ICE_CUBE_KEY,
			new IceCubeItem(new Item.Properties().setId(ICE_CUBE_KEY).stacksTo(64)));

	public static final Item MACHINE_ICE = Registry.register(BuiltInRegistries.ITEM, MACHINE_ICE_KEY,
			new StationItem(new Item.Properties().setId(MACHINE_ICE_KEY).stacksTo(16),
					level -> new MachineIceEntity(ModEntities.MACHINE_ICE, level)));

	public static final Item UPGRADER = Registry.register(BuiltInRegistries.ITEM, UPGRADER_KEY,
			new StationItem(new Item.Properties().setId(UPGRADER_KEY).stacksTo(16),
					level -> new UpgraderEntity(ModEntities.UPGRADER, level)));

	/** Atira Ice Cubes sem carregar (2x dano e 2x dinheiro). */
	public static final Item GUN_ICE = Registry.register(BuiltInRegistries.ITEM, GUN_ICE_KEY,
			new GunIceItem(new Item.Properties().setId(GUN_ICE_KEY).stacksTo(1)));

	/** Raio contínuo (4x dano e 4x dinheiro). */
	public static final Item LAZER_ICE = Registry.register(BuiltInRegistries.ITEM, LAZER_ICE_KEY,
			new LazerIceItem(new Item.Properties().setId(LAZER_ICE_KEY).stacksTo(1)));

	/** Capturador de Cometas: captura um cometa e o lança no Sol (precisa da melhoria comprada). */
	public static final Item GRAVITY_GUN = Registry.register(BuiltInRegistries.ITEM, GRAVITY_GUN_KEY,
			new GravityGunItem(new Item.Properties().setId(GRAVITY_GUN_KEY).stacksTo(1)));

	/** Só o desenho do Fragmento Solar voando (brilho branco); não está no inventário criativo. */
	public static final Item SOLAR_SPARK = Registry.register(BuiltInRegistries.ITEM, SOLAR_SPARK_KEY,
			new Item(new Item.Properties().setId(SOLAR_SPARK_KEY)));

	/** Só o desenho do Fragmento Solar no chão (estrela dourada); não está no inventário criativo. */
	public static final Item SOLAR_FRAGMENT = Registry.register(BuiltInRegistries.ITEM, SOLAR_FRAGMENT_KEY,
			new Item(new Item.Properties().setId(SOLAR_FRAGMENT_KEY)));

	public static void register() {
		// Só carrega a classe.
	}

	private ModItems() {}
}
