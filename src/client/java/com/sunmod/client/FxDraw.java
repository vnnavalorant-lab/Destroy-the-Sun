package com.sunmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Peças de desenho dos efeitos visuais (mesma técnica do raio do Lazer): todas as posições ficam no espaço "relativo
 * à câmera" (a câmera na origem, eixos do mundo), obtido com {@link #cam}. Cada peça é um quad texturizado e sempre
 * visível dos dois lados; cor e transparência são por vértice (ARGB), então dá para fazer degradês.
 */
final class FxDraw {
	private FxDraw() {}

	/** Ponto (x, y, z) relativo à entidade -> espaço relativo à câmera. */
	static Vector3f cam(PoseStack.Pose pose, float x, float y, float z) {
		Matrix4f m = pose.pose();
		Vector4f v = new Vector4f(x, y, z, 1.0F).mul(m);
		return new Vector3f(v.x(), v.y(), v.z());
	}

	static int argb(int rgb, float alpha) {
		int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	static int mix(int rgbA, int rgbB, float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round(((rgbA >> 16) & 255) * (1 - t) + ((rgbB >> 16) & 255) * t);
		int g = Math.round(((rgbA >> 8) & 255) * (1 - t) + ((rgbB >> 8) & 255) * t);
		int b = Math.round((rgbA & 255) * (1 - t) + (rgbB & 255) * t);
		return (r << 16) | (g << 8) | b;
	}

	/** Quadrado virado para a câmera, centrado em {@code c} (espaço da câmera), girado em {@code roll} radianos. */
	static void billboard(PoseStack.Pose pose, VertexConsumer consumer, Vector3f c, float size, float roll, int argb) {
		Vector3f forward = new Vector3f(c);
		if (forward.lengthSquared() < 1.0E-6F) {
			return;
		}
		forward.normalize();
		Vector3f right = new Vector3f(forward).cross(0.0F, 1.0F, 0.0F);
		if (right.lengthSquared() < 1.0E-6F) {
			right.set(1.0F, 0.0F, 0.0F);
		}
		right.normalize();
		Vector3f up = new Vector3f(right).cross(forward).normalize();
		float cos = (float) Math.cos(roll), sin = (float) Math.sin(roll);
		Vector3f r = new Vector3f(right).mul(cos).fma(sin, up).mul(size * 0.5F);
		Vector3f u = new Vector3f(up).mul(cos).fma(-sin, right).mul(size * 0.5F);
		Vector3f[] p = {
				new Vector3f(c).sub(r).sub(u), new Vector3f(c).add(r).sub(u),
				new Vector3f(c).add(r).add(u), new Vector3f(c).sub(r).add(u)};
		emit(pose, consumer, p, new float[] {0, 1, 1, 0}, new float[] {1, 1, 0, 0}, new int[] {argb, argb, argb, argb});
	}

	/** Brilho puxado para a câmera (para não ser cortado por uma superfície em que está encostado). */
	static void billboardTowardCamera(PoseStack.Pose pose, VertexConsumer consumer, Vector3f c, float size, float roll, int argb, float pull) {
		Vector3f dir = new Vector3f(c);
		if (dir.lengthSquared() > 1.0E-6F) {
			dir.normalize();
		}
		billboard(pose, consumer, new Vector3f(c).fma(-pull, dir), size, roll, argb);
	}

	/**
	 * Faixa de {@code a} até {@code b} (espaço da câmera), sempre virada para a câmera, com largura e cor diferentes
	 * em cada ponta (rastro que afina e some). A textura é o degradê horizontal {@code fx_beam}.
	 */
	static void ribbon(PoseStack.Pose pose, VertexConsumer consumer, Vector3f a, Vector3f b, float widthA, float widthB, int colorA, int colorB) {
		Vector3f d = new Vector3f(b).sub(a);
		if (d.lengthSquared() < 1.0E-6F) {
			return;
		}
		Vector3f sideA = side(d, a, widthA * 0.5F);
		Vector3f sideB = side(d, b, widthB * 0.5F);
		Vector3f[] p = {
				new Vector3f(a).sub(sideA), new Vector3f(a).add(sideA),
				new Vector3f(b).add(sideB), new Vector3f(b).sub(sideB)};
		emit(pose, consumer, p, new float[] {0, 1, 1, 0}, new float[] {0, 0, 1, 1}, new int[] {colorA, colorA, colorB, colorB});
	}

	/** Quadrado deitado (horizontal) de lado {@code 2 * radius} em {@code c}, girado {@code yaw} radianos. */
	static void flat(PoseStack.Pose pose, VertexConsumer consumer, Vector3f c, float radius, float yaw, int argb) {
		float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
		float[][] corner = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
		Vector3f[] p = new Vector3f[4];
		for (int i = 0; i < 4; i++) {
			float x = corner[i][0] * radius, z = corner[i][1] * radius;
			p[i] = new Vector3f(c.x() + x * cos - z * sin, c.y(), c.z() + x * sin + z * cos);
		}
		emit(pose, consumer, p, new float[] {0, 1, 1, 0}, new float[] {0, 0, 1, 1}, new int[] {argb, argb, argb, argb});
	}

	/** Plano vertical de largura {@code 2 * halfW} e altura {@code height} a partir de {@code base}, girado {@code yaw}. */
	static void vertical(PoseStack.Pose pose, VertexConsumer consumer, Vector3f base, float height, float halfW, float yaw, int colorBottom, int colorTop) {
		float dx = (float) Math.cos(yaw) * halfW, dz = (float) Math.sin(yaw) * halfW;
		Vector3f[] p = {
				new Vector3f(base.x() - dx, base.y(), base.z() - dz), new Vector3f(base.x() + dx, base.y(), base.z() + dz),
				new Vector3f(base.x() + dx, base.y() + height, base.z() + dz), new Vector3f(base.x() - dx, base.y() + height, base.z() - dz)};
		emit(pose, consumer, p, new float[] {0, 1, 1, 0}, new float[] {1, 1, 0, 0}, new int[] {colorBottom, colorBottom, colorTop, colorTop});
	}

	/** Vetor lateral (perpendicular à faixa e à linha de visão) com o comprimento {@code half}. */
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

	/** Escreve o quad de frente para a câmera (câmera na origem), invertendo a ordem dos vértices se preciso. */
	private static void emit(PoseStack.Pose pose, VertexConsumer consumer, Vector3f[] p, float[] u, float[] v, int[] color) {
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
			consumer.addVertex(p[k].x(), p[k].y(), p[k].z(), color[k], u[k], v[k], overlay, light, n.x(), n.y(), n.z());
		}
	}
}
