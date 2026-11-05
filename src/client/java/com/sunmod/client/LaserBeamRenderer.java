package com.sunmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sunmod.LaserBeamEntity;
import com.sunmod.SunMod;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Desenha o raio do Lazer Ice: uma fita grossa sempre virada para a câmera, com textura em degradê
 * (centro branco → ciano → transparente nas bordas), mais um brilho no cano e outro no ponto de impacto.
 *
 * <p>As posições são calculadas no espaço "relativo à câmera" (a câmera fica na origem depois de
 * multiplicar pela matriz da pose), o mesmo espaço usado pelo {@code ShieldMesh}.
 */
public class LaserBeamRenderer extends EntityRenderer<LaserBeamEntity, LaserBeamRenderState> {
	private static final Identifier BEAM_TEXTURE = SunMod.id("textures/entity/laser_beam.png");
	private static final Identifier GLOW_TEXTURE = SunMod.id("textures/entity/laser_glow.png");

	/** Largura total da fita em blocos (o centro branco ocupa ~1/4 dela). */
	private static final float BEAM_WIDTH = 0.7F;
	private static final float MUZZLE_GLOW = 0.9F;

	public LaserBeamRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public LaserBeamRenderState createRenderState() {
		return new LaserBeamRenderState();
	}

	@Override
	public void extractRenderState(LaserBeamEntity entity, LaserBeamRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		Vec3 start = entity.getStart();
		Vec3 end = entity.getEnd();
		state.sx = (float) (start.x - state.x);
		state.sy = (float) (start.y - state.y);
		state.sz = (float) (start.z - state.z);
		state.ex = (float) (end.x - state.x);
		state.ey = (float) (end.y - state.y);
		state.ez = (float) (end.z - state.z);
		state.endSize = entity.getEndSize();
	}

	@Override
	public void submit(LaserBeamRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		float pulse = 1.0F + 0.07F * Mth.sin(state.ageInTicks * 1.9F); // leve pulsação: raio "vivo"
		float width = BEAM_WIDTH * pulse;

		collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(BEAM_TEXTURE),
				(pose, consumer) -> ribbon(pose, consumer, state, width));
		collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(GLOW_TEXTURE),
				(pose, consumer) -> glows(pose, consumer, state, pulse));
		super.submit(state, poseStack, collector, camera);
	}

	private static void ribbon(PoseStack.Pose pose, VertexConsumer consumer, LaserBeamRenderState s, float width) {
		Matrix4f m = pose.pose();
		Vector4f a4 = new Vector4f(s.sx, s.sy, s.sz, 1.0F).mul(m);
		Vector4f b4 = new Vector4f(s.ex, s.ey, s.ez, 1.0F).mul(m);
		Vector3f a = new Vector3f(a4.x(), a4.y(), a4.z());
		Vector3f b = new Vector3f(b4.x(), b4.y(), b4.z());
		Vector3f d = new Vector3f(b).sub(a);
		if (d.lengthSquared() < 1.0E-6F) {
			return;
		}
		Vector3f sideA = side(d, a, width * 0.5F);
		Vector3f sideB = side(d, b, width * 0.5F);

		Vector3f[] p = {
				new Vector3f(a).sub(sideA), new Vector3f(a).add(sideA),
				new Vector3f(b).add(sideB), new Vector3f(b).sub(sideB)};
		float[] u = {0.0F, 1.0F, 1.0F, 0.0F};
		float[] v = {0.0F, 0.0F, 1.0F, 1.0F};
		emit(pose, consumer, p, u, v);
	}

	private static void glows(PoseStack.Pose pose, VertexConsumer consumer, LaserBeamRenderState s, float pulse) {
		Matrix4f m = pose.pose();
		Vector4f a4 = new Vector4f(s.sx, s.sy, s.sz, 1.0F).mul(m);
		Vector4f b4 = new Vector4f(s.ex, s.ey, s.ez, 1.0F).mul(m);
		Vector3f a = new Vector3f(a4.x(), a4.y(), a4.z());
		Vector3f b = new Vector3f(b4.x(), b4.y(), b4.z());

		// brilho no cano
		glow(pose, consumer, a, MUZZLE_GLOW * pulse);

		// brilho no impacto, puxado um pouco para perto da câmera (para não ser cortado pela superfície)
		Vector3f dir = new Vector3f(b).sub(a);
		if (dir.lengthSquared() > 1.0E-6F) {
			dir.normalize();
		}
		float size = s.endSize * pulse;
		Vector3f c = new Vector3f(b).fma(-size * 0.5F, dir);
		glow(pose, consumer, c, size);
	}

	/** Quadrado de brilho virado para a câmera, centrado em {@code c} (espaço relativo à câmera). */
	private static void glow(PoseStack.Pose pose, VertexConsumer consumer, Vector3f c, float size) {
		Vector3f forward = new Vector3f(c);
		if (forward.lengthSquared() < 1.0E-6F) {
			return;
		}
		forward.normalize();
		Vector3f right = new Vector3f(forward).cross(0.0F, 1.0F, 0.0F);
		if (right.lengthSquared() < 1.0E-6F) {
			right.set(1.0F, 0.0F, 0.0F);
		}
		right.normalize().mul(size * 0.5F);
		Vector3f up = new Vector3f(right).cross(forward).normalize().mul(size * 0.5F);

		Vector3f[] p = {
				new Vector3f(c).sub(right).sub(up), new Vector3f(c).add(right).sub(up),
				new Vector3f(c).add(right).add(up), new Vector3f(c).sub(right).add(up)};
		float[] u = {0.0F, 1.0F, 1.0F, 0.0F};
		float[] v = {1.0F, 1.0F, 0.0F, 0.0F};
		emit(pose, consumer, p, u, v);
	}

	/** Vetor lateral (perpendicular ao raio e à linha de visão) com o comprimento {@code half}. */
	private static Vector3f side(Vector3f dir, Vector3f point, float half) {
		Vector3f side = new Vector3f(dir).cross(point);
		if (side.lengthSquared() < 1.0E-8F) {
			side.set(dir).cross(0.0F, 1.0F, 0.0F);
			if (side.lengthSquared() < 1.0E-8F) {
				side.set(1.0F, 0.0F, 0.0F);
			}
		}
		return side.normalize().mul(half);
	}

	/** Escreve o quad de frente para a câmera (a câmera está na origem), invertendo a ordem se preciso. */
	private static void emit(PoseStack.Pose pose, VertexConsumer consumer, Vector3f[] p, float[] u, float[] v) {
		Vector3f e1 = new Vector3f(p[1]).sub(p[0]);
		Vector3f e2 = new Vector3f(p[2]).sub(p[0]);
		Vector3f normal = e1.cross(e2);
		boolean facing = normal.dot(-p[0].x(), -p[0].y(), -p[0].z()) > 0.0F;

		Vector3f n = new Vector3f();
		pose.transformNormal(n.set(0.0F, 1.0F, 0.0F), n); // sem sombreamento
		int light = LightTexture.FULL_BRIGHT;
		int overlay = OverlayTexture.NO_OVERLAY;
		for (int step = 0; step < 4; step++) {
			int k = facing ? step : 3 - step;
			consumer.addVertex(p[k].x(), p[k].y(), p[k].z(), 0xFFFFFFFF, u[k], v[k], overlay, light, n.x(), n.y(), n.z());
		}
	}
}
