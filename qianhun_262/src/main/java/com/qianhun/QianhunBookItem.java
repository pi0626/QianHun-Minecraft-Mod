package com.qianhun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * 千魂书。原著：黑色封皮、极沉、说出真名才苏醒。
 *
 * 版本说明（26.2）：appendHoverText 换成了带 TooltipDisplay 与 Consumer 的签名。
 */
public class QianhunBookItem extends Item {
	public QianhunBookItem(Properties properties) {
		super(properties);
	}

	/**
	 * 心脏温血的用法（用户决策 2026-09-30）：在背包里把一滴心脏温血点到千魂书上，
	 * 温血被消耗，你即刻成为神选者。这就是"激活"——神选者本身不给任何数值，
	 * 它只意味着"那些死去的千魂，开始护佑你"。
	 *
	 * 钩子与原版束口袋同款：别的物品点到"我"身上时，会先问我要不要接管这次点击。
	 */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack book, ItemStack other, Slot slot,
			ClickAction action, Player player, SlotAccess carried) {
		if (action != ClickAction.PRIMARY || other.isEmpty()
				|| other.getItem() != QianhunItems.HEART_BLOOD) {
			return false;
		}
		if (player.level().isClientSide()) {
			// 客户端先拦下原版的合并/交换动画，真正的授予只发生在服务端
			return true;
		}
		if (QianhunEffects.has(player)) {
			player.sendSystemMessage(Component.literal("你已经是神选者了。")
					.withStyle(ChatFormatting.GRAY));
			return true;
		}
		other.shrink(1);
		carried.set(other);
		QianhunEffects.grant(player);
		player.sendSystemMessage(Component.literal("心脏温血渗进书页。那些死去的千魂，开始护佑你。")
				.withStyle(ChatFormatting.LIGHT_PURPLE));
		QianhunMod.LOGGER.info("[千魂] 玩家 {} 用心脏温血成为神选者", player.getName().getString());
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		boolean awake = QianhunSouls.awakened(stack);
		tooltip.accept(Component.literal(awake ? "封皮在缓慢流动" : "死气沉沉"));
		tooltip.accept(QianhunSoulCounter.progressText());
		tooltip.accept(Component.literal("持书者行动迟缓"));
		tooltip.accept(Component.literal("把一滴心脏温血点到这本书上，成为神选者"));
	}
}
