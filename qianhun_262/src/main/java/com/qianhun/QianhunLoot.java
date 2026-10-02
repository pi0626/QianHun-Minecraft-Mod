package com.qianhun;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

/**
 * 千魂书的生存获取途径 —— 林地府邸。
 *
 * 用户决策：千魂书在林地府邸的箱子里刷新。
 * 用战利品表注入实现，不覆盖原版 JSON，跨版本更稳。
 */
public final class QianhunLoot {
	private static final ResourceKey<LootTable> WOODLAND_MANSION =
			ResourceKey.create(Registries.LOOT_TABLE, Identifier.withDefaultNamespace("chests/woodland_mansion"));

	private QianhunLoot() {
	}

	public static void register() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			if (!WOODLAND_MANSION.equals(key)) {
				return;
			}
			// 府邸箱子很多，单箱权重压低，避免一书满地。
			tableBuilder.pool(LootPool.lootPool()
					.setRolls(ConstantValue.exactly(1.0F))
					.add(LootItem.lootTableItem(QianhunItems.QIANHUN_BOOK)
							.setWeight(3)
							.apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F))))
					.build());
		});
	}
}
