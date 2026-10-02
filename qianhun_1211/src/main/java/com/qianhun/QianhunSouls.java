package com.qianhun;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 千魂书自身的状态读写：只放"这本书醒没醒"，以及游尸待生成队列。
 *
 * 注意：**千魂计数已经不在这里了。**
 * 用户决策（2026-09-26）：计数改为全服统一、不持书也计，
 * 所以它搬去了 {@link QianhunSoulCounter}，存在世界存档里。
 */
public final class QianhunSouls {
	private static final String KEY_AWAKENED = "Awakened";

	private QianhunSouls() {
	}

	/** 供游尸队列等其他模块复用，避免各自重复实现 NBT 存取。 */
	public static CompoundTag rawTag(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
	}

	public static void writeRawTag(ItemStack stack, CompoundTag tag) {
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static boolean awakened(ItemStack stack) {
		return stack.is(QianhunItems.QIANHUN_BOOK) && rawTag(stack).getBoolean(KEY_AWAKENED);
	}

	public static void awaken(ItemStack stack) {
		if (!stack.is(QianhunItems.QIANHUN_BOOK)) {
			return;
		}
		CompoundTag tag = rawTag(stack);
		tag.putBoolean(KEY_AWAKENED, true);
		writeRawTag(stack, tag);
	}

	/** 在玩家背包里找千魂书。 */
	public static ItemStack findBook(Player player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(QianhunItems.QIANHUN_BOOK)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}
}
