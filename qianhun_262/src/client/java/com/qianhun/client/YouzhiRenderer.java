package com.qianhun.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qianhun.QianhunMod;
import com.qianhun.YouzhiEntity;
import com.qianhun.client.mixin.WalkAnimationStateAccessor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 游尸渲染器 —— 幽灵实体代理。
 *
 * 方案（设计规格 v3 §4.3）：
 *   服务端游尸的 NBT 里存 source_type（来源实体类型 ID）
 *   客户端按这个类型造一个"幽灵实体"，把位置、朝向、tick 同步过去
 *   调原版渲染器画幽灵，再把整体压暗
 *
 * 与 v3 规格的差异（26.2 实测结论）：
 *   v3 写的 RenderSystem.setShaderColor 在 26.2 已被删除，
 *   改为包一层 SubmitNodeCollector 传暗色系数（见 DarkenCollector），效果等价。
 *
 * 肢体摆动：通过 WalkAnimationStateAccessor 逐帧同步主人的行走动画状态。
 */
public class YouzhiRenderer extends EntityRenderer<YouzhiEntity, YouzhiRenderState> {
	/** 幽灵实体缓存。WeakHashMap 保证游尸卸载后能回收。 */
	private final Map<YouzhiEntity, Entity> ghosts = new WeakHashMap<>();
	/** 无法解析来源类型时的兜底：原版僵尸的人形骨架。 */
	private static final Identifier FALLBACK_TYPE = Identifier.withDefaultNamespace("zombie");

	public YouzhiRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.5F;
		this.shadowStrength = 0.0F; // 游尸没有影子，更像"不该存在的东西"
	}

	@Override
	public YouzhiRenderState createRenderState() {
		return new YouzhiRenderState();
	}

	@Override
	public void extractRenderState(YouzhiEntity entity, YouzhiRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.owner = entity;

		Entity ghost = resolveGhost(entity);
		if (ghost == null) {
			state.ghost = null;
			state.ghostState = null;
			state.fallsBack = true;
			return;
		}

		syncTransform(entity, ghost);
		state.ghost = ghost;
		state.fallsBack = false;
		try {
			state.ghostState = this.entityRenderDispatcher.extractEntity(ghost, partialTick);
		} catch (Exception e) {
			// 个别实体渲染器依赖世界状态，提取失败就退回兜底（不渲染）。
			state.ghostState = null;
			state.fallsBack = true;
		}
	}

	@Override
	public void submit(YouzhiRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
			CameraRenderState camera) {
		if (state.ghostState == null) {
			return;
		}
		// 位置传 (0,0,0)：管线交给我们的 poseStack 已经平移到实体所在处（相机相对），
		// 再传一次绝对坐标会把位移叠两遍。实测位姿平移曾等于 2*实体-相机，
		// 模型被甩到远处，屏幕上只剩几片碎片。幽灵与游尸同位置，所以偏移量为 0。
		this.entityRenderDispatcher.submit(state.ghostState, camera, 0.0D, 0.0D, 0.0D, poseStack,
				new DarkenCollector(collector));
	}

	// ---- 幽灵管理 ----

	private Entity resolveGhost(YouzhiEntity entity) {
		Entity cached = ghosts.get(entity);
		EntityType<?> wanted = resolveType(entity.getSourceType());
		if (cached != null && cached.getType() == wanted) {
			return cached;
		}
		Entity created = createGhost(entity, wanted);
		if (created == null) {
			ghosts.remove(entity);
			return null;
		}
		ghosts.put(entity, created);
		return created;
	}

	private EntityType<?> resolveType(String typeId) {
		EntityType<?> fallback = BuiltInRegistries.ENTITY_TYPE.getValue(FALLBACK_TYPE);
		if (typeId == null || typeId.isEmpty()) {
			return fallback;
		}
		Identifier id = Identifier.tryParse(typeId);
		if (id == null) {
			return fallback;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
		if (type == null || !type.canSummon()) {
			return fallback;
		}
		return type;
	}

	private Entity createGhost(YouzhiEntity entity, EntityType<?> type) {
		if (type == null || entity.level() == null) {
			return null;
		}
		try {
			Entity ghost = type.create(entity.level(), EntitySpawnReason.EVENT);
			if (ghost != null) {
				// 关键：幽灵是凭空虚造的、从没进过世界，所以没有实体 ID。
				// 26.2 的 LivingEntityRenderer 抽取状态时会经 ItemModelResolver 读
				// entity.getId()（渲染手持/装备用），未分配就抛
				// IllegalStateException: Tried to access entity ID before ID assignment，
				// 整只游尸就画不出来。这里手动补一个唯一 ID。
				ghost.setId(NEXT_GHOST_ID.getAndIncrement());
			}
			return ghost;
		} catch (Exception e) {
			return null;
		}
	}

	/** 幽灵 ID 发号器。从一个大数起，避开真实实体的 ID 段。 */
	private static final java.util.concurrent.atomic.AtomicInteger NEXT_GHOST_ID =
			new java.util.concurrent.atomic.AtomicInteger(1_000_000);

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
