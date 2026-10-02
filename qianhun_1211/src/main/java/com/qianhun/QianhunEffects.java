package com.qianhun;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * 神选效果（MC 1.21.1）。
 *
 * 原著 L3913/L1575：第一千零一个人成为神选者，「死去千魂护佑之」。
 *
 * 用户决策（2026-09-30）：**神选者什么都不给。** 它只是一枚身份标记，
 * 不发任何属性、不刷任何药水效果。唯一的作用是"这个人可以许愿"。
 */
public final class QianhunEffects {
	public static final Holder<MobEffect> CHOSEN =
			Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, QianhunItems.id("chosen"),
					new ChosenEffect());

	private QianhunEffects() {
	}

	public static void register() {
		QianhunMod.LOGGER.info("[千魂] 神选效果已注册");
	}

	/** 授予/续期神选。永久，不显示粒子。 */
	public static void grant(Player player) {
		player.addEffect(new MobEffectInstance(CHOSEN, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
	}

	public static boolean has(Player player) {
		return player.hasEffect(CHOSEN);
	}

	/** 神选：纯标记，不给任何属性、不刷任何效果。 */
	private static final class ChosenEffect extends MobEffect {
		private ChosenEffect() {
			super(MobEffectCategory.NEUTRAL, 0x6B4FA8);
		}
	}
}
