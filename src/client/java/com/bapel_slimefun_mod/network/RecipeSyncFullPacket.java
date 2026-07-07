package com.bapel_slimefun_mod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

public record RecipeSyncFullPacket(List<RecipeSyncPacket> recipes) implements CustomPacketPayload {
    public static final Type<RecipeSyncFullPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bapelmod", "recipe_sync_full"));

    public static final StreamCodec<FriendlyByteBuf, RecipeSyncFullPacket> CODEC = StreamCodec.of(
        (buf, packet) -> {
            buf.writeInt(packet.recipes().size());
            for (RecipeSyncPacket r : packet.recipes()) {
                RecipeSyncPacket.CODEC.encode(buf, r);
            }
        },
        buf -> {
            int count = buf.readInt();
            List<RecipeSyncPacket> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(RecipeSyncPacket.CODEC.decode(buf));
            }
            return new RecipeSyncFullPacket(list);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
