package com.sunmod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: o jogador apertou a tecla de uma habilidade (o servidor valida nível, recarga e alvo). */
public record UseAbilityPayload(String id) implements CustomPacketPayload {
	public static final Type<UseAbilityPayload> TYPE = new Type<>(SunMod.id("use_ability"));
	public static final StreamCodec<RegistryFriendlyByteBuf, UseAbilityPayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.STRING_UTF8, UseAbilityPayload::id, UseAbilityPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
