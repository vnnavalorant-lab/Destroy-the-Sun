package com.sunmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
	/** Vida total do Sol (100x a original: eram 100). Cada Ice Cube causa {@link IceCubeProjectile#DAMAGE}. */
	public static final float MAX_HEALTH = 50000.0F;

	/** Temperatura (°C) mostrada na HUD com a vida cheia; cai junto com a vida. */
	public static final int MAX_TEMPERATURE = 144982;

	/**
	 * Raio da hitbox real: a esfera mais externa do modelo (o outline), 25 blocos.
	 * Entidades gigantes não são achadas pela busca normal de colisão do Minecraft (ela só procura
	 * perto da posição da entidade), por isso o projétil testa o Sol diretamente por esta lista.
	 */
	public static final double RADIUS = 25.0;

	/** A barreira nasce a cada 5 minutos (contados do início de uma barreira até o início da próxima)... */
	public static final int SHIELD_INTERVAL_TICKS = 5 * 60 * 20;

	/** ...e fica ativa por 2 minutos; depois some e o Sol volta a receber dano. */
	public static final int SHIELD_DURATION_TICKS = 2 * 60 * 20;

	/** Raio da barreira: um pouco maior que o outline do Sol (25), para envolvê-lo. */
	public static final double SHIELD_RADIUS = 27.0;

	/** Vida sincronizada com os clientes (a HUD Sun Temperature lê daqui). */
	private static final EntityDataAccessor<Float> DATA_HEALTH =
			SynchedEntityData.defineId(SunEntity.class, EntityDataSerializers.FLOAT);

	/**
	 * Tick de jogo em que a barreira termina (0 = sem barreira). Só muda na ativação/desativação,
	 * então quase não gera tráfego de rede; o cliente calcula o tempo restante sozinho (fade, piscar).
	 */
	private static final EntityDataAccessor<Long> DATA_SHIELD_END =
			SynchedEntityData.defineId(SunEntity.class, EntityDataSerializers.LONG);

	/** Tick de jogo da próxima ativação da barreira (só no servidor; salvo no mundo). */
	private long nextShieldAt = 0L;

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

	/** Tick de jogo em que a barreira acaba (0 = inativa). */
	public long getShieldEnd() {
		return this.entityData.get(DATA_SHIELD_END);
	}

	/** A barreira está ativa agora? (o Sol não recebe dano enquanto estiver) */
	public boolean isShielded() {
		long end = this.getShieldEnd();
		return end != 0L && this.level().getGameTime() < end;
	}

	/** Raio da hitbox atual: a barreira (maior) enquanto ativa, senão o outline do Sol. */
	public double getHitRadius() {
		return this.isShielded() ? SHIELD_RADIUS : RADIUS;
	}

	/** O segmento from→to (mundo) toca a esfera do Sol (inclui começar dentro dela)? */
	public boolean hitsSegment(Vec3 from, Vec3 to) {
		double radius = this.getHitRadius();
		Vec3 center = this.position().add(0.0, RADIUS, 0.0);
		Vec3 d = to.subtract(from);
		double len2 = d.lengthSqr();
		double t = len2 > 1.0E-7 ? Math.max(0.0, Math.min(1.0, center.subtract(from).dot(d) / len2)) : 0.0;
		Vec3 closest = from.add(d.scale(t));
		return closest.distanceToSqr(center) <= radius * radius;
	}

	/**
	 * Primeiro ponto em que o raio from→to entra na esfera do Sol (ou {@code from}, se já começar dentro).
	 * Devolve null se o segmento não toca a esfera. Usado pelos tiros instantâneos (Lazer Ice).
	 */
	public Vec3 rayHit(Vec3 from, Vec3 to) {
		double radius = this.getHitRadius();
		Vec3 center = this.position().add(0.0, RADIUS, 0.0);
		Vec3 d = to.subtract(from);
		double a = d.lengthSqr();
		if (a < 1.0E-7) {
			return null;
		}
		Vec3 oc = from.subtract(center);
		double c = oc.lengthSqr() - radius * radius;
		if (c <= 0.0) {
			return from;
		}
		double b = oc.dot(d);
		double disc = b * b - a * c;
		if (disc < 0.0) {
			return null;
		}
		double t = (-b - Math.sqrt(disc)) / a;
		return (t < 0.0 || t > 1.0) ? null : from.add(d.scale(t));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_HEALTH, MAX_HEALTH);
		builder.define(DATA_SHIELD_END, 0L);
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

		if (this.level() instanceof ServerLevel level) {
			this.tickShield(level);
		}
	}

	/** Ciclo da barreira: liga a cada {@link #SHIELD_INTERVAL_TICKS}, desliga após {@link #SHIELD_DURATION_TICKS}. */
	private void tickShield(ServerLevel level) {
		long now = level.getGameTime();
		if (this.nextShieldAt <= 0L) {
			this.nextShieldAt = now + SHIELD_INTERVAL_TICKS; // Sol recém-colocado: 1ª barreira em 5 min
		}

		long end = this.getShieldEnd();
		if (end != 0L && now >= end) {
			this.entityData.set(DATA_SHIELD_END, 0L);
			end = 0L;
			this.playSound(SoundEvents.GLASS_BREAK, 16.0F, 0.5F);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + RADIUS, this.getZ(),
					200, 20.0, 20.0, 20.0, 0.3);
		}

		if (end == 0L && now >= this.nextShieldAt) {
			this.entityData.set(DATA_SHIELD_END, now + SHIELD_DURATION_TICKS);
			this.nextShieldAt = now + SHIELD_INTERVAL_TICKS;
			this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 16.0F, 1.8F);
			level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + RADIUS, this.getZ(),
					200, 20.0, 20.0, 20.0, 0.1);
		}
	}

	/** Chamado quando um projétil bate na barreira: só faíscas (sem som e sem dano). */
	public void onShieldHit(ServerLevel level, Vec3 from) {
		Vec3 p = this.surfacePoint(from);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 30, 1.0, 1.0, 1.0, 0.3);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// Único dano aceito: o vindo do projétil Ice Cube (inclui os tiros da Gun Ice).
		if (!(source.getDirectEntity() instanceof IceCubeProjectile projectile)) {
			return false;
		}
		ServerPlayer shooter = source.getEntity() instanceof ServerPlayer player ? player : null;
		return this.applyWeaponDamage(level, shooter, projectile.position(), amount, projectile.getCoinValue());
	}

	/**
	 * Dano das armas (projétil ou raio): tira vida, faz efeitos e, se foi um jogador, nasce uma moeda
	 * com {@code coinValue} de dinheiro no ponto do acerto. Com a barreira ativa não faz nada.
	 *
	 * @return true se o dano foi aplicado
	 */
	public boolean applyWeaponDamage(ServerLevel level, ServerPlayer shooter, Vec3 hitPos, float amount, int coinValue) {
		// Com a barreira ativa o Sol é invulnerável.
		if (this.isShielded()) {
			return false;
		}
		float remaining = this.getHealthValue() - amount;
		this.setHealthValue(Math.max(0.0F, remaining));

		// Armas de tiro rápido: menos partículas por acerto (para não pesar com 4-5 acertos por segundo).
		int particles = amount > IceCubeProjectile.DAMAGE ? 40 : 150;
		level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + RADIUS, this.getZ(),
				particles, 15.0, 15.0, 15.0, 0.05);

		// Dano causado por um jogador: nasce uma moeda na superfície do Sol, no ponto do acerto.
		if (shooter != null) {
			MoneyCoinEntity.spawn(level, this.surfacePoint(hitPos), shooter, coinValue);
		}

		if (remaining <= 0.0F) {
			level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + RADIUS, this.getZ(),
					800, 20.0, 20.0, 20.0, 0.2);
			this.playSound(SoundEvents.GENERIC_EXPLODE.value(), 16.0F, 0.5F);
			this.discard();
		}
		return true;
	}

	/** Ponto da superfície do Sol (ou da barreira, se ativa) mais próximo de {@code from} (ou o próprio ponto, se estiver dentro). */
	private Vec3 surfacePoint(Vec3 from) {
		double radius = this.getHitRadius();
		Vec3 center = this.position().add(0.0, RADIUS, 0.0);
		Vec3 offset = from.subtract(center);
		double len = offset.length();
		return len > radius ? center.add(offset.scale(radius / len)) : from;
	}

	/** Precisa ser "pickable" para que projéteis (e o cursor) possam acertá-lo. */
	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setHealthValue(input.getFloatOr("SunHealth", MAX_HEALTH));
		this.entityData.set(DATA_SHIELD_END, input.getLongOr("SunShieldEnd", 0L));
		this.nextShieldAt = input.getLongOr("SunNextShield", 0L);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putFloat("SunHealth", this.getHealthValue());
		output.putLong("SunShieldEnd", this.getShieldEnd());
		output.putLong("SunNextShield", this.nextShieldAt);
	}
}
