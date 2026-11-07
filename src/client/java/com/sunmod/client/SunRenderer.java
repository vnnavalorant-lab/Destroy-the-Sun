package com.sunmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sunmod.SunEntity;
import com.sunmod.SunMod;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;

/**
 * Desenha a malha das 3 esferas do sun_vanilla.bbmodel (50 x 50 x 50 blocos).
 * As 2 esferas externas são o "outline" (casco invertido): precisam de culling
 * de faces para que só apareça a borda ao redor do núcleo.
 * Enquanto a barreira está ativa, desenha por cima o orbe holográfico translúcido
 * (textures/entity/sun_shield.png) envolvendo o Sol. É enviada com order(1) para ser
 * desenhada DEPOIS do Sol (senão ela grava profundidade antes e esconde o Sol que está dentro).
 */
public class SunRenderer extends EntityRenderer<SunEntity, SunRenderState> {
	private static final Identifier TEXTURE = SunMod.id("textures/entity/sun_vanilla.png");
	private static final Identifier SHIELD_TEXTURE = SunMod.id("textures/entity/sun_shield.png");

	private static final float FADE_IN_TICKS = 20.0F;   // 1 s para aparecer
	private static final float FADE_OUT_TICKS = 40.0F;  // 2 s para sumir
	private static final float BLINK_TICKS = 200.0F;    // pisca nos últimos 10 s (aviso)
	private static final float SPIN_DEGREES_PER_TICK = 0.3F;

	public SunRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public SunRenderState createRenderState() {
		return new SunRenderState();
	}

	@Override
	public void extractRenderState(SunEntity sun, SunRenderState state, float partialTick) {
		super.extractRenderState(sun, state, partialTick);
		state.shieldAlpha = 0.0F;
		state.shieldSpin = (sun.tickCount + partialTick) * SPIN_DEGREES_PER_TICK;

		long end = sun.getShieldEnd();
		if (end != 0L) {
			float remaining = end - (sun.level().getGameTime() + partialTick);
			if (remaining > 0.0F) {
				float elapsed = (sun.level().getGameTime() + partialTick) - sun.getShieldStart();
				float alpha = Math.min(Math.min(1.0F, elapsed / FADE_IN_TICKS), remaining / FADE_OUT_TICKS);
				if (remaining < BLINK_TICKS) {
					alpha *= 0.75F + 0.25F * (float) Math.sin(remaining * 0.6F);
				}
				state.shieldAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
			}
		}
	}

	@Override
	public void submit(SunRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE),
				(pose, consumer) -> QuadMesh.SUN.render(pose, consumer, LightTexture.FULL_BRIGHT, true));

		if (state.shieldAlpha > 0.01F) {
			int argb = (Math.round(state.shieldAlpha * 255.0F) << 24) | 0x00FFFFFF;
			poseStack.pushPose();
			poseStack.translate(0.0, SunEntity.RADIUS, 0.0); // centro do Sol
			poseStack.mulPose(Axis.YP.rotationDegrees(state.shieldSpin));
			collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(SHIELD_TEXTURE),
					(pose, consumer) -> ShieldMesh.render(pose, consumer, argb, LightTexture.FULL_BRIGHT));
			poseStack.popPose();
		}
		super.submit(state, poseStack, collector, camera);
	}
}
