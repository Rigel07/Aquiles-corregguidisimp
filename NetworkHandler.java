package com.achilles.network;

import com.achilles.Achilles;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Achilles.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, OpenStatuePacket.class,
                OpenStatuePacket::encode, OpenStatuePacket::decode, OpenStatuePacket::handle);
        CHANNEL.registerMessage(1, UpgradePacket.class,
                UpgradePacket::encode, UpgradePacket::decode, UpgradePacket::handle);
        CHANNEL.registerMessage(2, TrustPacket.class,
                TrustPacket::encode, TrustPacket::decode, TrustPacket::handle);
        CHANNEL.registerMessage(3, ZonePreviewRequestPacket.class,
                ZonePreviewRequestPacket::encode, ZonePreviewRequestPacket::decode, ZonePreviewRequestPacket::handle);
        CHANNEL.registerMessage(4, ZonePreviewPacket.class,
                ZonePreviewPacket::encode, ZonePreviewPacket::decode, ZonePreviewPacket::handle);
    }
}
