package com.sunmod;

import net.minecraft.core.particles.DustParticleOptions;
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
	/** Cores do raio (RGB): ciano puro no brilho e quase branco-ciano no núcleo. */
	private static final int GLOW_COLOR = 0x00F0FF;
	private static final int CORE_COLOR = 0xC8FFFF;

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

		// Visual: raio ciano denso (núcleo claro + brilho ciano) saindo da mão, com faíscas no ponto final.
		Vec3 start = eye.add(look.scale(0.8)).add(0.0, -0.2, 0.0);
		double total = start.distanceTo(end);
		double t = 0.0;
		while (t < total) {
			Vec3 p = start.add(look.scale(t));
			// longe da câmera o passo aumenta e a partícula engorda, para o raio não sumir de longe
			double step = t < 24.0 ? 0.4 : (t < 96.0 ? 1.0 : 2.0);
			float glow = t < 24.0 ? 1.6F : (t < 96.0 ? 2.6F : 4.0F);
			level.sendParticles(new DustParticleOptions(GLOW_COLOR, glow), p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
			level.sendParticles(new DustParticleOptions(CORE_COLOR, glow * 0.45F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			t += step;
		}
		// Impacto: explosão de ciano, faíscas elétricas e estilhaços de gelo.
		level.sendParticles(new DustParticleOptions(GLOW_COLOR, 3.0F), end.x, end.y, end.z, 25, 0.8, 0.8, 0.8, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 20, 0.6, 0.6, 0.6, 0.4);
		level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 6, 0.4, 0.4, 0.4, 0.15);
		level.sendParticles(ParticleTypes.SNOWFLAKE, end.x, end.y, end.z, 10, 0.5, 0.5, 0.5, 0.08);
		// Brilho na boca da arma
		level.sendParticles(new DustParticleOptions(GLOW_COLOR, 2.0F), start.x, start.y, start.z, 3, 0.1, 0.1, 0.1, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 2.0F);
		level.playSound(null, end.x, end.y, end.z,
				SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.4F, 0.5F);

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
