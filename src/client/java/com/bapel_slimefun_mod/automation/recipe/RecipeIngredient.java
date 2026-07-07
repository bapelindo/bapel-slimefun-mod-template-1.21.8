package com.bapel_slimefun_mod.automation.recipe;

import com.bapel_slimefun_mod.automation.util.ItemKeyUtil;
import net.minecraft.world.item.ItemStack;

/**  
 * Satu bahan dalam sebuah resep.  
 */  
public record RecipeIngredient(  
    String  itemKey,       // "slimefun:COPPER_DUST"  
    int     amount,        // jumlah yang dibutuhkan  
    boolean isOptional     // opsional atau wajib  
) {  
    public static RecipeIngredient of(ItemStack stack, int amount) {  
        return new RecipeIngredient(ItemKeyUtil.getKey(stack), amount, false);  
    }  

    public static RecipeIngredient of(String key, int amount) {  
        return new RecipeIngredient(key, amount, false);  
    }  
}
