package com.sunmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: abre a GUI "Melhorias" (enviado quando o jogador usa o Upgrader). */
public record OpenUpgraderPayload() implements CustomPacketPayload {
	public static final Type<OpenUpgraderPayload> TYPE = new Type<>(SunMod.id("open_upgrader"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenUpgraderPayload> CODEC =
			StreamCodec.unit(new OpenUpgraderPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
