package com.friendfinder.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record PingPayload(String sender, int x, int y, int z) implements CustomPayload {

    public static final CustomPayload.Id<PingPayload> ID =
            new CustomPayload.Id<>(Identifier.of("friendfinder", "ping"));

    public static final PacketCodec<RegistryByteBuf, PingPayload> CODEC =
            new PacketCodec<>() {
                @Override
                public PingPayload decode(RegistryByteBuf buf) {
                    return new PingPayload(
                            buf.readString(), buf.readInt(), buf.readInt(), buf.readInt());
                }

                @Override
                public void encode(RegistryByteBuf buf, PingPayload payload) {
                    buf.writeString(payload.sender());
                    buf.writeInt(payload.x());
                    buf.writeInt(payload.y());
                    buf.writeInt(payload.z());
                }
            };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
