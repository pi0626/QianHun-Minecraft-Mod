package com.qianhun;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 全服统一的千魂计数。
 *
 * 用户决策（2026-09-26）：**不持书也计数，且全服共用同一个计数。**
 * 因此它不再随千魂书走，而是落在世界存档目录下的一份 JSON 里，
 * 任何玩家的击杀都累加到同一个数上。
 *
 * 实现取舍：没有用原版 SavedData，因为 26.2 把它的 API 完全重构了
 * （SavedData.Factory 消失、save(CompoundTag, HolderLookup) 消失），
 * 1.21.1 与 26.2 写法不通用。自己读写 JSON 反而两版同一份代码。
 */
public final class QianhunSoulCounter {
	private static final String FILE_NAME = "qianhun_souls.json";
	/** 落盘节流：两次写盘至少间隔 10 秒，避免刷怪塔把硬盘写爆。 */
	private static final long SAVE_INTERVAL_MS = 10_000L;

	private static int count;
	private static Path file;
	private static boolean dirty;
	private static long lastSaveAt;

	private QianhunSoulCounter() {
	}

	/** 服务端启动时调用：定位世界目录并载入计数。 */
	public static void load(MinecraftServer server) {
		file = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
		count = 0;
		if (Files.exists(file)) {
			try {
				String raw = Files.readString(file, StandardCharsets.UTF_8);
				JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
				count = obj.has("souls") ? obj.get("souls").getAsInt() : 0;
			} catch (Exception e) {
				QianhunMod.LOGGER.warn("[千魂] 千魂计数读取失败，从 0 开始：{}", e.toString());
			}
		}
		dirty = false;
		lastSaveAt = System.currentTimeMillis();
		QianhunMod.LOGGER.info("[千魂] 全服千魂计数已载入：{}", count);
	}

	/** 立即落盘。服务端存档与关服时调用。 */
	public static void save() {
		if (!dirty || file == null) {
			return;
		}
		try {
			JsonObject obj = new JsonObject();
			obj.addProperty("souls", count);
			Files.createDirectories(file.getParent());
			Files.writeString(file, obj.toString(), StandardCharsets.UTF_8);
			dirty = false;
			lastSaveAt = System.currentTimeMillis();
		} catch (IOException e) {
			QianhunMod.LOGGER.warn("[千魂] 千魂计数写入失败：{}", e.toString());
		}
	}

	public static int get() {
		return count;
	}

	/** 记一笔。任何玩家的击杀都算。 */
	public static void add(int delta) {
		count = Math.max(0, count + delta);
		dirty = true;
		if (System.currentTimeMillis() - lastSaveAt >= SAVE_INTERVAL_MS) {
			save();
		}
	}

	/** 手动改写（调试命令用）。 */
	public static void set(int value) {
		count = Math.max(0, value);
		dirty = true;
		save();
	}

	/** 计数是否已满（阈值 0 视为不卡）。 */
	public static boolean isFull() {
		int threshold = QianhunConfig.get().soulThreshold;
		return threshold <= 0 || count >= threshold;
	}

	/** 距可许愿还差多少。阈值 0 返回 0。 */
	public static int remaining() {
		int threshold = QianhunConfig.get().soulThreshold;
		return threshold <= 0 ? 0 : Math.max(0, threshold - count);
	}

	public static Component progressText() {
		int threshold = QianhunConfig.get().soulThreshold;
		if (threshold <= 0) {
			return Component.literal("千魂计数：不限（可随时许愿）");
		}
		return Component.literal("千魂计数（全服）：" + count + " / " + threshold);
	}
}
