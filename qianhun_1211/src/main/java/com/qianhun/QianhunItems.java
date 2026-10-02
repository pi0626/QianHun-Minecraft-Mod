package com.qianhun;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * 千魂物品注册。
 */
public final class QianhunItems {
	/** 千魂书（原名《黑暗默示录》）。原著 L6454。极沉，不可堆叠。 */
	public static final Item QIANHUN_BOOK = new QianhunBookItem(new Item.Properties().stacksTo(1));
	/** 心脏温血。原著 L1286。 */
	public static final Item HEART_BLOOD = new Item(new Item.Properties());

	private QianhunItems() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, id("qianhun_book"), QIANHUN_BOOK);
		Registry.register(BuiltInRegistries.ITEM, id("heart_blood"), HEART_BLOOD);
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(QianhunMod.MOD_ID, path);
	}
}
