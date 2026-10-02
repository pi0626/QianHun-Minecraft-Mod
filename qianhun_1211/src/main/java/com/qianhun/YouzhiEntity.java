package com.qianhun;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;

/**
 * 游尸。原著依据：
 *   成因 L2500「因这本书而死的人都会复活」
 *   表现 L6108/L6749/L7202「呆滞、僵硬、漫无目的游走、蹒跚机械、不攻击人」
 *   气味 L6743/L7202「恶臭」
 *   消散 L7724「太阳升起之后，岛上的游尸都消失了」
 *
 * 本体不自带模型：外观由来源实体的模型照搬而来（见客户端 YouzhiRenderer）。
 */
public class YouzhiEntity extends PathfinderMob {
	private static final EntityDataAccessor<String> DATA_SOURCE_TYPE =
			SynchedEntityData.defineId(YouzhiEntity.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> DATA_SOURCE_PROFILE =
			SynchedEntityData.defineId(YouzhiEntity.class, EntityDataSerializers.STRING);

	private static final int BURN_CHECK_INTERVAL = 20;
	private static final int STENCH_INTERVAL = 12;

	public YouzhiEntity(EntityType<? extends YouzhiEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SOURCE_TYPE, "");
		builder.define(DATA_SOURCE_PROFILE, "");
	}

	@Override
	protected void registerGoals() {
		// 只保留"活着、漫无目的走、偶尔看看"三种行为。
		// targetSelector 一条不加 —— 这就是原著「不攻击人」。
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.55D));
		this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
	}

	// ---- 来源实体 ----

	public String getSourceType() {
		return this.entityData.get(DATA_SOURCE_TYPE);
	}

	public void setSourceType(String typeId) {
		this.entityData.set(DATA_SOURCE_TYPE, typeId == null ? "" : typeId);
	}

	public String getSourceProfile() {
		return this.entityData.get(DATA_SOURCE_PROFILE);
	}

	public void setSourceProfile(String profile) {
		this.entityData.set(DATA_SOURCE_PROFILE, profile == null ? "" : profile);
	}

	// ---- 行为 ----

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
		Level level = this.level();

		if (this.tickCount % STENCH_INTERVAL == 0 && level instanceof ServerLevel serverLevel) {
			// 原著 L6743/L7202：恶臭，挥之不去。
			serverLevel.sendParticles(ParticleTypes.SMOKE,
					this.getX(), this.getY() + 1.0D, this.getZ(),
					2, 0.28D, 0.35D, 0.28D, 0.004D);
		}

		if (QianhunConfig.get().youzhiBurnInDaylight
				&& this.tickCount % BURN_CHECK_INTERVAL == 0
				&& isDaylightHere()) {
			// 原著 L7724：太阳升起后消失。不带掉落、不计数。
			this.discard();
		}
	}

	/** 是否处在日光下。 */
	private boolean isDaylightHere() {
		Level level = this.level();
		if (!level.dimensionType().hasSkyLight()) {
			return false;
		}
		if (!level.canSeeSky(this.blockPosition())) {
			return false;
		}
		// 白昼判据沿用原版那套。1.21.1 是 isDay()，26.2 改名为 isBrightOutside()。
		return level.isDay();
	}

	/** 死亡时不留经验、不掉落。 */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level,
			net.minecraft.world.damagesource.DamageSource source, boolean hitByPlayer) {
		// 刻意留空：游尸只是"消失"，不是被杀死。
	}

	// ---- 存档 ----

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putString("SourceType", this.getSourceType());
		tag.putString("SourceProfile", this.getSourceProfile());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.setSourceType(tag.getString("SourceType"));
		this.setSourceProfile(tag.getString("SourceProfile"));
	}
}
