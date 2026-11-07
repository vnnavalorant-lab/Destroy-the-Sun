package com.sunmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: evento concluído (tempo, gemas de recompensa e de bônus). Depois o cliente abre a escolha de benefício. */
public record InstabilityClearedPayload(int seconds, int reward, int bonus) implements CustomPacketPayload {
	public static final Type<InstabilityClearedPayload> TYPE = new Type<>(SunMod.id("instability_cleared"));
	public static final StreamCodec<RegistryFriendlyByteBuf, InstabilityClearedPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, InstabilityClearedPayload::seconds,
			ByteBufCodecs.VAR_INT, InstabilityClearedPayload::reward,
			ByteBufCodecs.VAR_INT, InstabilityClearedPayload::bonus,
			InstabilityClearedPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
