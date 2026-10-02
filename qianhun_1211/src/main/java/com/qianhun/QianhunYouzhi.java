package com.qianhun;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
 * 游尸生成与结算（MC 1.21.1）。
 *
 * 规则（v3 设计规格 3.2）：
 *   入池 a 被玩家击杀 / b 死于 AI 许愿生成的命令（原著 L2500）
 *   生成 死亡后当夜生成 1 只，位置在死亡点附近
 *   消散 日光升起后消失（由 YouzhiEntity.tick 负责）
 *   上限 同类型同时存在数量可配置，默认 8
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
		ListTag list = tag.getList(KEY_PENDING, 10);
		if (list.size() >= PENDING_CAP) {
			return;
		}
		CompoundTag entry = new CompoundTag();
		entry.putString(KEY_TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType()).toString());
		entry.putDouble(KEY_X, killed.getX());
		entry.putDouble(KEY_Y, killed.getY());
		entry.putDouble(KEY_Z, killed.getZ());
		entry.putString(KEY_DIM, killed.level().dimension().location().toString());
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
		return level.isNight();
	}

	private static void drain(ServerLevel level, ItemStack book) {
		CompoundTag tag = QianhunSouls.rawTag(book);
		ListTag list = tag.getList(KEY_PENDING, 10);
		if (list.isEmpty()) {
			return;
		}
		List<CompoundTag> keep = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			String typeId = entry.getString(KEY_TYPE);
			String dim = entry.getString(KEY_DIM);
			if (!typeId.isEmpty() && !dim.equals(level.dimension().location().toString())) {
				keep.add(entry);
				continue;
			}
			if (typeId.isEmpty() || !spawn(level, entry, typeId)) {
				keep.add(entry);
			}
		}
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

	private static boolean spawn(ServerLevel level, CompoundTag entry, String typeId) {
		ResourceLocation id = ResourceLocation.tryParse(typeId);
		if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
			return true;
		}
		if (countOfType(level, typeId) >= QianhunConfig.get().youzhiPerTypeCap) {
			return false;
		}

		YouzhiEntity youzhi = QianhunEntities.YOUZHI.create(level);
		if (youzhi == null) {
			return false;
		}
		double x = entry.getDouble(KEY_X);
		double y = entry.getDouble(KEY_Y);
		double z = entry.getDouble(KEY_Z);
		youzhi.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		youzhi.setSourceType(typeId);
		youzhi.setSourceProfile(entry.getString(KEY_PROFILE));
		youzhi.setPersistenceRequired();
		level.addFreshEntity(youzhi);
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
			entry.putString(KEY_DIM, level.dimension().location().toString());
			if (!spawn(level, entry, typeId)) {
				break;
			}
			spawned++;
		}
		return spawned;
	}

	public static Map<String, Integer> census(ServerLevel level) {
		Map<String, Integer> map = new HashMap<>();
		for (Entity entity : level.getAllEntities()) {
			if (entity instanceof YouzhiEntity youzhi) {
				map.merge(youzhi.getSourceType(), 1, Integer::sum);
			}
		}
		return map;
	}

	public static int pendingCount(ItemStack book) {
		return QianhunSouls.rawTag(book).getList(KEY_PENDING, 10).size();
	}

	public static BlockPos posOf(CompoundTag entry) {
		return new BlockPos((int) entry.getDouble(KEY_X), (int) entry.getDouble(KEY_Y), (int) entry.getDouble(KEY_Z));
	}
}
