package com.sunmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: o Evento de Instabilidade está ativo? e quantos % do progresso (barra da HUD, gelo infinito). */
public record InstabilityStatePayload(boolean active, int progress) implements CustomPacketPayload {
	public static final Type<InstabilityStatePayload> TYPE = new Type<>(SunMod.id("instability_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, InstabilityStatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, InstabilityStatePayload::active,
			ByteBufCodecs.VAR_INT, InstabilityStatePayload::progress,
			InstabilityStatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
