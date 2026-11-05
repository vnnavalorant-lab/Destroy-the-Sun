package com.sunmod;

import com.mojang.brigadier.Command;
import java.util.HashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;

public class SunMod implements ModInitializer {
	public static final String MOD_ID = "sunmod";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModAttachments.register();
		ModEntities.register();
		ModItems.register();

		// Rede da GUI "Melhorias": servidor abre a tela; cliente pede a compra (o servidor valida tudo).
		PayloadTypeRegistry.playS2C().register(OpenUpgraderPayload.TYPE, OpenUpgraderPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BuyUpgradePayload.TYPE, BuyUpgradePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BuyUpgradePayload.TYPE,
				(payload, context) -> Upgrades.tryBuy(context.player(), payload.id()));

		// Habilidades (teclas Z/X/C no cliente) e bônus de velocidade de movimento.
		PayloadTypeRegistry.playC2S().register(UseAbilityPayload.TYPE, UseAbilityPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(UseAbilityPayload.TYPE,
				(payload, context) -> Abilities.activate(context.player(), payload.id()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (var player : server.getPlayerList().getPlayers()) {
				Abilities.tickPlayer(player);
			}
		});

		// /sunmod reset: zera os Money Coins e todas as melhorias de quem digitou (só afeta o próprio jogador).
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("sunmod")
						.then(Commands.literal("reset").executes(ctx -> {
							var player = ctx.getSource().getPlayerOrException();
							player.setAttached(ModAttachments.MONEY, 0);
							player.setAttached(ModAttachments.UPGRADES, new HashMap<String, Integer>());
							Abilities.resetState(player);
							ctx.getSource().sendSuccess(() -> Component.translatable("command.sunmod.reset"), false);
							return Command.SINGLE_SUCCESS;
						}))));

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
			entries.accept(ModItems.SUN);
			entries.accept(ModItems.MACHINE_ICE);
			entries.accept(ModItems.UPGRADER);
			entries.accept(ModItems.ICE_CUBE);
			entries.accept(ModItems.GUN_ICE);
			entries.accept(ModItems.LAZER_ICE);
			entries.accept(ModItems.GRAVITY_GUN);
		});

		// Botão direito em uma máquina: só vale se o olhar acertar o modelo (hitbox detalhada).
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(entity instanceof StationEntity station) || !station.isUsable() || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (hand != InteractionHand.MAIN_HAND || !station.isLookedAt(player)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel serverLevel) {
				station.onUse(player, serverLevel);
			}
			return InteractionResult.SUCCESS;
		});
	}
}
