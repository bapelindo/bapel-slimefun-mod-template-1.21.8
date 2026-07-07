package com.bapel_slimefun_mod.automation.infra;

import net.minecraft.core.BlockPos;

/**  
 * Mengontrol rotasi kamera pemain secara programatik.  
 */  
public interface CameraController {  

    /**  
     * Mulai memutar kamera secara smooth ke arah pos target.  
     * Tidak blocking — cek dengan isLookingAt() setelah beberapa tick.  
     */  
    void lookAt(BlockPos target);  

    /**  
     * @return true jika kamera sudah mengarah ke target  
     *         dengan toleransi ±2 derajat  
     */  
    boolean isLookingAt(BlockPos target);  

    /**  
     * Bebaskan kontrol kamera kembali ke pemain.  
     */  
    void release();  

    /**  
     * Cek apakah kamera sedang dalam mode automation  
     * (dikontrol oleh mod, bukan player).  
     */  
    boolean isControlled();  
}
