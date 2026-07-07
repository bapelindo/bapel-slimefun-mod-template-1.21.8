package com.bapel_slimefun_mod.automation.infra;

import net.minecraft.world.item.ItemStack;

/**  
 * Mengunci resep di GUI FastMachine dan mengeksekusi crafting.  
 */  
public interface RecipeLockExecutor {  

    /**  
     * Klik slot resep di GUI mesin untuk item target,  
     * lalu trigger eksekusi.  
     *  
     * @return true jika berhasil mengunci dan memulai crafting,  
     *         false jika GUI belum siap atau resep tidak ditemukan  
     */  
    boolean lockAndExecute(ItemStack targetItem);  

    /**  
     * Cek apakah proses crafting sedang berjalan di mesin aktif.  
     */  
    boolean isCraftingInProgress();  
}
