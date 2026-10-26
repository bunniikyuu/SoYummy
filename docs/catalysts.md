# Processor Catalysts

A catalyst is a behavior object that modifies a processor each server tick. A processor declares which catalyst **types** it supports; every registered catalyst item of a supported type placed in that type's catalyst slot auto-attaches and runs `preTick`/`postTick` hooks. Strength (`power`) scales with the catalyst item's stack count.

## Model

```java
enum CatalystType { ENERGY, SPEED, EFFICIENCY }

abstract class Catalyst {
    final CatalystType type;
    final GlobalBlockEntity host;   // the processor BE
    int power;                      // = catalyst item stack count
    Item item;                      // backing item (host detects slot changes)
    void preTick()  {}              // runs before the processor's operation logic
    void postTick() {}              // runs after the processor's operation logic
}
```

Catalysts are **stateless**: `power` and `item` are re-derived from the catalyst slot every tick, so nothing is persisted to NBT.

## Registering a catalyst

`addCatalyst` creates the catalyst **item** and records a `CatalystDef` in `CatalystRegistry`. It returns a normal `ModEntry`, so the catalyst item participates in [config gating](./entry-config-gating.md) like any item.

```java
// setup/ModEntries.java
public static final ModEntry SPEED_UPGRADE =
        addCatalyst("speed_upgrade", CatalystType.SPEED, SpeedCatalyst::new);

public static final ModEntry ENERGY_UPGRADE =
        addCatalyst("energy_upgrade", CatalystType.ENERGY, EnergyCatalyst::new);
```

The factory is `(GlobalBlockEntity host) -> Catalyst`. Multiple catalyst items may share a type; the one actually in the slot is what attaches.

## Declaring support on a processor

Call `.catalysts(types...)`. One catalyst slot is allocated **per type**, in `CatalystType` ordinal order (`ENERGY`, `SPEED`, `EFFICIENCY`):

```java
public static final ModEntry EXAMPLE_MACHINE = addProcessor("example_machine")
        .itemCap(1, 1)
        .catalysts(CatalystType.ENERGY, CatalystType.SPEED)  // 2 catalyst slots
        .build();
```

`addProcessor` defaults to no catalysts. The catalyst slot count is added to the item handler during `build()`, regardless of whether `.itemCap(...)` is called before or after `.catalysts(...)`.

### Slot indices

Catalyst slots are part of the "extra" slot block, ordered after inputs and outputs:

```
catalyst slot for type[i] = inputSlots + outputSlots + globalSlots + i
```

where `i` is the type's position in ordinal order among the supported set.

## Tick flow

`UniversalProcessorBE.serverTick()` overrides the base tick:

```
refreshCatalysts()                 // rebuild active catalysts from slots, set power = stack count
recipeInfo.resetCatalystModifiers()// restore multiplier=1 and energyPerTick to recipe baseline
for c in catalysts: c.preTick()    // catalysts apply their effects
super.serverTick()                 // contentHandler.tick() + recipeInfo.tick() (operation logic)
for c in catalysts: c.postTick()
```

`resetCatalystModifiers()` runs every tick so catalyst effects re-apply fresh and never compound (e.g. a removed speed catalyst resets the multiplier back to 1 next tick).

## Reference catalysts

Three concrete catalysts ship as references - replace or extend them as needed:

| Class | Type | Hook | Effect |
|---|---|---|---|
| `SpeedCatalyst` | SPEED | `preTick` | `recipeInfo.multiplier += power` - more progress per tick |
| `EnergyCatalyst` | ENERGY | `preTick` | lowers effective `energyPerTick` by `power * 5` (floored at 0) |
| `EfficiencyCatalyst` | EFFICIENCY | `postTick` | on a tick that just produced output, power-scaled chance to yield one bonus item into an output stack |

`EfficiencyCatalyst` keys off `RecipeInfo.justProduced`, set true only on the tick an operation's outputs were placed.

## Writing your own

```java
public class CoolingCatalyst extends Catalyst {
    public CoolingCatalyst(GlobalBlockEntity host) {
        super(CatalystType.EFFICIENCY, host);
    }

    @Override
    public void preTick() {
        // host.recipeInfo, host.energyStorage, host.contentHandler are all accessible
    }
}
```

Register it with `addCatalyst("cooling_core", CatalystType.EFFICIENCY, CoolingCatalyst::new)` and add the type to a processor's `.catalysts(...)`.
