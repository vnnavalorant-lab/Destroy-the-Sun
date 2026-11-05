package com.sunmod;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Base das máquinas (Machine Ice e Upgrader): ficam paradas, são interagidas pelo botão direito
 * e removidas com um soco. Só contam como "tocadas" se o olhar do jogador acertar o modelo de verdade
 * ({@link DetailedHitbox}), e não apenas a caixa retangular da entidade.
 */
public abstract class StationEntity extends Entity {
	private static final double REACH = 7.0;

	protected int cooldown;

	protected StationEntity(EntityType<? extends StationEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
	}

	protected abstract DetailedHitbox hitbox();

	protected abstract Item dropItem();

	/** Chamado no servidor quando o jogador usa (botão direito) a máquina. */
	public abstract void onUse(Player player, ServerLevel level);

	/** O olhar do jogador acerta alguma parte do modelo (respeitando os outlines)? */
	public boolean isLookedAt(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(REACH));
		return this.hitbox().rayHits(eye, end, this.position(), this.getYRot());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();
		this.setDeltaMovement(Vec3.ZERO);
		if (this.cooldown > 0) {
			this.cooldown--;
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// Soco de um jogador (corpo a corpo) remove a máquina e devolve o item.
		if (source.getDirectEntity() instanceof Player player && this.isLookedAt(player)) {
			if (!player.isCreative()) {
				level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY() + 0.5, this.getZ(),
						new ItemStack(this.dropItem())));
			}
			this.playSound(SoundEvents.ANVIL_PLACE, 0.6F, 0.8F);
			this.discard();
			return true;
		}
		return false;
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
