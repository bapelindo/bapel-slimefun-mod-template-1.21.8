package com.bapel_slimefun_mod.automation.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class RecipeCacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("RecipeCache");
    private static final String FILENAME = "bapelmod-recipes.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class RecipeDTO {
        public String outputKey;
        public int outputAmount;
        public List<IngredientDTO> ingredients;
        public String requiredMachine;
    }

    public static class IngredientDTO {
        public String itemKey;
        public int amount;
    }

    public static void load() {
        Path path = getPath();
        if (!Files.exists(path)) return;

        try (Reader reader = Files.newBufferedReader(path)) {
            List<RecipeDTO> list = GSON.fromJson(reader, new TypeToken<List<RecipeDTO>>(){}.getType());
            if (list != null) {
                SlimefunRecipeRegistry reg = SlimefunRecipeRegistry.get();
                for (RecipeDTO dto : list) {
                    List<RecipeIngredient> ingredients = new ArrayList<>();
                    if (dto.ingredients != null) {
                        for (IngredientDTO ing : dto.ingredients) {
                            ingredients.add(RecipeIngredient.of(ing.itemKey, ing.amount));
                        }
                    }
                    reg.addRecipe(new RecipeEntry(
                        dto.outputKey,
                        net.minecraft.world.item.ItemStack.EMPTY,
                        dto.outputAmount,
                        ingredients,
                        dto.requiredMachine
                    ));
                }
                LOGGER.info("Loaded {} recipes from local cache: {}", list.size(), path);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load local recipe cache", e);
        }
    }

    public static void save() {
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            List<RecipeDTO> list = new ArrayList<>();
            SlimefunRecipeRegistry reg = SlimefunRecipeRegistry.get();
            
            for (String outputKey : reg.getKnownOutputs()) {
                for (RecipeEntry entry : reg.getRecipesFor(outputKey)) {
                    RecipeDTO dto = new RecipeDTO();
                    dto.outputKey = entry.outputKey();
                    dto.outputAmount = entry.outputAmount();
                    dto.requiredMachine = entry.requiredMachine();
                    dto.ingredients = new ArrayList<>();
                    for (RecipeIngredient ing : entry.ingredients()) {
                        IngredientDTO ingDTO = new IngredientDTO();
                        ingDTO.itemKey = ing.itemKey();
                        ingDTO.amount = ing.amount();
                        dto.ingredients.add(ingDTO);
                    }
                    list.add(dto);
                }
            }

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(list, writer);
            }
            LOGGER.debug("Saved recipe cache with {} entries", list.size());
        } catch (Exception e) {
            LOGGER.error("Failed to save local recipe cache", e);
        }
    }

    private static Path getPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILENAME);
    }
}
