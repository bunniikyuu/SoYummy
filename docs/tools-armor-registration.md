# Tools & Armor Registration

Register a full tool set (sword, pickaxe, axe, shovel, hoe) or armor set (helmet, chestplate, leggings, boots) via `ModEntryBuilder`. Items are auto-registered, placed in the appropriate creative tab, and exposed on the produced `ModEntry` via `toolSetEntry()` / `armorSetEntry()`.

## Tool Sets

### Vanilla tool classes

```java
// setup/ModEntries.java
public static final ModEntry SILVER_TOOLS =
        addToolSet("silver", Tiers.NETHERITE).build();
```

Registers: `silver_sword`, `silver_pickaxe`, `silver_axe`, `silver_shovel`, `silver_hoe` using the vanilla `SwordItem`, `PickaxeItem`, `AxeItem`, `ShovelItem`, `HoeItem` classes.

### Custom tool classes (per-slot factories)

Pass a `BiFunction<Tier, Item.Properties, ? extends Item>` for any slot. Slots without a factory fall back to the vanilla class.

```java
public static final ModEntry SILVER_TOOLS = addToolSet("silver", Tiers.NETHERITE)
        .sword((tier, props)   -> new MyGlowSword(tier, props))
        .pickaxe(MyDrillPickaxe::new)
        .axe(MyChainAxe::new)
        // shovel + hoe stay vanilla
        .build();
```

### Same class for every tool

```java
addToolSet("silver", Tiers.NETHERITE)
        .toolFactory(MyMagicTool::new) // applied to all 5 slots
        .build();
```

### Accessing the registered items

```java
ToolSetEntry tools = SILVER_TOOLS.toolSetEntry();
DeferredItem<Item> sword = tools.sword();
// .pickaxe() / .axe() / .shovel() / .hoe()
```

## Armor Sets

### Define an `ArmorMaterialEntry` first

```java
public static final ArmorMaterialEntry SILVER_MATERIAL = ArmorMaterialEntry.builder("silver")
        .defense(3, 6, 7, 3)              // boots, leggings, chestplate, helmet
        .enchantmentValue(9)
        .equipSound(SoundEvents.ARMOR_EQUIP_IRON)
        .repairItem(() -> Ingredient.of(SILVER.materialEntry().ingot().get()))
        .toughness(0.2F)
        .knockbackResistance(0.2F)
        .durabilityMultiplier(17)
        .build();
```

### Vanilla `ArmorItem`

```java
public static final ModEntry SILVER_ARMOR =
        addArmorSet("silver", SILVER_MATERIAL).build();
```

Registers: `silver_helmet`, `silver_chestplate`, `silver_leggings`, `silver_boots`.

Alternative entry points:

```java
addArmorSet("silver", holder);                          // raw Holder<ArmorMaterial>
addArmorSet("silver", holder, durabilityMultiplier);    // override durability multiplier
```

### Custom armor classes (per-slot factories)

Pass a `TriFunction<Holder<ArmorMaterial>, ArmorItem.Type, Item.Properties, ? extends Item>` for any slot. Unset slots fall back to vanilla `ArmorItem`.

```java
public static final ModEntry SILVER_ARMOR = addArmorSet("silver", SILVER_MATERIAL)
        .helmet(MyNightVisionHelmet::new)
        .chestplate((mat, type, props) -> new MyJetpackChest(mat, type, props))
        // leggings + boots stay vanilla
        .build();
```

`TriFunction` is `org.apache.commons.lang3.function.TriFunction`.

### Same class for every piece

```java
addArmorSet("silver", SILVER_MATERIAL)
        .armorFactory(MyPoweredArmor::new) // applied to all 4 pieces
        .build();
```

### Accessing the registered items

```java
ArmorSetEntry armor = SILVER_ARMOR.armorSetEntry();
DeferredItem<Item> helmet = armor.helmet();
// .chestplate() / .leggings() / .boots()
```

## Item Class Requirements

Your custom classes must have a constructor matching the corresponding factory shape:

| Set | Required constructor |
|---|---|
| Tools | `(Tier tier, Item.Properties props)` |
| Armor | `(Holder<ArmorMaterial> material, ArmorItem.Type type, Item.Properties props)` |

If your class wants extra state, capture it in a lambda:

```java
.sword((tier, props) -> new MyConfigurableSword(tier, props, myExtraConfig))
```

## Datagen

Tool and armor entries are picked up automatically by:

- `ModItemModelProvider` - generates `handheld`/`generated` item models
- `ModItemTagProvider` - tags swords/pickaxes/axes/shovels/hoes into the matching vanilla item tags
- `ModLanguageProvider` - generates English display names from the entry name

No manual datagen wiring needed.


All you need to place textures at:
- `resources/assets/modtemplate/textures/item/armor`
- `resources/assets/modtemplate/textures/item/tools`
- `resources/assets/modtemplate/textures/models/armor`