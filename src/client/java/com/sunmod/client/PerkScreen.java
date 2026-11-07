package com.sunmod.client;

import com.sunmod.PickPerkPayload;
import com.sunmod.Perks;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * "ESCOLHER BENEFÍCIO": aparece depois de concluir um Evento de Instabilidade. Três cartas (Dose Dupla, Eco de Poder,
 * Força de Destruição); clicar numa envia {@link PickPerkPayload} e fecha. Não fecha com ESC (é preciso escolher).
 */
public class PerkScreen extends Screen {
	private static final int CARD_W = 120, CARD_H = 136, GAP = 10;
	private static final int GOLD = 0xFFFFC21E, WHITE = 0xFFFFFFFF, GRAY = 0xFFC8CCD4;

	public PerkScreen() {
		super(Component.translatable("gui.sunmod.perk.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private int cardX(int i) {
		int total = Perks.ALL.size() * CARD_W + (Perks.ALL.size() - 1) * GAP;
		return (this.width - total) / 2 + i * (CARD_W + GAP);
	}

	private int cardY() {
		return this.height / 2 - CARD_H / 2 + 8;
	}

	private static Item iconFor(String id) {
		return switch (id) {
			case Perks.DOUBLE_DOSE -> Items.NETHER_STAR;
			case Perks.POWER_ECHO -> Items.AMETHYST_SHARD;
			default -> Items.TNT;
		};
	}

	private static Object[] argsFor(String id) {
		return switch (id) {
			case Perks.DOUBLE_DOSE -> new Object[] {(int) Math.round(Perks.DOUBLE_DOSE_CHANCE * 100.0)};
			case Perks.POWER_ECHO -> new Object[] {Perks.ECHO_SECONDS};
			default -> new Object[] {(int) Math.round(Perks.DESTRUCTION_BONUS * 100.0)};
		};
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		HudText.draw(g, this.font, this.title.getString(), this.width / 2.0F, this.cardY() - 18.0F, 2.0F, GOLD, true);

		for (int i = 0; i < Perks.ALL.size(); i++) {
			String id = Perks.ALL.get(i);
			int x = this.cardX(i), y = this.cardY();
			boolean hover = mouseX >= x && mouseX < x + CARD_W && mouseY >= y && mouseY < y + CARD_H;
			g.fill(x - 1, y - 1, x + CARD_W + 1, y + CARD_H + 1, hover ? GOLD : 0xFF2C3446);
			g.fill(x, y, x + CARD_W, y + CARD_H, hover ? 0xF01C2436 : 0xF0141A28);

			g.pose().pushMatrix();
			g.pose().translate(x + CARD_W / 2.0F - 20.0F, y + 12.0F);
			g.pose().scale(2.5F);
			g.renderItem(new ItemStack(iconFor(id)), 0, 0);
			g.pose().popMatrix();

			HudText.draw(g, this.font, Component.translatable("perk.sunmod." + id + ".name").getString(),
					x + CARD_W / 2.0F, y + 66.0F, 1.1F, WHITE, true);
			List<FormattedCharSequence> lines = this.font.split(
					Component.translatable("perk.sunmod." + id + ".desc", argsFor(id)), CARD_W - 14);
			for (int l = 0; l < Math.min(5, lines.size()); l++) {
				g.drawString(this.font, lines.get(l), x + 7, y + 80 + l * 10, GRAY, false);
			}
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			for (int i = 0; i < Perks.ALL.size(); i++) {
				int x = this.cardX(i), y = this.cardY();
				if (event.x() >= x && event.x() < x + CARD_W && event.y() >= y && event.y() < y + CARD_H) {
					ClientPlayNetworking.send(new PickPerkPayload(Perks.ALL.get(i)));
					this.onClose();
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}
}
