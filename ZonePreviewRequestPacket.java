package com.achilles.network;

import com.achilles.data.StatueRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Cliente -> servidor: pide los datos de la zona de la estatua permitida más cercana. */
public class ZonePreviewRequestPacket {
    public static void encode(ZonePreviewRequestPacket p, FriendlyByteBuf buf) {}
    public static ZonePreviewRequestPacket decode(FriendlyByteBuf buf) { return new ZonePreviewRequestPacket(); }

    public static void handle(ZonePreviewRequestPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            var statue = StatueRegistry.findNearestAllowed(player.level(), player.position(), 96.0D, player);
            if (statue == null) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ZonePreviewPacket(false, player.blockPosition(), 0.0D));
            } else {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new ZonePreviewPacket(true, statue.getBlockPos(), statue.getRadius()));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
