package com.sunmod.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Texto da HUD com a fonte padrão do Minecraft, escala livre e contorno preto (como nas referências). */
final class HudText {
	private static final int[][] OUTLINE = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};

	/**
	 * @param x       posição em unidades de GUI (centro horizontal se {@code centered}, senão a esquerda)
	 * @param centerY centro vertical do texto
	 */
	static void draw(GuiGraphics graphics, Font font, String text, float x, float centerY, float scale, int color, boolean centered) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, centerY);
		graphics.pose().scale(scale);
		int drawX = centered ? -font.width(text) / 2 : 0;
		int drawY = -4; // metade da altura da fonte (9)
		for (int[] o : OUTLINE) {
			graphics.drawString(font, text, drawX + o[0], drawY + o[1], 0xFF000000, false);
		}
		graphics.drawString(font, text, drawX, drawY, color, false);
		graphics.pose().popMatrix();
	}

	/** Largura em unidades de GUI do texto na escala dada. */
	static float width(Font font, String text, float scale) {
		return font.width(text) * scale;
	}

	private HudText() {}
}
