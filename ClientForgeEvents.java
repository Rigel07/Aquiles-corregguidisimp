package com.achilles.client;

import com.achilles.Achilles;
import com.achilles.network.NetworkHandler;
import com.achilles.network.ZonePreviewRequestPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client events that belong to MinecraftForge.EVENT_BUS.
 *
 * RenderLevelStageEvent and ClientTickEvent are Forge bus events, not
 * Mod.EventBus events. Keeping them in a separate subscriber prevents
 * Forge from rejecting the class during mod loading.
 */
@Mod.EventBusSubscriber(modid = Achilles.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {
    /** La zona se muestra 3 segundos cada 10 segundos, sin usar ninguna tecla. */
    private static final int SHOW_TICKS = 60;
    private static final int INTERVAL_TICKS = 200;
    private static int previewTimer = INTERVAL_TICKS - 1;
    private static int visibleTicks = 0;

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            ClientHooks.clearZonePreview();
            visibleTicks = 0;
            return;
        }

        if (visibleTicks > 0) {
            visibleTicks--;
            if (visibleTicks == 0) {
                ClientHooks.clearZonePreview();
            }
            return;
        }

        previewTimer++;
        if (previewTimer >= INTERVAL_TICKS) {
            previewTimer = 0;
            visibleTicks = SHOW_TICKS;
            NetworkHandler.CHANNEL.sendToServer(new ZonePreviewRequestPacket());
        }
    }

    @SubscribeEvent
    public static void renderZone(RenderLevelStageEvent event) {
        if (visibleTicks <= 0 || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        BlockPos pos = ClientHooks.getZonePos();
        double radius = ClientHooks.getZoneRadius();
        Minecraft mc = Minecraft.getInstance();
        if (pos == null || radius <= 0 || mc.player == null) return;

        double maxDistance = radius + 32.0D;
        if (mc.player.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D) > maxDistance * maxDistance) return;

        PoseStack pose = event.getPoseStack();
        Camera camera = event.getCamera();
        var cam = camera.getPosition();

        pose.pushPose();
        pose.translate(pos.getX() + 0.5D - cam.x, pos.getY() + 0.06D - cam.y, pos.getZ() + 0.5D - cam.z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        Matrix3f normal = pose.last().normal();
        var matrix = pose.last().pose();
        int segments = 96;

        float pulse = 0.72F + 0.28F * (float) Math.sin((SHOW_TICKS - visibleTicks) * 0.22D);
        int alpha = (int) (150.0F * pulse);

        for (int i = 0; i < segments; i++) {
            double a0 = i * (Math.PI * 2.0D / segments);
            double a1 = (i + 1) * (Math.PI * 2.0D / segments);
            float x0 = (float) (Math.cos(a0) * radius);
            float z0 = (float) (Math.sin(a0) * radius);
            float x1 = (float) (Math.cos(a1) * radius);
            float z1 = (float) (Math.sin(a1) * radius);
            vc.vertex(matrix, x0, 0, z0).color(110, 220, 255, alpha).normal(normal, 0, 1, 0).endVertex();
            vc.vertex(matrix, x1, 0, z1).color(110, 220, 255, alpha).normal(normal, 0, 1, 0).endVertex();
        }

        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }
}
