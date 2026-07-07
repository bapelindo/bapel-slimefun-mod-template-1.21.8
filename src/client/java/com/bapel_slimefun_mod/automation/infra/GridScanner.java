package com.bapel_slimefun_mod.automation.infra;

import com.bapel_slimefun_mod.automation.CraftingJob;

/**  
 * Scanner untuk isi Network Grid GUI — pagination dan pull bahan.  
 */  
public interface GridScanner {  

    /**  
     * Hitung berapa bahan yang masih kurang untuk job ini  
     * berdasarkan inventory + isi Grid halaman saat ini.  
     */  
    int getMissingAmount(CraftingJob job);  

    /**  
     * Lakukan shift-click untuk semua bahan yang relevan  
     * di halaman Grid tertentu.  
     *  
     * @return true jika halaman ini mengandung item yang relevan  
     */  
    boolean shiftClickMaterials(CraftingJob job, int page);  

    /**  
     * Navigasi ke halaman berikutnya di GUI Grid.  
     */  
    void navigateToPage(int page);  

    /**  
     * @return true jika ini halaman terakhir (tidak ada next page)  
     */  
    boolean isLastPage(int page);  
}
