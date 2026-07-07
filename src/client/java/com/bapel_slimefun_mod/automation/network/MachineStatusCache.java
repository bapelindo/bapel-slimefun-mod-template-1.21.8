package com.bapel_slimefun_mod.automation.network;  

import net.minecraft.core.BlockPos;  
import java.util.Map;  
import java.util.concurrent.ConcurrentHashMap;  

/**  
 * Cache status mesin yang dikirim server.  
 * Digunakan oleh FastMachineLocatorImpl untuk filter mesin yang available.  
 */  
public final class MachineStatusCache {  

    private static final MachineStatusCache INSTANCE = new MachineStatusCache();  
    public static MachineStatusCache get() { return INSTANCE; }  

    private final Map<BlockPos, MachineStatus> cache = new ConcurrentHashMap<>();  

    private MachineStatusCache() {}  

    public void update(BlockPos pos, MachineStatus status) {  
        if (status == MachineStatus.OFFLINE) {  
            cache.remove(pos);  
        } else {  
            cache.put(pos, status);  
        }  
    }  

    public MachineStatus getStatus(BlockPos pos) {  
        return cache.getOrDefault(pos, MachineStatus.ONLINE);  
    }  

    public boolean isAvailable(BlockPos pos) {  
        return getStatus(pos) == MachineStatus.ONLINE;  
    }  

    public void clear() { cache.clear(); }  
}
