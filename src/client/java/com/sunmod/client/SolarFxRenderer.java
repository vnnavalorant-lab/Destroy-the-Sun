package com.sunmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sunmod.CometEntity;
import com.sunmod.FxEntity;
import com.sunmod.SolarFragmentEntity;
import com.sunmod.SunMod;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Desenha os efeitos visuais do mod sem partículas: faixas com degradê (rastros), colunas de luz, discos no chão,
 * brilhos e anéis texturizados (mesma técnica do raio do Lazer). Tudo é calculado a cada frame a partir do tempo da
 * entidade, então os efeitos são contínuos e suaves (nada pisca nem "esguicha").
 * <ul>
 *   <li>Fragmento Solar em voo: estrela brilhante com halo e rastro laranja/branco; no chão: estrela dourada flutuando,
 *       com coluna de luz até o céu e brilho no chão.</li>
 *   <li>Cometa: bola de fogo com cauda.</li>
 *   <li>{@link FxEntity}: poça de lava, explosão (bola de fogo + onda de choque), explosão no ar e mira de ponto fraco.</li>
 * </ul>
 */
public class SolarFxRenderer<T extends Entity> extends EntityRenderer<T, SolarFxRenderState> {
	private static final Identifier GLOW = SunMod.id("textures/entity/fx_glow.png");
	private static final Identifier BEAM = SunMod.id("textures/entity/fx_beam.png");
	private static final Identifier RING = SunMod.id("textures/entity/fx_ring.png");
	private static final Identifier FLARE = SunMod.id("textures/entity/fx_flare.png");
	private static final Identifier FRAGMENT = SunMod.id("textures/entity/fx_fragment.png");
	private static final Identifier LAVA = SunMod.id("textures/entity/fx_lava.png");
	private static final Identifier RETICLE = SunMod.id("textures/entity/fx_reticle.png");

	private static final int K_FRAG_FLYING = 1, K_FRAG_LANDED = 2, K_COMET = 3, K_LAVA = 4, K_EXPLOSION = 5, K_BLAST = 6, K_WEAK = 7;

	private static final int ORANGE = 0xFF8A1E, GOLD = 0xFFB030, WARM_WHITE = 0xFFF2C0, WHITE = 0xFFFFFF;

	private interface Drawer {
		void draw(PoseStack.Pose pose, VertexConsumer consumer);
	}

	public SolarFxRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	/** Colunas de luz e poças são maiores que a caixa da entidade: a caixa de culling é bem generosa. */
	@Override
	protected AABB getBoundingBoxForCulling(T entity) {
		return entity.getBoundingBox().inflate(14.0, 100.0, 14.0);
	}

	@Override
	public SolarFxRenderState createRenderState() {
		return new SolarFxRenderState();
	}

	@Override
	public void extractRenderState(T entity, SolarFxRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.vx = (float) (entity.getX() - entity.xo);
		state.vy = (float) (entity.getY() - entity.yo);
		state.vz = (float) (entity.getZ() - entity.zo);
		state.centerY = entity.getBbHeight() * 0.5F;
		state.seed = (entity.getId() * 0.6180339F) % 1.0F;
		state.kind = 0;
		if (entity instanceof FxEntity fx) {
			state.kind = switch (fx.getKind()) {
				case FxEntity.LAVA_POOL -> K_LAVA;
				case FxEntity.WEAK_POINT -> K_WEAK;
				case FxEntity.BLAST -> K_BLAST;
				default -> K_EXPLOSION;
			};
			state.radius = fx.getRadius();
			state.life = fx.getLife();
			state.tint = fx.getTint();
		} else if (entity instanceof SolarFragmentEntity fragment) {
			state.kind = fragment.isLanded() ? K_FRAG_LANDED : K_FRAG_FLYING;
		} else if (entity instanceof CometEntity) {
			state.kind = K_COMET;
		}
	}

	@Override
	public void submit(SolarFxRenderState s, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		switch (s.kind) {
			case K_FRAG_FLYING -> flyingFragment(s, poseStack, collector);
			case K_FRAG_LANDED -> landedFragment(s, poseStack, collector);
			case K_COMET -> comet(s, poseStack, collector);
			case K_LAVA -> lavaPool(s, poseStack, collector);
			case K_EXPLOSION, K_BLAST -> explosion(s, poseStack, collector, s.kind == K_EXPLOSION);
			case K_WEAK -> weakPoint(s, poseStack, collector);
			default -> {
			}
		}
		super.submit(s, poseStack, collector, camera); // nome em cima (Fragmento Solar)
	}

	private static void layer(SubmitNodeCollector c, PoseStack ps, int order, Identifier texture, Drawer drawer) {
		c.order(order).submitCustomGeometry(ps, RenderTypes.entityTranslucent(texture),
				(pose, consumer) -> drawer.draw(pose, consumer));
	}

	/** Direção do movimento (unitária); para baixo se a entidade estiver parada. */
	private static Vector3f direction(SolarFxRenderState s) {
		Vector3f d = new Vector3f(s.vx, s.vy, s.vz);
		if (d.lengthSquared() < 1.0E-6F) {
			return new Vector3f(0.0F, -1.0F, 0.0F);
		}
		return d.normalize();
	}

	// ------------------------------------------------------------------ fragmento em voo

	private static void flyingFragment(SolarFxRenderState s, PoseStack ps, SubmitNodeCollector c) {
		float pulse = 1.0F + 0.08F * Mth.sin(s.ageInTicks * 0.9F);
		Vector3f dir = direction(s);
		float cy = s.centerY, len = 13.0F;

		layer(c, ps, 0, BEAM, (pose, consumer) -> { // rastro: laranja largo + núcleo branco fino, somem na ponta
			Vector3f head = FxDraw.cam(pose, 0.0F, cy, 0.0F);
			Vector3f tail = FxDraw.cam(pose, -dir.x * len, cy - dir.y * len, -dir.z * len);
			Vector3f tailShort = FxDraw.cam(pose, -dir.x * len * 0.62F, cy - dir.y * len * 0.62F, -dir.z * len * 0.62F);
			FxDraw.ribbon(pose, consumer, tail, head, 0.0F, 1.7F * pulse, FxDraw.argb(0xFF6A10, 0.0F), FxDraw.argb(0xFF9A28, 0.85F));
			FxDraw.ribbon(pose, consumer, tailShort, head, 0.0F, 0.6F, FxDraw.argb(0xFFD070, 0.0F), FxDraw.argb(WHITE, 1.0F));
		});
		layer(c, ps, 1, GLOW, (pose, consumer) -> {
			Vector3f head = FxDraw.cam(pose, 0.0F, cy, 0.0F);
			FxDraw.billboardTowardCamera(pose, consumer, head, 7.0F * pulse, 0.0F, FxDraw.argb(ORANGE, 0.55F), 0.5F);
			FxDraw.billboardTowardCamera(pose, consumer, head, 3.2F * pulse, 0.0F, FxDraw.argb(WARM_WHITE, 0.9F), 0.5F);
		});
		layer(c, ps, 2, FLARE, (pose, consumer) ->
				FxDraw.billboardTowardCamera(pose, consumer, FxDraw.cam(pose, 0.0F, cy, 0.0F), 5.0F * pulse, s.ageInTicks * 0.02F,
						FxDraw.argb(WHITE, 1.0F), 0.5F));
	}

	// ------------------------------------------------------------------ fragmento no chão

	private static void landedFragment(SolarFxRenderState s, PoseStack ps, SubmitNodeCollector c) {
		float age = s.ageInTicks;
		float pulse = 1.0F + 0.07F * Mth.sin(age * 0.25F);
		float cy = s.centerY + 0.2F * Mth.sin(age * 0.1F + s.seed * 6.2831F); // flutua devagar
		float ground = -0.5F;                                                   // a entidade fica 0,5 acima do chão
		float height = 90.0F;

		layer(c, ps, 0, BEAM, (pose, consumer) -> { // coluna de luz até o céu: planos cruzados, laranja + núcleo branco
			Vector3f base = FxDraw.cam(pose, 0.0F, ground, 0.0F);
			int bottom = FxDraw.argb(0xFF9A28, 0.85F), top = FxDraw.argb(0xFF9A28, 0.0F);
			float quarter = (float) (Math.PI / 4.0), half = (float) (Math.PI / 2.0);
			FxDraw.vertical(pose, consumer, base, height, 0.75F * pulse, 0.0F, bottom, top);
			FxDraw.vertical(pose, consumer, base, height, 0.75F * pulse, half, bottom, top);
			FxDraw.vertical(pose, consumer, base, height, 0.55F * pulse, quarter, bottom, top);
			FxDraw.vertical(pose, consumer, base, height, 0.55F * pulse, quarter + half, bottom, top);
			int innerBottom = FxDraw.argb(WHITE, 1.0F), innerTop = FxDraw.argb(WHITE, 0.0F);
			FxDraw.vertical(pose, consumer, base, height * 0.8F, 0.22F, 0.0F, innerBottom, innerTop);
			FxDraw.vertical(pose, consumer, base, height * 0.8F, 0.22F, half, innerBottom, innerTop);
		});
		layer(c, ps, 1, GLOW, (pose, consumer) -> {
			FxDraw.flat(pose, consumer, FxDraw.cam(pose, 0.0F, ground + 0.05F, 0.0F), 3.4F * pulse, 0.0F, FxDraw.argb(GOLD, 0.75F));
			FxDraw.billboardTowardCamera(pose, consumer, FxDraw.cam(pose, 0.0F, cy, 0.0F), 5.0F * pulse, 0.0F, FxDraw.argb(GOLD, 0.6F), 0.4F);
		});
		layer(c, ps, 2, FRAGMENT, (pose, consumer) ->
				FxDraw.billboardTowardCamera(pose, consumer, FxDraw.cam(pose, 0.0F, cy, 0.0F), 2.8F, age * 0.025F,
						FxDraw.argb(WHITE, 1.0F), 0.4F));
	}

	// ------------------------------------------------------------------ cometa

	private static void comet(SolarFxRenderState s, PoseStack ps, SubmitNodeCollector c) {
		float pulse = 1.0F + 0.06F * Mth.sin(s.ageInTicks * 0.7F);
		Vector3f dir = direction(s);
		float speed = (float) Math.sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz);
		float len = Mth.clamp(speed * 7.0F, 5.0F, 14.0F); // lançado = cauda longa; em órbita = cauda curta
		float cy = s.centerY;

		layer(c, ps, 0, BEAM, (pose, consumer) -> {
			Vector3f head = FxDraw.cam(pose, 0.0F, cy, 0.0F);
			Vector3f tail = FxDraw.cam(pose, -dir.x * len, cy - dir.y * len, -dir.z * len);
			FxDraw.ribbon(pose, consumer, tail, head, 0.0F, 2.4F * pulse, FxDraw.argb(0xFF5A10, 0.0F), FxDraw.argb(0xFF8A20, 0.85F));
			FxDraw.ribbon(pose, consumer, tail, head, 0.0F, 0.9F, FxDraw.argb(0xFFE070, 0.0F), FxDraw.argb(0xFFF0B0, 1.0F));
		});
		layer(c, ps, 1, GLOW, (pose, consumer) -> {
			Vector3f head = FxDraw.cam(pose, 0.0F, cy, 0.0F);
			FxDraw.billboardTowardCamera(pose, consumer, head, 6.0F * pulse, 0.0F, FxDraw.argb(ORANGE, 0.5F), 0.5F);
			FxDraw.billboardTowardCamera(pose, consumer, head, 2.6F * pulse, 0.0F, FxDraw.argb(WARM_WHITE, 0.95F), 0.5F);
		});
	}

	// ------------------------------------------------------------------ poça de lava

	private static void lavaPool(SolarFxRenderState s, PoseStack ps, SubmitNodeCollector c) {
		float age = s.ageInTicks, r = s.radius;
		float fade = Math.min(1.0F, age / 6.0F) * Mth.clamp((s.life - age) / 16.0F, 0.0F, 1.0F);
		float pulse = 0.8F + 0.2F * Mth.sin(age * 0.15F);

		layer(c, ps, 0, GLOW, (pose, consumer) -> // brilho externo
				FxDraw.flat(pose, consumer, FxDraw.cam(pose, 0.0F, 0.03F, 0.0F), r * 1.35F, 0.0F, FxDraw.argb(0xFF6A10, 0.45F * fade * pulse)));
		layer(c, ps, 1, LAVA, (pose, consumer) -> // disco de lava, girando bem devagar
				FxDraw.flat(pose, consumer, FxDraw.cam(pose, 0.0F, 0.05F, 0.0F), r, age * 0.004F, FxDraw.argb(WHITE, fade)));
		layer(c, ps, 2, GLOW, (pose, consumer) -> // miolo amarelo que pulsa
				FxDraw.flat(pose, consumer, FxDraw.cam(pose, 0.0F, 0.07F, 0.0F), r * 0.7F, 0.0F, FxDraw.argb(0xFFE040, 0.55F * fade * pulse)));
	}

	// ------------------------------------------------------------------ explosão

	private static void explosion(SolarFxRenderState s, PoseStack ps, SubmitNodeCollector c, boolean ground) {
		float t = Mth.clamp(s.ageInTicks / Math.max(1, s.life), 0.0F, 1.0F);
		float e = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t); // abre rápido e desacelera
		float fade = (float) Math.pow(1.0F - t, 1.4);
		float r = s.radius;
		int tint = s.tint;

		if (ground) { // onda de choque no chão
			layer(c, ps, 0, RING, (pose, consumer) ->
					FxDraw.flat(pose, consumer, FxDraw.cam(pose, 0.0F, 0.12F, 0.0F), r * (0.3F + 1.7F * e), 0.0F,
							FxDraw.argb(FxDraw.mix(WHITE, tint, 0.4F), fade * 0.9F)));
		}
		layer(c, ps, 1, GLOW, (pose, consumer) -> {
			for (int i = 0; i < 7; i++) { // bola de fogo: 7 lóbulos que se afastam do centro
				float a = s.seed * 6.2831F + i * 2.399F;
				float frac = (i * 0.381F + s.seed) % 1.0F;
				float dist = r * 0.5F * e * (0.35F + 0.65F * frac);
				float y = r * (0.25F + 0.35F * e * (0.4F + frac));
				float size = r * (0.9F + 0.6F * frac) * (0.3F + 0.9F * e);
				int color = FxDraw.argb(FxDraw.mix(0xFFF2C0, tint, t * 1.2F), fade * 0.9F);
				FxDraw.billboardTowardCamera(pose, consumer, FxDraw.cam(pose, Mth.cos(a) * dist, y, Mth.sin(a) * dist), size, 0.0F, color, size * 0.3F);
			}
			Vector3f core = FxDraw.cam(pose, 0.0F, r * (0.28F + 0.2F * e), 0.0F);
			FxDraw.billboardTowardCamera(pose, consumer, core, r * 2.4F * (0.3F + 0.9F * e), 0.0F,
					FxDraw.argb(FxDraw.mix(WHITE, tint, t), fade), r * 0.4F);
		});
		if (t < 0.25F) { // clarão branco nos primeiros instantes
			float k = 1.0F - t * 4.0F;
			layer(c, ps, 2, GLOW, (pose, consumer) ->
					FxDraw.billboardTowardCamera(pose, consumer, FxDraw.cam(pose, 0.0F, r * 0.4F, 0.0F), r * 6.0F * k, 0.0F,
							FxDraw.argb(WHITE, 0.5F * k), r * 0.5F));
		}
	}

	// ------------------------------------------------------------------ ponto super fraco

	private static void weakPoint(SolarFxRenderState s, PoseStack ps, SubmitNodeCollector c) {
		float age = s.ageInTicks, r = s.radius;
		float fade = Math.min(1.0F, age / 8.0F) * Mth.clamp((s.life - age) / 10.0F, 0.0F, 1.0F);
		float pulse = 1.0F + 0.08F * Mth.sin(age * 0.2F);
		float pull = r * 0.9F; // puxa para a câmera: o ponto está colado na superfície do Sol

		layer(c, ps, 0, GLOW, (pose, consumer) ->
				FxDraw.billboardTowardCamera(pose, consumer, FxDraw.cam(pose, 0.0F, 0.0F, 0.0F), r * 2.4F * pulse, 0.0F,
						FxDraw.argb(0xFF5020, 0.45F * fade), pull));
		layer(c, ps, 1, RETICLE, (pose, consumer) -> {
			Vector3f p = FxDraw.cam(pose, 0.0F, 0.0F, 0.0F);
			FxDraw.billboardTowardCamera(pose, consumer, p, r * 2.3F * pulse, age * 0.02F, FxDraw.argb(0xFF3A2A, 0.95F * fade), pull);
			FxDraw.billboardTowardCamera(pose, consumer, p, r * 1.5F, -age * 0.035F, FxDraw.argb(0xFFB040, 0.8F * fade), pull);
		});
	}
}
