package com.bapel_slimefun_mod.network;

import com.bapel_slimefun_mod.automation.recipe.RecipeEntry;
import com.bapel_slimefun_mod.automation.recipe.RecipeIngredient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record RecipeSyncPacket(  
    String              outputKey,  
    int                 outputAmount,  
    String              requiredMachine,  
    List<IngredientData> ingredients  
) implements CustomPacketPayload {  

    public static final Type<RecipeSyncPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("bapelmod", "recipe_sync_single"));

    public record IngredientData(  
        String  itemKey,  
        int     amount,  
        boolean optional  
    ) {}  

    public static final StreamCodec<FriendlyByteBuf, RecipeSyncPacket> CODEC =  
        StreamCodec.of(RecipeSyncPacket::encode, RecipeSyncPacket::decode);  

    public static void encode(FriendlyByteBuf buf, RecipeSyncPacket packet) {  
        buf.writeUtf(packet.outputKey());  
        buf.writeInt(packet.outputAmount());  
        buf.writeUtf(packet.requiredMachine());  
        buf.writeInt(packet.ingredients().size());  
        for (IngredientData ing : packet.ingredients()) {  
            buf.writeUtf(ing.itemKey());  
            buf.writeInt(ing.amount());  
            buf.writeBoolean(ing.optional());  
        }  
    }  

    public static RecipeSyncPacket decode(FriendlyByteBuf buf) {  
        String outputKey       = buf.readUtf();  
        int    outputAmount    = buf.readInt();  
        String requiredMachine = buf.readUtf();  
        int    count           = buf.readInt();  

        List<IngredientData> ingredients = new ArrayList<>(count);  
        for (int i = 0; i < count; i++) {  
            ingredients.add(new IngredientData(  
                buf.readUtf(),  
                buf.readInt(),  
                buf.readBoolean()  
            ));  
        }  

        return new RecipeSyncPacket(outputKey, outputAmount, requiredMachine, ingredients);  
    }  

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public RecipeEntry toRecipeEntry() {  
        List<RecipeIngredient> recipeIngredients = ingredients.stream()  
            .map(ing -> new RecipeIngredient(ing.itemKey(), ing.amount(), ing.optional()))  
            .toList();  

        return new RecipeEntry(  
            outputKey,  
            net.minecraft.world.item.ItemStack.EMPTY,  
            outputAmount,  
            recipeIngredients,  
            requiredMachine  
        );  
    }  
}
