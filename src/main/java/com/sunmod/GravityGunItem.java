package com.sunmod;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gravity Gun (melhoria "Capturador de Cometas"): botão direito captura o cometa que estiver na mira (ou o mais
 * próximo, se nenhum estiver na mira) e o lança no Sol. Só funciona depois de comprar a melhoria.
 */
public class GravityGunItem extends Item {
	/** Alcance da mira (blocos). */
	public static final double AIM_RANGE = 160.0;
	/** Folga da mira: o cometa conta se passar a menos disso do centro da linha de visão. */
	private static final double AIM_RADIUS = 4.0;
	/** Sem nada na mira, pega o cometa mais próximo dentro deste raio. */
	private static final double NEAR_RANGE = 40.0;

	public GravityGunItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!Upgrades.isUnlocked(player, "comet_catcher")) {
			if (!level.isClientSide()) {
				player.displayClientMessage(Component.translatable("message.sunmod.upgrade_locked",
						Upgrades.byId("comet_catcher").name()), true);
			}
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			CometEntity comet = findComet(serverLevel, player);
			if (comet == null || !comet.capture(serverPlayer)) {
				player.displayClientMessage(Component.translatable("message.sunmod.no_comet"), true);
				return InteractionResult.FAIL;
			}
		}
		return InteractionResult.SUCCESS;
	}

	private static CometEntity findComet(ServerLevel level, Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		AABB area = new AABB(eye, eye.add(look.scale(AIM_RANGE))).inflate(AIM_RADIUS);

		CometEntity aimed = null;
		double aimedT = Double.MAX_VALUE;
		CometEntity near = null;
		double nearDist = NEAR_RANGE * NEAR_RANGE;
		for (CometEntity comet : level.getEntitiesOfClass(CometEntity.class, area.minmax(new AABB(eye, eye).inflate(NEAR_RANGE)))) {
			if (comet.isLaunched() || comet.isRemoved()) {
				continue;
			}
			Vec3 toComet = comet.getBoundingBox().getCenter().subtract(eye);
			double t = toComet.dot(look); // distância ao longo do olhar
			if (t > 0.0 && t < AIM_RANGE && toComet.lengthSqr() - t * t <= AIM_RADIUS * AIM_RADIUS && t < aimedT) {
				aimed = comet;
				aimedT = t;
			}
			double d = toComet.lengthSqr();
			if (d < nearDist) {
				nearDist = d;
				near = comet;
			}
		}
		return aimed != null ? aimed : near;
	}
}
