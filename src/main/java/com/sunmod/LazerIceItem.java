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

/**
 * Lazer Ice: raio instantâneo disparado sem parar enquanto o botão estiver apertado.
 * Tira 4x a vida do Sol que um Ice Cube e rende 4x mais dinheiro por acerto.
 * O raio para no primeiro bloco e é bloqueado pela barreira do Sol (igual ao Ice Cube).
 */
public class LazerIceItem extends AutoFireItem {
	public static final float DAMAGE = IceCubeProjectile.DAMAGE * 4;      // 20
	public static final int COIN_VALUE = MoneyCoinEntity.VALUE * 4;       // $12
	public static final int FIRE_INTERVAL_TICKS = 4;                      // 5 pulsos por segundo
	public static final double RANGE = 400.0;
	/** Só os primeiros blocos do raio ganham partículas (para não pesar na rede). */
	private static final double BEAM_PARTICLE_LENGTH = 40.0;

	public LazerIceItem(Properties properties) {
		super(properties);
	}

	@Override
	protected int fireInterval() {
		return FIRE_INTERVAL_TICKS;
	}

	@Override
	protected void fire(ServerLevel level, Player player, ItemStack weapon) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(RANGE));

		// O raio para no primeiro bloco no caminho.
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}

		// Sol mais próximo atingido pelo raio.
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

		// Visual: linha de partículas saindo da mão + faíscas no ponto final.
		Vec3 start = eye.add(look.scale(0.8)).add(0.0, -0.2, 0.0);
		double length = Math.min(start.distanceTo(end), BEAM_PARTICLE_LENGTH);
		for (double t = 0.0; t < length; t += 1.0) {
			Vec3 p = start.add(look.scale(t));
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.SNOWFLAKE, end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 2.0F);

		if (target != null) {
			if (target.isShielded()) {
				target.onShieldHit(level, hit);
			} else {
				ServerPlayer shooter = player instanceof ServerPlayer sp ? sp : null;
				target.applyWeaponDamage(level, shooter, hit, DAMAGE, COIN_VALUE);
			}
		}
	}
}
