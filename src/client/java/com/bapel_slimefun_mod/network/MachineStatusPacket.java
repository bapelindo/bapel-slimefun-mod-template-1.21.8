package com.bapel_slimefun_mod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MachineStatusPacket(int x, int y, int z, byte status) implements CustomPacketPayload {
    public static final Type<MachineStatusPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bapelmod", "machine_status"));

    public static final StreamCodec<FriendlyByteBuf, MachineStatusPacket> CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeInt(packet.x());
            buf.writeInt(packet.y());
            buf.writeInt(packet.z());
            buf.writeByte(packet.status());
        },
        buf -> new MachineStatusPacket(buf.readInt(), buf.readInt(), buf.readInt(), buf.readByte())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
