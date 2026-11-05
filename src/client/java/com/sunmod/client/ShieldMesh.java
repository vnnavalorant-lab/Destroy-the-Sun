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
 * UV equirretangular (mesmas fórmulas de tools_gen_shield_texture.py). Cada quad sai duas vezes,
 * uma virada para fora e outra para dentro, então a barreira aparece de qualquer lado
 * (inclusive o fundo dela, visto através da frente, e por dentro se o jogador estiver dentro).
 */
final class ShieldMesh {
	private static final int LON_SEGMENTS = 64;
	private static final int LAT_SEGMENTS = 32;

	/** x y z u v por vértice, 4 vértices por quad; primeiro as faces externas, depois as internas. */
	private static final float[] DATA = build();
	private static final int FLOATS_PER_VERTEX = 5;

	private ShieldMesh() {
	}

	private static float[] build() {
		int quads = LON_SEGMENTS * LAT_SEGMENTS;
		float[] out = new float[quads * 2 * 4 * 5];
		int idx = 0;
		for (int pass = 0; pass < 2; pass++) {
			for (int j = 0; j < LAT_SEGMENTS; j++) {
				for (int i = 0; i < LON_SEGMENTS; i++) {
					// Ordem anti-horária vista de fora: baixo-esq, baixo-dir, cima-dir, cima-esq.
					int[][] corners = {{i, j + 1}, {i + 1, j + 1}, {i + 1, j}, {i, j}};
					if (pass == 1) { // cópia interna: ordem invertida
						corners = new int[][]{corners[3], corners[2], corners[1], corners[0]};
					}
					for (int[] c : corners) {
						float u = (float) c[0] / LON_SEGMENTS;
						float v = (float) c[1] / LAT_SEGMENTS;
						double lon = -Math.PI + 2.0 * Math.PI * u;
						double lat = Math.PI / 2.0 - Math.PI * v;
						out[idx++] = (float) (Math.cos(lat) * Math.sin(lon) * SunEntity.SHIELD_RADIUS);
						out[idx++] = (float) (Math.sin(lat) * SunEntity.SHIELD_RADIUS);
						out[idx++] = (float) (Math.cos(lat) * Math.cos(lon) * SunEntity.SHIELD_RADIUS);
						out[idx++] = u;
						out[idx++] = v;
					}
				}
			}
		}
		return out;
	}

	/** @param argb cor/opacidade aplicada à malha (branco com alpha = fade da barreira) */
	static void render(PoseStack.Pose pose, VertexConsumer consumer, int argb, int light) {
		Matrix4f matrix = pose.pose();
		Vector4f p = new Vector4f();
		Vector3f n = new Vector3f();
		pose.transformNormal(n.set(0.0F, 1.0F, 0.0F), n); // sem sombreamento: normal fixa
		int overlay = OverlayTexture.NO_OVERLAY;
		for (int idx = 0; idx < DATA.length; idx += FLOATS_PER_VERTEX) {
			p.set(DATA[idx], DATA[idx + 1], DATA[idx + 2], 1.0F).mul(matrix);
			consumer.addVertex(p.x(), p.y(), p.z(), argb, DATA[idx + 3], DATA[idx + 4],
					overlay, light, n.x(), n.y(), n.z());
		}
	}
}
