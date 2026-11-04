package com.sunmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** O Ice Cube em voo. Causa dano apenas no {@link SunEntity}. */
public class IceCubeProjectile extends ThrowableItemProjectile {
	public static final float DAMAGE = 5.0F;

	public IceCubeProjectile(EntityType<? extends IceCubeProjectile> type, Level level) {
		super(type, level);
	}

	public IceCubeProjectile(Level level, LivingEntity owner, ItemStack stack) {
		super(ModEntities.ICE_CUBE_PROJECTILE, owner, level, stack);
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.ICE_CUBE;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (this.level() instanceof ServerLevel level && hit.getEntity() instanceof SunEntity sun) {
			sun.hurtServer(level, this.damageSources().thrown(this, this.getOwner()), DAMAGE);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(), 12, 0.2, 0.2, 0.2, 0.05);
			level.playSound(null, this.getX(), this.getY(), this.getZ(),
					SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 0.6F, 1.4F);
			this.discard();
		}
	}
}
