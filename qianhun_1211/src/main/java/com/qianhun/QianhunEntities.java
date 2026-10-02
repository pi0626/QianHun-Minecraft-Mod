package com.qianhun;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 游尸实体注册（MC 1.21.1）。
 *
 * 版本说明：1.21.1 的 EntityType.Builder.build 收字符串 id；
 * 26.2 收 ResourceKey。
 */
public final class QianhunEntities {
	public static final EntityType<YouzhiEntity> YOUZHI = EntityType.Builder
			.of(YouzhiEntity::new, MobCategory.MONSTER)
			.sized(0.6F, 1.95F)
			.clientTrackingRange(10)
			.build("qianhun:youzhi");

	private QianhunEntities() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, QianhunItems.id("youzhi"), YOUZHI);

		// 游尸：血少、走得慢、不还手。
		// 必须用 Mob.createMobAttributes() 作为基表，不能用 AttributeSupplier.builder()，
		// 否则缺少 LivingEntity 必需属性，实体创建时直接抛异常。
		AttributeSupplier.Builder attributes = Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.16D)
				.add(Attributes.ATTACK_DAMAGE, 0.0D)
				.add(Attributes.FOLLOW_RANGE, 16.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.35D);
		FabricDefaultAttributeRegistry.register(YOUZHI, attributes);
	}
}
