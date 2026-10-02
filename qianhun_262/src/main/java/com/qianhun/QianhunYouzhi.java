package com.qianhun;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游尸生成与结算。
 *
 * 规则（v3 设计规格 3.2）：
 *   入池条件 a 被玩家击杀，或 b 死于 AI 许愿生成的命令（原著 L2500）
 *   入池内容 实体类型 ID；若为玩家，另存皮肤 profile
 *   生成时机 死亡后当夜生成 1 只，位置在死亡点附近
 *   消散     日光升起后消失（由 YouzhiEntity.tick 负责）
 *   上限     同类型同时存在数量可配置，默认 8
 *
 * 待生成队列存在千魂书自身的自定义数据里，随书走。
 */
public final class QianhunYouzhi {
	private static final String KEY_PENDING = "YouzhiPending";
	private static final String KEY_TYPE = "T";
	private static final String KEY_X = "X";
	private static final String KEY_Y = "Y";
	private static final String KEY_Z = "Z";
	private static final String KEY_DIM = "D";
	private static final String KEY_PROFILE = "P";
	private static final int PENDING_CAP = 64;

	private QianhunYouzhi() {
	}

	/** 把一次死亡记进"当夜要生成的游尸"队列。 */
	public static void enqueue(ItemStack book, LivingEntity killed) {
		CompoundTag tag = QianhunSouls.rawTag(book);
		ListTag list = tag.getListOrEmpty(KEY_PENDING);
		if (list.size() >= PENDING_CAP) {
			return;
		}
		CompoundTag entry = new CompoundTag();
		entry.putString(KEY_TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()).toString());
		entry.putDouble(KEY_X, killed.getX());
		entry.putDouble(KEY_Y, killed.getY());
		entry.putDouble(KEY_Z, killed.getZ());
		entry.putString(KEY_DIM, killed.level().dimension().identifier().toString());
		if (killed instanceof Player player) {
			entry.putString(KEY_PROFILE, player.getName().getString());
		}
		list.add(entry);
		tag.put(KEY_PENDING, list);
		QianhunSouls.writeRawTag(book, tag);
	}

	/** 服务端每 tick 调一次：夜里把队列里的记录兑现成游尸。 */
	public static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			if (!isNight(level)) {
				continue;
			}
			// 千魂书在玩家背包里，队列跟着书走。
			for (Player player : level.players()) {
				ItemStack book = QianhunSouls.findBook(player);
				if (book.isEmpty()) {
					continue;
				}
				drain(level, book);
			}
		}
	}

	private static boolean isNight(ServerLevel level) {
		if (!level.dimensionType().hasSkyLight()) {
			return false;
		}
		// 与 YouzhiEntity.isDaylightHere 用同一套原版判定，保持一致。
		return !level.isBrightOutside();
	}

	private static void drain(ServerLevel level, ItemStack book) {
		CompoundTag tag = QianhunSouls.rawTag(book);
		ListTag list = tag.getListOrEmpty(KEY_PENDING);
		if (list.isEmpty()) {
			return;
		}
		List<CompoundTag> keep = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompoundOrEmpty(i);
			String typeId = entry.getString(KEY_TYPE).orElse("");
			String dim = entry.getString(KEY_DIM).orElse("");
			if (!typeId.isEmpty() && !dim.equals(level.dimension().identifier().toString())) {
				keep.add(entry);
				continue;
			}
			if (typeId.isEmpty() || !spawn(level, entry, typeId)) {
				keep.add(entry);
			}
		}
		// 全部兑现完则清空，避免无限重试堆积。
		if (keep.isEmpty()) {
			tag.remove(KEY_PENDING);
		} else {
			ListTag next = new ListTag();
			for (CompoundTag entry : keep) {
				next.add(entry);
			}
			tag.put(KEY_PENDING, next);
		}
		QianhunSouls.writeRawTag(book, tag);
	}

	/** 生成一只游尸。返回 true 表示已生成。 */
	private static boolean spawn(ServerLevel level, CompoundTag entry, String typeId) {
		Identifier id = Identifier.tryParse(typeId);
		if (id == null) {
			return true; // 类型无效，丢弃该记录
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
		if (type == null) {
			return true;
		}
		if (countOfType(level, typeId) >= QianhunConfig.get().youzhiPerTypeCap) {
			QianhunMod.LOGGER.info("[千魂] 游尸未生成：{} 已达上限 {}",
					typeId, QianhunConfig.get().youzhiPerTypeCap);
			return false; // 同类满了，留到明天
		}

		YouzhiEntity youzhi = QianhunEntities.YOUZHI.create(level, EntitySpawnReason.EVENT);
		if (youzhi == null) {
			return false;
		}
		double x = entry.getDoubleOr(KEY_X, 0.0D);
		double y = entry.getDoubleOr(KEY_Y, 0.0D);
		double z = entry.getDoubleOr(KEY_Z, 0.0D);
		youzhi.snapTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		youzhi.setSourceType(typeId);
		youzhi.setSourceProfile(entry.getString(KEY_PROFILE).orElse(""));
		youzhi.setPersistenceRequired();
		level.addFreshEntity(youzhi);
		QianhunMod.LOGGER.info("[千魂] 游尸生成成功：来源={} 位置={} {} {}", typeId,
				(int) x, (int) y, (int) z);
		return true;
	}

	private static int countOfType(ServerLevel level, String typeId) {
		int n = 0;
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof YouzhiEntity youzhi && typeId.equals(youzhi.getSourceType())) {
				n++;
			}
		}
		return n;
	}

	/** 手动生成（调试命令用）。 */
	public static int spawnManual(ServerLevel level, Player near, String typeId, int count) {
		int spawned = 0;
		for (int i = 0; i < count; i++) {
			CompoundTag entry = new CompoundTag();
			entry.putString(KEY_TYPE, typeId);
			entry.putDouble(KEY_X, near.getX() + level.getRandom().nextInt(5) - 2);
			entry.putDouble(KEY_Y, near.getY());
			entry.putDouble(KEY_Z, near.getZ() + level.getRandom().nextInt(5) - 2);
			entry.putString(KEY_DIM, level.dimension().identifier().toString());
			try {
				if (!spawn(level, entry, typeId)) {
					QianhunMod.LOGGER.info("[千魂] 手动生成中止于第 {} 只（spawn 返回 false）", i + 1);
					break;
				}
			} catch (Exception e) {
				QianhunMod.LOGGER.error("[千魂] 手动生成抛异常，来源={}", typeId, e);
				break;
			}
			spawned++;
		}
		return spawned;
	}

	/** 当前各来源类型存量，供 status 命令展示。 */
	public static Map<String, Integer> census(ServerLevel level) {
		Map<String, Integer> map = new HashMap<>();
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof YouzhiEntity youzhi) {
				map.merge(youzhi.getSourceType(), 1, Integer::sum);
			}
		}
		return map;
	}

	/** 队列里还有多少条待生成。 */
	public static int pendingCount(ItemStack book) {
		return QianhunSouls.rawTag(book).getListOrEmpty(KEY_PENDING).size();
	}

	/** 供配置项读取的位置辅助，避免调用方直接碰 NBT。 */
	public static BlockPos posOf(CompoundTag entry) {
		return new BlockPos(
				(int) entry.getDoubleOr(KEY_X, 0.0D),
				(int) entry.getDoubleOr(KEY_Y, 0.0D),
				(int) entry.getDoubleOr(KEY_Z, 0.0D));
	}
}
