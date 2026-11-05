package com.sunmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * O Sol: 50x50x50 blocos, fica parado no ar (sem gravidade, nunca se move) e
 * só recebe dano de um {@link IceCubeProjectile}. A vida é a "temperatura" mostrada na HUD
 * e cada dano faz nascer uma {@link MoneyCoinEntity} para o jogador que acertou.
 */
public class SunEntity extends Entity {
	/** Vida total do Sol. Cada Ice Cube causa {@link IceCubeProjectile#DAMAGE}. */
	public static final float MAX_HEALTH = 100.0F;

	/** Temperatura (°C) mostrada na HUD com a vida cheia; cai junto com a vida. */
	public static final int MAX_TEMPERATURE = 144982;

	/**
	 * Raio da hitbox real: a esfera mais externa do modelo (o outline), 25 blocos.
	 * Entidades gigantes não são achadas pela busca normal de colisão do Minecraft (ela só procura
	 * perto da posição da entidade), por isso o projétil testa o Sol diretamente por esta lista.
	 */
	public static final double RADIUS = 25.0;

	/** Vida sincronizada com os clientes (a HUD Sun Temperature lê daqui). */
	private static final EntityDataAccessor<Float> DATA_HEALTH =
			SynchedEntityData.defineId(SunEntity.class, EntityDataSerializers.FLOAT);

	private static final Set<SunEntity> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

	/** Sóis carregados no servidor. */
	public static List<SunEntity> activeSuns() {
		synchronized (ACTIVE) {
			return new ArrayList<>(ACTIVE);
		}
	}

	public SunEntity(EntityType<? extends SunEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	public float getHealthValue() {
		return this.entityData.get(DATA_HEALTH);
	}

	private void setHealthValue(float value) {
		this.entityData.set(DATA_HEALTH, value);
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

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_HEALTH, MAX_HEALTH);
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
		if (!(source.getDirectEntity() instanceof IceCubeProjectile projectile)) {
			return false;
		}
		float remaining = this.getHealthValue() - amount;
		this.setHealthValue(Math.max(0.0F, remaining));

		this.playSound(SoundEvents.GLASS_BREAK, 8.0F, 0.6F);
		level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + RADIUS, this.getZ(),
				150, 15.0, 15.0, 15.0, 0.05);

		// Dano causado por um jogador: nasce uma moeda na superfície do Sol, no ponto do acerto.
		if (source.getEntity() instanceof ServerPlayer player) {
			MoneyCoinEntity.spawn(level, this.surfacePoint(projectile.position()), player);
		}

		if (remaining <= 0.0F) {
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + RADIUS, this.getZ(),
					800, 20.0, 20.0, 20.0, 0.2);
			this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 16.0F, 0.5F);
			this.discard();
		}
		return true;
	}

	/** Ponto da superfície do Sol mais próximo de {@code from} (ou o próprio ponto, se estiver dentro). */
	private Vec3 surfacePoint(Vec3 from) {
		Vec3 center = this.position().add(0.0, RADIUS, 0.0);
		Vec3 offset = from.subtract(center);
		double len = offset.length();
		return len > RADIUS ? center.add(offset.scale(RADIUS / len)) : from;
	}

	/** Precisa ser "pickable" para que projéteis (e o cursor) possam acertá-lo. */
	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setHealthValue(input.getFloatOr("SunHealth", MAX_HEALTH));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putFloat("SunHealth", this.getHealthValue());
	}
}
