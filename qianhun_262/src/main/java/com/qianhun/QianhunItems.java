package com.qianhun;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * 千魂物品注册（MC 26.2）。
 *
 * 版本说明（26.2）：Item 的构造会去读 Properties 里的 itemId，
 * 没设就直接 NPE（Item id not set）。所以必须先 ResourceKey.create 再 setId，
 * 最后用同一个 key 注册。**这一步编译期查不出来，只有真跑起来才会炸。**
 */
public final class QianhunItems {
	/** 千魂书（原名《黑暗默示录》）。原著 L6454。极沉，不可堆叠。 */
	public static final ResourceKey<Item> QIANHUN_BOOK_KEY =
			ResourceKey.create(Registries.ITEM, id("qianhun_book"));
	public static final Item QIANHUN_BOOK = new QianhunBookItem(
			new Item.Properties().stacksTo(1).setId(QIANHUN_BOOK_KEY));

	/** 心脏温血。原著 L1286。 */
	public static final ResourceKey<Item> HEART_BLOOD_KEY =
			ResourceKey.create(Registries.ITEM, id("heart_blood"));
	public static final Item HEART_BLOOD = new Item(
			new Item.Properties().setId(HEART_BLOOD_KEY));

	private QianhunItems() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, QIANHUN_BOOK_KEY, QIANHUN_BOOK);
		Registry.register(BuiltInRegistries.ITEM, HEART_BLOOD_KEY, HEART_BLOOD);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(QianhunMod.MOD_ID, path);
	}
}
