package com.sunmod.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

/** Dados que o {@link SolarFxRenderer} copia da entidade para desenhar um frame. */
public class SolarFxRenderState extends EntityRenderState {
	/** Qual efeito desenhar (constantes K_* do renderer). */
	public int kind;
	/** Velocidade da entidade em blocos por tick (para a direção do rastro). */
	public float vx, vy, vz;
	/** Altura do centro visual acima do ponto da entidade. */
	public float centerY;
	/** Tamanho do efeito, duração em ticks e cor principal (RGB). */
	public float radius;
	public int life;
	public int tint;
	/** Número fixo (0..1) por entidade, para variar sem aleatoriedade a cada frame. */
	public float seed;
}
