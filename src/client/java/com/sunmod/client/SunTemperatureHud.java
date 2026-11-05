package com.sunmod.client;

import com.sunmod.SunEntity;
import com.sunmod.SunMod;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Sun Temperature (canto superior direito): a temperatura é a vida do Sol (144.982 °C = 100 %).
 * Cada Ice Cube que acerta o Sol faz o número e a porcentagem caírem. Só aparece com um Sol por perto.
 * O disco (assets/sunmod/textures/gui/sun_hud_disk.png) é o sun_vanilla_icon.png com o contorno preto,
 * em resolução 4 px por unidade de GUI.
 */
public class SunTemperatureHud implements HudElement {
	private static final Identifier DISK = SunMod.id("textures/gui/sun_hud_disk.png");
	private static final int DISK_PX = 336;                 // 84 unidades de GUI
	private static final int TEX_PER_UNIT = 4;

	private static final double SEARCH_RANGE = 400.0;
	private static final int ORANGE = 0xFFFEC12A;
	private static final int WHITE = 0xFFFFFFFF;

	private static SunEntity cached;
	private static long cachedAt = Long.MIN_VALUE;

	@Override
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		SunEntity sun = findSun(mc);
		if (sun == null) {
			return;
		}

		float ratio = Math.max(0.0F, Math.min(1.0F, sun.getHealthValue() / SunEntity.MAX_HEALTH));
		int temperature = Math.round(SunEntity.MAX_TEMPERATURE * ratio);
		int percent = Math.round(100.0F * ratio);

		Font font = mc.font;
		float cx = graphics.guiWidth() - 51.0F;             // centro da coluna (margem como na referência)
		float diskUnits = (float) DISK_PX / TEX_PER_UNIT;
		float circleCenterY = 60.0F;

		// Disco
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx - diskUnits / 2.0F, circleCenterY - diskUnits / 2.0F);
		graphics.pose().scale(1.0F / TEX_PER_UNIT);
		graphics.blit(RenderPipelines.GUI_TEXTURED, DISK, 0, 0, 0, 0, DISK_PX, DISK_PX, DISK_PX, DISK_PX);
		graphics.pose().popMatrix();

		// Título, temperatura e porcentagem (fonte padrão do Minecraft)
		String title = Component.translatable("hud.sunmod.sun_temperature").getString();
		HudText.draw(graphics, font, title, cx, 10.0F, 1.1F, ORANGE, true);

		String temp = String.format(Locale.US, "%,d", temperature) + "\u00B0C";
		float tempScale = Math.min(1.5F, 74.0F / Math.max(1, font.width(temp)));
		HudText.draw(graphics, font, temp, cx, circleCenterY, tempScale, WHITE, true);

		HudText.draw(graphics, font, percent + "%", cx, 114.0F, 1.6F, WHITE, true);
	}

	/** Sol mais próximo carregado no cliente (busca a cada 10 ticks). */
	private static SunEntity findSun(Minecraft mc) {
		long time = mc.level.getGameTime();
		if (cachedAt == Long.MIN_VALUE || Math.abs(time - cachedAt) >= 10 || (cached != null && cached.isRemoved())) {
			cachedAt = time;
			cached = null;
			double best = Double.MAX_VALUE;
			for (SunEntity sun : mc.level.getEntitiesOfClass(SunEntity.class, mc.player.getBoundingBox().inflate(SEARCH_RANGE))) {
				double d = sun.distanceToSqr(mc.player);
				if (d < best) {
					best = d;
					cached = sun;
				}
			}
		}
		return cached;
	}
}
