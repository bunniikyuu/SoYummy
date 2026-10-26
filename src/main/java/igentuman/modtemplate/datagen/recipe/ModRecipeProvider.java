package igentuman.modtemplate.datagen.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.neoforged.neoforge.common.conditions.IConditionBuilder;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider  extends RecipeProvider implements IConditionBuilder {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    public void buildRecipes(RecipeOutput recipeOutput) {

        ExampleMachineRecipes.generate(recipeOutput);
        FooMultiblockRecipes.generate(recipeOutput);
/*        ShapedRecipeBuilder.shaped(MISC, EXAMPLE_ITEM.item())
                .pattern(" S ")
                .pattern("WEW")
                .pattern("TWT")
                .define('S', Items.STRING).define('W', ItemTags.WOOL)
                .define('E', Items.REDSTONE).define('T', ItemTags.ANVIL)
                .unlockedBy(getHasName(Items.ENDER_CHEST), has(Items.ANVIL)).save(recipeOutput);*/

    }
}
