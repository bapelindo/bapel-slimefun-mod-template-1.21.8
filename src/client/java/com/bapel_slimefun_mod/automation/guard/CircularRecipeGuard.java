package com.bapel_slimefun_mod.automation.guard;

import com.bapel_slimefun_mod.automation.CraftingJob;
import java.util.Collection;

public class CircularRecipeGuard {
    public static boolean isCircular(CraftingJob job, Collection<String> visitedKeys) {
        return visitedKeys.contains(job.getItemKey());
    }
}
