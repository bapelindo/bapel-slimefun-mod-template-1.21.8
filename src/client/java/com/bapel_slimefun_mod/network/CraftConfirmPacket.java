package com.bapel_slimefun_mod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CraftConfirmPacket(String itemKey, int amount) implements CustomPacketPayload {
    public static final Type<CraftConfirmPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bapelmod", "craft_confirm"));

    public static final StreamCodec<FriendlyByteBuf, CraftConfirmPacket> CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeUtf(packet.itemKey());
            buf.writeInt(packet.amount());
        },
        buf -> new CraftConfirmPacket(buf.readUtf(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
