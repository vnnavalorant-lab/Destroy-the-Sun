package com.sunmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Malha de quads convertida de um .bbmodel (assets/sunmod/models/*.mesh), já em escala de blocos.
 * Triângulos têm o último vértice repetido. Formato por vértice: x y z u v nx ny nz.
 */
final class QuadMesh {
	static final QuadMesh SUN = new QuadMesh("sun_vanilla");
	static final QuadMesh MACHINE_ICE = new QuadMesh("machine_ice");
	static final QuadMesh UPGRADER = new QuadMesh("upgrader");

	private static final int FLOATS_PER_VERTEX = 8;

	private final String name;
	private float[] data;
	private int faceCount;

	private QuadMesh(String name) {
		this.name = name;
	}

	private synchronized void load() {
		if (this.data != null) {
			return;
		}
		String path = "/assets/sunmod/models/" + this.name + ".mesh";
		try (InputStream raw = QuadMesh.class.getResourceAsStream(path)) {
			if (raw == null) {
				throw new IOException("Recurso não encontrado: " + path);
			}
			DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
			in.readInt(); // magic "SUN1"
			int layers = in.readInt();
			float[][] layerData = new float[layers][];
			int total = 0;
			for (int l = 0; l < layers; l++) {
				int count = in.readInt();
				layerData[l] = new float[count * 4 * FLOATS_PER_VERTEX];
				for (int i = 0; i < layerData[l].length; i++) {
					layerData[l][i] = in.readFloat();
				}
				total += count;
			}
			float[] all = new float[total * 4 * FLOATS_PER_VERTEX];
			int off = 0;
			for (float[] ld : layerData) { // camadas na ordem do arquivo (internas primeiro)
				System.arraycopy(ld, 0, all, off, ld.length);
				off += ld.length;
			}
			this.faceCount = total;
			this.data = all;
		} catch (IOException e) {
			throw new IllegalStateException("Falha ao carregar a malha " + this.name, e);
		}
	}

	/**
	 * @param flat true = sem sombreamento (normal fixa para cima, usado no Sol que brilha por conta própria)
	 */
	void render(PoseStack.Pose pose, VertexConsumer consumer, int light, boolean flat) {
		this.load();
		Matrix4f matrix = pose.pose();
		Vector4f p = new Vector4f();
		Vector3f n = new Vector3f();
		int overlay = OverlayTexture.NO_OVERLAY;

		int idx = 0;
		for (int f = 0; f < this.faceCount; f++) {
			for (int v = 0; v < 4; v++) {
				p.set(this.data[idx], this.data[idx + 1], this.data[idx + 2], 1.0F).mul(matrix);
				if (flat) {
					n.set(0.0F, 1.0F, 0.0F);
				} else {
					n.set(this.data[idx + 5], this.data[idx + 6], this.data[idx + 7]);
				}
				pose.transformNormal(n, n);
				consumer.addVertex(p.x(), p.y(), p.z(), 0xFFFFFFFF, this.data[idx + 3], this.data[idx + 4],
						overlay, light, n.x(), n.y(), n.z());
				idx += FLOATS_PER_VERTEX;
			}
		}
	}
}
