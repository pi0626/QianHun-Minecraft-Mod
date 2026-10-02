package com.qianhun;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /qianhun 命令。
 */
public final class QianhunCommands {
	private QianhunCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("qianhun")
						.then(Commands.literal("status")
								.executes(ctx -> status(ctx.getSource())))
						.then(Commands.literal("wish")
								.then(Commands.argument("text", StringArgumentType.greedyString())
										.executes(ctx -> wish(ctx.getSource(), StringArgumentType.getString(ctx, "text")))))
						.then(Commands.literal("reload")
								.executes(ctx -> reload(ctx.getSource())))
						.then(Commands.literal("key")
								.executes(ctx -> keyStatus(ctx.getSource()))
								.then(Commands.literal("base")
										.then(Commands.argument("url", StringArgumentType.greedyString())
												.executes(ctx -> keySetBase(ctx.getSource(),
														StringArgumentType.getString(ctx, "url")))))
								.then(Commands.literal("clear")
										.executes(ctx -> keyClear(ctx.getSource())))
								.then(Commands.argument("apikey", StringArgumentType.greedyString())
										.executes(ctx -> keySetApi(ctx.getSource(),
												StringArgumentType.getString(ctx, "apikey")))))
						.then(Commands.literal("spawn")
								.then(Commands.argument("type", StringArgumentType.string())
										.executes(ctx -> spawn(ctx.getSource(),
												StringArgumentType.getString(ctx, "type"), 1))
										.then(Commands.argument("count",
														com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 64))
												.executes(ctx -> spawn(ctx.getSource(),
														StringArgumentType.getString(ctx, "type"),
														com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"))))))
						.then(Commands.literal("config")
								.then(Commands.argument("key", StringArgumentType.word())
										.executes(ctx -> configGet(ctx.getSource(),
												StringArgumentType.getString(ctx, "key")))
										.then(Commands.argument("value", StringArgumentType.greedyString())
												.executes(ctx -> configSet(ctx.getSource(),
														StringArgumentType.getString(ctx, "key"),
														StringArgumentType.getString(ctx, "value"))))))
				));
	}

	private static int status(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("该命令只能由玩家执行。"));
			return 0;
		}
		ItemStack book = QianhunSouls.findBook(player);
		if (book.isEmpty()) {
			source.sendSuccess(() -> Component.literal("你身上没有千魂书。"), false);
			return 0;
		}
		QianhunConfig cfg = QianhunConfig.get();
		source.sendSuccess(() -> Component.literal("—— 千魂书 ——").withStyle(ChatFormatting.DARK_PURPLE), false);
		source.sendSuccess(() -> QianhunSoulCounter.progressText(), false);
		source.sendSuccess(() -> Component.literal(QianhunSouls.awakened(book) ? "状态：已苏醒" : "状态：沉睡（在聊天中说出真名）"), false);
		source.sendSuccess(() -> Component.literal("阈值：" + cfg.soulThreshold + "，黑名单：" + (cfg.commandBlacklist ? "开" : "关")), false);
		source.sendSuccess(() -> Component.literal("AI：" + cfg.aiModel + " @ "
				+ (QianhunPlayerKeys.ready(player) ? QianhunPlayerKeys.baseUrlOf(player) : "（你还没填，用 /qianhun key）")), false);
		source.sendSuccess(() -> Component.literal("神选：" + (QianhunEffects.has(player) ? "已获得" : "未获得")
				+ "，待生成游尸：" + QianhunYouzhi.pendingCount(book)), false);
		if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
			var census = QianhunYouzhi.census(level);
			String text = census.isEmpty() ? "（本维度暂无游尸）" : census.toString();
			QianhunMod.LOGGER.info("[千魂] status 结果：全服计数={} 书已苏醒={} 待生成={} 游尸={}",
					QianhunSoulCounter.get(), QianhunSouls.awakened(book),
					QianhunYouzhi.pendingCount(book), text);
			source.sendSuccess(() -> Component.literal("游尸存量：" + text), false);
		}
		return 1;
	}

	private static int wish(CommandSourceStack source, String text) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("该命令只能由玩家执行。"));
			return 0;
		}
		QianhunWish.submit(player, text);
		return 1;
	}

	private static int reload(CommandSourceStack source) {
		QianhunConfig.get().save();
		source.sendSuccess(() -> Component.literal("配置已重新写出。"), false);
		return 1;
	}

	/**
	 * /qianhun key —— AI 接入凭据，**只影响自己**，不需要 OP 权限。
	 *
	 * 仓库里不带任何密钥，所以每个玩家第一次玩都要自己填一次。
	 * 权限故意放开：这是单人也要能用 AI 的玩法，服务器管理员不该当中间人。
	 * 但也只到"自己的"为止——全局配置（config/qianhun.json）仍然要 2 级权限才能改。
	 */
	private static int keyStatus(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("该命令只能由玩家执行。"));
			return 0;
		}
		boolean key = !QianhunPlayerKeys.apiKeyOf(player).isBlank();
		boolean url = !QianhunPlayerKeys.baseUrlOf(player).isBlank();
		source.sendSuccess(() -> Component.literal("—— 你的 AI 接入 ——").withStyle(ChatFormatting.DARK_PURPLE), false);
		source.sendSuccess(() -> Component.literal("Base URL：" + (url
				? QianhunPlayerKeys.baseUrlOf(player)
				: "（未填）/qianhun key base <地址>")), false);
		source.sendSuccess(() -> Component.literal("API Key：" + (key ? "（已填写）" : "（未填）/qianhun key <密钥>")), false);
		source.sendSuccess(() -> Component.literal("清除：/qianhun key clear"), false);
		return 1;
	}

	private static int keySetApi(CommandSourceStack source, String value) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("该命令只能由玩家执行。"));
			return 0;
		}
		String key = value.trim();
		if (key.isEmpty()) {
			source.sendFailure(Component.literal("密钥不能为空。"));
			return 0;
		}
		QianhunPlayerKeys.setApiKey(player, key);
		source.sendSuccess(() -> Component.literal("记下了。书现在知道该问谁了。")
				.withStyle(ChatFormatting.LIGHT_PURPLE), false);
		if (QianhunPlayerKeys.baseUrlOf(player).isBlank()) {
			source.sendSuccess(() -> Component.literal("还缺 Base URL：/qianhun key base <地址>")
					.withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int keySetBase(CommandSourceStack source, String value) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("该命令只能由玩家执行。"));
			return 0;
		}
		String cleaned = value.trim();
		while (cleaned.endsWith("/")) {
			cleaned = cleaned.substring(0, cleaned.length() - 1);
		}
		final String url = cleaned;
		if (url.isEmpty() || !url.startsWith("http://") && !url.startsWith("https://")) {
			source.sendFailure(Component.literal("这不像个网址，要以 http:// 或 https:// 开头。"));
			return 0;
		}
		QianhunPlayerKeys.setBaseUrl(player, url);
		source.sendSuccess(() -> Component.literal("记下了：" + url).withStyle(ChatFormatting.LIGHT_PURPLE), false);
		if (QianhunPlayerKeys.apiKeyOf(player).isBlank()) {
			source.sendSuccess(() -> Component.literal("还缺密钥：/qianhun key <你的 API Key>")
					.withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}

	private static int keyClear(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("该命令只能由玩家执行。"));
			return 0;
		}
		QianhunPlayerKeys.clear(player);
		source.sendSuccess(() -> Component.literal("已经忘掉了你的 Base URL 和密钥。"), false);
		return 1;
	}

	/** /qianhun spawn <type> [count] */
	private static int spawn(CommandSourceStack source, String typeId, int count) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("该命令只能由玩家执行。"));
			return 0;
		}
		if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			source.sendFailure(Component.literal("当前维度不支持。"));
			return 0;
		}
		int spawned = QianhunYouzhi.spawnManual(level, player, typeId, count);
		int n = spawned;
		source.sendSuccess(() -> Component.literal("生成了 " + n + " 只游尸（来源 " + typeId + "）。"), false);
		return spawned;
	}

	/** /qianhun config <key> —— 读 */
	private static int configGet(CommandSourceStack source, String key) {
		String value = QianhunConfig.describe(key);
		if (value == null) {
			source.sendFailure(Component.literal("没有这个配置项：" + key));
			return 0;
		}
		source.sendSuccess(() -> Component.literal(key + " = " + value), false);
		return 1;
	}

	/** /qianhun config <key> <value> —— 写 */
	private static int configSet(CommandSourceStack source, String key, String value) {
		if (!source.hasPermission(2)) {
			source.sendFailure(Component.literal("需要管理员权限。"));
			return 0;
		}
		String error = QianhunConfig.apply(key, value);
		if (error != null) {
			source.sendFailure(Component.literal(error));
			return 0;
		}
		source.sendSuccess(() -> Component.literal("已设置 " + key + " = " + value), false);
		return 1;
	}
}
