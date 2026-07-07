package com.bapel_slimefun_mod.automation;  

import com.bapel_slimefun_mod.automation.recipe.*;  
import org.junit.jupiter.api.*;  

import static org.assertj.core.api.Assertions.*;  

@DisplayName("Circular Recipe Guard Tests")  
class CircularRecipeGuardTest {  

    private AutomationContext ctx;  

    @BeforeEach  
    void setUp() {  
        ctx = new AutomationContext();  
    }  

    // ─────────────────────────────────────────────────────────────  

    @Test  
    @DisplayName("pushJob kedua kali dengan key sama harus ditolak (circular)")  
    void shouldRejectCircularRecipe() {  
        CraftingJob jobA = new CraftingJob(  
            "slimefun:ITEM_A", net.minecraft.world.item.ItemStack.EMPTY, 1, 0  
        );  
        CraftingJob jobACopy = new CraftingJob(  
            "slimefun:ITEM_A", net.minecraft.world.item.ItemStack.EMPTY, 1, 1  
        );  

        boolean first  = ctx.pushJob(jobA);  
        boolean second = ctx.pushJob(jobACopy); // circular!  

        assertThat(first).isTrue();  
        assertThat(second).isFalse(); // harus ditolak  
    }  

    @Test  
    @DisplayName("pushJob berbeda key harus diterima")  
    void shouldAcceptDifferentKeys() {  
        CraftingJob jobA = new CraftingJob(  
            "slimefun:ITEM_A", net.minecraft.world.item.ItemStack.EMPTY, 1, 0  
        );  
        CraftingJob jobB = new CraftingJob(  
            "slimefun:ITEM_B", net.minecraft.world.item.ItemStack.EMPTY, 1, 1  
        );  

        assertThat(ctx.pushJob(jobA)).isTrue();  
        assertThat(ctx.pushJob(jobB)).isTrue();  
    }  

    @Test  
    @DisplayName("popJob harus release circular guard untuk key tersebut")  
    void shouldReleaseGuardOnPop() {  
        CraftingJob job = new CraftingJob(  
            "slimefun:ITEM_A", net.minecraft.world.item.ItemStack.EMPTY, 1, 0  
        );  

        ctx.pushJob(job);  
        ctx.popJob(); // release  

        CraftingJob jobAgain = new CraftingJob(  
            "slimefun:ITEM_A", net.minecraft.world.item.ItemStack.EMPTY, 1, 0  
        );  
        assertThat(ctx.pushJob(jobAgain)).isTrue();  
    }  

    @Test  
    @DisplayName("stack depth harus dibatasi MAX_DEPTH = 8")  
    void shouldEnforceMaxDepth() {  
        for (int i = 0; i < 8; i++) {  
            CraftingJob job = new CraftingJob(  
                "slimefun:ITEM_" + i,  
                net.minecraft.world.item.ItemStack.EMPTY, 1, i  
            );  
            assertThat(ctx.pushJob(job)).isTrue();  
        }  

        CraftingJob overflow = new CraftingJob(  
            "slimefun:ITEM_OVERFLOW",  
            net.minecraft.world.item.ItemStack.EMPTY, 1, 8  
        );  
        assertThat(ctx.pushJob(overflow)).isFalse();  
    }  
}
