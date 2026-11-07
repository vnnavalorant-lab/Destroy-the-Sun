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
import net.minecraft.world.phys.Vec3;

/**
 * Efeito visual do mod (sem colisão nem lógica): o desenho (discos, brilhos, anéis, colunas) é feito pelo
 * {@code SolarFxRenderer} no cliente a partir de textura, então fica contínuo e sem partículas soltas. O servidor
 * só cria a entidade e a remove quando o tempo ({@link #getLife()}) acaba. Não é salva no mundo.
 *
 * <p>Tipos: {@link #LAVA_POOL} (poça de lava no chão), {@link #EXPLOSION} (bola de fogo + onda de choque no chão),
 * {@link #BLAST} (bola de fogo no ar, sem anel) e {@link #WEAK_POINT} (mira de ponto super fraco no Sol).
 */
public class FxEntity extends Entity {
	public static final int LAVA_POOL = 0;
	public static final int EXPLOSION = 1;
	public static final int WEAK_POINT = 2;
	public static final int BLAST = 3;

	private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(FxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(FxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TINT = SynchedEntityData.defineId(FxEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(FxEntity.class, EntityDataSerializers.FLOAT);

	public FxEntity(EntityType<? extends FxEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/**
	 * Cria o efeito em {@code pos}.
	 *
	 * @param kind   um dos tipos acima
	 * @param radius tamanho do efeito (blocos)
	 * @param life   duração em ticks
	 * @param tint   cor principal RGB (0xRRGGBB)
	 */
	public static FxEntity spawn(ServerLevel level, Vec3 pos, int kind, float radius, int life, int tint) {
		FxEntity fx = new FxEntity(ModEntities.FX, level);
		fx.entityData.set(DATA_KIND, kind);
		fx.entityData.set(DATA_LIFE, life);
		fx.entityData.set(DATA_TINT, tint);
		fx.entityData.set(DATA_RADIUS, radius);
		fx.setPos(pos.x, pos.y, pos.z);
		level.addFreshEntity(fx);
		return fx;
	}

	public int getKind() {
		return this.entityData.get(DATA_KIND);
	}

	public int getLife() {
		return this.entityData.get(DATA_LIFE);
	}

	public int getTint() {
		return this.entityData.get(DATA_TINT);
	}

	public float getRadius() {
		return this.entityData.get(DATA_RADIUS);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_KIND, EXPLOSION);
		builder.define(DATA_LIFE, 20);
		builder.define(DATA_TINT, 0xFF9A30);
		builder.define(DATA_RADIUS, 4.0F);
	}

	@Override
	public void tick() {
		this.baseTick();
		if (!this.level().isClientSide() && this.tickCount >= this.getLife()) {
			this.discard();
		}
	}

	/** Os efeitos grandes (colunas, poças) precisam ser desenhados de longe. */
	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 300.0 * 300.0;
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
