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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * O Sol: 50x50x50 blocos, fica parado no ar (sem gravidade, nunca se move) e
 * só recebe dano de um {@link IceCubeProjectile}.
 */
public class SunEntity extends Entity {
	/** Vida total do Sol. Cada Ice Cube causa {@link IceCubeProjectile#DAMAGE}. */
	public static final float MAX_HEALTH = 100.0F;

	/**
	 * Raio da hitbox real: a esfera mais externa do modelo (o outline), 25 blocos.
	 * Entidades gigantes não são achadas pela busca normal de colisão do Minecraft (ela só procura
	 * perto da posição da entidade), por isso o projétil testa o Sol diretamente por esta lista.
	 */
	public static final double RADIUS = 25.0;

	private static final Set<SunEntity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

	private float health = MAX_HEALTH;

	/** Sóis carregados no servidor. */
	public static List<SunEntity> activeSuns() {
		synchronized (ACTIVE) {
			return new ArrayList<>(ACTIVE);
		}
	}

	/** O segmento from→to (mundo) toca a esfera do Sol (inclui começar dentro dela)? */
	public boolean hitsSegment(Vec3 from, Vec3 to) {
		Vec3 center = this.position().add(0.0, RADIUS, 0.0);
		Vec3 d = to.subtract(from);
		double len2 = d.lengthSqr();
		double t = len2 > 1.0E-7 ? Math.max(0.0, Math.min(1.0, center.subtract(from).dot(d) / len2)) : 0.0;
		Vec3 closest = from.add(d.scale(t));
		return closest.distanceToSqr(center) <= RADIUS * RADIUS;
	}

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
		if (this.level() instanceof ServerLevel) {
			synchronized (ACTIVE) {
				ACTIVE.add(this);
			}
		}
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
		this.playSound(SoundEvents.GLASS_BREAK, 8.0F, 0.6F);
		level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 25.0, this.getZ(),
				150, 15.0, 15.0, 15.0, 0.05);
		if (this.health <= 0.0F) {
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 25.0, this.getZ(),
					800, 20.0, 20.0, 20.0, 0.2);
			this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 16.0F, 0.5F);
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
