package com.bapel_slimefun_mod.network;

import com.bapel_slimefun_mod.automation.recipe.RecipeEntry;
import com.bapel_slimefun_mod.automation.recipe.RecipeIngredient;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**  
 * Mengirim custom packet dari server ke client.  
 */  
public final class ServerPacketSender {  

    private static final Logger LOGGER = LoggerFactory.getLogger("ServerPacketSender");  

    private ServerPacketSender() {}  

    // ─────────────────────────────────────────────────────────────  

    /**  
     * Kirim full recipe registry ke satu player.  
     */  
    public static void sendFullRecipeSync(  
        ServerPlayer player,  
        List<RecipeEntry>  recipes  
    ) {  
        if (!ServerPlayNetworking.canSend(player, RecipeSyncFullPacket.TYPE)) {  
            LOGGER.warn("Player {} tidak support recipe sync packet", player.getName());  
            return;  
        }  

        var list = recipes.stream().map(ServerPacketSender::toPacket).toList();
        ServerPlayNetworking.send(player, new RecipeSyncFullPacket(list));  
        LOGGER.info("Full recipe sync sent to {}: {} recipes",  
            player.getName().getString(), recipes.size());  
    }  

    /**  
     * Broadcast delta update (satu resep baru) ke semua player online.  
     */  
    public static void broadcastRecipeAdd(  
        net.minecraft.server.MinecraftServer server,  
        RecipeEntry newRecipe  
    ) {  
        RecipeSyncDeltaPacket pkt = new RecipeSyncDeltaPacket((byte) 0, toPacket(newRecipe), null);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {  
            if (ServerPlayNetworking.canSend(player, RecipeSyncDeltaPacket.TYPE)) {  
                ServerPlayNetworking.send(player, pkt);  
            }  
        }  

        LOGGER.info("Recipe delta ADD broadcast: {}", newRecipe.outputKey());  
    }  

    /**  
     * Broadcast delta update (hapus resep) ke semua player online.  
     */  
    public static void broadcastRecipeRemove(  
        net.minecraft.server.MinecraftServer server,  
        String outputKey  
    ) {  
        RecipeSyncDeltaPacket pkt = new RecipeSyncDeltaPacket((byte) 1, null, outputKey);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {  
            if (ServerPlayNetworking.canSend(player, RecipeSyncDeltaPacket.TYPE)) {  
                ServerPlayNetworking.send(player, pkt);  
            }  
        }  

        LOGGER.info("Recipe delta REMOVE broadcast: {}", outputKey);  
    }  

    /**  
     * Kirim konfirmasi crafting ke player tertentu.  
     */  
    public static void sendCraftConfirm(  
        ServerPlayer player,  
        String itemKey,  
        int    amount  
    ) {  
        if (ServerPlayNetworking.canSend(player, CraftConfirmPacket.TYPE)) {
            ServerPlayNetworking.send(player, new CraftConfirmPacket(itemKey, amount));
        }
    }  

    // ─────────────────────────────────────────────────────────────  

    private static RecipeSyncPacket toPacket(RecipeEntry entry) {  
        var ingredients = entry.ingredients().stream()  
            .map(ing -> new RecipeSyncPacket.IngredientData(  
                ing.itemKey(), ing.amount(), ing.isOptional()  
            ))  
            .toList();  

        return new RecipeSyncPacket(  
            entry.outputKey(),  
            entry.outputAmount(),  
            entry.requiredMachine(),  
            ingredients  
        );  
    }  
}
