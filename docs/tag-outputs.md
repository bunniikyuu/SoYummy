# Tag-Based Recipe Outputs

A processor recipe's item and fluid outputs may be declared as **tags** instead of concrete stacks. At production time the block entity resolves a tag to one concrete stack using a configurable mod-priority list. This lets one recipe ship a generic output (e.g. "some copper ingot") that adapts to whatever mods are present.

## Output forms

Each output entry is either concrete or a tag. `count` (items) defaults to `1`; `amount` (fluids, mB) defaults to `1000`. Defaults are omitted from generated JSON.

```json
"item_outputs": [
  { "item": "minecraft:iron_ingot", "count": 1 },
  { "tag":  "c:ingots/copper",      "count": 2 }
],
"fluid_outputs": [
  { "fluid": "minecraft:lava",   "amount": 1000 },
  { "tag":   "c:molten_copper",  "amount": 144  }
]
```

Backed by `recipe/ItemOutput.java` and `recipe/FluidOutput.java` (union record + codec + stream codec). `getItemOutputs()` / `getFluidOutputs()` return these union lists (JEI/EMI consume them); `getOutputStack(i)` / `getOutputFluidStack(i)` return the resolved concrete stack used during production.

## Resolution - `mod_tag_priority`

Tag resolution is driven by a string list in **`config/<modid>/common.toml`**:

```toml
[tags]
    mod_tag_priority = ["minecraft", "mekanism", "my_fancy_mod"]
```

Resolution picks the tag member whose namespace (mod id) appears **earliest** in this list:

- Earlier in the list = higher priority.
- Namespaces not listed rank last.
- Tiebreak between equal-rank members = the tag's iteration order (stable).

The resolved priority order is cached in a global map (`recipe/TagOutputResolver.java`), rebuilt on config **load and reload** via `Common.refreshTagPriority()` - not recomputed per call.

## Completeness gate

`UniversalProcessorRecipe.isComplete()` treats a tag output as complete only if the tag resolves to at least one member. `RecipeInfo.getRecipes()` already filters on `isComplete()`, so a recipe whose output tag is empty is skipped at runtime. Tags are bound by the time recipes are queried (a level is available).

## Datagen

`UniversalProcessorRecipeBuilder` gained tag overloads:

| Method | Description |
|---|---|
| `.itemOutput(item)` / `.itemOutput(item, count)` | Concrete item output |
| `.itemOutput(TagKey<Item>, count)` | Item **tag** output |
| `.fluidOutput(fluid, amount)` / `.fluidOutput(FluidStack)` | Concrete fluid output |
| `.fluidOutput(TagKey<Fluid>, amount)` | Fluid **tag** output |

```java
processor("example_machine")
        .itemInput(Items.SAND)
        .itemOutput(ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "ingots/copper")), 2)
        .save(output, "sand_to_copper");
```

### Load-time conditions

When an **item** output is a tag, `save(...)` emits a `NOT(tag_empty(tag))` NeoForge condition, so the recipe is dropped at load if the tag is empty.

**Fluid tags have no load-time condition** - NeoForge 21.1.228's `TagEmptyCondition` is item-only (no fluid equivalent). Fluid-tag recipes rely on the runtime `isComplete()` gate instead. A custom `FluidTagEmptyCondition` is feasible (the condition context's `getTag` is generic) but is out of scope for the template.

## JEI / EMI display

- **JEI** - a tag output slot shows all tag members (rotating), primary first.
- **EMI** - the slot renders the tag members as a rotating ingredient; `getOutputs()` (recipe-tree resolution) supplies the resolved primary stack.

In both, members are ordered by `mod_tag_priority` so the resolved output shows first.
