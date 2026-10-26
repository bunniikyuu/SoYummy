# JEI Integration

JEI recipe categories are registered **dynamically** for every `ModEntry` that has recipes. No manual JEI code is needed when you add a new processor.

## How It Works

`ModJeiPlugin` (in `compat/jei/`) is annotated with `@JeiPlugin` and implements `IModPlugin`. It iterates over `ModEntries.ENTRIES` at registration time and creates one JEI category per entry that has a recipe type.

For each such entry, three things are registered:

| What | Where |
|---|---|
| **Recipe category** | `registerCategories` - titled with the block's translation name, using the machine block as icon |
| **Recipe catalyst** | `registerRecipeCatalysts` - the machine block item opens its category in JEI |
| **Recipes** | `registerRecipes` - all recipes of the machine's `RecipeType` from the `RecipeManager` |

The category layout (`ProcessorRecipeCategory`) arranges item and fluid input/output slots automatically based on the `ModEntry` capability definitions (`itemCap` / `fluidCap`).

## Zero-Code Setup

When you register a processor in `ModEntries.java`:

```java
public static final ModEntry MY_MACHINE = addProcessor("my_machine")
        .fluidCap(1, 1, 0)
        .itemCap(2, 1)
        .build();
```

And add at least one recipe (via datagen or a hand-written JSON), the JEI category appears automatically on the next game launch. No changes to `ModJeiPlugin.java` are required.

## Category Layout

`ProcessorRecipeCategory` reads slot positions from `SlotsLayout` (which is derived from the entry's `itemCap` / `fluidCap` counts). The layout is:

- **Input item slots** - left side, stacked vertically
- **Output item slots** - right side, stacked vertically
- **Input fluid tanks** - left side, next to item inputs
- **Output fluid tanks** - right side, next to item outputs
- **Energy cost + process time** - displayed as text below the slots

## Custom Recipe Classes and JEI

`ModJeiPlugin` is typed to `UniversalProcessorRecipe`. If you use a **custom recipe class**, you have two options:

### Option A - make your recipe extend `UniversalProcessorRecipe`

If your custom recipe subclasses `UniversalProcessorRecipe` and adds only extra fields, the existing `ProcessorRecipeCategory` can display it without any changes - standard inputs/outputs are already handled.

```java
public class AdvancedMachineRecipe extends UniversalProcessorRecipe {
    private final int requiredTemperature;

    public AdvancedMachineRecipe(String processorName, List<SizedIngredient> itemInputs,
                                 List<ItemStack> itemOutputs, int processTime,
                                 int requiredTemperature) {
        super(processorName, itemInputs, List.of(), itemOutputs, List.of(), processTime, 20);
        this.requiredTemperature = requiredTemperature;
    }
}
```

### Option B - create a custom JEI category

If your recipe is completely different, create a dedicated `IRecipeCategory` implementation and register it manually by adding to `ModJeiPlugin.registerCategories`:

```java
// compat/jei/ModJeiPlugin.java

@Override
public void registerCategories(IRecipeCategoryRegistration registration) {
    var guiHelper = registration.getJeiHelpers().getGuiHelper();

    for (ModEntry entry : ModEntries.ENTRIES.values()) {
        if (!entry.hasRecipes()) continue;

        if (entry.name().equals("advanced_machine")) {
            RecipeType<AdvancedMachineRecipe> jeiType =
                    RecipeType.create(MODID, "advanced_machine", AdvancedMachineRecipe.class);
            registration.addRecipeCategories(
                    new AdvancedMachineRecipeCategory(guiHelper, entry, jeiType));
        } else {
            RecipeType<UniversalProcessorRecipe> jeiType = getOrCreateRecipeType(entry);
            registration.addRecipeCategories(
                    new ProcessorRecipeCategory(guiHelper, entry, jeiType));
        }
    }
}
```

## Dependencies

JEI integration is discovered automatically via `@JeiPlugin`. If JEI is absent from the mod list, the class is never loaded and no errors occur because it is in the optional `compat/jei/` package, which is not referenced from any non-optional code path.
