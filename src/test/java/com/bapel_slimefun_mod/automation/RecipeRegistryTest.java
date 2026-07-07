package com.bapel_slimefun_mod.automation;  

import com.bapel_slimefun_mod.automation.recipe.*;  
import net.minecraft.world.item.ItemStack;  
import org.junit.jupiter.api.*;  

import java.util.List;  

import static org.assertj.core.api.Assertions.*;  

@DisplayName("SlimefunRecipeRegistry Tests")  
class RecipeRegistryTest {  

    private SlimefunRecipeRegistry registry;  

    @BeforeEach  
    void setUp() {  
        registry = SlimefunRecipeRegistry.get();  
        registry.clear();  
    }  

    @AfterEach  
    void tearDown() {  
        registry.clear();  
    }  

    // ─────────────────────────────────────────────────────────────  

    @Test  
    @DisplayName("Registry kosong setelah clear()")  
    void shouldBeEmptyAfterClear() {  
        registry.addRecipe(makeRecipe("slimefun:TEST", "slimefun:electric_smeltery"));  
        registry.clear();  

        assertThat(registry.hasRecipe("slimefun:TEST")).isFalse();  
        assertThat(registry.totalRecipes()).isEqualTo(0);  
    }  

    @Test  
    @DisplayName("addRecipe dan hasRecipe harus konsisten")  
    void shouldFindRecipeAfterAdd() {  
        registry.addRecipe(makeRecipe("slimefun:COPPER_INGOT", "slimefun:electric_smeltery"));  

        assertThat(registry.hasRecipe("slimefun:COPPER_INGOT")).isTrue();  
        assertThat(registry.hasRecipe("slimefun:UNKNOWN")).isFalse();  
    }  

    @Test  
    @DisplayName("getRecipesFor harus return semua resep untuk output")  
    void shouldReturnAllRecipesForOutput() {  
        registry.addRecipe(makeRecipe("slimefun:ITEM_A", "slimefun:machine_1"));  
        registry.addRecipe(makeRecipe("slimefun:ITEM_A", "slimefun:machine_2"));  

        assertThat(registry.getRecipesFor("slimefun:ITEM_A")).hasSize(2);  
    }  

    @Test  
    @DisplayName("getBestRecipe harus return resep pertama yang terdaftar")  
    void shouldReturnFirstRegisteredRecipe() {  
        registry.addRecipe(makeRecipe("slimefun:ITEM_A", "slimefun:machine_1"));  
        registry.addRecipe(makeRecipe("slimefun:ITEM_A", "slimefun:machine_2"));  

        var best = registry.getBestRecipe("slimefun:ITEM_A");  
        assertThat(best).isPresent();  
        assertThat(best.get().requiredMachine()).isEqualTo("slimefun:machine_1");  
    }  

    @Test  
    @DisplayName("removeRecipe harus menghapus semua resep untuk outputKey")  
    void shouldRemoveAllRecipesForKey() {  
        registry.addRecipe(makeRecipe("slimefun:ITEM_A", "slimefun:machine_1"));  
        registry.addRecipe(makeRecipe("slimefun:ITEM_A", "slimefun:machine_2"));  
        registry.removeRecipe("slimefun:ITEM_A");  

        assertThat(registry.hasRecipe("slimefun:ITEM_A")).isFalse();  
    }  

    // ─────────────────────────────────────────────────────────────  

    private RecipeEntry makeRecipe(String outputKey, String machine) {  
        return new RecipeEntry(  
            outputKey, ItemStack.EMPTY, 1,  
            List.of(RecipeIngredient.of("minecraft:stone", 1)),  
            machine  
        );  
    }  
}
