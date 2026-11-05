package com.sunmod;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.UUID;

/**
 * Money Coin: nasce no Sol quando ele leva dano de um Ice Cube, toca a animação "spawn" do modelo,
 * voa até o jogador que causou o dano e some ao encostar nele, somando {@link #VALUE} ao dinheiro (HUD).
 */
public class MoneyCoinEntity extends Entity {
	/** Dinheiro ganho por moeda. */
	public static final int VALUE = 3;
	/** Duração da animação de spawn (0,625 s do .bbmodel = 12,5 ticks). */
	public static final int SPAWN_TICKS = 13;

	private static final int MAX_LIFE = 20 * 60;
	private static final double MAX_SPEED = 1.5;
	private static final int TRAIL_LENGTH = 10;

	private UUID targetId;
	/** Posições recentes (só no cliente), usadas no rastro branco. */
	private final ArrayDeque<Vec3> trail = new ArrayDeque<>();

	public MoneyCoinEntity(EntityType<? extends MoneyCoinEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.noPhysics = true; // atravessa blocos até chegar no jogador
	}

	/** Cria uma moeda em {@code pos} destinada ao jogador {@code target}. */
	public static void spawn(ServerLevel level, Vec3 pos, ServerPlayer target) {
		MoneyCoinEntity coin = new MoneyCoinEntity(ModEntities.MONEY_COIN, level);
		coin.setPos(pos.x, pos.y, pos.z);
		coin.targetId = target.getUUID();
		level.addFreshEntity(coin);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
	}

	public Collection<Vec3> getTrail() {
		return this.trail;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();

		if (this.level().isClientSide()) {
			if (this.tickCount > SPAWN_TICKS) {
				this.trail.addFirst(this.position());
				while (this.trail.size() > TRAIL_LENGTH) {
					this.trail.removeLast();
				}
			}
			return;
		}
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.targetId == null || this.tickCount > MAX_LIFE) {
			this.discard();
			return;
		}
		if (this.tickCount < SPAWN_TICKS) {
			return; // animação de spawn: fica parada no Sol
		}
		Player target = level.getPlayerByUUID(this.targetId);
		if (target == null || !target.isAlive()) {
			return; // espera o jogador voltar (até MAX_LIFE)
		}

		double halfHeight = this.getBbHeight() * 0.5;
		Vec3 center = this.position().add(0.0, halfHeight, 0.0);
		Vec3 goal = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 delta = goal.subtract(center);
		double distance = delta.length();

		if (distance < 1.0 || target.getBoundingBox().inflate(0.25).contains(center)) {
			target.setAttached(ModAttachments.MONEY, target.getAttachedOrElse(ModAttachments.MONEY, 0) + VALUE);
			level.playSound(null, target.getX(), target.getY(), target.getZ(),
					SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.5F);
			this.discard();
			return;
		}

		double speed = Math.min(MAX_SPEED, 0.2 + (this.tickCount - SPAWN_TICKS) * 0.05);
		Vec3 step = delta.scale(Math.min(speed, distance) / distance);
		this.setDeltaMovement(step);
		this.setPos(this.position().add(step));
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
