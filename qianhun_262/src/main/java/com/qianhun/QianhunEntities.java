package com.qianhun;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 游尸实体注册。
 *
 * 版本说明（26.2）：EntityType.Builder.build 现在必须传 ResourceKey。
 */
public final class QianhunEntities {
	public static final ResourceKey<EntityType<?>> YOUZHI_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, QianhunItems.id("youzhi"));

	public static final EntityType<YouzhiEntity> YOUZHI = EntityType.Builder
			.of(YouzhiEntity::new, MobCategory.MONSTER)
			.sized(0.6F, 1.95F)
			.clientTrackingRange(10)
			.build(YOUZHI_KEY);

	private QianhunEntities() {
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, YOUZHI_KEY, YOUZHI);

		// 游尸：血少、走得慢、不还手。
		// 必须用 Mob.createMobAttributes() 作为基表，不能用 AttributeSupplier.builder()。
		// 26.2 的 LivingEntity 会去要 WAYPOINT_TRANSMIT_RANGE 等一批属性，
		// 空表起步会在 EntityType.create 时抛 IllegalArgumentException。
		AttributeSupplier.Builder attributes = Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.16D)
				.add(Attributes.ATTACK_DAMAGE, 0.0D)
				.add(Attributes.FOLLOW_RANGE, 16.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.35D);
		FabricDefaultAttributeRegistry.register(YOUZHI, attributes);
	}
}
