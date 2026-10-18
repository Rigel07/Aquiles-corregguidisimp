package com.achilles.network;

import com.achilles.client.ClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: una zona de protección para dibujar de forma sutil. */
public class ZonePreviewPacket {
    public final boolean visible;
    public final BlockPos pos;
    public final double radius;

    public ZonePreviewPacket(boolean visible, BlockPos pos, double radius) {
        this.visible = visible; this.pos = pos; this.radius = radius;
    }

    public static void encode(ZonePreviewPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.visible); buf.writeBlockPos(p.pos); buf.writeDouble(p.radius);
    }
    public static ZonePreviewPacket decode(FriendlyByteBuf buf) {
        return new ZonePreviewPacket(buf.readBoolean(), buf.readBlockPos(), buf.readDouble());
    }
    public static void handle(ZonePreviewPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.updateZonePreview(packet)));
        ctx.get().setPacketHandled(true);
    }
}
