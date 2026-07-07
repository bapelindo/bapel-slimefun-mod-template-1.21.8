package com.bapel_slimefun_mod.automation.recipe;

import net.minecraft.world.item.ItemStack;
import java.util.List;

/**  
 * Satu entri resep lengkap — output + bahan + mesin target.  
 */  
public record RecipeEntry(  
    String                  outputKey,       // key item output  
    ItemStack               outputItem,      // sample output ItemStack  
    int                     outputAmount,    // jumlah output per craft  
    List<RecipeIngredient>  ingredients,     // semua bahan  
    String                  requiredMachine  // ID mesin yang bisa craft ini  
) {  
    /**  
     * Hitung berapa kali harus di-craft untuk memenuhi jumlah target.  
     */  
    public int craftCountNeeded(int targetAmount) {  
        if (outputAmount <= 0) return 0;  
        return (int) Math.ceil((double) targetAmount / outputAmount);  
    }  

    /**  
     * Hitung total bahan yang dibutuhkan untuk N kali craft.  
     */  
    public int getTotalIngredientAmount(String ingredientKey, int craftCount) {  
        return ingredients.stream()  
            .filter(i -> i.itemKey().equals(ingredientKey))  
            .mapToInt(i -> i.amount() * craftCount)  
            .sum();  
    }  
}
