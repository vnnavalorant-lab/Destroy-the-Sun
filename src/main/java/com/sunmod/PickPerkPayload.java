package com.sunmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: o jogador escolheu um benefício na tela "Escolher Benefício". */
public record PickPerkPayload(String id) implements CustomPacketPayload {
	public static final Type<PickPerkPayload> TYPE = new Type<>(SunMod.id("pick_perk"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PickPerkPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, PickPerkPayload::id,
			PickPerkPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
