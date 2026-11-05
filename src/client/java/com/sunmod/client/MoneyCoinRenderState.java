package com.sunmod.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class MoneyCoinRenderState extends EntityRenderState {
	/** Idade em ticks (com tick parcial). */
	public float age;
	/** Pontos do rastro relativos à posição da moeda; o primeiro é a própria moeda (0,0,0). */
	public final List<Vec3> trail = new ArrayList<>();
}
