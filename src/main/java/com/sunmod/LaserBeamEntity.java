package com.sunmod;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Raio visual do Lazer Ice: uma linha contínua do cano até o ponto de impacto, desenhada pelo
 * {@code LaserBeamRenderer} (centro branco, em volta ciano). Não causa dano. O item o atualiza a cada
 * tick enquanto o botão está apertado; se ficar mais de 3 ticks sem atualização ele some sozinho.
 */
public class LaserBeamEntity extends Entity {
	private static final EntityDataAccessor<Float> SX = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> SY = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> SZ = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> EX = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> EY = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> EZ = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);
	/** Tamanho (em blocos) do brilho no ponto de impacto. */
	private static final EntityDataAccessor<Float> END_SIZE = SynchedEntityData.defineId(LaserBeamEntity.class, EntityDataSerializers.FLOAT);

	private static final long MAX_IDLE_TICKS = 3L;

	/** Tick de jogo da última atualização (só no servidor). */
	private long lastRefresh;

	public LaserBeamEntity(EntityType<? extends LaserBeamEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.noPhysics = true;
	}

	/** Define o raio (coordenadas do mundo). Chamar só no servidor. */
	public void setBeam(Vec3 start, Vec3 end, float endSize, long gameTime) {
		this.entityData.set(SX, (float) start.x);
		this.entityData.set(SY, (float) start.y);
		this.entityData.set(SZ, (float) start.z);
		this.entityData.set(EX, (float) end.x);
		this.entityData.set(EY, (float) end.y);
		this.entityData.set(EZ, (float) end.z);
		this.entityData.set(END_SIZE, endSize);
		this.setPos(start.x, start.y, start.z);
		this.lastRefresh = gameTime;
	}

	public Vec3 getStart() {
		return new Vec3(this.entityData.get(SX), this.entityData.get(SY), this.entityData.get(SZ));
	}

	public Vec3 getEnd() {
		return new Vec3(this.entityData.get(EX), this.entityData.get(EY), this.entityData.get(EZ));
	}

	public float getEndSize() {
		return this.entityData.get(END_SIZE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(SX, 0.0F);
		builder.define(SY, 0.0F);
		builder.define(SZ, 0.0F);
		builder.define(EX, 0.0F);
		builder.define(EY, 0.0F);
		builder.define(EZ, 0.0F);
		builder.define(END_SIZE, 1.0F);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && level.getGameTime() - this.lastRefresh > MAX_IDLE_TICKS) {
			this.discard();
		}
	}

	/** O raio pode ter centenas de blocos: a caixa de culling cobre do início ao fim. */
	@Override
	public AABB getBoundingBoxForCulling() {
		return new AABB(this.getStart(), this.getEnd()).inflate(4.0);
	}

	/** Sempre visível (a caixa da entidade é minúscula e o teste de distância padrão a esconderia). */
	@Override
	public boolean shouldRender(double x, double y, double z) {
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
