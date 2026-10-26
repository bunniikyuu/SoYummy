# KubeJS Support

The mod integrates with [KubeJS](https://kubejs.com/) to allow server-side scripting of processor recipes. Recipe schemas are registered **automatically** for every `ModEntry` that has a recipe type - no manual KubeJS code is needed when you add a new processor.

## How It Works

`ModKubeJSPlugin` (in `compat/kubejs/`) implements `KubeJSPlugin` and registers a `RecipeSchema` for each entry with recipes during `registerRecipeSchemas`. It is discovered automatically via the service loader registration in `kubejs.plugins.txt`.

The schema covers the standard `UniversalProcessorRecipe` fields:

| Field | KubeJS key | Type |
|---|---|---|
| Item inputs | `item_inputs` | `SizedIngredient[]` (input) |
| Fluid inputs | `fluid_inputs` | `SizedFluidIngredient[]` (input) |
| Item outputs | `item_outputs` | `ItemStack[]` (output) |
| Fluid outputs | `fluid_outputs` | `FluidStack[]` (output) |
| Process time | `process_time` | `int` |
| Energy per tick | `energy_per_tick` | `int` |

All fields except `process_time` and `energy_per_tick` are optional (default to empty lists / 0).

## Writing Recipes in KubeJS

Place scripts in the `kubejs/server_scripts/` folder of your modpack or dev environment.

### Add a recipe

```js
// kubejs/server_scripts/example_machine_recipes.js
ServerEvents.recipes(event => {
    event.recipes.modtemplate.example_machine({
        item_inputs:  [{ item: 'minecraft:sand', count: 1 }],
        fluid_inputs: [{ fluid: 'minecraft:water', amount: 1000 }],
        fluid_outputs: [{ fluid: 'minecraft:lava', amount: 1000 }],
        process_time: 200,
        energy_per_tick: 20
    })
})
```

The recipe type id is always `<modid>:<processor_name>`, e.g. `modtemplate:example_machine`.

### Remove a recipe

```js
ServerEvents.recipes(event => {
    event.remove({ type: 'modtemplate:example_machine', id: 'modtemplate:example_machine/sand_water_to_lava' })
})
```

### Modify a recipe

```js
ServerEvents.recipes(event => {
    event.replaceInput(
        { type: 'modtemplate:example_machine' },
        'minecraft:sand',
        'minecraft:gravel'
    )
})
```

## Adding a New Processor (no extra KubeJS code needed)

When you register a new processor in `ModEntries.java`:

```java
public static final ModEntry MY_MACHINE = addProcessor("my_machine")
        .itemCap(2, 1)
        .build();
```

KubeJS automatically picks it up on the next game reload. You can immediately write recipes using `event.recipes.modtemplate.my_machine({ ... })`.

## Custom Recipe Fields

If you supply a **custom recipe serializer** with extra fields (e.g. `required_temperature`), the default `UNIVERSAL_PROCESSOR_SCHEMA` in `ModKubeJSPlugin` will not know about those fields. You have two options:

### Option A - extend the plugin with an additional schema

Add a new schema and register it explicitly inside `registerRecipeSchemas`:

```java
// compat/kubejs/ModKubeJSPlugin.java
private static final RecipeKey<Integer> REQUIRED_TEMPERATURE =
        NumberComponent.INT.otherKey("required_temperature");

private static final RecipeSchema ADVANCED_MACHINE_SCHEMA = new RecipeSchema(
        ITEM_INPUTS, FLUID_INPUTS, ITEM_OUTPUTS, FLUID_OUTPUTS,
        PROCESS_TIME, ENERGY_PER_TICK, REQUIRED_TEMPERATURE
);

@Override
public void registerRecipeSchemas(RecipeSchemaRegistry registry) {
    ModEntries.init();
    for (ModEntry entry : ModEntries.ENTRIES.values()) {
        if (!entry.hasRecipes()) continue;
        ResourceLocation id = entry.recipeType().getId();
        if (id.getPath().equals("advanced_machine")) {
            registry.register(id, ADVANCED_MACHINE_SCHEMA);
        } else {
            registry.register(id, UNIVERSAL_PROCESSOR_SCHEMA);
        }
    }
}
```

### Option B - write recipes as raw JSON

KubeJS also accepts raw JSON recipe definitions, bypassing schema validation:

```js
ServerEvents.recipes(event => {
    event.custom({
        type: 'modtemplate:advanced_machine',
        item_inputs: [{ ingredient: { item: 'minecraft:diamond' }, count: 2 }],
        item_outputs: [{ id: 'modtemplate:advanced_component', count: 1 }],
        process_time: 400,
        required_temperature: 1500
    })
})
```

## Dependencies

KubeJS support is loaded only when KubeJS is present. The plugin is registered via:

```
src/main/resources/kubejs.plugins.txt
```

Content:
```
igentuman.modtemplate.compat.kubejs.ModKubeJSPlugin
```

If KubeJS is absent from the mod list, this file is ignored and no errors occur.
