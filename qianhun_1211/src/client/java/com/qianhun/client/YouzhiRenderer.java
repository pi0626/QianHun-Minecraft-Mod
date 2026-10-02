package com.qianhun.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.qianhun.QianhunItems;
import com.qianhun.YouzhiEntity;
import com.qianhun.client.mixin.WalkAnimationStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 游尸渲染器 —— 幽灵实体代理（MC 1.21.1）。
 *
 * 方案（设计规格 v3 §4.3）：
 *   服务端游尸的 NBT 里存 source_type（来源实体类型 ID）
 *   客户端按这个类型造一个"幽灵实体"，把位置、朝向同步过去
 *   调原版渲染器画幽灵，再用 RenderSystem.setShaderColor 整体压暗
 *
 * 版本说明：1.21.1 的渲染仍是单段式 render(entity, yaw, partialTick, ...)，
 * 没有 26.2 的 RenderState 阶段，所以这里能直接用 setShaderColor；
 * 26.2 的等价做法是包一层 SubmitNodeCollector（见 26.2 侧 DarkenCollector）。
 */
public class YouzhiRenderer extends EntityRenderer<YouzhiEntity> {
	/** 深灰黑调系数，与设计规格「整体压暗为深灰黑调」对应。 */
	private static final float DARK_R = 0.38F;
	private static final float DARK_G = 0.35F;
	private static final float DARK_B = 0.35F;

	/** 幽灵实体缓存。WeakHashMap 保证游尸卸载后能回收。 */
	private final Map<YouzhiEntity, Entity> ghosts = new WeakHashMap<>();
	/** 幽灵 ID 发号器。从一个大数起，避开真实实体的 ID 段。 */
	private static final java.util.concurrent.atomic.AtomicInteger NEXT_GHOST_ID =
			new java.util.concurrent.atomic.AtomicInteger(1_000_000);
	/** 无法解析来源类型时的兜底：原版僵尸的人形骨架。 */
	private static final ResourceLocation FALLBACK_TYPE =
			ResourceLocation.fromNamespaceAndPath("minecraft", "zombie");

	public YouzhiRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5F;
		this.shadowStrength = 0.0F; // 游尸没有影子，更像"不该存在的东西"
	}

	@Override
	public void render(YouzhiEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		Entity ghost = resolveGhost(entity);
		if (ghost == null) {
			return;
		}
		syncTransform(entity, ghost);

		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		RenderSystem.setShaderColor(DARK_R, DARK_G, DARK_B, 1.0F);
		try {
			dispatcher.render(ghost, 0.0D, 0.0D, 0.0D, entityYaw, partialTick, poseStack, buffer, packedLight);
		} catch (Exception e) {
			// 个别实体渲染器依赖世界状态，画不出来就跳过，不让它拖崩整个渲染线程。
		} finally {
			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		}

		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(YouzhiEntity entity) {
		return QianhunItems.id("textures/entity/youzhi.png");
	}

	// ---- 幽灵管理 ----

	private Entity resolveGhost(YouzhiEntity entity) {
		Entity cached = ghosts.get(entity);
		EntityType<?> wanted = resolveType(entity.getSourceType());
		if (wanted == null) {
			return null;
		}
		if (cached != null && cached.getType() == wanted) {
			return cached;
		}
		Entity created;
		try {
			created = wanted.create(entity.level());
			if (created != null) {
				// 幽灵从没进过世界，没有实体 ID；渲染时读 getId() 会抛
				// "Tried to access entity ID before ID assignment"。手动补一个。
				created.setId(NEXT_GHOST_ID.getAndIncrement());
			}
		} catch (Exception e) {
			created = null;
		}
		if (created == null) {
			ghosts.remove(entity);
			return null;
		}
		ghosts.put(entity, created);
		return created;
	}

	private EntityType<?> resolveType(String typeId) {
		EntityType<?> fallback = BuiltInRegistries.ENTITY_TYPE.get(FALLBACK_TYPE);
		if (typeId == null || typeId.isEmpty()) {
			return fallback;
		}
		ResourceLocation id = ResourceLocation.tryParse(typeId);
		if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
			return fallback;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
		// 防止自己画自己造成无限递归。
		if (type == null || type == net.minecraft.world.entity.EntityType.PLAYER) {
			return fallback;
		}
		return type;
	}

	private void syncTransform(LivingEntity owner, Entity ghost) {
		ghost.setPos(owner.getX(), owner.getY(), owner.getZ());
		ghost.setOldPosAndRot();
		ghost.setYRot(owner.getYRot());
		ghost.setXRot(owner.getXRot());
		ghost.yRotO = owner.yRotO;
		ghost.xRotO = owner.xRotO;
		ghost.tickCount = owner.tickCount;
		ghost.setDeltaMovement(owner.getDeltaMovement());
		ghost.setShiftKeyDown(owner.isShiftKeyDown());
		if (ghost instanceof LivingEntity ghostLiving && owner instanceof LivingEntity ownerLiving) {
			ghostLiving.yBodyRot = owner.yBodyRot;
			ghostLiving.yBodyRotO = owner.yBodyRotO;
			ghostLiving.yHeadRot = owner.yHeadRot;
			ghostLiving.yHeadRotO = owner.yHeadRotO;
			ghostLiving.hurtTime = 0;
			ghostLiving.deathTime = 0;
			ghostLiving.setHealth(ghostLiving.getMaxHealth());
			// 走路摆动：幽灵不参与世界 tick，动画状态永远停在 0，
			// 所以每帧把主人的行走状态原样抄过去。
			// 读写都走 Mixin Accessor（AW 在 client 源集不生效，实测）。
			WalkAnimationStateAccessor ghostWalk =
					(WalkAnimationStateAccessor) (Object) ghostLiving.walkAnimation;
			WalkAnimationStateAccessor ownerWalk =
					(WalkAnimationStateAccessor) (Object) ownerLiving.walkAnimation;
			ghostWalk.setSpeedOld(ownerWalk.getSpeedOld());
			ghostWalk.setSpeed(ownerWalk.getSpeed());
			ghostWalk.setPosition(ownerWalk.getPosition());
		}
	}
}
