package com.qianhun;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * 千魂事件：击杀计数、真名唤醒、游尸入池。
 *
 * 版本说明（1.21.1）：
 *   聊天事件第二参数是 PlayerChatMessage（Mojang 映射名），不是 SignedMessage。
 *   LivingEntity.getKillCredit() 返回 LivingEntity，需要自己判玩家。
 */
public final class QianhunEvents {
	/** 真名。原著 L6473：持有者说出真名，书才苏醒。 */
	public static final String TRUE_NAME = "千魂书";
	private static final String KEY_POOL = "YouzhiPool";
	private static final String POOL_SEPARATOR = ",";
	private static final int POOL_CAP = 64;

	private QianhunEvents() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(QianhunEvents::onDeath);
		ServerMessageEvents.CHAT_MESSAGE.register(QianhunEvents::onChat);
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		LivingEntity credit = entity.getKillCredit();
		if (!(credit instanceof ServerPlayer serverPlayer)) {
			return;
		}
		// 26.2 的 spawnAtLocation 需要 ServerLevel，先取出来给温血掉落用
		ServerLevel serverLevel = serverPlayer.level();
		// 全服统一计数：不持书也计（用户决策 2026-09-26）
		QianhunSoulCounter.add(1);

		// 以下几项仍需要击杀者背包里有千魂书：游尸队列与来源记录都写在书上。
		ItemStack book = QianhunSouls.findBook(serverPlayer);
		if (book.isEmpty()) {
			return;
		}
		recordPool(book, entity);

		// 游尸入池：死亡后当夜生成（原著 L2500 的 Minecraft 化）
		if (QianhunConfig.get().youzhiSpawnEnabled) {
			QianhunYouzhi.enqueue(book, entity);
		}

		// 心脏温血：从被击杀生物的心脏里取（原著 L1286）
		QianhunConfig cfg = QianhunConfig.get();
		if (cfg.heartBloodDropChance > 0.0D
				&& entity.getRandom().nextDouble() < cfg.heartBloodDropChance) {
			entity.spawnAtLocation(serverLevel, new ItemStack(QianhunItems.HEART_BLOOD), 0.0F);
		}
	}

	/** 记录击杀来源类型，供游尸照搬模型。 */
	private static void recordPool(ItemStack book, LivingEntity killed) {
		String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()).toString();
		CompoundTag tag = book.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		// 26.2 起 getString 返回 Optional，用 orElse 兜底
		String pool = tag.getString(KEY_POOL).orElse("");
		if (pool.contains(typeId)) {
			return;
		}
		String merged = pool.isEmpty() ? typeId : pool + POOL_SEPARATOR + typeId;
		String[] parts = merged.split(POOL_SEPARATOR);
		if (parts.length > POOL_CAP) {
			StringBuilder sb = new StringBuilder();
			for (int i = parts.length - POOL_CAP; i < parts.length; i++) {
				if (sb.length() > 0) {
					sb.append(POOL_SEPARATOR);
				}
				sb.append(parts[i]);
			}
			merged = sb.toString();
		}
		tag.putString(KEY_POOL, merged);
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	private static void onChat(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound bound) {
		String text = message.signedContent();
		if (!text.contains(TRUE_NAME)) {
			return;
		}
		ItemStack book = QianhunSouls.findBook(sender);
		if (book.isEmpty() || QianhunSouls.awakened(book)) {
			return;
		}
		QianhunSouls.awaken(book);
		sender.sendSystemMessage(Component.literal("那本黑色封皮的书动了一下。").withStyle(ChatFormatting.DARK_PURPLE));
		sender.sendSystemMessage(Component.literal("它认出了自己的名字。").withStyle(ChatFormatting.DARK_GRAY));
		// 告诉玩家怎么许愿。否则他随便打句话会石沉大海（消息被当成愿望送去 AI 了）。
		String prefix = QianhunConfig.get().chatWishPrefix;
		if (prefix != null && !prefix.isEmpty()) {
			sender.sendSystemMessage(Component.literal("想许愿，就说：" + prefix + " 你的愿望")
					.withStyle(ChatFormatting.GRAY));
			sender.sendSystemMessage(Component.literal("（这句话只有你自己看得见）").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
