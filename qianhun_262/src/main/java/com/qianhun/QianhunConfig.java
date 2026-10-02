package com.qianhun;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 千魂配置。写在 config/qianhun.json。
 */
public final class QianhunConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("qianhun.json");

	/** 千魂计数阈值。达到后才可许愿。0 表示不卡许愿。 */
	public int soulThreshold = 1000;
	/** 是否启用命令黑名单。关闭即完全放开，风险自担。 */
	public boolean commandBlacklist = true;
	/** 黑名单命令前缀。 */
	public List<String> blacklistEntries = List.of(
			"/stop", "/op", "/deop", "/ban", "/ban-ip", "/pardon", "/pardon-ip",
			"/kick", "/whitelist", "/save-off", "/save-all", "/reload", "/shutdown"
	);
	/** 游尸是否在日光下消散。 */
	public boolean youzhiBurnInDaylight = true;
	/** 同类型游尸同时存在上限。 */
	public int youzhiPerTypeCap = 8;
	/** 单次愿望命令条数上限。0 表示不限。 */
	public int wishMaxCommands = 0;
	/** 许愿是否需要消耗一个心脏温血。
	 *  用户裁决（2026-10-01）：原著里心脏温血只在"印掌纹"时用一次（蘸着别人的、
	 *  从心脏里直接取出的温热血），之后许愿不再需要。所以这里默认 false——
	 *  心脏温血只通过"拖到书上"那次激活消耗，见 QianhunBookItem。 */
	public boolean wishRequiresHeartBlood = false;
	/** 击杀生物掉落心脏温血的概率，0..1。 */
	public double heartBloodDropChance = 0.25D;
	/**
	 * 聊天许愿前缀。留空表示"只要书已苏醒且千魂已满，任何一句聊天都算愿望"。
	 *
	 * 用户决策（2026-10-02）：默认必须有前缀。否则**满千魂玩家的任何一句闲聊都会被吞掉**
	 * ——千魂计数是全服共用的，一旦满了，服务器里所有拿着醒着的书的人将无法正常聊天。
	 */
	public String chatWishPrefix = "许愿";
	/** 是否启用"当夜生成游尸"。 */
	public boolean youzhiSpawnEnabled = true;
	/**
	 * AI 接入参数。
	 *
	 * 用户决策（2026-10-02）：**默认全空**。仓库里不留任何密钥，也不留别人的服务地址。
	 * 玩家在游戏里用 `/qianhun key base <地址>` 和 `/qianhun key <密钥>` 填自己的，
	 * 按 UUID 分开存在世界存档里。服务器管理员想统一配置才动这两个字段。
	 */
	public String aiBaseUrl = "";
	public String aiModel = "agnes-2.5-flash";
	public String aiApiKey = "";
	public int aiTimeoutSeconds = 60;

	private static QianhunConfig instance;

	public static QianhunConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static QianhunConfig load() {
		if (Files.exists(PATH)) {
			try {
				String raw = Files.readString(PATH, StandardCharsets.UTF_8);
				JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
				return GSON.fromJson(obj, QianhunConfig.class);
			} catch (Exception e) {
				QianhunMod.LOGGER.warn("[千魂] 配置读取失败，使用默认值：{}", e.toString());
			}
		}
		QianhunConfig fresh = new QianhunConfig();
		fresh.save();
		return fresh;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			QianhunMod.LOGGER.warn("[千魂] 配置写入失败：{}", e.toString());
		}
	}

	/** 读一个配置项的可读值；未知键返回 null。 */
	public static String describe(String key) {
		QianhunConfig c = get();
		return switch (key.toLowerCase(java.util.Locale.ROOT)) {
			case "soulthreshold" -> Integer.toString(c.soulThreshold);
			case "commandblacklist" -> Boolean.toString(c.commandBlacklist);
			case "youzhiburnindaylight" -> Boolean.toString(c.youzhiBurnInDaylight);
			case "youzhipertypecap" -> Integer.toString(c.youzhiPerTypeCap);
			case "youzhispawnenabled" -> Boolean.toString(c.youzhiSpawnEnabled);
			case "wishmaxcommands" -> Integer.toString(c.wishMaxCommands);
			case "wishrequiresheartblood" -> Boolean.toString(c.wishRequiresHeartBlood);
			case "heartblooddropchance" -> Double.toString(c.heartBloodDropChance);
			case "chatwishprefix" -> c.chatWishPrefix;
			case "aibaseurl" -> c.aiBaseUrl;
			case "aimodel" -> c.aiModel;
			case "aitimeoutseconds" -> Integer.toString(c.aiTimeoutSeconds);
			case "aiapikey" -> c.aiApiKey == null || c.aiApiKey.isBlank() ? "(未设置)" : "(已设置)";
			case "soulcount" -> Integer.toString(QianhunSoulCounter.get());
			default -> null;
		};
	}

	/** 写一个配置项；成功返回 null，失败返回原因。 */
	public static String apply(String key, String value) {
		QianhunConfig c = get();
		try {
			switch (key.toLowerCase(java.util.Locale.ROOT)) {
				case "soulthreshold" -> c.soulThreshold = Integer.parseInt(value);
				case "commandblacklist" -> c.commandBlacklist = parseBool(value);
				case "youzhiburnindaylight" -> c.youzhiBurnInDaylight = parseBool(value);
				case "youzhipertypecap" -> c.youzhiPerTypeCap = Integer.parseInt(value);
				case "youzhispawnenabled" -> c.youzhiSpawnEnabled = parseBool(value);
				case "wishmaxcommands" -> c.wishMaxCommands = Integer.parseInt(value);
					case "wishrequiresheartblood" -> c.wishRequiresHeartBlood = parseBool(value);
				case "heartblooddropchance" -> c.heartBloodDropChance = Double.parseDouble(value);
				case "chatwishprefix" -> c.chatWishPrefix = value;
				case "aibaseurl" -> c.aiBaseUrl = value;
				case "aimodel" -> c.aiModel = value;
				case "aitimeoutseconds" -> c.aiTimeoutSeconds = Integer.parseInt(value);
				case "aiapikey" -> c.aiApiKey = value;
				case "soulcount" -> QianhunSoulCounter.set(Integer.parseInt(value));
				default -> {
					return "没有这个配置项：" + key;
				}
			}
		} catch (NumberFormatException e) {
			return "值格式不对：" + value;
		}
		c.save();
		return null;
	}

	private static boolean parseBool(String value) {
		return "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "1".equals(value);
	}
}
