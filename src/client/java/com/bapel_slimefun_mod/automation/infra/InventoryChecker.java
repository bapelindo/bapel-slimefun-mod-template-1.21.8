package com.bapel_slimefun_mod.automation.infra;

import net.minecraft.world.item.ItemStack;

/**  
 * Utilitas pengecekan inventory pemain.  
 */  
public interface InventoryChecker {  

    /**  
     * Hitung jumlah item ini di inventory pemain.  
     * Pengecekan menggunakan Slimefun item ID + NBT, bukan hanya item type.  
     */  
    int countInInventory(ItemStack item);  

    /**  
     * Cek apakah inventory pemain memiliki cukup ruang  
     * untuk menerima output crafting.  
     */  
    boolean hasInventorySpace();  
}
