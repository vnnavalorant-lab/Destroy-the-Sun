package com.sunmod.client;

import com.sunmod.Instability;
import com.sunmod.ModAttachments;
import com.sunmod.PowerUps;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * HUD do Evento de Instabilidade Solar: barra "INSTABILITY PROGRESS" + "GELO INFINITO ATIVO" no topo, névoa
 * avermelhada no horizonte, painel "INSTABILITY CLEARED!" ao concluir (e depois a tela de benefício), popup "IMPULSO DE
 * PODER!" ao pegar um power-up, lista dos power-ups ativos e o contador de gemas (canto inferior esquerdo).
 */
public class InstabilityHud implements HudElement {
	private static final int GOLD = 0xFFFFC21E, WHITE = 0xFFFFFFFF, CYAN = 0xFF7FD8FF, PINK = 0xFFE63DD0, GRAY = 0xFFC8CCD4;
	private static final long CLEARED_PANEL_MS = 6000L;
	private static final long POPUP_MS = 4500L;

	// ---- estado recebido do servidor (só cliente) ----
	private static long clearedAtMs = -1L;
	private static int clearedSeconds, clearedReward, clearedBonus;
	private static boolean perkPending;
	private static String popupId;
	private static long popupUntilMs;
	private static final Map<String, Long> ACTIVE_UNTIL_MS = new HashMap<>();

	public static void onCleared(int seconds, int reward, int bonus) {
		clearedAtMs = System.currentTimeMillis();
		clearedSeconds = seconds;
		clearedReward = reward;
		clearedBonus = bonus;
		perkPending = true;
	}

	public static void onPowerUp(String id, int ticks, boolean popup) {
		long now = System.currentTimeMillis();
		ACTIVE_UNTIL_MS.put(id, now + ticks * 50L);
		if (popup) {
			popupId = id;
			popupUntilMs = now + POPUP_MS;
		}
	}

	private static ItemStack iconFor(String id) {
		return new ItemStack(id.equals(PowerUps.FREQUENCY) ? Items.LIGHTNING_ROD : Items.BLUE_ICE);
	}

	@Override
	public void render(GuiGraphics g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.isSpectator()) {
			return;
		}
		int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2;
		long nowMs = System.currentTimeMillis();

		if (Instability.isActive()) {
			// névoa vermelha/rosa no horizonte
			g.fillGradient(0, (int) (h * 0.30), w, (int) (h * 0.62), 0x00FF5070, 0x38FF5070);
			g.fillGradient(0, (int) (h * 0.62), w, h, 0x38FF5070, 0x00FF5070);
			this.drawProgress(g, mc, cx);
		}

		// gemas (rosa), acima das Money Coins
		int gems = mc.player.getAttachedOrElse(ModAttachments.GEMS, 0);
		if (gems > 0 || Instability.isActive()) {
			HudText.draw(g, mc.font, "\u2666 " + gems, 11.0F, h - 4.0F - 33.0F - 7.0F, 1.3F, PINK, false);
		}

		if (clearedAtMs > 0 && nowMs - clearedAtMs < CLEARED_PANEL_MS) {
			this.drawCleared(g, mc, cx, (int) (h * 0.26));
		}
		if (perkPending && clearedAtMs > 0 && nowMs - clearedAtMs >= CLEARED_PANEL_MS) {
			perkPending = false;
			if (mc.screen == null) {
				mc.setScreen(new PerkScreen());
			}
		}

		if (popupId != null && nowMs < popupUntilMs) {
			this.drawPopup(g, mc, cx - 130, (int) (h * 0.2), popupId);
		}
		this.drawActivePowerUps(g, mc, w, (int) (h * 0.34), nowMs);
	}

	private void drawProgress(GuiGraphics g, Minecraft mc, int cx) {
		int barW = 110, x0 = cx - barW / 2, y0 = 12;
		int pct = Instability.getProgressPercent();
		String pctText = pct + "%";
		HudText.draw(g, mc.font, Component.translatable("hud.sunmod.instability_progress").getString(), x0, y0 + 3, 0.8F, GOLD, false);
		HudText.draw(g, mc.font, pctText, x0 + barW - HudText.width(mc.font, pctText, 0.8F), y0 + 3, 0.8F, WHITE, false);
		g.fill(x0 - 1, y0 + 8, x0 + barW + 1, y0 + 18, 0xFF000000);
		g.fill(x0, y0 + 9, x0 + barW, y0 + 17, 0xFF202838);
		int fill = Math.round(barW * Math.min(100, pct) / 100.0F);
		if (fill > 0) {
			g.fill(x0, y0 + 9, x0 + fill, y0 + 17, 0xFFF4B41E);
			g.fill(x0, y0 + 9, x0 + fill, y0 + 11, 0xFFFFD35A);
		}
		HudText.draw(g, mc.font, Component.translatable("hud.sunmod.infinite_ice").getString(), cx, y0 + 24, 0.65F, CYAN, true);
	}

	private void drawCleared(GuiGraphics g, Minecraft mc, int cx, int y) {
		HudText.draw(g, mc.font, Component.translatable("hud.sunmod.instability_cleared").getString(), cx, y, 1.6F, GOLD, true);
		HudText.draw(g, mc.font, Component.translatable("hud.sunmod.seconds", clearedSeconds).getString(), cx, y + 13, 0.8F, WHITE, true);
		this.drawRow(g, mc, cx, y + 24, Component.translatable("hud.sunmod.reward").getString(), clearedReward);
		this.drawRow(g, mc, cx, y + 42, Component.translatable("hud.sunmod.time_bonus").getString(), clearedBonus);
	}

	private void drawRow(GuiGraphics g, Minecraft mc, int cx, int y, String label, int gems) {
		int rw = 120;
		g.fill(cx - rw / 2, y, cx + rw / 2, y + 15, 0xCC0E1320);
		HudText.draw(g, mc.font, label, cx - rw / 2 + 6, y + 8, 0.8F, WHITE, false);
		String value = "+" + gems + " \u2666";
		HudText.draw(g, mc.font, value, cx + rw / 2 - 6 - HudText.width(mc.font, value, 0.9F), y + 8, 0.9F, PINK, false);
	}

	private void drawPopup(GuiGraphics g, Minecraft mc, int x, int y, String id) {
		g.fill(x - 1, y - 1, x + 261, y + 35, 0xFF3C4A5E);
		g.fill(x, y, x + 260, y + 34, 0xF0121824);
		g.pose().pushMatrix();
		g.pose().translate(x + 5.0F, y + 5.0F);
		g.pose().scale(1.5F);
		g.renderItem(iconFor(id), 0, 0);
		g.pose().popMatrix();
		HudText.draw(g, mc.font, Component.translatable("hud.sunmod.power_up").getString(), x + 34, y + 7, 0.6F, CYAN, false);
		HudText.draw(g, mc.font, Component.translatable("powerup.sunmod." + id + ".name").getString(), x + 34, y + 16, 1.0F, WHITE, false);
		Component desc = Component.translatable("powerup.sunmod." + id + ".desc",
				(int) Math.round(PowerUps.FREQUENCY_BONUS * 100.0), PowerUps.DURATION_SECONDS);
		HudText.draw(g, mc.font, desc.getString(), x + 34, y + 27, 0.5F, GRAY, false);
	}

	private void drawActivePowerUps(GuiGraphics g, Minecraft mc, int w, int y, long nowMs) {
		int line = 0;
		for (Map.Entry<String, Long> e : ACTIVE_UNTIL_MS.entrySet()) {
			long left = e.getValue() - nowMs;
			if (left <= 0L) {
				continue;
			}
			String text = Component.translatable("powerup.sunmod." + e.getKey() + ".name").getString()
					+ " " + (left + 999L) / 1000L + "s";
			HudText.draw(g, mc.font, text, w - 6.0F - HudText.width(mc.font, text, 0.75F), y + line * 10.0F, 0.75F, CYAN, false);
			line++;
		}
	}
}
