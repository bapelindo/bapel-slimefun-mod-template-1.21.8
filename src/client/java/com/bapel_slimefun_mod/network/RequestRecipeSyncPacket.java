package com.bapel_slimefun_mod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RequestRecipeSyncPacket() implements CustomPacketPayload {
    public static final Type<RequestRecipeSyncPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bapelmod", "request_recipe_sync"));

    public static final StreamCodec<FriendlyByteBuf, RequestRecipeSyncPacket> CODEC = StreamCodec.of(
        (buf, packet) -> {},
        buf -> new RequestRecipeSyncPacket()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
