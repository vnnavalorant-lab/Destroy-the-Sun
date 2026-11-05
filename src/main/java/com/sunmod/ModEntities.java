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
	private static final ResourceKey<EntityType<?>> MACHINE_ICE_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, SunMod.id("machine_ice"));
	private static final ResourceKey<EntityType<?>> UPGRADER_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, SunMod.id("upgrader"));
	private static final ResourceKey<EntityType<?>> LASER_BEAM_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, SunMod.id("laser_beam"));
	private static final ResourceKey<EntityType<?>> MONEY_COIN_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, SunMod.id("money_coin"));

	/** Sol: caixa de colisão 50 x 50 x 50 blocos (a hitbox real é a esfera externa, raio 25). */
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

	/** Machine Ice (modelo ~3,03 x 2,27 x 1,19 blocos). */
	public static final EntityType<MachineIceEntity> MACHINE_ICE = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, MACHINE_ICE_KEY,
			EntityType.Builder.<MachineIceEntity>of(MachineIceEntity::new, MobCategory.MISC)
					.sized(3.1F, 2.3F)
					.fireImmune()
					.clientTrackingRange(10)
					.updateInterval(20)
					.build(MACHINE_ICE_KEY));

	/** Upgrader (modelo ~2,45 x 2,39 x 1,31 blocos). */
	public static final EntityType<UpgraderEntity> UPGRADER = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, UPGRADER_KEY,
			EntityType.Builder.<UpgraderEntity>of(UpgraderEntity::new, MobCategory.MISC)
					.sized(2.5F, 2.4F)
					.fireImmune()
					.clientTrackingRange(10)
					.updateInterval(20)
					.build(UPGRADER_KEY));

	/** Money Coin que voa do Sol até o jogador. */
	public static final EntityType<MoneyCoinEntity> MONEY_COIN = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, MONEY_COIN_KEY,
			EntityType.Builder.<MoneyCoinEntity>of(MoneyCoinEntity::new, MobCategory.MISC)
					.sized(0.5F, 0.5F)
					.noSave()
					.fireImmune()
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(MONEY_COIN_KEY));

	/** Raio visual do Lazer Ice (só desenho: o dano é feito pelo item). */
	public static final EntityType<LaserBeamEntity> LASER_BEAM = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, LASER_BEAM_KEY,
			EntityType.Builder.<LaserBeamEntity>of(LaserBeamEntity::new, MobCategory.MISC)
					.sized(0.1F, 0.1F)
					.noSave()
					.fireImmune()
					.clientTrackingRange(32)
					.updateInterval(1)
					.build(LASER_BEAM_KEY));

	public static void register() {
		// Só carrega a classe (os campos estáticos acima fazem o registro).
	}

	private ModEntities() {}
}
