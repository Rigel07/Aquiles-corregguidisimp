package com.achilles.client;

import com.achilles.network.OpenStatuePacket;
import com.achilles.network.ZonePreviewPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;

/** Puente servidor -> cliente (solo se carga en el cliente). */
public class ClientHooks {
    private static BlockPos zonePos;
    private static double zoneRadius;

    public static void updateZonePreview(ZonePreviewPacket packet) {
        zonePos = packet.visible ? packet.pos : null;
        zoneRadius = packet.radius;
    }

    public static BlockPos getZonePos() { return zonePos; }
    public static double getZoneRadius() { return zoneRadius; }
    public static void clearZonePreview() { zonePos = null; zoneRadius = 0.0D; }
    public static void openStatue(OpenStatuePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof StatueScreen screen && screen.getPos().equals(packet.pos)) {
            screen.refresh(packet);
        } else {
            mc.setScreen(new StatueScreen(packet));
        }
    }
}
