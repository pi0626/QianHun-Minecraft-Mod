package com.qianhun;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * AI 许愿系统。
 *
 * 流程：玩家愿望 → 送 AI（OpenAI 兼容）→ 返回 JSON 命令数组 → 逐条校验 → 最高权限执行 → 扣除许愿者血量。
 */
public final class QianhunWish {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();

	private QianhunWish() {
	}

	/** 许愿前置检查。返回 null 表示通过。 */
	public static Component check(ServerPlayer player, boolean quiet) {
		net.minecraft.world.item.ItemStack book = QianhunSouls.findBook(player);
		if (book.isEmpty()) {
			return quiet ? null : Component.literal("你身上没有千魂书。");
		}
		if (!QianhunSouls.awakened(book)) {
			return quiet ? null : Component.literal("书还在沉睡。在聊天中说一次它的真名。").withStyle(ChatFormatting.DARK_GRAY);
		}
		if (!QianhunSoulCounter.isFull()) {
			return quiet ? null : Component.literal("千魂未满，它还听不见你。还差 "
					+ QianhunSoulCounter.remaining() + " 个。").withStyle(ChatFormatting.DARK_RED);
		}
		if (QianhunConfig.get().wishRequiresHeartBlood && !hasHeartBlood(player)) {
			return quiet ? null : Component.literal("许愿需要一个心脏温血来激活。").withStyle(ChatFormatting.DARK_RED);
		}
		return null;
	}

	/** 背包里有没有心脏温血。 */
	private static boolean hasHeartBlood(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(QianhunItems.HEART_BLOOD)) {
				return true;
			}
		}
		return false;
	}

	/** 消耗一个心脏温血。 */
	private static void consumeHeartBlood(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(QianhunItems.HEART_BLOOD)) {
				stack.shrink(1);
				return;
			}
		}
	}

	public static void submit(ServerPlayer player, String wish) {
		Component problem = check(player, false);
		if (problem != null) {
			player.sendSystemMessage(problem);
			return;
		}
		QianhunConfig cfg = QianhunConfig.get();
		if (!QianhunPlayerKeys.ready(player)) {
			player.sendSystemMessage(Component.literal("书还不知道该去问谁。两件事，游戏里输入就行：")
					.withStyle(ChatFormatting.RED));
			player.sendSystemMessage(Component.literal("/qianhun key base <Base URL，形如 https://你的服务商/v1>")
					.withStyle(ChatFormatting.GRAY));
			player.sendSystemMessage(Component.literal("/qianhun key <你的 API Key>")
					.withStyle(ChatFormatting.GRAY));
			player.sendSystemMessage(Component.literal("只存在你自己的存档里，别人看不到也改不了。")
					.withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		String apiKey = QianhunPlayerKeys.apiKeyOf(player);
		String baseUrl = QianhunPlayerKeys.baseUrlOf(player);

		player.sendSystemMessage(Component.literal("千魂书在翻动……").withStyle(ChatFormatting.DARK_GRAY));

		String body = buildRequestBody(player, wish);
		URI uri = URI.create(trimSlash(baseUrl) + "/chat/completions");
		HttpRequest request = HttpRequest.newBuilder(uri)
				.header("Content-Type", "application/json")
				.header("Authorization", "Bearer " + apiKey)
				.timeout(Duration.ofSeconds(Math.max(5, cfg.aiTimeoutSeconds)))
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build();

		MinecraftServer server = player.getServer();
		CompletableFuture<HttpResponse<String>> future =
				CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

		future.whenComplete((response, error) -> {
			if (server == null) {
				return;
			}
			server.execute(() -> {
				if (error != null) {
					player.sendSystemMessage(Component.literal("书合上了，什么都没发生。（" + error.getClass().getSimpleName() + "）")
							.withStyle(ChatFormatting.DARK_RED));
					QianhunMod.LOGGER.warn("[千魂] 许愿请求失败", error);
					return;
				}
				if (response.statusCode() / 100 != 2) {
					player.sendSystemMessage(Component.literal("书沉默了。HTTP " + response.statusCode())
							.withStyle(ChatFormatting.DARK_RED));
					QianhunMod.LOGGER.warn("[千魂] 许愿返回非 2xx：{}", response.statusCode());
					return;
				}
				List<String> commands;
				try {
					commands = parseCommands(response.body());
				} catch (Exception e) {
					player.sendSystemMessage(Component.literal("书给了你一堆看不懂的墨迹。")
							.withStyle(ChatFormatting.DARK_RED));
					QianhunMod.LOGGER.warn("[千魂] 愿望解析失败：{}", e.toString());
					return;
				}
				runCommands(server, player, commands);
			});
		});
	}

	private static String buildRequestBody(ServerPlayer player, String wish) {
		QianhunConfig cfg = QianhunConfig.get();
		String system = """
				你是 Minecraft 千魂书的许愿机制。玩家说出一个愿望，你要把它翻译成 Minecraft 命令。
				必须只输出 JSON，格式：{"commands":["/命令1","/命令2"]}
				规则：
				1 命令必须以 / 开头，不带前导斜杠以外的多余字符。
				2 一条命令能达成就只给一条；复杂的愿望才给多条。
				3 不要输出解释、注释、Markdown 代码块或任何 JSON 之外的内容。
				4 玩家坐标与维度已给出，尽量用相对坐标或选择器。
				5 不要使用 @a 之类影响全服的宽泛目标，除非愿望本身要求。
				""";
		String context = "玩家名：" + player.getGameProfile().getName()
				+ "，坐标：" + (int) player.getX() + " " + (int) player.getY() + " " + (int) player.getZ()
				+ "，维度：" + player.level().dimension().location()
				+ "，游戏模式：" + player.gameMode.getGameModeForPlayer().getName()
				+ "。愿望：" + wish;

		JsonObject root = new JsonObject();
		root.addProperty("model", cfg.aiModel);
		root.addProperty("temperature", 0.7);
		JsonArray messages = new JsonArray();
		messages.add(message("system", system));
		messages.add(message("user", context));
		root.add("messages", messages);
		return root.toString();
	}

	private static JsonObject message(String role, String content) {
		JsonObject m = new JsonObject();
		m.addProperty("role", role);
		m.addProperty("content", content);
		return m;
	}

	/** 从 OpenAI 兼容响应里取出命令列表。 */
	static List<String> parseCommands(String responseBody) {
		JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
		String content = root.getAsJsonArray("choices")
				.get(0).getAsJsonObject()
				.getAsJsonObject("message")
				.get("content").getAsString();
		String cleaned = stripFence(content);
		JsonObject parsed = JsonParser.parseString(cleaned).getAsJsonObject();
		List<String> commands = new ArrayList<>();
		if (parsed.has("commands")) {
			for (var el : parsed.getAsJsonArray("commands")) {
				commands.add(el.getAsString());
			}
		} else if (parsed.has("command")) {
			commands.add(parsed.get("command").getAsString());
		}
		return commands;
	}

	private static String stripFence(String raw) {
		String text = raw.trim();
		if (text.startsWith("```")) {
			int firstBreak = text.indexOf('\n');
			if (firstBreak > 0) {
				text = text.substring(firstBreak + 1);
			}
			int lastFence = text.lastIndexOf("```");
			if (lastFence >= 0) {
				text = text.substring(0, lastFence);
			}
		}
		return text.trim();
	}

	/** 执行命令。任何异常都只在聊天里回执，不抛出。 */
	static void runCommands(MinecraftServer server, ServerPlayer player, List<String> commands) {
		QianhunConfig cfg = QianhunConfig.get();
		int limit = cfg.wishMaxCommands;
		int executed = 0;
		for (String raw : commands) {
			if (limit > 0 && executed >= limit) {
				player.sendSystemMessage(Component.literal("愿望太长，后面的部分被咽了回去。")
						.withStyle(ChatFormatting.DARK_GRAY));
				break;
			}
			String command = stripSlashes(raw);
			if (command.isEmpty()) {
				continue;
			}
			String rejected = rejectReason(command, cfg);
			if (rejected != null) {
				player.sendSystemMessage(Component.literal("书拒绝了这条命令：" + rejected)
						.withStyle(ChatFormatting.DARK_RED));
				continue;
			}
			try {
				var source = server.createCommandSourceStack()
						.withPermission(4)
						.withMaximumPermission(4)
						.withSuppressedOutput();
				server.getCommands().getDispatcher().execute(command, source);
				executed++;
				QianhunMod.LOGGER.info("[千魂] 愿望命令已执行：{}", command);
			} catch (Exception e) {
				player.sendSystemMessage(Component.literal("这条命令没能生效：" + command)
						.withStyle(ChatFormatting.DARK_RED));
				QianhunMod.LOGGER.warn("[千魂] 命令执行失败：{}", command, e);
			}
		}
		if (executed > 0) {
			if (QianhunConfig.get().wishRequiresHeartBlood) {
				consumeHeartBlood(player);
			}
			// 第一次真正许愿成功即成为神选者（原著 L3913 的 Minecraft 化）
			if (!QianhunEffects.has(player)) {
				QianhunEffects.grant(player);
				player.sendSystemMessage(Component.literal("那些死去的千魂，开始护佑你。")
						.withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			// 原著里许愿不掉血（用户裁决 2026-10-01，payCost 已整体移除）
		}
	}

	/** 返回拒绝原因；null 表示允许。 */
	static String rejectReason(String command, QianhunConfig cfg) {
		if (!cfg.commandBlacklist) {
			return null;
		}
		// 配置里的黑名单项带前导斜杠，这里补回来比对。
		String head = "/" + command.toLowerCase(Locale.ROOT).split("\\s+")[0];
		for (String banned : cfg.blacklistEntries) {
			if (head.equals(banned.toLowerCase(Locale.ROOT))) {
				return banned;
			}
		}
		return null;
	}

	/**
	 * 去掉命令开头的斜杠。
	 *
	 * Brigadier 的 CommandDispatcher.execute 要求输入**不带**前导斜杠，
	 * 带上去会在 position 0 直接抛 "Unknown or incomplete command"。
	 * AI 按提示词返回的是带斜杠的形式，所以这里必须剥掉。
	 */
	static String stripSlashes(String raw) {
		String command = raw == null ? "" : raw.trim();
		while (command.startsWith("/")) {
			command = command.substring(1).trim();
		}
		return command;
	}

	private static String trimSlash(String url) {
		String out = url == null ? "" : url.trim();
		while (out.endsWith("/")) {
			out = out.substring(0, out.length() - 1);
		}
		return out;
	}
}
