package com.sunmod;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Cometa: nasce em volta do Sol (ver {@link SunEntity#tickComets}) e fica orbitando devagar. Com o Capturador de
 * Cometas (Gravity Gun) o jogador captura um cometa e o lança no Sol: o impacto tira {@link #DAMAGE} de vida e rende
 * {@link #COIN_VALUE} de dinheiro. Não machuca jogadores nem blocos, não é salvo no mundo e some sozinho.
 *
 * <p>Estende {@link ThrowableItemProjectile} só para herdar o básico de projétil; o desenho (bola de fogo com
 * cauda) é do {@code SolarFxRenderer} e o movimento é todo feito à mão em {@link #tick()}. Sem partículas.
 */
public class CometEntity extends ThrowableItemProjectile {
	/** Dano no Sol de um cometa lançado (antes do bônus de Poder de Resfriamento). */
	public static final float DAMAGE = 250.0F;
	/** Dinheiro ganho por cometa que acerta o Sol. */
	public static final int COIN_VALUE = 60;

	/** Vida de um cometa que ninguém capturou (ticks). */
	public static final int LIFE_TICKS = 20 * 90;
	/** Volta completa em volta do Sol: 2*pi / ORBIT_STEP ticks (~78 s). */
	private static final double ORBIT_STEP = 0.004;
	/** Velocidade (blocos por tick) de um cometa lançado. */
	private static final double LAUNCH_SPEED = 2.0;
	private static final int LAUNCH_LIFE_TICKS = 20 * 15;

	private SunEntity sun;
	private boolean launched;
	private Vec3 velocity = Vec3.ZERO;
	private float launchDamage = DAMAGE;
	private UUID ownerId;

	public CometEntity(EntityType<? extends CometEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.noPhysics = true;
	}

	/** Liga o cometa a um Sol (ele orbita em volta dele). Só no servidor, logo depois de criar. */
	public void orbit(SunEntity sun) {
		this.sun = sun;
	}

	public boolean isLaunched() {
		return this.launched;
	}

	/** A cauda e o brilho precisam aparecer de longe. */
	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 300.0 * 300.0;
	}

	@Override
	protected Item getDefaultItem() {
		return Items.FIRE_CHARGE;
	}

	/** Captura o cometa e o lança direto no centro do Sol. Devolve false se ele já tinha sido lançado. */
	public boolean capture(ServerPlayer owner) {
		if (this.launched || this.sun == null || this.sun.isRemoved()) {
			return false;
		}
		Vec3 center = this.sun.position().add(0.0, SunEntity.RADIUS, 0.0);
		this.launched = true;
		this.ownerId = owner.getUUID();
		this.launchDamage = DAMAGE * Upgrades.coolingMultiplier(owner);
		this.velocity = center.subtract(this.position()).normalize().scale(LAUNCH_SPEED);
		this.tickCount = 0; // a vida de um cometa lançado conta a partir de agora
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
				SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.6F);
		return true;
	}

	@Override
	public void tick() {
		this.baseTick();
		if (!(this.level() instanceof ServerLevel level)) {
			return; // o cliente só recebe a posição do servidor
		}
		if (this.sun == null || this.sun.isRemoved()) {
			this.discard();
			return;
		}
		if (this.launched) {
			this.tickLaunched(level);
		} else {
			this.tickOrbit(level);
		}
	}

	private void tickOrbit(ServerLevel level) {
		if (this.tickCount > LIFE_TICKS) {
			this.discard();
			return;
		}
		Vec3 center = this.sun.position().add(0.0, SunEntity.RADIUS, 0.0);
		Vec3 off = this.position().subtract(center);
		double cos = Math.cos(ORBIT_STEP), sin = Math.sin(ORBIT_STEP);
		this.setPos(center.add(off.x * cos - off.z * sin, off.y, off.x * sin + off.z * cos));
	}

	private void tickLaunched(ServerLevel level) {
		if (this.tickCount > LAUNCH_LIFE_TICKS) {
			this.discard();
			return;
		}
		Vec3 from = this.position();
		Vec3 to = from.add(this.velocity);
		if (this.sun.hitsSegment(from, to)) {
			this.impact(level, from);
			return;
		}
		this.setPos(to);
	}

	private void impact(ServerLevel level, Vec3 from) {
		if (this.sun.isShielded()) { // barreira ativa: o cometa se despedaça sem causar dano
			this.sun.onShieldHit(level, from);
		} else {
			Player owner = this.ownerId != null ? level.getPlayerByUUID(this.ownerId) : null;
			ServerPlayer shooter = owner instanceof ServerPlayer sp ? sp : null;
			this.sun.applyWeaponDamage(level, shooter, from, this.launchDamage, COIN_VALUE);
		}
		FxEntity.spawn(level, from, FxEntity.BLAST, 10.0F, 30, 0xFF8A20); // bola de fogo no ponto do impacto
		level.playSound(null, from.x, from.y, from.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.8F);
		this.discard();
	}

	/** Nada machuca nem empurra o cometa. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}
}
