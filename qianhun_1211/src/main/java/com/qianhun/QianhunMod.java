package com.qianhun;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 千魂 mod 主入口（MC 1.21.1）。
 */
public class QianhunMod implements ModInitializer {
	public static final String MOD_ID = "qianhun";
	public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MOD_ID);

	/** 游尸结算的节流计数：每 40 tick 一次。 */
	private static final AtomicInteger TICK_COUNTER = new AtomicInteger();

	@Override
	public void onInitialize() {
		QianhunConfig.get();
		QianhunItems.register();
		QianhunEntities.register();
		QianhunEffects.register();
		QianhunEvents.register();
		QianhunChatWish.register();
		QianhunLoot.register();
		QianhunCommands.register();

		ServerTickEvents.END_SERVER_TICK.register(QianhunMod::onServerTick);

		// 全服千魂计数：存在世界存档里，起服载入、关服落盘。
		ServerLifecycleEvents.SERVER_STARTED.register(QianhunSoulCounter::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> QianhunSoulCounter.save());

		// 玩家各自的 AI 凭据：同样落世界存档，按 UUID 分开。
		ServerLifecycleEvents.SERVER_STARTED.register(QianhunPlayerKeys::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> QianhunPlayerKeys.save());

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
			entries.accept(QianhunItems.QIANHUN_BOOK);
			entries.accept(QianhunItems.HEART_BLOOD);
		});

		LOGGER.info("[千魂] 主入口加载完成");
	}

	/** 入夜后把"死亡队列"兑现成游尸。 */
	private static void onServerTick(MinecraftServer server) {
		if (TICK_COUNTER.incrementAndGet() % 40 != 0) {
			return;
		}
		if (!QianhunConfig.get().youzhiSpawnEnabled) {
			return;
		}
		QianhunYouzhi.tick(server);
	}
}
