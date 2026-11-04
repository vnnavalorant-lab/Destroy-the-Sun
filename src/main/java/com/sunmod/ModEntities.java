package com.sunmod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ResourceKey<EntityType<?>> SUN_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, SunMod.id("sun_vanilla"));
	private static final ResourceKey<EntityType<?>> ICE_CUBE_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, SunMod.id("ice_cube"));

	/** Sol: caixa de colisão 50 x 50 x 50 blocos. */
	public static final EntityType<SunEntity> SUN = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, SUN_KEY,
			EntityType.Builder.<SunEntity>of(SunEntity::new, MobCategory.MISC)
					.sized(50.0F, 50.0F)
					.fireImmune()
					.clientTrackingRange(32)
					.updateInterval(20)
					.build(SUN_KEY));

	/** Projétil do Ice Cube. */
	public static final EntityType<IceCubeProjectile> ICE_CUBE_PROJECTILE = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, ICE_CUBE_KEY,
			EntityType.Builder.<IceCubeProjectile>of(IceCubeProjectile::new, MobCategory.MISC)
					.sized(0.25F, 0.25F)
					.clientTrackingRange(4)
					.updateInterval(10)
					.build(ICE_CUBE_KEY));

	public static void register() {
		// Só carrega a classe (os campos estáticos acima fazem o registro).
	}

	private ModEntities() {}
}
