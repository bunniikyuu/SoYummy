# ComputerCraft Support

Every machine that extends `UniversalProcessorBE` is automatically exposed as a CC peripheral. No extra registration needed - `CCCompatHandler` iterates all `ModEntries.ENTRIES` at startup and wires the `PeripheralCapability` for each block entity type.

## Peripheral Type

The peripheral type string equals the machine's internal name (set via `ModEntryBuilder`). Use it to find the peripheral:

```lua
local machine = peripheral.find("your_machine_name")
-- or by position
local machine = peripheral.wrap("top")
```

## Lua Functions

All slots and tank indices are **1-indexed**.

---

### `getName()` → string

Returns peripheral type / machine name.

```lua
print(machine.getName())  -- e.g. "crusher"
```

---

### `getProgress()` → number

Current recipe progress in ticks.

---

### `getMaxProgress()` → number

Total ticks required to finish current recipe. Returns `0` when idle.

---

### `isProcessing()` → boolean

`true` if a recipe is active, `false` when idle or missing ingredients.

---

### `getEnergyPerTick()` → number

FE consumed per tick by the active recipe. Returns `0` when idle.

---

### `getEnergy()` → table | nil

Returns `{stored, capacity}`. Returns `nil` if machine has no energy storage.

```lua
local e = machine.getEnergy()
if e then
  print(e.stored .. " / " .. e.capacity .. " FE")
end
```

---

### `list()` → table

Returns all non-empty item slots and non-empty fluid tanks in one call.

```lua
-- Return shape:
-- {
--   items  = { [slot] = { name, count } },
--   fluids = { [tank] = { name, amount, capacity } }
-- }
local contents = machine.list()
for slot, item in pairs(contents.items) do
  print("Slot " .. slot .. ": " .. item.name .. " x" .. item.count)
end
for tank, fluid in pairs(contents.fluids) do
  print("Tank " .. tank .. ": " .. fluid.name .. " " .. fluid.amount .. "mB")
end
```

---

### `getItem(slot)` → table | nil

Returns `{name, count, maxCount}` for a specific item slot. Returns `nil` if slot is empty or out of range.

```lua
local item = machine.getItem(1)
if item then
  print(item.name, item.count, item.maxCount)
end
```

---

### `tanks()` → table

Returns all fluid tanks including empty ones. Shape: `{[index] = {name, amount, capacity}}`. Empty tanks have `name = ""` and `amount = 0`.

```lua
local tanks = machine.tanks()
for i, t in pairs(tanks) do
  print("Tank " .. i .. ": " .. t.name .. " " .. t.amount .. "/" .. t.capacity .. "mB")
end
```

---

### `getTank(index)` → table | nil

Returns `{name, amount, capacity}` for a specific tank. Returns `nil` if index out of range.

```lua
local t = machine.getTank(1)
if t and t.amount > 0 then
  print(t.name .. ": " .. t.amount .. "mB")
end
```

---

### `voidSlot(slot)` → boolean

Destroys items in the given slot. Returns `true` on success, `false` if no inventory or slot out of range.

```lua
machine.voidSlot(2)  -- clear output slot 2
```

---

### `voidTank(index)` → boolean

Drains the given fluid tank. Returns `true` on success.

```lua
machine.voidTank(1)  -- drain tank 1
```

---

## Example Scripts

### Monitor Progress Bar

Displays processing progress on a connected monitor. Run on a computer placed next to the machine and a monitor.

```lua
local machine = peripheral.find("crusher")
local mon = peripheral.find("monitor")

if not machine then error("No machine found") end
if not mon then error("No monitor found") end

mon.setTextScale(0.5)

while true do
  mon.clear()
  mon.setCursorPos(1, 1)

  local progress = machine.getProgress()
  local maxProg  = machine.getMaxProgress()
  local energy   = machine.getEnergy()
  local active   = machine.isProcessing()

  mon.write("Machine: " .. machine.getName())
  mon.setCursorPos(1, 2)
  mon.write("Status:  " .. (active and "RUNNING" or "IDLE"))

  if active and maxProg > 0 then
    local pct = math.floor(progress / maxProg * 100)
    mon.setCursorPos(1, 3)
    mon.write("Progress: " .. pct .. "% (" .. machine.getEnergyPerTick() .. " FE/t)")
    -- draw bar
    local barLen = 20
    local filled = math.floor(pct / 100 * barLen)
    mon.setCursorPos(1, 4)
    mon.write("[" .. string.rep("=", filled) .. string.rep("-", barLen - filled) .. "]")
  end

  if energy then
    mon.setCursorPos(1, 5)
    local pct = math.floor(energy.stored / energy.capacity * 100)
    mon.write("Energy:  " .. energy.stored .. "/" .. energy.capacity .. " FE (" .. pct .. "%)")
  end

  sleep(1)
end
```

---

### Auto-Void Output Slots

Watches output slots and voids them when full, useful for machines producing unwanted byproducts during testing.

```lua
local machine = peripheral.find("crusher")
local OUTPUT_SLOTS = {3, 4}   -- adjust to your machine's output slot indices
local VOID_THRESHOLD = 64

while true do
  for _, slot in ipairs(OUTPUT_SLOTS) do
    local item = machine.getItem(slot)
    if item and item.count >= VOID_THRESHOLD then
      print("Voiding slot " .. slot .. ": " .. item.name)
      machine.voidSlot(slot)
    end
  end
  sleep(5)
end
```

---

### Energy Guard - Pause External Machinery

Sends a redstone signal when energy drops below 20%, signalling connected machines to stop feeding inputs.

```lua
local machine = peripheral.find("crusher")
local SIGNAL_SIDE = "right"
local LOW_THRESHOLD = 0.20   -- 20 %

while true do
  local e = machine.getEnergy()
  if e then
    local ratio = e.stored / e.capacity
    redstone.setOutput(SIGNAL_SIDE, ratio < LOW_THRESHOLD)
  end
  sleep(2)
end
```

---

### Log All Contents to File

Dumps the full inventory and tank state to a log file, handy for debugging recipe inputs.

```lua
local machine = peripheral.find("crusher")
local file = fs.open("machine_log.txt", "w")

local contents = machine.list()

file.writeLine("=== Items ===")
for slot, item in pairs(contents.items) do
  file.writeLine("  [" .. slot .. "] " .. item.name .. " x" .. item.count)
end

file.writeLine("=== Fluids ===")
for tank, fluid in pairs(contents.fluids) do
  file.writeLine("  [" .. tank .. "] " .. fluid.name .. " " .. fluid.amount .. "/" .. fluid.capacity .. "mB")
end

file.close()
print("Logged to machine_log.txt")
```

---

## Adding a Peripheral for a Custom Block Entity

`CCCompatHandler` auto-registers peripherals only for block entities that are instances of `UniversalProcessorBE`. If your block entity extends `GlobalBlockEntity` directly (or is a completely custom class), you must wire it up manually.

### Step 1 - Create a peripheral class

Implement `IPeripheral`. Expose methods with `@LuaFunction`.

```java
package com.example.mymod.compat.cc;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import com.example.mymod.block_entity.MyCustomBE;
import org.jetbrains.annotations.Nullable;

public class MyCustomPeripheral implements IPeripheral {

    private final MyCustomBE be;

    public MyCustomPeripheral(MyCustomBE be) {
        this.be = be;
    }

    @Override
    public String getType() {
        return "my_custom_machine";   // must match the peripheral.find() string in Lua
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof MyCustomPeripheral p && p.be == be;
    }

    @Override public void attach(IComputerAccess computer) {}
    @Override public void detach(IComputerAccess computer) {}

    @LuaFunction
    public final String getStatus() {
        return be.getStatus();        // example: expose custom BE state
    }

    @LuaFunction
    public final int getTemperature() {
        return be.temperature;
    }
}
```

### Step 2 - Register the capability

Call `event.registerBlockEntity` inside a `RegisterCapabilitiesEvent` listener. Wire it in your mod's main class or a dedicated compat handler.

```java
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class MyCCCompatHandler {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(MyCCCompatHandler::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                PeripheralCapability.get(),
                MyMod.MY_CUSTOM_BE.get(),          // your BlockEntityType DeferredHolder
                (be, side) -> new MyCustomPeripheral(be)
        );
    }
}
```

If the block entity type is registered via `ModEntry` (i.e. it appears in `ModEntries.ENTRIES`), pass `entry.blockEntity().get()` as the type argument - the same pattern used in `CCCompatHandler`.

### Step 3 - Call `register` at startup

In your mod's main class constructor (or `@Mod` annotated constructor):

```java
@Mod(MyMod.MOD_ID)
public class MyMod {
    public MyMod(IEventBus modEventBus) {
        // ... other setup ...
        if (ModList.get().isLoaded("computercraft")) {
            MyCCCompatHandler.register(modEventBus);
        }
    }
}
```

The `ModList` guard prevents a crash when CC: Tweaked is not installed.

### Step 4 - Use from Lua

```lua
local machine = peripheral.find("my_custom_machine")
print(machine.getStatus())
print(machine.getTemperature())
```

### Extending `ProcessorPeripheral`

If your block entity extends `UniversalProcessorBE` and you want the default inventory/fluid/energy API **plus** extra methods, subclass `ProcessorPeripheral`:

```java
public class MyCustomPeripheral extends ProcessorPeripheral {

    private final MyCustomBE be;

    public MyCustomPeripheral(MyCustomBE be) {
        super(be);
        this.be = be;
    }

    @Override
    public String getType() {
        return "my_custom_machine";
    }

    @LuaFunction
    public final int getTemperature() {
        return be.temperature;
    }
}
```

Then register it the same way as Step 2, casting `be` to `MyCustomBE` inside the lambda:

`java/igentuman/modtemplate/compat/cc/CCCompatHandler.java`
```java
event.registerBlockEntity(
        PeripheralCapability.get(),
        ModEntries.get("my_custom_machine").blockEntity().get(),
        (be, side) -> be instanceof MyCustomBE cbe ? new MyCustomPeripheral(cbe) : null
);
```
