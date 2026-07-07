package com.bapel_slimefun_mod.automation.network;  

public enum MachineStatus {  
    ONLINE,   // siap dipakai  
    OFFLINE,  // tidak aktif  
    BUSY;     // sedang dipakai player lain  

    public static MachineStatus fromByte(byte b) {  
        return switch (b) {  
            case 1  -> OFFLINE;  
            case 2  -> BUSY;  
            default -> ONLINE;  
        };  
    }  
}
