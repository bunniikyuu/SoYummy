package igentuman.modtemplate.datagen.recipe;

import igentuman.modtemplate.setup.ModEntries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import static igentuman.modtemplate.datagen.recipe.UniversalProcessorRecipeBuilder.processor;

public class ExampleMachineRecipes {

    public static void generate(RecipeOutput recipeOutput) {
        // example_machine: 1 item input + 1 fluid input → 1 fluid output

        // Recipe 1: sand + water = lava
        processor("example_machine")
                .itemInput(Items.SAND)
                .fluidInput(Fluids.WATER, 1000)
                .fluidInput("heavy_water", 1000)
                .fluidOutput(Fluids.LAVA, 1000)
                .processTime(200)
                .energyPerTick(20)
                .save(recipeOutput, "sand_water_to_lava");

        // Recipe 2: dirt + molten silver = water
        processor("example_machine")
                .itemInput(Items.DIRT)
                .fluidInput(ModEntries.SILVER.materialEntry().materialFluid().source().get(), 1000)
                .fluidOutput(Fluids.WATER, 1000)
                .processTime(200)
                .energyPerTick(20)
                .save(recipeOutput, "dirt_silver_to_water");
    }
}
