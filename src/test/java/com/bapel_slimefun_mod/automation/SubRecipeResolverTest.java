package com.bapel_slimefun_mod.automation;  

import com.bapel_slimefun_mod.automation.impl.SubRecipeResolverImpl;  
import com.bapel_slimefun_mod.automation.infra.InventoryChecker;  
import com.bapel_slimefun_mod.automation.recipe.*;  
import net.minecraft.world.item.ItemStack;  
import org.junit.jupiter.api.*;  

import java.util.List;  
import java.util.Optional;  

import static org.assertj.core.api.Assertions.*;  

@DisplayName("SubRecipeResolver Tests")  
class SubRecipeResolverTest {  

    private static class MockInventoryChecker implements InventoryChecker {
        public int count = 0;

        @Override
        public int countInInventory(ItemStack stack) {
            return count;
        }

        @Override
        public boolean hasInventorySpace() {
            return true;
        }
    }

    private MockInventoryChecker inventoryChecker;
    private SubRecipeResolverImpl resolver;  

    @BeforeEach  
    void setUp() {  
        inventoryChecker = new MockInventoryChecker();
        resolver = new SubRecipeResolverImpl(inventoryChecker);  

        SlimefunRecipeRegistry reg = SlimefunRecipeRegistry.get();  
        reg.clear();  

        reg.addRecipe(new RecipeEntry(  
            "slimefun:COPPER_INGOT",  
            ItemStack.EMPTY, 1,  
            List.of(RecipeIngredient.of("slimefun:COPPER_DUST", 4)),  
            "slimefun:electric_smeltery"  
        ));  

        reg.addRecipe(new RecipeEntry(  
            "slimefun:COPPER_DUST",  
            ItemStack.EMPTY, 2,  
            List.of(RecipeIngredient.of("minecraft:raw_copper", 1)),  
            "slimefun:electric_ore_grinder"  
        ));  
    }  

    @AfterEach  
    void tearDown() {  
        resolver.clearAllReservations();  
        SlimefunRecipeRegistry.get().clear();  
    }  

    // ─────────────────────────────────────────────────────────────  

    @Test  
    @DisplayName("getMissingAmount: semua ada di inventory → return 0")  
    void shouldReturnZeroWhenInventoryHasEnough() {  
        CraftingJob job = new CraftingJob(  
            "slimefun:COPPER_INGOT", ItemStack.EMPTY, 1, 0  
        );  
        inventoryChecker.count = 5;

        assertThat(resolver.getMissingAmount(job)).isEqualTo(0);  
    }  

    @Test  
    @DisplayName("getMissingAmount: inventory kosong → return amountNeeded")  
    void shouldReturnFullAmountWhenInventoryEmpty() {  
        CraftingJob job = new CraftingJob(  
            "slimefun:COPPER_INGOT", ItemStack.EMPTY, 3, 0  
        );  
        inventoryChecker.count = 0;

        assertThat(resolver.getMissingAmount(job)).isEqualTo(3);  
    }  

    @Test  
    @DisplayName("resolveSubJob: bahan kurang harus return sub-job")  
    void shouldResolveSubJobForMissingIngredient() {  
        CraftingJob parentJob = new CraftingJob(  
            "slimefun:COPPER_INGOT", ItemStack.EMPTY, 1, 0  
        );  

        Optional<CraftingJob> subJob = resolver.resolveSubJob(parentJob, 0);  

        assertThat(subJob).isPresent();  
        assertThat(subJob.get().getItemKey()).isEqualTo("slimefun:COPPER_DUST");  
        assertThat(subJob.get().getDepth()).isEqualTo(1);  
    }  

    @Test  
    @DisplayName("resolveSubJob: depth >= MAX_DEPTH harus return empty")  
    void shouldReturnEmptyAtMaxDepth() {  
        CraftingJob job = new CraftingJob(  
            "slimefun:COPPER_INGOT", ItemStack.EMPTY, 1, 0  
        );  

        Optional<CraftingJob> result = resolver.resolveSubJob(job, 8);  

        assertThat(result).isEmpty();  
    }  
}
