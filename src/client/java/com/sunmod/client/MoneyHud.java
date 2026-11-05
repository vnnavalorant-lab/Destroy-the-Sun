package com.sunmod.client;

import com.sunmod.ModAttachments;
import com.sunmod.SunMod;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Money Coins (canto inferior esquerdo): ícone da moeda + dinheiro ("$00").
 * O dinheiro não é item: vem do anexo {@link ModAttachments#MONEY}, sincronizado com o jogador.
 * Ícone em alta resolução (4 px por unidade de GUI, filtro suave).
 */
public class MoneyHud implements HudElement {
	private static final Identifier ICON = SunMod.id("textures/gui/money_hud_icon.png");
	private static final int ICON_W_PX = 112, ICON_H_PX = 131;
	private static final int TEX_PER_UNIT = 4;

	@Override
	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.isSpectator()) {
			return;
		}
		int money = mc.player.getAttachedOrElse(ModAttachments.MONEY, 0);

		float iconH = (float) ICON_H_PX / TEX_PER_UNIT;
		float x = 11.0F;
		float y = graphics.guiHeight() - 4.0F - iconH;

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(1.0F / TEX_PER_UNIT);
		graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, 0, 0, 0, 0, ICON_W_PX, ICON_H_PX, ICON_W_PX, ICON_H_PX);
		graphics.pose().popMatrix();

		HudText.draw(graphics, mc.font, String.format("$%02d", money), 47.0F, y + iconH / 2.0F, 1.7F, 0xFFFFFFFF, false);
	}
}
