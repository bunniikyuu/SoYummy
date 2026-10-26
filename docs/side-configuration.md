# Side Configuration System

Every processor machine exposes a **side configuration UI** that lets players control how each item/fluid slot behaves on each face of the block. Configuration is stored in NBT on the block entity and survives chunk unload and world restart.

## Slot Modes

| Mode | Applies to | Behaviour |
|---|---|---|
| `INPUT` | Input slots | Accepts items/fluids pushed in from external pipes |
| `PULL` | Input slots | Actively pulls items/fluids from an adjacent inventory each tick |
| `OUTPUT` | Output slots | Accepts extraction by external pipes |
| `PUSH` | Output slots | Actively pushes items/fluids into an adjacent inventory each tick |
| `DISABLED` | Any slot | No access from this face |
| `DEFAULT` | Extra slots | Read/write, no auto-transfer |

Click cycles:
- Input slots: `INPUT` → `PULL` → `DISABLED` → `INPUT`
- Output slots: `OUTPUT` → `PUSH` → `DISABLED` → `OUTPUT`

## Architecture

```
SidedContentHandler
  ├── ItemCapabilityHandler      (extends AbstractCapabilityHandler)
  │     ├── ItemHandlerWrapper   (per-face filtered view)
  │     ├── pushItems()          (called each tick for PUSH faces)
  │     └── pullItems()          (called each tick for PULL faces)
  └── FluidCapabilityHandler     (extends AbstractCapabilityHandler)
        ├── FluidHandlerWrapper  (per-face filtered view)
        ├── pushFluids()
        └── pullFluids()
```

### Key classes

| Class | Responsibility |
|---|---|
| `SlotModePair` | Packs `(slot index, SlotMode)` into a single `int` for compact NBT storage |
| `AbstractCapabilityHandler` | Stores `sideMap` (`Direction → SlotModePair[]`), provides `toggleMode`, `hasPush`, `hasPull`, NBT save/load |
| `ItemCapabilityHandler` | Extends the base with item-specific push/pull tick logic |
| `FluidCapabilityHandler` | Extends the base with fluid-specific push/pull tick logic |
| `ItemHandlerWrapper` | Per-face `IItemHandler` view returned by capability queries |
| `FluidHandlerWrapper` | Per-face `IFluidHandler` view returned by capability queries |
| `SidedContentHandler` | Combines both handlers, drives the per-tick push/pull cycle, delegates `toggleSideConfig` |
| `PacketSideConfigToggle` | Client-to-server packet sent when a player clicks a slot mode button |
| `SideConfigScreen` | First step of the side config GUI: pick a face |
| `SideConfigSlotSelectionScreen` | Second step: toggle slot modes for the selected face |

## Relative Direction Mapping

The block always stores configuration in **relative** directions (FRONT, BACK, LEFT, RIGHT, UP, DOWN) so rotating the block preserves intent. The conversion happens at access time via `SidedContentHandler.RelativeDirection.toRelative(absoluteDir, facing)`.

## Usage in a Processor

Side configuration is automatically enabled for every block entity that extends `GlobalBlockEntity`. The `contentHandler` field is always present and drives the tick logic:

```java
// GlobalBlockEntity.java (simplified)
public final SidedContentHandler contentHandler;

// called every tick by UniversalProcessorBlock's BlockEntityTicker
public void tick(Level level, BlockPos pos, BlockState state) {
    contentHandler.tick(level, pos, state);  // runs push/pull cycle
    recipeInfo.tick(level, pos, state);      // processes recipes
}
```

Capability queries from pipes/cables call `contentHandler.getItemHandler(side)` and `contentHandler.getFluidHandler(side)`, which return a filtered wrapper that only exposes slots allowed by the current side configuration.

## Customizing Side Config for a Custom BlockEntity

If you create a custom block entity extending `GlobalBlockEntity`, side configuration works automatically. To add extra custom slots that are **not** part of the standard item/fluid capability, override `initContentHandler` and register them with the handler:

```java
// No override needed in most cases - GlobalBlockEntity handles everything
// The slot count and tank count come from the ModEntry capability definitions:
//   .itemCap(inputSlots, outputSlots)
//   .fluidCap(inputTanks, outputTanks, defaultTanks)
```

To change the **default mode** for a specific face when the block is first placed, override the initialization inside your block entity constructor and call `contentHandler.setSideMode(...)`.

## Opening the Side Config GUI

The `SideConfigButton` widget (in `screen/element/`) opens the side configuration screen when clicked. Add it to any custom screen:

```java
// In your Screen's init() method:
addRenderableWidget(new SideConfigButton(
    leftPos + 152, topPos + 6,
    menu.getBlockEntity(),
    this
));
```

The button opens `SideConfigScreen` on click. Players select a face, then individually toggle slot modes in `SideConfigSlotSelectionScreen`. Changes are sent to the server via `PacketSideConfigToggle`.

## NBT Storage Format

Side configuration is saved automatically by `AbstractCapabilityHandler.saveNBT()`. The compound tag structure is:

```
{
  "side_config": {
    "NORTH": [packed_int, packed_int, ...],   // one int per slot
    "SOUTH": [...],
    "EAST":  [...],
    "WEST":  [...],
    "UP":    [...],
    "DOWN":  [...]
  }
}
```

Each `int` is a `SlotModePair`: `(slotIndex << 4) | modeOrdinal`.
