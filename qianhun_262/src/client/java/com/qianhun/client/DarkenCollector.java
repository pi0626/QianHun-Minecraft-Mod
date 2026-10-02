package com.qianhun.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

import java.util.List;

/**
 * 压暗用的提交收集器。
 *
 * 为什么不是 setShaderColor：26.2 已经删掉了 RenderSystem.setShaderColor，
 * 整套渲染改成了"提交节点"两段式。设计规格 v3 里写的 shader 压暗在 26.2 上不存在。
 * 替代做法：把 SubmitNodeCollector 包一层，凡是要提交模型的地方，
 * 都把颜色参数乘上暗色系数。等效于原来那句 setShaderColor，且不用 Mixin。
 */
public final class DarkenCollector implements SubmitNodeCollector {
	/** 深灰黑调系数，与设计规格「整体压暗为深灰黑调」对应。
	 *  早先用 0.38，实机截图下来近乎纯黑剪影、贴图细节全丢，调到 0.52 保住细节。 */
	public static final float FACTOR = 0.52F;
	private static final float WARM = 0.92F; // 保留一点冷调偏青，接近尸体感

	private final OrderedSubmitNodeCollector delegate;

	public DarkenCollector(OrderedSubmitNodeCollector delegate) {
		this.delegate = delegate;
	}

	@Override
	public OrderedSubmitNodeCollector order(int order) {
		// order(int) 只声明在 SubmitNodeCollector 上，拿不到可包装的实例。
		// 排序只决定提交顺序，不影响染色，所以直接把本实例交回去，
		// 保证后续所有模型提交仍然经过压暗。
		return this;
	}

	@Override
	public void submitShadow(PoseStack poseStack, float radius,
			List<EntityRenderState.ShadowPiece> pieces) {
		delegate.submitShadow(poseStack, radius, pieces);
	}

	@Override
	public void submitNameTag(PoseStack poseStack, Vec3 pos, int offset, Component name,
			boolean seeThrough, int light, CameraRenderState camera) {
		delegate.submitNameTag(poseStack, pos, offset, name, seeThrough, light, camera);
	}

	@Override
	public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text,
			boolean shadow, Font.DisplayMode mode, int light, int color, int backgroundColor, int outlineColor) {
		delegate.submitText(poseStack, x, y, text, shadow, mode, light, color, backgroundColor, outlineColor);
	}

	@Override
	public void submitFlame(PoseStack poseStack, EntityRenderState state, Quaternionf rotation) {
		delegate.submitFlame(poseStack, state, rotation);
	}

	@Override
	public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leash) {
		delegate.submitLeash(poseStack, leash);
	}

	@Override
	public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
			int light, int overlay, int color, TextureAtlasSprite sprite, int outlineColor,
			ModelFeatureRenderer.CrumblingOverlay crumbling) {
		delegate.submitModel(model, state, poseStack, renderType, light, overlay, darken(color), sprite,
				outlineColor, crumbling);
	}

	@Override
	public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState state, int outlineColor) {
		delegate.submitMovingBlock(poseStack, state, outlineColor);
	}

	@Override
	public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts,
			int[] tints, int light, int overlay, int outlineColor) {
		delegate.submitBlockModel(poseStack, renderType, parts, darkenAll(tints), light, overlay, outlineColor);
	}

	@Override
	public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int outlineColor) {
		delegate.submitBreakingBlockModel(poseStack, parts, outlineColor);
	}

	@Override
	public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color,
			float lineWidth, boolean seeThrough) {
		delegate.submitShapeOutline(poseStack, shape, renderType, color, lineWidth, seeThrough);
	}

	@Override
	public void submitItem(PoseStack poseStack, ItemDisplayContext context, int light, int overlay, int outlineColor,
			int[] tints, List<net.minecraft.client.resources.model.geometry.BakedQuad> quads,
			net.minecraft.client.renderer.item.ItemStackRenderState.FoilType foil) {
		delegate.submitItem(poseStack, context, light, overlay, outlineColor, darkenAll(tints), quads, foil);
	}

	@Override
	public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
			SubmitNodeCollector.CustomGeometryRenderer renderer) {
		delegate.submitCustomGeometry(poseStack, renderType, renderer);
	}

	@Override
	public void submitQuadParticleGroup(QuadParticleRenderState group) {
		delegate.submitQuadParticleGroup(group);
	}

	@Override
	public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera,
			boolean seeThrough) {
		delegate.submitGizmoPrimitives(group, camera, seeThrough);
	}

	// ---- 颜色处理 ----

	/** 把 ARGB 的 RGB 分量乘上暗色系数。 */
	public static int darken(int argb) {
		int a = (argb >>> 24) & 0xFF;
		int r = (int) (((argb >> 16) & 0xFF) * FACTOR);
		int g = (int) (((argb >> 8) & 0xFF) * FACTOR * WARM);
		int b = (int) ((argb & 0xFF) * FACTOR * WARM);
		return (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int[] darkenAll(int[] tints) {
		if (tints == null) {
			return null;
		}
		int[] out = new int[tints.length];
		for (int i = 0; i < tints.length; i++) {
			out[i] = tints[i] == 0 ? 0 : darken(tints[i]);
		}
		return out;
	}

	private static int clamp(int v) {
		return v < 0 ? 0 : Math.min(v, 255);
	}
}
