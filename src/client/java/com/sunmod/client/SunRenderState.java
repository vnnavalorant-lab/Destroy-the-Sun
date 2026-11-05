package com.sunmod.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class SunRenderState extends EntityRenderState {
	/** Opacidade da barreira (0 = sem barreira, 1 = totalmente visível). */
	public float shieldAlpha;
	/** Giro lento da barreira, em graus. */
	public float shieldSpin;
}
