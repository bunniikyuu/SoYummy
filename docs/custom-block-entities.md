# Custom Block Entities

When the universal classes (`UniversalProcessorBlock`, `UniversalProcessorBE`, `UniversalProcessorContainer`, `UniversalProcessorScreen`) are not enough, you can supply your own implementations for any layer and let the builder wire everything together.

## Constructor Signatures

Your custom classes must follow these signatures so `ModEntryBuilder` can instantiate them:

| Layer | Required constructor / factory |
|---|---|
| **Block** | `MyBlock(String name)` |
| **BlockEntity** | `MyBE(BlockPos pos, BlockState state, String name)` (as `TriFunction`) |
| **Container** | `MyContainer(int containerId, Inventory inv, RegistryFriendlyByteBuf data)` (as `IContainerFactory`) |
| **Screen** | `MyScreen(MyContainer menu, Inventory inv, Component title)` (registered via client event, see below) |

---

## Step-by-Step: Adding a Custom Machine

### 1. Register the entry in `ModEntries.java`

You can override any layer. Start from `addProcessor` and replace only what you need:

```java
// setup/ModEntries.java
public static final ModEntry ADVANCED_MACHINE = addProcessor("advanced_machine")
        .block(AdvancedMachineBlock::new)
        .blockEntity(AdvancedMachineBE::new)
        .menu(AdvancedMachineContainer::new)
        .withEnergyInput(500_000)
        .fluidCap(2, 1, 0)
        .itemCap(3, 2)
        .build();
```

Or start from scratch with `add()`:

```java
public static final ModEntry ADVANCED_MACHINE = add("advanced_machine")
        .block(AdvancedMachineBlock::new)
        .blockEntity(AdvancedMachineBE::new)
        .menu(AdvancedMachineContainer::new)
        .withEnergyInput(500_000)
        .fluidCap(2, 1, 0)
        .itemCap(3, 2)
        .withRecipes()
        .build();
```

### 2. Create the custom Block class

Extend `UniversalProcessorBlock` (or `BaseEntityBlock` directly) and override what you need:

```java
// block/AdvancedMachineBlock.java
public class AdvancedMachineBlock extends UniversalProcessorBlock {

    public AdvancedMachineBlock(String name) {
        super(name);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level,
                                            BlockPos pos, Player player, BlockHitResult hit) {
        // Custom right-click logic, or call super for default "open GUI" behaviour
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean movedByPiston) {
        // Custom on-break logic (e.g. drop custom inventory)
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
```

### 3. Create the custom BlockEntity class

Extend `GlobalBlockEntity` (which already handles energy, items, fluids, side config, NBT, and ticking):

```java
// block_entity/AdvancedMachineBE.java
public class AdvancedMachineBE extends GlobalBlockEntity implements MenuProvider {

    // Add extra fields - annotate them so they sync to client via ContainerData
    @NBTField public int temperature = 0;

    public AdvancedMachineBE(BlockPos pos, BlockState state, String name) {
        super(ModEntries.get(name).blockEntity().get(), pos, state, name);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.modtemplate.advanced_machine");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new AdvancedMachineContainer(id, inv, this, containerData);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        super.tick(level, pos, state);   // runs recipe processing, push/pull, etc.
        // Add custom per-tick logic here
        if (!level.isClientSide) {
            temperature = Math.min(temperature + 1, 1000);
        }
    }
}
```

**`GlobalBlockEntity` provides out of the box:**
- `energyStorage` - `CustomEnergyStorage` (if `.withEnergyInput(...)` was called)
- `contentHandler` - `SidedContentHandler` (item + fluid + side config)
- `recipeInfo` - `RecipeInfo` (finds and processes recipes each tick)
- `containerData` - `ContainerData` syncing all `int`/`boolean` fields annotated with `@NBTField`
- NBT save/load for all `@NBTField`-annotated fields automatically

### 4. Create the custom Container (menu)

Extend `AbstractContainerMenu`. The slot layout is driven by `SlotsLayout` which is computed from the `ModEntry` capability definitions:

```java
// container/AdvancedMachineContainer.java
public class AdvancedMachineContainer extends AbstractContainerMenu {

    private final AdvancedMachineBE blockEntity;

    public AdvancedMachineContainer(int containerId, Inventory playerInventory,
                                    RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory,
                (AdvancedMachineBE) playerInventory.player.level()
                        .getBlockEntity(extraData.readBlockPos()),
                null);
    }

    public AdvancedMachineContainer(int containerId, Inventory playerInventory,
                                    AdvancedMachineBE blockEntity, ContainerData data) {
        super(ModEntries.get(blockEntity.name).menu().get(), containerId);
        this.blockEntity = blockEntity;

        ContainerData syncData = data != null ? data
                : new SimpleContainerData(blockEntity.getSyncFieldCount());
        addDataSlots(syncData);

        // Add item slots from the block entity's item handler
        IItemHandler itemHandler = blockEntity.contentHandler.itemHandler;
        ModEntry entry = ModEntries.get(blockEntity.name);
        SlotsLayout layout = entry.slotsLayout();
        if (itemHandler != null && layout != null) {
            for (SlotDef slot : layout.machineSlots()) {
                addSlot(new SlotItemHandler(itemHandler, slot.index(), slot.x(), slot.y()));
            }
        }

        // Add player inventory + hotbar
        layoutPlayerInventorySlots(playerInventory, 8, 84);
    }

    private void layoutPlayerInventorySlots(Inventory playerInventory, int leftCol, int topRow) {
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, leftCol + col * 18, topRow + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, leftCol + col * 18, topRow + 58));
    }

    @Override
    public boolean stillValid(Player player) {
        return ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos())
                .evaluate((level, pos) -> player.distanceToSqr(pos.getX() + 0.5,
                        pos.getY() + 0.5, pos.getZ() + 0.5) < 64.0, true);
    }

    public AdvancedMachineBE getBlockEntity() { return blockEntity; }
}
```

### 5. Create the custom Screen class

Extend `AbstractContainerScreen`. The screen is registered on the client via `setup/Client.java` automatically for all entries that have a menu - but only if you use `UniversalProcessorScreen`. For a **custom screen**, register it manually in `setup/Client.java`:

```java
// screen/AdvancedMachineScreen.java
public class AdvancedMachineScreen extends AbstractContainerScreen<AdvancedMachineContainer> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MODID, "textures/gui/advanced_machine.png");

    public AdvancedMachineScreen(AdvancedMachineContainer menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        // render progress bar, energy bar, fluid tanks, etc.
        addRenderableWidget(new EnergyBar(leftPos + 8, topPos + 10,
                () -> menu.getBlockEntity().energyStorage));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
```

**Register the custom screen** by adding it to `setup/Client.java` inside the `onRegisterMenuScreens` event handler:

```java
// setup/Client.java  (inside @SubscribeEvent onRegisterMenuScreens)
event.register(
    ModEntries.ADVANCED_MACHINE.menu().get(),
    AdvancedMachineScreen::new
);
```

The auto-registration loop in `Client.java` already handles `UniversalProcessorScreen` for all entries that do NOT have a manually registered screen - add your registration **before** the loop, or add a condition to skip that entry in the loop.

### 6. Place the GUI texture

```
src/main/resources/assets/modtemplate/textures/gui/
  advanced_machine.png      - 176x166 px (standard GUI background size)
```

### 7. Place block textures

Same as a standard processor - see [Processors Registration](./processors-registration.md#2-place-block-textures).

```
src/main/resources/assets/modtemplate/textures/block/advanced_machine/
  front.png
  side.png
```

### 8. Run datagen

```bash
./gradlew runData
```

Block state, item models, loot table, and language entries are all generated automatically since the block is registered via `ModEntryBuilder`.

### 9. Add custom recipe classes (optional)

If you need extra recipe fields (e.g. required temperature, catalyst), supply a custom recipe type and serializer:

```java
.withRecipes(
    () -> RecipeType.simple(rl("advanced_machine")),
    () -> new AdvancedMachineRecipeSerializer()
)
```

See [Processors Registration - Recipe JSON Format](./processors-registration.md#recipe-json-format) and the full recipe class examples in the main README for details.

---

## Using GUI Widgets

The template ships several ready-made GUI widgets in `screen/element/`:

| Widget | Usage |
|---|---|
| `EnergyBar` | Vertical FE fill bar with hover tooltip |
| `ProgressBar` | Animated arrow or custom progress indicator |
| `SideConfigButton` | Opens the side configuration screen |
| `SlotWidget` | Renders a slot background at a given position |

Example - add an energy bar and side config button to any custom screen:

```java
@Override
protected void init() {
    super.init();
    addRenderableWidget(new EnergyBar(leftPos + 8, topPos + 10,
            () -> menu.getBlockEntity().energyStorage));
    addRenderableWidget(new SideConfigButton(leftPos + 152, topPos + 6,
            menu.getBlockEntity(), this));
}
```
