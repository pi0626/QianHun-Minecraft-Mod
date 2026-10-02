package com.qianhun.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 游尸的渲染状态。
 *
 * 26.2 起渲染走"状态提取 + 提交"两段式：
 *   createRenderState(T, partialTick) → extractRenderState(T, S, partialTick) → submit(S, ...)
 * 所以幽灵实体与幽灵状态都要挂在这上面带过去。
 */
public class YouzhiRenderState extends EntityRenderState {
	/** 服务端游尸在客户端的镜像。 */
	public LivingEntity owner;

	/** 来源类型的幽灵实体。null 表示要退回内置人形。 */
	public Entity ghost;

	/** 幽灵实体提取出来的渲染状态。 */
	public EntityRenderState ghostState;

	public boolean fallsBack;
}
