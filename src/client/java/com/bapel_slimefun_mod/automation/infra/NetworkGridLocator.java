package com.bapel_slimefun_mod.automation.infra;

import net.minecraft.core.BlockPos;
import java.util.Optional;

/**  
 * Mencari posisi Network Grid terdekat dari posisi pemain.  
 */  
public interface NetworkGridLocator {  

    /**  
     * @param origin  posisi pemain saat ini  
     * @param radius  radius pencarian dalam blok  
     * @return BlockPos Grid terdekat, atau empty jika tidak ada  
     */  
    Optional<BlockPos> findNearest(BlockPos origin, int radius);  

    /**  
     * Cek apakah blok di pos tersebut benar-benar Network Grid  
     * dan dalam kondisi aktif/online.  
     */  
    boolean isGridActive(BlockPos pos);  
}
