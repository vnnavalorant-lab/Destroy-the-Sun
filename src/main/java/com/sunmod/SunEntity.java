package com.sunmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * O Sol: 10x10x10 blocos, fica parado no ar (sem gravidade, nunca se move) e
 * só recebe dano de um {@link IceCubeProjectile}.
 */
public class SunEntity extends Entity {
	/** Vida total do Sol. Cada Ice Cube causa {@link IceCubeProjectile#DAMAGE}. */
	public static final float MAX_HEALTH = 100.0F;

	private float health = MAX_HEALTH;

	public SunEntity(EntityType<? extends SunEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		// Garante que nada (água, empurrões, etc.) tire o Sol de onde foi colocado.
		this.setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// Único dano aceito: o vindo do projétil Ice Cube.
		if (!(source.getDirectEntity() instanceof IceCubeProjectile)) {
			return false;
		}
		this.health -= amount;
		this.playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.6F);
		level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 5.0, this.getZ(),
				40, 3.0, 3.0, 3.0, 0.05);
		if (this.health <= 0.0F) {
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 5.0, this.getZ(),
					300, 4.0, 4.0, 4.0, 0.2);
			this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 4.0F, 0.5F);
			this.discard();
		}
		return true;
	}

	/** Precisa ser "pickable" para que projéteis (e o cursor) possam acertá-lo. */
	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.health = input.getFloatOr("SunHealth", MAX_HEALTH);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putFloat("SunHealth", this.health);
	}
}
