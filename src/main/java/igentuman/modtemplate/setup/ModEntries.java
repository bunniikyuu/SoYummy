package igentuman.modtemplate.setup;

import igentuman.modtemplate.api.impl.DeterminedMultiblockValidator;
import igentuman.modtemplate.api.multiblock.BlockPredicate;
import igentuman.modtemplate.multiblock.MultiblockEntry;
import igentuman.modtemplate.multiblock.MultiblockEntryBuilder;
import igentuman.modtemplate.registration.ArmorMaterialEntry;
import igentuman.modtemplate.registration.ModEntry;
import igentuman.modtemplate.util.SlotsLayout;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.awt.*;
import java.util.HashMap;

import static igentuman.modtemplate.registration.ModEntryBuilder.*;

public class ModEntries {
    public static final HashMap<String, ModEntry> ENTRIES = new HashMap<>();
    public static BlockBehaviour.Properties COMMON_BLOCK_PROPS = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops();



    public static final ModEntry EXAMPLE_ITEM = addItem("example_item").build();

    public static final ModEntry EXAMPLE_MACHINE = addProcessor("example_machine")
            .withLayout(SlotsLayout.THREE_TO_TWO)
            .fluidCap(2,2, 0)
            .itemCap(1,0)
            .build();

    public static final ModEntry SILVER = addMetalOreMaterial("silver", Color.LIGHT_GRAY.getRGB()).build();

    public static final ArmorMaterialEntry SILVER_MATERIAL = ArmorMaterialEntry.builder("silver")
            .defense(3, 6, 7, 3)
            .enchantmentValue(9)
            .equipSound(SoundEvents.ARMOR_EQUIP_IRON)
            .repairItem(() -> Ingredient.of(SILVER.materialEntry().ingot().get()))
            .toughness(0.2F)
            .knockbackResistance(0.2F)
            .durabilityMultiplier(17)
            .build();

    public static final ModEntry SILVER_TOOLS = addToolSet("silver", Tiers.NETHERITE).build();

    public static final ModEntry SILVER_ARMOR = addArmorSet("silver", SILVER_MATERIAL).build();

    public static final ModEntry FOO_CONTROLLER = addMultiblockController("foo_controller")
            .withLayout(SlotsLayout.ONE_TO_ONE)
            .itemCap(1, 1)
            .withEnergyOutput(1_000_000_000)
            .build();

    public static final ModEntry FOO_PORT = addMultiblockPart("foo_port");

    public static final MultiblockEntry FOO_MULTIBLOCK = MultiblockEntryBuilder.name("foo_multiblock")
            .controller(FOO_CONTROLLER).ports(FOO_PORT)
            .validator(() -> new DeterminedMultiblockValidator(
                    "foo_multiblock",
                    BlockPredicate.of(FOO_CONTROLLER.block().get())))
            .build();

    public static void init() {}

    public static ModEntry get(String name) {
        return ENTRIES.getOrDefault(name, null);
    }

    public static boolean isEnabled(String name) {
        ModEntry entry = get(name);
        return entry == null || entry.isEnabled();
    }
}
