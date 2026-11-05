package com.sunmod;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

public final class ModAttachments {
	/**
	 * Dinheiro (Money Coins) do jogador: não é item, não ocupa inventário. É salvo com o jogador,
	 * mantido ao morrer e sincronizado com o próprio jogador para aparecer na HUD.
	 */
	public static final AttachmentType<Integer> MONEY = AttachmentRegistry.create(
			SunMod.id("money"),
			builder -> builder
					.initializer(() -> 0)
					.persistent(Codec.INT)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	public static void register() {
		// Só carrega a classe (o campo estático faz o registro).
	}

	private ModAttachments() {}
}
