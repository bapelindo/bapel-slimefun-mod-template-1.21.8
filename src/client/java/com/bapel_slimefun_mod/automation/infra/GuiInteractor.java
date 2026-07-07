package com.bapel_slimefun_mod.automation.infra;

import net.minecraft.core.BlockPos;

/**  
 * Abstraksi interaksi dengan GUI blok (klik, tutup, cek status).  
 */  
public interface GuiInteractor {  

    /** Kirim right-click ke blok di pos ini */  
    void rightClickBlock(BlockPos pos);  

    /** Tutup GUI yang sedang terbuka */  
    void closeCurrentGui();  

    /** Cek apakah GUI Network Grid sedang terbuka */  
    boolean isGridGuiOpen();  

    /** Cek apakah GUI FastMachine sedang terbuka */  
    boolean isMachineGuiOpen();  

    /**  
     * Shift-click slot index tertentu di GUI yang sedang terbuka.  
     * Digunakan untuk pull item dari Grid ke inventory.  
     */  
    void shiftClickSlot(int slotIndex);  

    /**  
     * Ambil total jumlah slot yang terisi di GUI saat ini.  
     */  
    int getActiveGuiSlotCount();  
}
