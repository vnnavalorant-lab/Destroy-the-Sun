package com.sunmod.client;

import com.sunmod.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;
import com.sunmod.OpenUpgraderPayload;
import com.sunmod.SunMod;
import com.sunmod.UseAbilityPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class SunModClient implements ClientModInitializer {
	/** Teclas das habilidades (configuráveis em Controles > Jogabilidade). Cada uma manda o id ao servidor. */
	private static void registerAbilityKeys() {
		String[][] abilities = {{"adrenaline", "Z"}, {"instant_reload", "X"}, {"critical_offense", "C"}};
		int[] keyCodes = {InputConstants.KEY_Z, InputConstants.KEY_X, InputConstants.KEY_C};
		KeyMapping[] keys = new KeyMapping[abilities.length];
		for (int i = 0; i < abilities.length; i++) {
			keys[i] = KeyBindingHelper.registerKeyBinding(new KeyMapping(
					"key.sunmod." + abilities[i][0], InputConstants.Type.KEYSYM, keyCodes[i], KeyMapping.Category.GAMEPLAY));
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			for (int i = 0; i < keys.length; i++) {
				while (keys[i].consumeClick()) {
					if (client.player != null && client.screen == null) {
						ClientPlayNetworking.send(new UseAbilityPayload(abilities[i][0]));
					}
				}
			}
		});
	}

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
		// Cometa: item "Fire Charge" voando, 3x maior e sempre claro (fica visível em volta do Sol).
		EntityRendererRegistry.register(ModEntities.COMET, context -> new ThrownItemRenderer<>(context, 3.0F, true));

		// Fragmento Solar: brilho branco em voo / estrela dourada no chão, 2,5x maior e sempre claro.
		EntityRendererRegistry.register(ModEntities.SOLAR_FRAGMENT, context -> new ThrownItemRenderer<>(context, 2.5F, true));

		registerAbilityKeys();

		// GUI "Melhorias": o servidor manda abrir quando o jogador usa o Upgrader
		ClientPlayNetworking.registerGlobalReceiver(OpenUpgraderPayload.TYPE,
				(payload, context) -> context.client().setScreen(new UpgraderScreen()));

		// HUDs: Ice (inferior direito), Sun Temperature (superior direito) e Money Coins (inferior esquerdo)
		HudElementRegistry.addLast(SunMod.id("ice_hud"), new IceHud());
		HudElementRegistry.addLast(SunMod.id("sun_temperature_hud"), new SunTemperatureHud());
		HudElementRegistry.addLast(SunMod.id("money_hud"), new MoneyHud());
	}
}
