package com.sunmod.client;

/**
 * Animação "spawn" do money_coin.bbmodel (0,625 s, hold): escala 0 → 1,5 → 1, subida de 6 px e giro de 360° no eixo Y.
 * Interpolação Catmull-Rom, como no Blockbench. Valores em px do modelo (1 px = 1/16 de bloco).
 */
final class CoinAnimation {
	static final float LENGTH = 0.625F;

	private static final float[] T_SCALE = {0.0F, 0.5F, 0.625F};
	private static final float[] V_SCALE = {0.0F, 1.5F, 1.0F};
	private static final float[] T_POS = {0.0F, 0.5F, 0.625F};
	private static final float[] V_POS = {0.0F, 6.0F, 0.0F};

	static float scale(float seconds) {
		return catmullRom(T_SCALE, V_SCALE, seconds);
	}

	/** Deslocamento vertical em px do modelo. */
	static float offsetY(float seconds) {
		return catmullRom(T_POS, V_POS, seconds);
	}

	/** Rotação em Y, em graus (0 → 360 linear). */
	static float rotationY(float seconds) {
		return 360.0F * Math.min(1.0F, Math.max(0.0F, seconds / LENGTH));
	}

	private static float catmullRom(float[] times, float[] values, float t) {
		int last = times.length - 1;
		if (t <= times[0]) {
			return values[0];
		}
		if (t >= times[last]) {
			return values[last];
		}
		int i = 0;
		while (t > times[i + 1]) {
			i++;
		}
		float p1 = values[i];
		float p2 = values[i + 1];
		float p0 = i > 0 ? values[i - 1] : p1;
		float p3 = i + 2 <= last ? values[i + 2] : p2;
		float u = (t - times[i]) / (times[i + 1] - times[i]);
		return 0.5F * ((2.0F * p1) + (-p0 + p2) * u
				+ (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * u * u
				+ (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * u * u * u);
	}

	private CoinAnimation() {}
}
