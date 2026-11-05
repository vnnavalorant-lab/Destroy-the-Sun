package com.sunmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sunmod.StationEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.Identifier;

/** Renderer das máquinas (Machine Ice e Upgrader): malha convertida do .bbmodel, com culling (outlines). */
public class StationRenderer<T extends StationEntity> extends EntityRenderer<T, StationRenderState> {
	private final QuadMesh mesh;
	private final Identifier texture;

	public StationRenderer(EntityRendererProvider.Context context, QuadMesh mesh, Identifier texture) {
		super(context);
		this.mesh = mesh;
		this.texture = texture;
	}

	@Override
	public StationRenderState createRenderState() {
		return new StationRenderState();
	}

	@Override
	public void extractRenderState(T entity, StationRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.yRot = entity.getYRot();
	}

	@Override
	public void submit(StationRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		// A frente do modelo é o lado -Z; com yaw 0 ela fica de frente para um jogador olhando para o sul.
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
		int light = state.lightCoords;
		collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(this.texture),
				(pose, consumer) -> this.mesh.render(pose, consumer, light, false));
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
