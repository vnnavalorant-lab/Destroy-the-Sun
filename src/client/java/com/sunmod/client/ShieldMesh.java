package com.sunmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sunmod.SunEntity;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Esfera da barreira do Sol (raio {@link SunEntity#SHIELD_RADIUS}), centrada na origem.
 * UV equirretangular (a textura sun_shield.png já vem com a projeção senoidal "assada",
 * veja tools_gen_shield_texture.py).
 *
 * <p>Sem Z-fighting: cada ponto da tela recebe no máximo UMA camada. A cada frame só saem os quads
 * virados para a câmera (de fora: a metade da frente; de dentro da esfera: todos, com a ordem
 * dos vértices invertida). Nada é desenhado duas vezes no mesmo lugar e o resultado não depende
 * de o render type fazer culling ou não.
 */
final class ShieldMesh {
	private static final int LON_SEGMENTS = 64;
	private static final int LAT_SEGMENTS = 32;
	private static final int QUADS = LON_SEGMENTS * LAT_SEGMENTS;

	/** x y z por vértice, 4 vértices por quad, em ordem anti-horária vista de fora. */
	private static final float[] POS = new float[QUADS * 4 * 3];
	/** u v por vértice. */
	private static final float[] UV = new float[QUADS * 4 * 2];

	static {
		int q = 0;
		for (int j = 0; j < LAT_SEGMENTS; j++) {
			for (int i = 0; i < LON_SEGMENTS; i++) {
				// anti-horário vista de fora: baixo-esq, baixo-dir, cima-dir, cima-esq
				int[][] corners = {{i, j + 1}, {i + 1, j + 1}, {i + 1, j}, {i, j}};
				for (int k = 0; k < 4; k++) {
					float u = (float) corners[k][0] / LON_SEGMENTS;
					float v = (float) corners[k][1] / LAT_SEGMENTS;
					double lon = -Math.PI + 2.0 * Math.PI * u;
					double lat = Math.PI / 2.0 - Math.PI * v;
					int p = (q * 4 + k) * 3;
					POS[p] = (float) (Math.cos(lat) * Math.sin(lon) * SunEntity.SHIELD_RADIUS);
					POS[p + 1] = (float) (Math.sin(lat) * SunEntity.SHIELD_RADIUS);
					POS[p + 2] = (float) (Math.cos(lat) * Math.cos(lon) * SunEntity.SHIELD_RADIUS);
					UV[(q * 4 + k) * 2] = u;
					UV[(q * 4 + k) * 2 + 1] = v;
				}
				q++;
			}
		}
	}

	private ShieldMesh() {
	}

	/** @param argb cor/opacidade aplicada à malha (branco com alpha = fade da barreira) */
	static void render(PoseStack.Pose pose, VertexConsumer consumer, int argb, int light) {
		Matrix4f matrix = pose.pose();
		Vector3f n = new Vector3f();
		pose.transformNormal(n.set(0.0F, 1.0F, 0.0F), n); // sem sombreamento: normal fixa
		int overlay = OverlayTexture.NO_OVERLAY;

		// Neste espaço a câmera está na origem. S = centro da esfera.
		Vector4f s = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F).mul(matrix);
		float r = (float) SunEntity.SHIELD_RADIUS;
		boolean inside = s.x() * s.x() + s.y() * s.y() + s.z() * s.z() < r * r;

		Vector4f v = new Vector4f();
		float[] tx = new float[12];
		for (int q = 0; q < QUADS; q++) {
			float cx = 0.0F, cy = 0.0F, cz = 0.0F;
			for (int k = 0; k < 4; k++) {
				int p = (q * 4 + k) * 3;
				v.set(POS[p], POS[p + 1], POS[p + 2], 1.0F).mul(matrix);
				tx[k * 3] = v.x();
				tx[k * 3 + 1] = v.y();
				tx[k * 3 + 2] = v.z();
				cx += v.x();
				cy += v.y();
				cz += v.z();
			}
			cx *= 0.25F;
			cy *= 0.25F;
			cz *= 0.25F;

			if (!inside) {
				// normal para fora = (centro do quad - centro da esfera); a câmera (origem) vê o quad se
				// a normal aponta para ela: dot(normal, -centro) > 0
				float facing = -((cx - s.x()) * cx + (cy - s.y()) * cy + (cz - s.z()) * cz);
				if (facing <= 0.0F) {
					continue; // metade de trás: não desenha (evita camada dupla/Z-fighting)
				}
			}
			for (int step = 0; step < 4; step++) {
				int k = inside ? 3 - step : step; // por dentro: ordem invertida
				consumer.addVertex(tx[k * 3], tx[k * 3 + 1], tx[k * 3 + 2], argb,
						UV[(q * 4 + k) * 2], UV[(q * 4 + k) * 2 + 1], overlay, light, n.x(), n.y(), n.z());
			}
		}
	}
}
