package com.qianhun.client.mixin;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 放开 WalkAnimationState 的三个私有字段。
 *
 * 用途：游尸的幽灵代理实体不参与世界 tick，行走动画状态永远停在 0，
 * 渲染前要把主人的状态逐帧抄过去，否则游尸永远保持静态站姿。
 *
 * 不用 Loom access widener 的原因：splitEnvironmentSourceSets 下
 * AW 只作用于 main 源集，client 源集编译仍报 private（实测）。
 * Mixin Accessor 对两个源集、两个版本都生效。
 */
@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {
	@Accessor("speedOld")
	float getSpeedOld();

	@Accessor("speed")
	float getSpeed();

	@Accessor("position")
	float getPosition();

	@Accessor("speedOld")
	void setSpeedOld(float value);

	@Accessor("speed")
	void setSpeed(float value);

	@Accessor("position")
	void setPosition(float value);
}
