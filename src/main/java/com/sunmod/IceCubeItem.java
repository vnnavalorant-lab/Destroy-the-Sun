package com.sunmod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** Funciona como um arco: segure o botão direito para carregar e solte para arremessar. */
public class IceCubeItem extends Item {
	/** Máximo de Ice Cubes que o jogador pode ter no inventário (o Machine Ice não entrega acima disso). */
	public static final int MAX_IN_INVENTORY = 64;

	public IceCubeItem(Properties properties) {
		super(properties);
	}

	/** Tira 1 Ice Cube do inventário (munição das armas). No criativo não gasta. Devolve false se não houver. */
	public static boolean consumeOne(Player player) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		Container inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ModItems.ICE_CUBE)) {
				s.shrink(1);
				return true;
			}
		}
		return false;
	}

	/** Tem pelo menos 1 Ice Cube (ou está no criativo)? */
	public static boolean hasAny(Player player) {
		return player.getAbilities().instabuild || MachineIceEntity.countIceCubes(player) > 0;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BOW;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
		if (!(user instanceof Player player)) {
			return false;
		}
		int chargeTicks = this.getUseDuration(stack, user) - remainingUseTicks;
		float power = BowItem.getPowerForTime(chargeTicks); // 0.0 .. 1.0, igual ao arco
		if (power < 0.1F) {
			return false;
		}

		if (level instanceof ServerLevel serverLevel) {
			IceCubeProjectile projectile = new IceCubeProjectile(level, player, stack.copyWithCount(1));
			projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);
			serverLevel.addFreshEntity(projectile);
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8F,
				1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
		player.awardStat(Stats.ITEM_USED.get(this));
		stack.consume(1, player); // não consome no criativo
		return true;
	}
}
