package com.sunmod.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class LaserBeamRenderState extends EntityRenderState {
	/** Início e fim do raio relativos à posição de desenho da entidade (blocos). */
	public float sx, sy, sz, ex, ey, ez;
	/** Tamanho do brilho no ponto de impacto. */
	public float endSize;
}
