package com.sunmod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Fragmento Solar (o "meteoro" que sai do Sol). Funciona em duas fases:
 * <ol>
 *   <li><b>Voo</b>: sai da parte de cima do Sol como um brilho branco, faz um arco no céu deixando um rastro de
 *       faíscas laranja e cai perto de um jogador. Os jogadores perto do Sol recebem o aviso "Um Fragmento Solar
 *       está caindo!".</li>
 *   <li><b>No chão</b>: explode ao pousar e fica uma estrela dourada com o nome "Fragmento Solar COMUM" e uma
 *       coluna de faíscas até o céu. Atirar nela com Ice Cube, Gun Ice ou Lazer Ice a estoura em Money Coins.
 *       Se ninguém acertar, some depois de {@link #LANDED_LIFE_TICKS}.</li>
 * </ol>
 * Não é salvo no mundo. Nada aqui usa partículas: o desenho (estrela, halo, rastro, coluna de luz, brilho no chão) é
 * feito pelo {@code SolarFxRenderer} no cliente, e explosões/estouro são {@link FxEntity}. O movimento é todo feito à
 * mão em {@link #tick()}.
 */
public class SolarFragmentEntity extends ThrowableItemProjectile {
	/** Vida do fragmento no chão (um Ice Cube tira 5; a Gun Ice 10 e o Lazer Ice 20, antes dos bônus). */
	public static final float HEALTH = 25.0F;
	/** Ao estourar solta {@code COIN_COUNT} moedas de {@code COIN_VALUE} de dinheiro cada. */
	public static final int COIN_COUNT = 8;
	public static final int COIN_VALUE = 25;
	/** Duração do voo (3,5 s). */
	public static final int FLIGHT_TICKS = 70;
	/** Quanto tempo o fragmento espera no chão antes de sumir (60 s). */
	public static final int LANDED_LIFE_TICKS = 20 * 60;
	/** Jogadores a esta distância do Sol recebem o aviso de queda. */
	public static final double WARN_RANGE = 250.0;

	/** Sincronizado: o cliente só precisa saber se já pousou (muda o desenho). */
	private static final EntityDataAccessor<Boolean> DATA_LANDED =
			SynchedEntityData.defineId(SolarFragmentEntity.class, EntityDataSerializers.BOOLEAN);

	private boolean landed;
	private boolean burst;
	private Vec3 from = Vec3.ZERO;
	private Vec3 to = Vec3.ZERO;
	private double arcHeight;
	private int flightTick;
	private int landedTick;
	private float health = HEALTH;

	public SolarFragmentEntity(EntityType<? extends SolarFragmentEntity> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.noPhysics = true;
	}

	/**
	 * Lança um fragmento do alto do Sol para um ponto do chão a 14-30 blocos de {@code target}, e avisa os
	 * jogadores perto do Sol.
	 */
	public static SolarFragmentEntity launch(ServerLevel level, SunEntity sun, Player target) {
		RandomSource random = level.getRandom();
		Vec3 center = sun.position().add(0.0, SunEntity.RADIUS, 0.0);

		// saída: ponto aleatório na metade de cima do Sol
		double y = 0.3 + random.nextDouble() * 0.7;
		double angle = random.nextDouble() * Math.PI * 2.0;
		double ring = Math.sqrt(1.0 - y * y);
		Vec3 start = center.add(Math.cos(angle) * ring * SunEntity.RADIUS, y * SunEntity.RADIUS,
				Math.sin(angle) * ring * SunEntity.RADIUS);

		// pouso: no chão, perto do jogador
		double landAngle = random.nextDouble() * Math.PI * 2.0;
		double dist = 14.0 + random.nextDouble() * 16.0;
		int x = Mth.floor(target.getX() + Math.cos(landAngle) * dist);
		int z = Mth.floor(target.getZ() + Math.sin(landAngle) * dist);
		int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		Vec3 end = new Vec3(x + 0.5, groundY + 0.5, z + 0.5);

		SolarFragmentEntity fragment = new SolarFragmentEntity(ModEntities.SOLAR_FRAGMENT, level);
		fragment.from = start;
		fragment.to = end;
		fragment.arcHeight = 14.0 + start.distanceTo(end) * 0.12;
		fragment.setPos(start.x, start.y - fragment.getBbHeight() * 0.5, start.z);
		level.addFreshEntity(fragment);

		level.playSound(null, start.x, start.y, start.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 4.0F, 0.7F);
		for (Player p : level.players()) {
			if (p.position().distanceToSqr(center) < WARN_RANGE * WARN_RANGE) {
				p.displayClientMessage(Component.translatable("message.sunmod.fragment_falling"), true);
			}
		}
		return fragment;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LANDED, false);
	}

	public boolean isLanded() {
		return this.entityData.get(DATA_LANDED);
	}

	/** A coluna de luz e o rastro são grandes: desenhar de longe. */
	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 300.0 * 300.0;
	}

	@Override
	protected Item getDefaultItem() {
		return ModItems.SOLAR_SPARK;
	}

	@Override
	public void tick() {
		this.baseTick();
		if (!(this.level() instanceof ServerLevel level)) {
			return; // o cliente só recebe a posição do servidor
		}
		if (this.landed) {
			this.tickLanded(level);
		} else {
			this.tickFlight(level);
		}
	}

	private void tickFlight(ServerLevel level) {
		this.flightTick++;
		double u = Math.min(1.0, this.flightTick / (double) FLIGHT_TICKS);
		// arco: reta do Sol até o chão + uma parábola para cima (máx. no meio do voo)
		Vec3 p = this.from.lerp(this.to, u).add(0.0, 4.0 * this.arcHeight * u * (1.0 - u), 0.0);
		this.setPos(p.x, p.y - this.getBbHeight() * 0.5, p.z);

		// (o rastro e o brilho são desenhados no cliente)
		if (u >= 1.0) {
			this.land(level);
		}
	}

	/** Pousa: grande explosão branca/amarela e vira a estrela dourada com nome. */
	private void land(ServerLevel level) {
		this.landed = true;
		this.setPos(this.to.x, this.to.y, this.to.z);
		this.setItem(new ItemStack(ModItems.SOLAR_FRAGMENT));
		this.setCustomName(Component.translatable("entity.sunmod.solar_fragment").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(" "))
				.append(Component.translatable("rarity.sunmod.common").withStyle(ChatFormatting.GRAY)));
		this.setCustomNameVisible(true);

		double cx = this.getX(), cy = this.getY() + 1.0, cz = this.getZ();
		this.entityData.set(DATA_LANDED, true);
		// explosão grande no chão: bola de fogo + onda de choque (1,5 s)
		FxEntity.spawn(level, new Vec3(this.to.x, this.to.y - 0.5, this.to.z), FxEntity.EXPLOSION, 7.0F, 30, 0xFF9A30);
		level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0F, 0.8F);
		Instability.onFragmentLanded(level, this.to); // no evento deixa uma poça de lava
	}

	private void tickLanded(ServerLevel level) {
		this.landedTick++;
		if (this.landedTick > LANDED_LIFE_TICKS) {
			FxEntity.spawn(level, new Vec3(this.getX(), this.getY() + 1.0, this.getZ()), FxEntity.BLAST, 2.2F, 12, 0xFFC060);
			this.discard();
		}
		// parado no lugar: a estrela flutua e a coluna de luz pulsa só no desenho do cliente
	}

	/**
	 * Dano de um jogador (projétil ou raio). Quando a vida acaba o fragmento estoura em Money Coins para quem deu o
	 * golpe final. Só vale com o fragmento já no chão.
	 */
	public boolean hit(ServerLevel level, ServerPlayer shooter, float amount) {
		if (!this.landed || this.burst || this.isRemoved()) {
			return false;
		}
		this.health -= amount;
		double cx = this.getX(), cy = this.getY() + 1.0, cz = this.getZ();
		FxEntity.spawn(level, new Vec3(cx, cy, cz), FxEntity.BLAST, 1.8F, 9, 0xFFD060);
		level.playSound(null, cx, cy, cz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
		if (this.health <= 0.0F) {
			this.burst(level, shooter);
		}
		return true;
	}

	private void burst(ServerLevel level, ServerPlayer shooter) {
		this.burst = true;
		double cx = this.getX(), cy = this.getY() + 1.0, cz = this.getZ();
		FxEntity.spawn(level, new Vec3(cx, cy - 0.8, cz), FxEntity.EXPLOSION, 4.5F, 22, 0xFFD040); // estouro dourado
		level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 1.4F);
		RandomSource random = level.getRandom();
		for (int i = 0; i < COIN_COUNT; i++) {
			Vec3 pos = new Vec3(cx + (random.nextDouble() - 0.5) * 3.0, cy + random.nextDouble() * 1.5 - 0.5,
					cz + (random.nextDouble() - 0.5) * 3.0);
			MoneyCoinEntity.spawn(level, pos, shooter, COIN_VALUE);
		}
		PowerUps.grantRandom(shooter);                       // Impulso de Poder
		Instability.addProgress(Instability.FRAGMENT_PROGRESS); // sobe a barra do evento (se houver)
		this.discard();
	}

	/** Só o projétil Ice Cube (inclui os tiros da Gun Ice) machuca o fragmento; o Lazer Ice chama {@link #hit}. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.getDirectEntity() instanceof IceCubeProjectile && source.getEntity() instanceof ServerPlayer player) {
			return this.hit(level, player, amount);
		}
		return false;
	}

	/** Só dá para acertar o fragmento depois que ele pousou. */
	@Override
	public boolean isPickable() {
		return this.landed && !this.burst;
	}
}
