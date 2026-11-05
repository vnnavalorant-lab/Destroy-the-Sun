package com.sunmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** O Ice Cube em voo. Causa dano apenas no {@link SunEntity}. */
public class IceCubeProjectile extends ThrowableItemProjectile {
	public static final float DAMAGE = 5.0F;

	/** Dano e dinheiro deste projétil (o Ice Cube usa os padrões; a Gun Ice troca por 2x). */
	private float damage = DAMAGE;
	private int coinValue = MoneyCoinEntity.VALUE;
	/** Vida máxima em ticks (0 = sem limite). Os tiros da Gun Ice somem sozinhos. */
	private int maxLife = 0;

	public IceCubeProjectile(EntityType<? extends IceCubeProjectile> type, Level level) {
		super(type, level);
	}

	public IceCubeProjectile(Level level, LivingEntity owner, ItemStack stack) {
		super(ModEntities.ICE_CUBE_PROJECTILE, owner, level, stack);
	}

	public float getDamage() {
		return this.damage;
	}

	public int getCoinValue() {
		return this.coinValue;
	}

	/** Multiplica o dano deste projétil (bônus de Poder de Resfriamento). */
	public void scaleDamage(float multiplier) {
		this.damage *= multiplier;
	}

	/** Configura como tiro de arma: dano, dinheiro por acerto, sem gravidade e com tempo de vida. */
	public IceCubeProjectile asBullet(float damage, int coinValue, int maxLife) {
		this.damage = damage;
		this.coinValue = coinValue;
		this.maxLife = maxLife;
		this.setNoGravity(true);
		return this;
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.ICE_CUBE;
	}

	/** Máquinas não são alvo do projétil: ele passa por elas. */
	@Override
	protected boolean canHitEntity(Entity target) {
		return !(target instanceof StationEntity) && !(target instanceof CometEntity) && super.canHitEntity(target);
	}

	@Override
	public void tick() {
		if (this.maxLife > 0 && this.tickCount > this.maxLife && !this.level().isClientSide()) {
			this.discard();
			return;
		}
		// Colisão com o Sol por esfera (hitbox do tamanho do modelo), antes de mover.
		if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
			Vec3 from = this.position();
			Vec3 to = from.add(this.getDeltaMovement());
			for (SunEntity sun : SunEntity.activeSuns()) {
				if (!sun.isRemoved() && sun.level() == this.level() && sun.hitsSegment(from, to)) {
					if (sun.isShielded()) { // barreira ativa: o cubo estilhaça sem causar dano
						sun.onShieldHit(level, from);
						this.breakApart(level);
						return;
					}
					sun.hurtServer(level, this.damageSources().thrown(this, this.getOwner()), this.damage);
					this.breakApart(level);
					return;
				}
			}
		}
		super.tick();
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (this.level() instanceof ServerLevel level && hit.getEntity() instanceof SunEntity sun) {
			sun.hurtServer(level, this.damageSources().thrown(this, this.getOwner()), this.damage);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level) {
			this.breakApart(level);
		}
	}

	private void breakApart(ServerLevel level) {
		level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(), 12, 0.2, 0.2, 0.2, 0.05);
		this.discard();
	}
}
