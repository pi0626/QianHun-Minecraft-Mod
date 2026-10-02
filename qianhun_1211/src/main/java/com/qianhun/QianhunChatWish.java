package com.qianhun;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 聊天许愿入口（用户明确要求，覆盖此前的 GUI 方案）。
 *
 * 玩家按 T，在聊天框里直接说愿望。条件是：身上有千魂书、书已苏醒、千魂已满。
 * 满足时这条消息不会广播给其他人，只有自己看得见，然后直接送 AI。
 */
public final class QianhunChatWish {
	private QianhunChatWish() {
	}

	public static void register() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(QianhunChatWish::allowChat);
	}

	private static boolean allowChat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound bound) {
		String text = message.signedContent();
		if (text == null || text.isBlank()) {
			return true;
		}
		String trimmed = text.trim();
		if (trimmed.startsWith("/")) {
			return true;
		}

		QianhunConfig cfg = QianhunConfig.get();
		String prefix = cfg.chatWishPrefix == null ? "" : cfg.chatWishPrefix;
		if (!prefix.isEmpty() && !trimmed.startsWith(prefix)) {
			return true;
		}

		ItemStack book = QianhunSouls.findBook(sender);
		if (book.isEmpty() || !QianhunSouls.awakened(book) || !QianhunSoulCounter.isFull()) {
			return true;
		}

		String wish = prefix.isEmpty() ? trimmed : trimmed.substring(prefix.length()).trim();
		if (wish.isEmpty()) {
			sender.sendSystemMessage(Component.literal("书在等你说出愿望。").withStyle(ChatFormatting.DARK_GRAY));
			return false;
		}

		sender.sendSystemMessage(Component.literal("<" + sender.getName().getString() + "> " + text)
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

		QianhunWish.submit(sender, wish);
		return false;
	}
}
