package com.qianhun;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每个玩家自己的 AI 接入凭据。
 *
 * 用户决策（2026-10-02）：
 * 1. 仓库与代码里**不留任何密钥，也不留默认 Base URL**——推给别人的仓库不能带别人的 Key；
 * 2. 玩家进游戏后自己填：`/qianhun key base <地址>` 与 `/qianhun key <密钥>`；
 * 3. 凭据按玩家 UUID 分开存，**别人看不到也改不了**。
 *    这点是多人服的刚需：全局单 Key 的话，任何玩家都能把全服请求劫持到自己的地址上。
 *
 * 落盘位置：世界存档目录下的 qianhun_players.json，随存档走，换世界不串。
 * 没有用原版 SavedData——26.2 把它的 API 重构了，两版写法不通用（见 QianhunSoulCounter 的说明）。
 */
public final class QianhunPlayerKeys {
	private static final String FILE_NAME = "qianhun_players.json";
	private static final Map<UUID, Entry> MAP = new HashMap<>();
	private static Path file;
	private static boolean dirty;

	private QianhunPlayerKeys() {
	}

	private record Entry(String apiKey, String baseUrl) {
	}

	/** 服务端启动时调用。 */
	public static void load(MinecraftServer server) {
		file = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
		MAP.clear();
		if (Files.exists(file)) {
			try {
				JsonObject root = JsonParser.parseString(
						Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
				for (String key : root.keySet()) {
					JsonObject one = root.getAsJsonObject(key);
					MAP.put(UUID.fromString(key),
							new Entry(str(one, "apiKey"), str(one, "baseUrl")));
				}
			} catch (Exception e) {
				QianhunMod.LOGGER.warn("[千魂] 玩家凭据读取失败：{}", e.toString());
			}
		}
		dirty = false;
	}

	public static void save() {
		if (!dirty || file == null) {
			return;
		}
		try {
			JsonObject root = new JsonObject();
			for (Map.Entry<UUID, Entry> e : MAP.entrySet()) {
				JsonObject one = new JsonObject();
				if (!e.getValue().apiKey().isEmpty()) {
					one.addProperty("apiKey", e.getValue().apiKey());
				}
				if (!e.getValue().baseUrl().isEmpty()) {
					one.addProperty("baseUrl", e.getValue().baseUrl());
				}
				root.add(e.getKey().toString(), one);
			}
			Files.createDirectories(file.getParent());
			Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
			dirty = false;
		} catch (IOException e) {
			QianhunMod.LOGGER.warn("[千魂] 玩家凭据写入失败：{}", e.toString());
		}
	}

	/** 玩家自己的 Key；没填则回退到全局配置（服务器管理员在 config/qianhun.json 里配的）。 */
	public static String apiKeyOf(ServerPlayer player) {
		Entry e = MAP.get(player.getUUID());
		if (e != null && !e.apiKey().isEmpty()) {
			return e.apiKey();
		}
		String global = QianhunConfig.get().aiApiKey;
		return global == null ? "" : global;
	}

	/** 同上，Base URL。 */
	public static String baseUrlOf(ServerPlayer player) {
		Entry e = MAP.get(player.getUUID());
		if (e != null && !e.baseUrl().isEmpty()) {
			return e.baseUrl();
		}
		String global = QianhunConfig.get().aiBaseUrl;
		return global == null ? "" : global;
	}

	/** 这个玩家是否已经填齐了。 */
	public static boolean ready(ServerPlayer player) {
		return !apiKeyOf(player).isBlank() && !baseUrlOf(player).isBlank();
	}

	public static void setApiKey(ServerPlayer player, String value) {
		put(player.getUUID(), value, baseUrlOfOwn(player));
	}

	public static void setBaseUrl(ServerPlayer player, String value) {
		put(player.getUUID(), apiKeyOfOwn(player), value);
	}

	public static void clear(ServerPlayer player) {
		MAP.remove(player.getUUID());
		dirty = true;
		save();
	}

	/** 只读自己那份，不带全局回退——避免把管理员的全局 Key 复制进玩家条目。 */
	private static String apiKeyOfOwn(ServerPlayer player) {
		Entry e = MAP.get(player.getUUID());
		return e == null ? "" : e.apiKey();
	}

	private static String baseUrlOfOwn(ServerPlayer player) {
		Entry e = MAP.get(player.getUUID());
		return e == null ? "" : e.baseUrl();
	}

	private static void put(UUID uuid, String apiKey, String baseUrl) {
		MAP.put(uuid, new Entry(apiKey, baseUrl));
		dirty = true;
		save();
	}

	private static String str(JsonObject obj, String field) {
		return obj.has(field) ? obj.get(field).getAsString() : "";
	}
}
