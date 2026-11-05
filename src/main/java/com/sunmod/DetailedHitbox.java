package com.sunmod;

import net.minecraft.world.phys.Vec3;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Hitbox detalhada: uma caixa orientada (OBB) por cubo do modelo .bbmodel, já com
 * rotação e com os contornos (outline/inflate), em blocos, relativa ao centro dos pés da entidade.
 * Os arquivos .bin são gerados a partir dos .bbmodel (data/sunmod/hitboxes/).
 */
public final class DetailedHitbox {
	private static final int FLOATS_PER_BOX = 15; // centro(3) meia-medida(3) eixos(9)

	private final float[] data;
	private final int count;

	private DetailedHitbox(float[] data, int count) {
		this.data = data;
		this.count = count;
	}

	public static DetailedHitbox load(String name) {
		String path = "/data/" + SunMod.MOD_ID + "/hitboxes/" + name + ".bin";
		try (InputStream raw = DetailedHitbox.class.getResourceAsStream(path)) {
			if (raw == null) {
				throw new IOException("Recurso não encontrado: " + path);
			}
			DataInputStream in = new DataInputStream(new BufferedInputStream(raw));
			int count = in.readInt();
			float[] data = new float[count * FLOATS_PER_BOX];
			for (int i = 0; i < data.length; i++) {
				data[i] = in.readFloat();
			}
			return new DetailedHitbox(data, count);
		} catch (IOException e) {
			throw new IllegalStateException("Falha ao carregar a hitbox " + name, e);
		}
	}

	/**
	 * O segmento from→to (coordenadas do mundo) toca alguma parte do modelo?
	 *
	 * @param origin posição da entidade (centro dos pés)
	 * @param yawDeg rotação da entidade; o modelo é desenhado girado em -yaw no eixo Y
	 */
	public boolean rayHits(Vec3 from, Vec3 to, Vec3 origin, float yawDeg) {
		double yaw = Math.toRadians(yawDeg);
		double cos = Math.cos(yaw);
		double sin = Math.sin(yaw);

		// mundo -> espaço do modelo (inverso da rotação de render)
		double ax = from.x - origin.x, ay = from.y - origin.y, az = from.z - origin.z;
		double bx = to.x - origin.x, by = to.y - origin.y, bz = to.z - origin.z;
		double oX = ax * cos + az * sin, oZ = -ax * sin + az * cos;
		double eX = bx * cos + bz * sin, eZ = -bx * sin + bz * cos;
		double dX = eX - oX, dY = by - ay, dZ = eZ - oZ;

		for (int b = 0; b < count; b++) {
			int i = b * FLOATS_PER_BOX;
			double px = oX - data[i], py = ay - data[i + 1], pz = oZ - data[i + 2];
			double tMin = 0.0, tMax = 1.0;
			boolean miss = false;
			for (int k = 0; k < 3 && !miss; k++) {
				int a = i + 6 + k * 3; // eixo k (linha da matriz)
				double o = data[a] * px + data[a + 1] * py + data[a + 2] * pz;
				double d = data[a] * dX + data[a + 1] * dY + data[a + 2] * dZ;
				double h = data[i + 3 + k];
				if (Math.abs(d) < 1.0E-9) {
					if (Math.abs(o) > h) {
						miss = true;
					}
				} else {
					double t1 = (-h - o) / d;
					double t2 = (h - o) / d;
					if (t1 > t2) {
						double tmp = t1; t1 = t2; t2 = tmp;
					}
					tMin = Math.max(tMin, t1);
					tMax = Math.min(tMax, t2);
					if (tMin > tMax) {
						miss = true;
					}
				}
			}
			if (!miss) {
				return true;
			}
		}
		return false;
	}
}
