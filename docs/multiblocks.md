# Multiblocks

The multiblock system lets you declare a controller block + optional port blocks + a shell/interior shape (or an authored NBT structure file) and have the template handle:

- Validator selection (auto-detected cubic, or NBT-driven `DeterminedMultiblockValidator`, or a custom `IMultiblockValidator`)
- Per-level off-thread tick executor for `IMultiblockLogic.tickServer`
- Structure-position index for O(1) block-change detection on break/place events
- Form / break lifecycle callbacks
- NBT persistence of the formed structure (skips re-validation on world reload)
- Client sync packets (`PacketMultiblockFormed` / `PacketMultiblockBroken`)
- Linking port block entities back to the controller via `MultiblockPartBE.setControllerPos`

## Quick Start

```java
// setup/ModEntries.java
public static final ModEntry FOO_CONTROLLER = addMultiblockController("foo_controller")
        .withLayout(SlotsLayout.ONE_TO_ONE)
        .itemCap(1, 1)
        .withEnergyOutput(1_000_000_000)
        .build();

public static final ModEntry FOO_PORT = addMultiblockPart("foo_port");

public static final MultiblockEntry FOO_MULTIBLOCK = MultiblockEntryBuilder.name("foo_multiblock")
        .controller(FOO_CONTROLLER)
        .ports(FOO_PORT)
        .casing(() -> SILVER.materialEntry().storageBlock().get(), () -> IRON_BLOCK)
        .interior(() -> DIRT)
        .sizeRange(3, 5, 3, 5, 3, 5)
        .build();
```

That call registers:

| What | Where |
|---|---|
| Controller block + BE + menu + screen | `BLOCKS`, `BLOCK_ENTITIES`, `CONTAINERS`, client screen registry |
| Port block + BE | `BLOCKS`, `BLOCK_ENTITIES` |
| `MultiblockEntry` | `MultiblockRegistry.ENTRIES` |
| Cubic validator (auto, from `sizeRange`) | runtime |
| Default logic + cache | runtime |
| Block-change listener | `MultiblockHandler` (NeoForge event bus) |

## Builder Methods

`MultiblockEntryBuilder.name(name)`

| Method | Purpose |
|---|---|
| `.controller(ModEntry)` | Controller `ModEntry` - must be created via `addMultiblockController(...)` |
| `.ports(ModEntry...)` | Port `ModEntry`s - created via `addMultiblockPart(...)`. Counted as valid shell blocks. |
| `.casing(Supplier<Block>...)` | Extra valid shell blocks (e.g. vanilla `IRON_BLOCK`, a material storage block) |
| `.interior(Supplier<Block>...)` | Valid interior blocks. If empty: interior is `BlockPredicate.any()`. |
| `.size(w, h, d)` | Fixed-size cube |
| `.sizeRange(minW, maxW, minH, maxH, minD, maxD)` | Cubic min/max dimensions |
| `.validator(Supplier<IMultiblockValidator>)` | Override the default cubic validator |
| `.logic(Supplier<IMultiblockLogic>)` | Override default `MultiblockLogicImpl` |
| `.cache(Supplier<IMultiblockCache>)` | Override default `MultiblockCacheImpl` |
| `.build()` | Register entry in `MultiblockRegistry` |

`addMultiblockController(name)` returns a `ModEntryBuilder` - use the standard `ModEntryBuilder` chain (`.itemCap`, `.fluidCap`, `.withEnergyInput`, `.withEnergyOutput`, `.withLayout`, `.withRecipes(...)`) and call `.build()`.

`addMultiblockPart(name)` / `addMultiblockPart(name, props)` returns a `ModEntry` directly.

## Validators

### Cubic (default)

Used when `.validator(...)` is not set. The validator (`CubicMultiblockValidator`) detects the bounding box by ray-casting from the controller, then validates the full outer shell against `shellPredicate` (controller + ports + casing) and the interior against `interiorPredicate`.

The controller may sit anywhere on the shell - face, edge, or corner. Detection uses six axis-aligned scans from the controller; for axes the controller is flush against, a probe walks into the structure to find the opposite face.

Dimension constraints come from `.size` / `.sizeRange`. If a detected dimension is out of range, validation fails.

### Determined (NBT structure)

Use when the multiblock has a fixed authored shape:

```java
.validator(() -> new DeterminedMultiblockValidator(
        "foo_multiblock",
        BlockPredicate.of(FOO_CONTROLLER.block().get())))
```

The structure file is loaded from `data/<modid>/structures/<name>.nbt` (standard MC structure NBT, the kind a Structure Block saves). All non-controller cells must match the saved state exactly (after horizontal rotation derived from controller `facing`). Air cells in the file must be air in the world. The controller cell is matched via the predicate so any controller `BlockState` rotation/variant is tolerated.

To author one: place the structure in-world, save it with a Structure Block named `<modid>:<name>`, copy the resulting `.nbt` into `src/main/resources/data/<modid>/structures/`.

### Custom

Implement `IMultiblockValidator`:

```java
public class MyValidator implements IMultiblockValidator {
    @Override
    public boolean validate(Level level, BlockPos controllerPos, Direction facing, IMultiblockCache cache) {
        // populate cache.getStructurePositions() with every block (incl. controller)
        // return true if the world matches
    }
}
```

Then `.validator(MyValidator::new)`.

**Contract:** the validator MUST populate `cache.getStructurePositions()` with `BlockPos.asLong()` of every cell belonging to the structure (including the controller). The handler uses this set to build the per-level position index - without it, block-change events won't trigger re-validation.

## Logic

Default `MultiblockLogicImpl` only wires `MultiblockPartBE.setControllerPos(...)` on form / break. To add behavior, subclass it or implement `IMultiblockLogic`:

```java
public interface IMultiblockLogic {
    void onFormed(Level level, BlockPos controllerPos, IMultiblockCache cache);
    void onBroken(Level level, BlockPos controllerPos, IMultiblockCache cache);
    void tickServer(Level level, BlockPos controllerPos, IMultiblockCache cache);
    void tickClient(Level level, BlockPos controllerPos);
}
```

**Threading warning:** `tickServer` runs on a per-level executor thread, NOT the main server thread. Any world mutation inside `tickServer` MUST be queued back via `((ServerLevel) level).getServer().execute(...)`. Reading state through `cache` is fine - the cache is single-instance per multiblock.

Wire via `.logic(MyLogic::new)`.

## Cache

`IMultiblockCache` caches `BlockState` and `BlockEntity` lookups for a single instance and owns the persistent set of structure positions. The default `MultiblockCacheImpl` covers most cases - override only if you need extra persisted state.

Cache is invalidated per-position on block-change events. Full clear happens on validator entry to ensure stale entries can't survive a re-validation.

The controller BE persists `cache.saveNbt(...)` in `saveAdditional` so a formed multiblock survives world reload without re-validation. On `onLoad`, `MultiblockHandler.restoreMultiblock` rebuilds the position index and calls `logic.onFormed` again.

## Lifecycle

```
Controller placed
  → MultiblockControllerBE.onControllerPlaced
  → MultiblockHandler.initMultiblock (creates MultiblockInstance, not yet formed)

Every 5 ticks (while not formed)
  → MultiblockHandler.submitTick (off-thread)
  → instance.tryValidate → validator.validate(...)
    → if valid: formed=true, indexStructure, logic.onFormed, sendFormed packet
    → else: nothing

Any block changes inside the structure
  → MultiblockHandler.handleBlockChange (lookup by position index)
  → instance.onStructureBlockChanged → re-validate
    → was formed + invalid: logic.onBroken, sendBroken packet
    → was unformed + valid: logic.onFormed, sendFormed packet

Controller broken
  → MultiblockControllerBE.onControllerRemoved
  → MultiblockHandler.destroyMultiblock → logic.onBroken if formed
```

## Port Block Entities

`MultiblockPartBE` stores a back-reference to its controller's `BlockPos`. Use it to forward capabilities (energy, items, fluids) from a port to the controller's `GlobalBlockEntity` capabilities. The reference is set/cleared automatically by `MultiblockLogicImpl.onFormed` / `onBroken`.

## Structure Preview

`igentuman.modtemplate.util.StructurePreviewRenderer` can render an example structure ghost overlay in-world (for "how do I build this" UX). Combine with `MultiblockEntry.getExampleStructure()` which resolves either a loaded structure or one at `data/<modid>/example_structures/<name>.nbt`.

## Files

```
api/multiblock/
  IMultiblockValidator.java   - validator interface
  IMultiblockLogic.java       - logic/tick interface
  IMultiblockCache.java       - cache + structure-position set
  BlockPredicate.java         - functional predicate over (BlockState, BlockEntity)

api/impl/
  CubicMultiblockValidator.java       - axis-aligned hollow box
  DeterminedMultiblockValidator.java  - NBT-structure matcher
  MultiblockCacheImpl.java            - default cache
  MultiblockLogicImpl.java            - default logic (wires port BEs)
  MultiblockPattern.java              - optional 3D BlockPredicate grid

multiblock/
  MultiblockEntry.java         - registered multiblock
  MultiblockEntryBuilder.java  - fluent builder
  MultiblockRegistry.java      - name → entry map
  MultiblockHandler.java       - per-level executor, instances, event subs

block/
  MultiblockControllerBlock.java
  MultiblockPartBlock.java
block_entity/
  MultiblockControllerBE.java
  MultiblockPartBE.java
util/
  MultiblockStructure.java       - parses MC structure NBT
  MultiblocksProvider.java       - resource-pack reload listener for structures
  StructurePreviewRenderer.java
```
