package com.sunmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Malha do sun_vanilla.bbmodel convertida para o arquivo binário
 * assets/sunmod/models/sun_vanilla.mesh (3 esferas, já em escala de blocos).
 * Cada face é um quad (os triângulos têm o último vértice repetido).
 */
final class SunMesh {
	private static final String PATH = "/assets/sunmod/models/sun_vanilla.mesh";
	private static final int FLOATS_PER_VERTEX = 8; // x y z u v nx ny nz

	private static float[] data;
	private static int faceCount;

	private static synchronized void load() {
		if (data != null) {
			return;
		}
		try (InputStream raw = SunMesh.class.getResourceAsStream(PATH)) {
			if (raw == null) {
				throw new IOException("Recurso não encontrado: " + PATH);
			}
			DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
			in.readInt(); // magic "SUN1"
			int layers = in.readInt();
			int[] counts = new int[layers];
			float[][] layerData = new float[layers][];
			int total = 0;
			for (int l = 0; l < layers; l++) {
				counts[l] = in.readInt();
				layerData[l] = new float[counts[l] * 4 * FLOATS_PER_VERTEX];
				for (int i = 0; i < layerData[l].length; i++) {
					layerData[l][i] = in.readFloat();
				}
				total += counts[l];
			}
			float[] all = new float[total * 4 * FLOATS_PER_VERTEX];
			int off = 0;
			for (float[] ld : layerData) { // camadas internas primeiro
				System.arraycopy(ld, 0, all, off, ld.length);
				off += ld.length;
			}
			faceCount = total;
			data = all;
		} catch (IOException e) {
			throw new IllegalStateException("Falha ao carregar a malha do Sol", e);
		}
	}

	static void render(PoseStack.Pose pose, VertexConsumer consumer) {
		load();
		Matrix4f matrix = pose.pose();
		Vector4f p = new Vector4f();
		Vector3f n = new Vector3f();
		int light = LightTexture.FULL_BRIGHT; // o Sol brilha por conta própria
		int overlay = OverlayTexture.NO_OVERLAY;

		int idx = 0;
		for (int f = 0; f < faceCount; f++) {
			for (int v = 0; v < 4; v++) {
				p.set(data[idx], data[idx + 1], data[idx + 2], 1.0F).mul(matrix);
				n.set(data[idx + 5], data[idx + 6], data[idx + 7]);
				pose.transformNormal(n, n);
				consumer.addVertex(p.x(), p.y(), p.z(), 0xFFFFFFFF, data[idx + 3], data[idx + 4],
						overlay, light, n.x(), n.y(), n.z());
				idx += FLOATS_PER_VERTEX;
			}
		}
	}

	private SunMesh() {}
}
