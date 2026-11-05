package com.sunmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: o jogador clicou em uma melhoria na GUI (o servidor valida preço, nível e distância). */
public record BuyUpgradePayload(String id) implements CustomPacketPayload {
	public static final Type<BuyUpgradePayload> TYPE = new Type<>(SunMod.id("buy_upgrade"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BuyUpgradePayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.STRING_UTF8, BuyUpgradePayload::id, BuyUpgradePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
