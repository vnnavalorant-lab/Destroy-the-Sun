package com.sunmod.client;

import com.sunmod.ModEntities;
import com.sunmod.OpenUpgraderPayload;
import com.sunmod.SunMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class SunModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(ModEntities.SUN, SunRenderer::new);
		EntityRendererRegistry.register(ModEntities.ICE_CUBE_PROJECTILE, ThrownItemRenderer::new);
		EntityRendererRegistry.register(ModEntities.MACHINE_ICE, context ->
				new StationRenderer<>(context, QuadMesh.MACHINE_ICE, SunMod.id("textures/entity/machine_ice.png")));
		EntityRendererRegistry.register(ModEntities.UPGRADER, context ->
				new StationRenderer<>(context, QuadMesh.UPGRADER, SunMod.id("textures/entity/upgrader.png")));

		EntityRendererRegistry.register(ModEntities.MONEY_COIN, MoneyCoinRenderer::new);
		EntityRendererRegistry.register(ModEntities.LASER_BEAM, LaserBeamRenderer::new);

		// GUI "Melhorias": o servidor manda abrir quando o jogador usa o Upgrader
		ClientPlayNetworking.registerGlobalReceiver(OpenUpgraderPayload.TYPE,
				(payload, context) -> context.client().setScreen(new UpgraderScreen()));

		// HUDs: Ice (inferior direito), Sun Temperature (superior direito) e Money Coins (inferior esquerdo)
		HudElementRegistry.addLast(SunMod.id("ice_hud"), new IceHud());
		HudElementRegistry.addLast(SunMod.id("sun_temperature_hud"), new SunTemperatureHud());
		HudElementRegistry.addLast(SunMod.id("money_hud"), new MoneyHud());
	}
}
