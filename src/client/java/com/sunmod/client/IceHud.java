package com.sunmod.client;

import com.sunmod.IceCubeItem;
import com.sunmod.Instability;
import com.sunmod.MachineIceEntity;
import com.sunmod.SunMod;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * UI Ice: ícone do Ice Cube + barra escura com o contador "tenho/limite".
 * A barra azul por baixo do número enche conforme os Ice Cubes no inventário.
 *
 * As texturas (assets/sunmod/textures/gui/) estão em alta resolução, 4 pixels de textura por unidade
 * de GUI, e são desenhadas reduzidas com filtro suave (.png.mcmeta "blur"). Assim não ficam
 * serrilhadas em nenhuma escala de GUI; na escala 4 (1080p automático) ficam 1:1 com a arte original.
 */
public class IceHud implements HudElement {
	private static final Identifier BG = SunMod.id("textures/gui/ice_hud_bg.png");
	private static final Identifier FILL = SunMod.id("textures/gui/ice_hud_fill.png");
	private static final Identifier ICON = SunMod.id("textures/gui/ice_hud_icon.png");

	/** Pixels de textura por unidade de GUI. */
	private static final int TEX_PER_UNIT = 4;

	// Medidas em pixels de textura
	private static final int BAR_W = 268, BAR_H = 84;
	private static final int BAR_X = 64, BAR_Y = 16;       // posição da barra em relação ao ícone
	private static final int ICON_W = 100, ICON_H = 115;
	private static final int HIDDEN_FILL = 36;             // trecho da barra escondido atrás do ícone
	private static final int VISIBLE_FILL = 220;           // trecho visível que enche de 0 a 100 %

	// Medidas em unidades de GUI (texto)
	private static final int TEXT_CENTER_X = 49, TEXT_Y = 10;
	private static final int WIDGET_W = (BAR_X + BAR_W) / TEX_PER_UNIT;   // 83
	private static final int WIDGET_H = (ICON_H + TEX_PER_UNIT - 1) / TEX_PER_UNIT; // 29
	private static final int MARGIN = 6;                   // canto inferior direito

	@Override
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || player.isSpectator()) {
			return;
		}

		int max = IceCubeItem.maxFor(player); // cresce com a melhoria Capacidade da Mochila
		boolean infinite = Instability.isInfiniteIce(); // Evento de Instabilidade: "∞ GELO", barra cheia
		int count = infinite ? max : MachineIceEntity.countIceCubes(player);
		float ratio = Math.min(1.0F, (float) count / max);

		int x = graphics.guiWidth() - WIDGET_W - MARGIN;
		int y = graphics.guiHeight() - WIDGET_H - MARGIN;

		// Texturas em alta resolução: desenhadas em coordenadas de textura sob escala 1/4.
		graphics.pose().pushMatrix();
		graphics.pose().translate((float) x, (float) y);
		graphics.pose().scale(1.0F / TEX_PER_UNIT);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BG, BAR_X, BAR_Y, 0, 0, BAR_W, BAR_H, BAR_W, BAR_H);
		if (count > 0) {
			int fillW = HIDDEN_FILL + Math.round(VISIBLE_FILL * ratio);
			graphics.blit(RenderPipelines.GUI_TEXTURED, FILL, BAR_X, BAR_Y, 0, 0, fillW, BAR_H, BAR_W, BAR_H);
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, 0, 0, 0, 0, ICON_W, ICON_H, ICON_W, ICON_H);
		graphics.pose().popMatrix();

		// Número com a fonte padrão do Minecraft (mc.font), branco com sombra, centralizado na barra.
		String text = infinite ? Component.translatable("hud.sunmod.ice_infinite").getString() : count + "/" + max;
		graphics.drawString(mc.font, text, x + TEXT_CENTER_X - mc.font.width(text) / 2, y + TEXT_Y, 0xFFFFFFFF, true);
	}
}
