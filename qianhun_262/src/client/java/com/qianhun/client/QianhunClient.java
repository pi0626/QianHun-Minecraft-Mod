package com.qianhun.client;

import com.qianhun.QianhunEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * 千魂 mod 客户端入口（MC 26.2）。
 */
public class QianhunClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 游尸不自带模型：它的外观由"被击杀实体的模型"照搬而来。
		EntityRendererRegistry.register(QianhunEntities.YOUZHI, YouzhiRenderer::new);
	}
}
