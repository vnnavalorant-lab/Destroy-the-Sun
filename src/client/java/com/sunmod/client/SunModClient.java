package com.sunmod.client;

import com.sunmod.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class SunModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.SUN, SunRenderer::new);
		EntityRendererRegistry.register(ModEntities.ICE_CUBE_PROJECTILE, ThrownItemRenderer::new);
	}
}
