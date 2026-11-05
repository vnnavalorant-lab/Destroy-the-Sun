package com.sunmod;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lazer Ice: raio contínuo enquanto o botão estiver apertado (sem carregar).
 * O desenho do raio (linha grossa, centro branco e em volta ciano) é a {@link LaserBeamEntity}, atualizada a cada tick;
 * o dano acontece a cada {@link #FIRE_INTERVAL_TICKS} ticks e gasta 1 Ice Cube.
 * Tira 4x a vida do Sol que um Ice Cube e rende 4x mais dinheiro por acerto.
 * O raio para no primeiro bloco e é bloqueado pela barreira do Sol (igual ao Ice Cube).
 */
public class LazerIceItem extends AutoFireItem {
	public static final float DAMAGE = IceCubeProjectile.DAMAGE * 4;      // 20
	public static final int COIN_VALUE = MoneyCoinEntity.VALUE * 4;       // $12
	public static final int FIRE_INTERVAL_TICKS = 4;                      // 5 pulsos de dano por segundo
	public static final double RANGE = 400.0;

	/** Raio visual de cada jogador que está atirando (só no servidor). */
	private static final Map<UUID, LaserBeamEntity> BEAMS = new HashMap<>();

	private record Trace(Vec3 start, Vec3 end, SunEntity sun, Vec3 hit) {
	}

	public LazerIceItem(Properties properties) {
		super(properties);
	}

	@Override
	protected int fireInterval() {
		return FIRE_INTERVAL_TICKS;
	}

	@Override
	protected String upgradeId() {
		return "cryo_beam";
	}

	/** Segue a mira a cada tick: o raio fica contínuo e suave. */
	@Override
	protected void holdTick(ServerLevel level, Player player, ItemStack weapon) {
		Trace trace = this.trace(level, player);
		Vec3 end = trace.end();

		UUID id = player.getUUID();
		float endSize = trace.sun() != null ? 6.0F : 1.4F;
		LaserBeamEntity beam = BEAMS.get(id);
		if (beam == null || beam.isRemoved() || beam.level() != level) {
			beam = new LaserBeamEntity(ModEntities.LASER_BEAM, level);
			beam.setBeam(trace.start(), end, endSize, level.getGameTime());
			BEAMS.put(id, beam);
			level.addFreshEntity(beam);
		} else {
			beam.setBeam(trace.start(), end, endSize, level.getGameTime());
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 2, 0.4, 0.4, 0.4, 0.3);
	}

	/** Dano (e dinheiro) a cada pulso. */
	@Override
	protected void fire(ServerLevel level, Player player, ItemStack weapon) {
		Trace trace = this.trace(level, player);
		Vec3 end = trace.end();

		level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 4, 0.4, 0.4, 0.4, 0.15);
		level.sendParticles(ParticleTypes.SNOWFLAKE, end.x, end.y, end.z, 6, 0.5, 0.5, 0.5, 0.08);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 2.0F);

		SunEntity target = trace.sun();
		if (target != null) {
			if (target.isShielded()) {
				target.onShieldHit(level, trace.hit());
			} else {
				ServerPlayer shooter = player instanceof ServerPlayer sp ? sp : null;
				target.applyWeaponDamage(level, shooter, trace.hit(), DAMAGE * Upgrades.coolingMultiplier(player), COIN_VALUE);
			}
		}
	}

	/** Calcula o raio: do cano até o primeiro bloco ou até a esfera do Sol, o que vier antes. */
	private Trace trace(ServerLevel level, Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(RANGE));

		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}

		SunEntity target = null;
		Vec3 hit = null;
		double best = Double.MAX_VALUE;
		for (SunEntity sun : SunEntity.activeSuns()) {
			if (sun.isRemoved() || sun.level() != level) {
				continue;
			}
			Vec3 p = sun.rayHit(eye, end);
			if (p != null) {
				double d = p.distanceToSqr(eye);
				if (d < best) {
					best = d;
					target = sun;
					hit = p;
				}
			}
		}
		if (hit != null) {
			end = hit;
		}
		// o raio sai um pouco à frente e abaixo dos olhos (na altura do cano)
		Vec3 start = eye.add(look.scale(0.9)).add(0.0, -0.25, 0.0);
		return new Trace(start, end, target, hit);
	}
}
