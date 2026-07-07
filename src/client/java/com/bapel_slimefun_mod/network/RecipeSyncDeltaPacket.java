package com.bapel_slimefun_mod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RecipeSyncDeltaPacket(byte action, RecipeSyncPacket packet, String outputKey) implements CustomPacketPayload {
    public static final Type<RecipeSyncDeltaPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bapelmod", "recipe_sync_delta"));

    public static final StreamCodec<FriendlyByteBuf, RecipeSyncDeltaPacket> CODEC = StreamCodec.of(
        (buf, p) -> {
            buf.writeByte(p.action());
            if (p.action() == 0) {
                RecipeSyncPacket.CODEC.encode(buf, p.packet());
            } else {
                buf.writeUtf(p.outputKey());
            }
        },
        buf -> {
            byte action = buf.readByte();
            if (action == 0) {
                return new RecipeSyncDeltaPacket(action, RecipeSyncPacket.CODEC.decode(buf), null);
            } else {
                return new RecipeSyncDeltaPacket(action, null, buf.readUtf());
            }
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
