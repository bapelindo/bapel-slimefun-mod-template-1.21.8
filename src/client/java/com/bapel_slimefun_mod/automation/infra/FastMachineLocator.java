package com.bapel_slimefun_mod.automation.infra;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import java.util.Optional;

/**  
 * Mencari posisi FastMachine yang bisa memproses item target.  
 */  
public interface FastMachineLocator {  

    /**  
     * @param origin      posisi pemain  
     * @param targetItem  item yang ingin di-craft  
     * @param radius      radius pencarian  
     * @return BlockPos mesin terdekat yang support resep tersebut  
     */  
    Optional<BlockPos> findNearest(BlockPos origin, ItemStack targetItem, int radius);  

    /**  
     * Cek apakah mesin di pos tersebut aktif dan tidak sedang dipakai.  
     */  
    boolean isMachineAvailable(BlockPos pos);  

    /**  
     * Cek apakah mesin di pos tersebut support recipe item ini.  
     */  
    boolean supportsRecipe(BlockPos pos, ItemStack targetItem);  
}
