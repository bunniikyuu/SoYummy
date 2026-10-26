# Entry Config Gating

Every non-material `ModEntryBuilder` registration gets its own TOML toggle. Toggling it disables the entry's runtime behavior and hides it everywhere, without unregistering the underlying block/item.

## Where the toggles live

Toggles are written to **`config/<modid>/entries.toml`** under the `[entries]` section, one boolean per entry name, default `true`:

```toml
[entries]
    example_machine = true
    example_item = true
    silver_tools = true
    silver_armor = true
    foo_controller = true
    foo_port = true
```

The spec is built by `config/Entries.java`, which walks every entry in `ModEntries.ENTRIES` where `materialEntry() == null`. Material entries are **not** here - they are gated per product type in `materials.toml` (see [Materials Registration](./materials-registration.md)).

## What "disabled" means

`DeferredRegister` registration happens at mod construction, so a disabled entry's block/item is **still registered** - it does not vanish from the world. Instead, disabling makes it inert:

| Surface | Behavior when disabled |
|---|---|
| Server tick | `GlobalBlockEntity.serverTick()` early-returns - no processing, no energy use |
| Block interaction | `UniversalProcessorBlock` will not open its GUI (block stays placeable but inert) |
| Creative tabs | Entry is skipped in `addCreative` (all kinds: item, tool, armor, block, machine) |
| JEI / EMI | Categories, catalysts, and recipes for the entry are not registered |

The tick and interaction gates read the config value live, so a config **reload** takes effect without a game restart (where Minecraft allows). Creative-tab and JEI/EMI visibility are evaluated when those are built.

## Checking enablement in code

```java
ModEntry entry = ModEntries.get("example_machine");
if (entry.isEnabled()) {
    // ...
}

// or by name
if (ModEntries.isEnabled("example_machine")) {
    // ...
}
```

`ModEntry.isEnabled()` is the single source of truth:
- material entries → always `true` here (gated per product type elsewhere)
- everything else → the `entries.toml` toggle (`Entries.isEnabled(name)`)

## Migration note

This folds the former `processors.toml` into `entries.toml`. The `Processors` class is retained as a thin alias (`Processors.isEnabled` delegates to `Entries.isEnabled`) so existing fork code keeps compiling, but the config file is now `entries.toml` and covers **all** entry kinds - not only block-entity machines. Multiblock controllers, which previously appeared under `processors`, now sit under `entries` like everything else.
