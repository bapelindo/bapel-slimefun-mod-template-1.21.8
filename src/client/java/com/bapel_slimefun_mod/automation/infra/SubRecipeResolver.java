package com.bapel_slimefun_mod.automation.infra;

import com.bapel_slimefun_mod.automation.CraftingJob;
import java.util.Optional;

/**  
 * Resolver untuk bahan yang kurang — cek apakah bisa dibuat via sub-recipe.  
 */  
public interface SubRecipeResolver {  

    /**  
     * Hitung berapa unit bahan yang masih kurang di inventory  
     * untuk menyelesaikan job ini.  
     *  
     * @return 0 jika cukup, >0 jika masih kurang  
     */  
    int getMissingAmount(CraftingJob job);  

    /**  
     * Coba resolve bahan kurang menjadi sub-job baru.  
     * Mempertimbangkan depth saat ini untuk guard max depth.  
     *  
     * @return sub-job jika bahan bisa dibuat, empty jika tidak ada resepnya  
     */  
    Optional<CraftingJob> resolveSubJob(CraftingJob parentJob, int currentDepth);  

    /**  
     * Hitung jumlah bahan yang sudah "dipesan" oleh job lain di stack  
     * (reserved materials) untuk mencegah double-pull dari Grid.  
     */  
    int getReservedAmount(String itemKey);  
}
