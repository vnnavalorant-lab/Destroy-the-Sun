package com.sunmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: um power-up ficou ativo por {@code ticks} (popup "IMPULSO DE PODER!" se {@code popup}). */
public record PowerUpPayload(String id, int ticks, boolean popup) implements CustomPacketPayload {
	public static final Type<PowerUpPayload> TYPE = new Type<>(SunMod.id("power_up"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PowerUpPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, PowerUpPayload::id,
			ByteBufCodecs.VAR_INT, PowerUpPayload::ticks,
			ByteBufCodecs.BOOL, PowerUpPayload::popup,
			PowerUpPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
