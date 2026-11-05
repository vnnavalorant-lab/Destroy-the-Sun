package com.sunmod.client;

import com.sunmod.BuyUpgradePayload;
import com.sunmod.ModAttachments;
import com.sunmod.SunMod;
import com.sunmod.Upgrades;
import com.sunmod.Upgrades.Category;
import com.sunmod.Upgrades.Upgrade;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * GUI "Melhorias" do Upgrader (aberta com o botão direito na máquina).
 *
 * <p>Painel com barra de título ciano e 3 colunas (Estatísticas / Armas / Habilidades). Cada coluna rola
 * sozinha (roda do mouse ou arrastando a barra fina ao lado) e o título da coluna rola junto com os cards, como na
 * referência. Clicar em um card compra o próximo nível (o servidor valida tudo em {@link Upgrades#tryBuy}).
 *
 * <p>Tudo é desenhado num painel "virtual" de {@code VW x VH} unidades e escalado para caber na janela, então o
 * visual é igual em qualquer GUI scale. Para mudar cores/medidas mexa nas constantes abaixo; as texturas de fundo vêm de
 * {@code tools_gen_upgrader_gui.py}. Os ícones das melhorias são itens do Minecraft ({@link Upgrade#icon()}), desenhados
 * com o renderizador de itens do jogo; as descrições têm um contorno preto fino para ler bem sobre qualquer fundo.
 */
public class UpgraderScreen extends Screen {
	// ---- Medidas do painel virtual ----
	private static final int VW = 400, VH = 236;
	private static final int TITLE_H = 36;
	private static final int COL_W = 120, COL_GAP = 8, COL_X0 = 12;
	private static final int BODY_TOP = TITLE_H + 4;
	private static final int BODY_BOTTOM = VH - 8;
	private static final int VIEW_H = BODY_BOTTOM - BODY_TOP;
	private static final int HEADER_H = 30;
	private static final int CARD_H = 72, CARD_GAP = 6;
	private static final int CLOSE_X = VW - 28, CLOSE_Y = 8, CLOSE_SIZE = 20;

	// ---- Cores ----
	private static final int COLOR_BORDER = 0xFF0A0C12;
	private static final int COLOR_BODY = 0xFF1B1F2B;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_CANT_AFFORD = 0xFFFF3B4D;
	private static final int COLOR_TRACK = 0xFF3A404E;
	private static final int COLOR_THUMB = 0xFFB4BAC6;

	// ---- Texturas ----
	private static final Identifier TITLE_BAR = SunMod.id("textures/gui/upgrader/title_bar.png");
	private static final Identifier ARROW_UP = SunMod.id("textures/gui/upgrader/arrow_up.png");
	private static final Identifier[] CARD_BG = {
			SunMod.id("textures/gui/upgrader/card_stats.png"),
			SunMod.id("textures/gui/upgrader/card_weapons.png"),
			SunMod.id("textures/gui/upgrader/card_abilities.png")};

	/** Espessura (em unidades do texto) do contorno: título = 1 (grosso), descrição = fino. */
	private static final float OUTLINE_TITLE = 1.0F, OUTLINE_DESC = 0.6F;

	private static final int[][] OUTLINE = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
	private static final Category[] CATEGORIES = Category.values();

	private final List<List<Upgrade>> columns = List.of(
			Upgrades.of(Category.STATS), Upgrades.of(Category.WEAPONS), Upgrades.of(Category.ABILITIES));
	/** Um ItemStack por melhoria, criado uma vez só (o ícone é o item do Minecraft da melhoria). */
	private final Map<String, ItemStack> icons = new HashMap<>();
	private final float[] scroll = new float[3];
	private final float[] scrollTarget = new float[3];

	private float scale = 1.0F, offX, offY;
	private int dragColumn = -1;
	private String failId = null;
	private long failTime;

	public UpgraderScreen() {
		super(Component.translatable("gui.sunmod.upgrader.title"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ transformação janela <-> painel virtual

	private void updateTransform() {
		this.scale = Math.min(this.width * 0.72F / VW, this.height * 0.84F / VH);
		this.offX = (this.width - VW * this.scale) / 2.0F;
		this.offY = (this.height - VH * this.scale) / 2.0F;
	}

	private float vx(double screenX) {
		return (float) ((screenX - this.offX) / this.scale);
	}

	private float vy(double screenY) {
		return (float) ((screenY - this.offY) / this.scale);
	}

	private static int columnX(int column) {
		return COL_X0 + column * (COL_W + COL_GAP);
	}

	private static float contentHeight(int itemCount) {
		return HEADER_H + itemCount * (CARD_H + CARD_GAP) - CARD_GAP + 2;
	}

	private float maxScroll(int column) {
		return Math.max(0.0F, contentHeight(this.columns.get(column).size()) - VIEW_H);
	}

	// ------------------------------------------------------------------ desenho

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		this.updateTransform();
		float mx = this.vx(mouseX), my = this.vy(mouseY);

		for (int c = 0; c < 3; c++) { // rolagem suave
			float diff = this.scrollTarget[c] - this.scroll[c];
			this.scroll[c] = Math.abs(diff) < 0.05F ? this.scrollTarget[c] : this.scroll[c] + diff * 0.3F;
		}

		g.pose().pushMatrix();
		g.pose().translate(this.offX, this.offY);
		g.pose().scale(this.scale);

		g.fill(0, 0, VW, VH, COLOR_BORDER);
		g.fill(2, 2, VW - 2, VH - 2, COLOR_BODY);

		this.drawTitleBar(g, mx, my);
		for (int c = 0; c < 3; c++) {
			this.drawColumn(g, c, mx, my);
		}

		g.pose().popMatrix();
	}

	private void drawTitleBar(GuiGraphics g, float mx, float my) {
		blitTexture(g, TITLE_BAR, 0, 0, 800, 72, 0.5F);
		blitTexture(g, ARROW_UP, 11, 5, 96, 112, 0.24F);
		this.drawText(g, Component.translatable("gui.sunmod.upgrader.title").withStyle(ChatFormatting.BOLD)
				.getVisualOrderText(), 44, 8, 2.0F, COLOR_WHITE, true);

		// botão X (vermelho)
		boolean hover = mx >= CLOSE_X && mx < CLOSE_X + CLOSE_SIZE && my >= CLOSE_Y && my < CLOSE_Y + CLOSE_SIZE;
		g.fill(CLOSE_X - 2, CLOSE_Y - 2, CLOSE_X + CLOSE_SIZE + 2, CLOSE_Y + CLOSE_SIZE + 2, COLOR_BORDER);
		g.fillGradient(CLOSE_X, CLOSE_Y, CLOSE_X + CLOSE_SIZE, CLOSE_Y + CLOSE_SIZE,
				hover ? 0xFFFF8A8A : 0xFFFF5F5F, hover ? 0xFFE83A4A : 0xFFD0202E);
		FormattedCharSequence x = Component.literal("X").withStyle(ChatFormatting.BOLD).getVisualOrderText();
		float xw = this.font.width(x) * 1.5F;
		this.drawText(g, x, CLOSE_X + (CLOSE_SIZE - xw) / 2.0F, CLOSE_Y + 4.5F, 1.5F, COLOR_WHITE, true);
	}

	private void drawColumn(GuiGraphics g, int c, float mx, float my) {
		int colX = columnX(c);
		List<Upgrade> list = this.columns.get(c);
		float top = BODY_TOP - this.scroll[c];

		g.enableScissor(colX - 2, BODY_TOP, colX + COL_W + 2, BODY_BOTTOM);

		// título da coluna (rola junto)
		FormattedCharSequence header = Component.translatable("gui.sunmod.upgrader." + CATEGORIES[c].key)
				.withStyle(ChatFormatting.BOLD).getVisualOrderText();
		float headerScale = Math.min(1.5F, (COL_W - 6) / (float) this.font.width(header));
		float headerW = this.font.width(header) * headerScale;
		this.drawText(g, header, colX + (COL_W - headerW) / 2.0F, top + 8 + (1.5F - headerScale) * 4.5F,
				headerScale, COLOR_WHITE, true);

		boolean mouseInView = my >= BODY_TOP && my < BODY_BOTTOM;
		for (int k = 0; k < list.size(); k++) {
			float y = top + HEADER_H + k * (CARD_H + CARD_GAP);
			if (y + CARD_H < BODY_TOP || y > BODY_BOTTOM) {
				continue; // fora da área visível
			}
			boolean hover = mouseInView && mx >= colX && mx < colX + COL_W && my >= y && my < y + CARD_H;
			g.pose().pushMatrix();
			g.pose().translate(colX, y);
			this.drawCard(g, c, list.get(k), hover);
			g.pose().popMatrix();
		}
		g.disableScissor();

		this.drawScrollbar(g, c);
	}

	/** Desenha um card em coordenadas locais (0,0 = canto do card). */
	private void drawCard(GuiGraphics g, int column, Upgrade up, boolean hover) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int level = Upgrades.getLevel(player, up.id());
		boolean maxed = level >= up.maxLevel();
		int price = up.price(level);
		boolean affordable = player.getAttachedOrElse(ModAttachments.MONEY, 0) >= price;
		boolean locked = level == 0 && up.category() != Category.STATS; // armas/habilidades ainda não compradas

		blitTexture(g, CARD_BG[column], 0, 0, 240, 144, 0.5F);
		if (up.category() == Category.WEAPONS && level == 0) {
			g.fill(2, 2, COL_W - 2, CARD_H - 2, 0x40000000); // arma não comprada fica mais escura
		}
		if (hover) {
			g.fill(2, 2, COL_W - 2, CARD_H - 2, 0x26FFFFFF);
		}
		if (up.id().equals(this.failId)) {
			long age = System.currentTimeMillis() - this.failTime;
			if (age < 350) {
				g.fill(2, 2, COL_W - 2, CARD_H - 2, ((int) (0x70 * (1.0F - age / 350.0F)) << 24) | 0xFF0000);
			}
		}

		// ícone: item do Minecraft num quadro escuro (escurecido enquanto a arma/habilidade não foi comprada)
		g.fill(4, 11, 42, 49, COLOR_BORDER);
		g.fill(5, 12, 41, 48, 0xFF2A3040);
		ItemStack iconStack = this.icons.computeIfAbsent(up.id(), id -> new ItemStack(up.icon()));
		g.pose().pushMatrix();
		g.pose().translate(5.0F, 12.0F);
		g.pose().scale(36.0F / 16.0F); // o item tem 16x16; o quadro tem 36x36
		g.renderItem(iconStack, 0, 0);
		g.pose().popMatrix();
		if (locked) {
			g.fill(5, 12, 41, 48, 0xB4000000);
		}

		// ▼ no canto superior direito
		for (int i = 0; i < 5; i++) {
			g.fill(108 + i - 1, 5 + i - 1, 118 - i + 1, 6 + i + 1, COLOR_BORDER);
		}
		for (int i = 0; i < 5; i++) {
			g.fill(108 + i, 5 + i, 118 - i, 6 + i, COLOR_WHITE);
		}

		// título + (nível/máx), quebrado em até 3 linhas
		Component title = up.name().copy().withStyle(ChatFormatting.BOLD)
				.append(Component.literal(" (" + level + "/" + up.maxLevel() + ")"));
		float titleScale = 0.85F;
		List<FormattedCharSequence> titleLines = this.font.split(title, (int) (72 / titleScale));
		int shown = Math.min(3, titleLines.size());
		for (int i = 0; i < shown; i++) {
			this.drawText(g, titleLines.get(i), 43, 12 + i * 9.0F, titleScale, COLOR_WHITE, true);
		}

		// descrição pequena, com contorno fino; usa todas as linhas que cabem no card
		float descScale = 0.55F;
		float lineH = 6.2F;
		List<FormattedCharSequence> descLines = this.font.split(up.description(), (int) (71 / descScale));
		float descY = 12 + shown * 9.0F + 3;
		int maxDescLines = Math.max(1, (int) ((CARD_H - 4 - descY) / lineH));
		for (int i = 0; i < Math.min(maxDescLines, descLines.size()); i++) {
			this.drawText(g, descLines.get(i), 43, descY + i * lineH, descScale, COLOR_WHITE, OUTLINE_DESC);
		}

		// preço no canto inferior esquerdo (vermelho se não tem dinheiro; some quando já está no nível máximo)
		if (!maxed) {
			FormattedCharSequence priceText = Component.literal(String.format(Locale.US, "$%,d", price))
					.withStyle(ChatFormatting.BOLD).getVisualOrderText();
			this.drawText(g, priceText, 6, CARD_H - 15, 1.0F, affordable ? COLOR_WHITE : COLOR_CANT_AFFORD, true);
		}
	}

	private void drawScrollbar(GuiGraphics g, int c) {
		int x = columnX(c) + COL_W + 3;
		g.fill(x, BODY_TOP, x + 2, BODY_BOTTOM, COLOR_TRACK);
		float max = this.maxScroll(c);
		if (max <= 0.0F) {
			return;
		}
		float content = contentHeight(this.columns.get(c).size());
		float thumbH = Math.max(18.0F, VIEW_H * VIEW_H / content);
		float thumbY = BODY_TOP + (VIEW_H - thumbH) * (this.scroll[c] / max);
		g.fill(x - 1, (int) thumbY, x + 3, (int) (thumbY + thumbH), COLOR_THUMB);
	}

	/** Desenha uma textura de {@code texW x texH} pixels na posição (x,y) do painel, multiplicada por {@code s}. */
	private static void blitTexture(GuiGraphics g, Identifier texture, float x, float y, int texW, int texH, float s) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(s);
		g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, texW, texH, texW, texH);
		g.pose().popMatrix();
	}

	/** Texto na fonte do Minecraft, com escala livre e (opcional) contorno preto grosso. (x,y) = canto superior esquerdo. */
	private void drawText(GuiGraphics g, FormattedCharSequence text, float x, float y, float s, int color, boolean outline) {
		this.drawText(g, text, x, y, s, color, outline ? OUTLINE_TITLE : 0.0F);
	}

	/**
	 * Igual ao de cima, mas com a espessura do contorno livre ({@code outline} em unidades do texto; 0 = sem contorno,
	 * só sombra). O contorno é o texto preto repetido nas 8 direções, deslocado por essa espessura.
	 */
	private void drawText(GuiGraphics g, FormattedCharSequence text, float x, float y, float s, int color, float outline) {
		Font f = this.font;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(s);
		if (outline > 0.0F) {
			for (int[] o : OUTLINE) {
				g.pose().pushMatrix();
				g.pose().translate(o[0] * outline, o[1] * outline);
				g.drawString(f, text, 0, 0, 0xFF000000, false);
				g.pose().popMatrix();
			}
		}
		g.drawString(f, text, 0, 0, color, outline <= 0.0F);
		g.pose().popMatrix();
	}

	// ------------------------------------------------------------------ entrada

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			this.updateTransform();
			float mx = this.vx(event.x()), my = this.vy(event.y());

			if (mx >= CLOSE_X && mx < CLOSE_X + CLOSE_SIZE && my >= CLOSE_Y && my < CLOSE_Y + CLOSE_SIZE) {
				this.playClick(1.0F);
				this.onClose();
				return true;
			}

			for (int c = 0; c < 3; c++) {
				int colX = columnX(c);
				// barra de rolagem: começa a arrastar
				if (mx >= colX + COL_W && mx < colX + COL_W + COL_GAP && my >= BODY_TOP && my < BODY_BOTTOM) {
					this.dragColumn = c;
					this.dragTo(my);
					return true;
				}
				// card
				if (mx >= colX && mx < colX + COL_W && my >= BODY_TOP && my < BODY_BOTTOM) {
					List<Upgrade> list = this.columns.get(c);
					for (int k = 0; k < list.size(); k++) {
						float y = BODY_TOP - this.scroll[c] + HEADER_H + k * (CARD_H + CARD_GAP);
						if (my >= y && my < y + CARD_H) {
							this.tryBuy(list.get(k));
							return true;
						}
					}
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (this.dragColumn >= 0) {
			this.updateTransform();
			this.dragTo(this.vy(event.y()));
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.dragColumn = -1;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		this.updateTransform();
		float mx = this.vx(mouseX);
		for (int c = 0; c < 3; c++) {
			int colX = columnX(c);
			if (mx >= colX - 2 && mx < colX + COL_W + COL_GAP) {
				this.scrollTarget[c] = Mth.clamp(this.scrollTarget[c] - (float) scrollY * 30.0F, 0.0F, this.maxScroll(c));
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	/** Move a coluna arrastada para que a barra fique na altura do mouse. */
	private void dragTo(float my) {
		int c = this.dragColumn;
		float max = this.maxScroll(c);
		float content = contentHeight(this.columns.get(c).size());
		float thumbH = Math.max(18.0F, VIEW_H * VIEW_H / content);
		float ratio = Mth.clamp((my - BODY_TOP - thumbH / 2.0F) / (VIEW_H - thumbH), 0.0F, 1.0F);
		this.scrollTarget[c] = ratio * max;
		this.scroll[c] = this.scrollTarget[c];
	}

	private void tryBuy(Upgrade up) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		int level = Upgrades.getLevel(player, up.id());
		if (level >= up.maxLevel()) {
			this.playClick(0.6F);
			return;
		}
		if (player.getAttachedOrElse(ModAttachments.MONEY, 0) < up.price(level)) {
			this.failId = up.id();
			this.failTime = System.currentTimeMillis();
			this.playClick(0.5F);
			return;
		}
		ClientPlayNetworking.send(new BuyUpgradePayload(up.id()));
		this.playClick(1.2F);
	}

	private void playClick(float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
	}
}
