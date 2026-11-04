package com.sunmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sunmod.SunEntity;
import com.sunmod.SunMod;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;

/**
 * Desenha a malha das 3 esferas do sun_vanilla.bbmodel (50 x 50 x 50 blocos).
 * As 2 esferas externas são o "outline" (casco invertido): precisam de culling
 * de faces para que só apareça a borda ao redor do núcleo.
 */
public class SunRenderer extends EntityRenderer<SunEntity, SunRenderState> {
	private static final Identifier TEXTURE = SunMod.id("textures/entity/sun_vanilla.png");

	public SunRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public SunRenderState createRenderState() {
		return new SunRenderState();
	}

	@Override
	public void submit(SunRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), SunMesh::render);
		super.submit(state, poseStack, collector, camera);
	}
}
