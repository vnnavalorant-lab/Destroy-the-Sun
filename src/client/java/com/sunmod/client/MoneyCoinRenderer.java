package com.sunmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sunmod.MoneyCoinEntity;
import com.sunmod.SunMod;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/** Desenha a moeda (money_coin.bbmodel) com a animação de spawn e, depois, um rastro branco fino e suave. */
public class MoneyCoinRenderer extends EntityRenderer<MoneyCoinEntity, MoneyCoinRenderState> {
	private static final Identifier COIN_TEXTURE = SunMod.id("textures/entity/money_coin.png");
	private static final Identifier TRAIL_TEXTURE = SunMod.id("textures/entity/coin_trail.png");

	/** O modelo tem ~0,9 bloco; na tela a moeda fica com este fator (ajuste a gosto). */
	private static final float COIN_SCALE = 0.6F;
	/** Altura do centro da moeda em relação à posição da entidade (metade da caixa de 0,5). */
	private static final float CENTER_Y = 0.25F;
	/** Giro contínuo depois do spawn, em graus por tick. */
	private static final float SPIN_DEG_PER_TICK = 12.0F;

	/** Rastro: meia-largura inicial em blocos (fino) e transparência máxima. */
	private static final float TRAIL_HALF_WIDTH = 0.025F;
	private static final float TRAIL_MAX_ALPHA = 0.85F;

	public MoneyCoinRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public MoneyCoinRenderState createRenderState() {
		return new MoneyCoinRenderState();
	}

	@Override
	public void extractRenderState(MoneyCoinEntity entity, MoneyCoinRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.age = entity.tickCount + partialTick;
		state.trail.clear();
		Vec3 base = entity.getPosition(partialTick);
		state.trail.add(Vec3.ZERO);
		for (Vec3 p : entity.getTrail()) {
			state.trail.add(p.subtract(base));
		}
	}

	@Override
	public void submit(MoneyCoinRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		boolean spawning = state.age < MoneyCoinEntity.SPAWN_TICKS;
		float seconds = state.age / 20.0F;

		float scale = 1.0F;
		float offsetY = 0.0F;
		float rotY;
		if (spawning) {
			scale = Math.max(0.001F, CoinAnimation.scale(seconds));
			offsetY = CoinAnimation.offsetY(seconds) / 16.0F * COIN_SCALE;
			rotY = CoinAnimation.rotationY(seconds);
		} else {
			rotY = (state.age - MoneyCoinEntity.SPAWN_TICKS) * SPIN_DEG_PER_TICK;
		}

		poseStack.pushPose();
		poseStack.translate(0.0, CENTER_Y + offsetY, 0.0);
		poseStack.mulPose(Axis.YP.rotationDegrees(rotY));
		float s = scale * COIN_SCALE;
		poseStack.scale(s, s, s);
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(COIN_TEXTURE),
				(pose, consumer) -> QuadMesh.MONEY_COIN.render(pose, consumer, LightTexture.FULL_BRIGHT, false));
		poseStack.popPose();

		if (!spawning && state.trail.size() > 1) {
			collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TRAIL_TEXTURE),
					(pose, consumer) -> drawTrail(state.trail, pose, consumer));
		}
		super.submit(state, poseStack, collector, camera);
	}

	/**
	 * Rastro: faixa fina que some aos poucos. Cada segmento é desenhado em dois planos perpendiculares
	 * (vistos de qualquer ângulo) e com as duas faces; a textura é um gradiente na largura (bordas suaves).
	 */
	private static void drawTrail(List<Vec3> trail, PoseStack.Pose pose, VertexConsumer consumer) {
		int n = trail.size();
		Matrix4f matrix = pose.pose();
		Vector4f p = new Vector4f();
		Vector3f normal = new Vector3f();
		int light = LightTexture.FULL_BRIGHT;
		int overlay = OverlayTexture.NO_OVERLAY;

		for (int i = 0; i < n - 1; i++) {
			Vec3 a = trail.get(i).add(0.0, CENTER_Y, 0.0);
			Vec3 b = trail.get(i + 1).add(0.0, CENTER_Y, 0.0);
			Vec3 dir = b.subtract(a);
			double len = dir.length();
			if (len < 1.0E-5) {
				continue;
			}
			dir = dir.scale(1.0 / len);
			Vec3 ref = Math.abs(dir.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
			Vec3 side1 = dir.cross(ref).normalize();
			Vec3 side2 = dir.cross(side1).normalize();

			float fa = 1.0F - (float) i / (n - 1);
			float fb = 1.0F - (float) (i + 1) / (n - 1);
			int colorA = argb(TRAIL_MAX_ALPHA * fa * fa);
			int colorB = argb(TRAIL_MAX_ALPHA * fb * fb);
			double wa = TRAIL_HALF_WIDTH * fa;
			double wb = TRAIL_HALF_WIDTH * fb;

			for (Vec3 side : new Vec3[]{side1, side2}) {
				Vec3[] corners = {
						a.subtract(side.scale(wa)), a.add(side.scale(wa)),
						b.add(side.scale(wb)), b.subtract(side.scale(wb))};
				float[] us = {0.0F, 1.0F, 1.0F, 0.0F};
				float[] vs = {0.0F, 0.0F, 1.0F, 1.0F};
				int[] colors = {colorA, colorA, colorB, colorB};
				// frente e verso (sem culling de faces)
				for (int pass = 0; pass < 2; pass++) {
					for (int k = 0; k < 4; k++) {
						int idx = pass == 0 ? k : 3 - k;
						Vec3 c = corners[idx];
						p.set((float) c.x, (float) c.y, (float) c.z, 1.0F).mul(matrix);
						normal.set(0.0F, 1.0F, 0.0F);
						pose.transformNormal(normal, normal);
						consumer.addVertex(p.x(), p.y(), p.z(), colors[idx], us[idx], vs[idx],
								overlay, light, normal.x(), normal.y(), normal.z());
					}
				}
			}
		}
	}

	private static int argb(float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
		return (a << 24) | 0x00FFFFFF;
	}
}
