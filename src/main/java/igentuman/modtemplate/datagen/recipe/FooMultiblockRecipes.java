package igentuman.modtemplate.datagen.recipe;

import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.item.Items;

import static igentuman.modtemplate.datagen.recipe.UniversalProcessorRecipeBuilder.controller;

public class FooMultiblockRecipes {

    public static void generate(RecipeOutput recipeOutput) {

        controller("foo_controller")
                .itemInput(Items.DIRT)
                .itemOutput(Items.SAND)
                .processTime(2000)
                .energyPerTick(-200)
                .save(recipeOutput, "dirt_silver_to_water");
    }
}
