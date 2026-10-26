package igentuman.modtemplate.compat.cc;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import igentuman.modtemplate.block_entity.MultiblockControllerBE;
import igentuman.modtemplate.block_entity.UniversalProcessorBE;
import igentuman.modtemplate.registration.ModEntry;
import igentuman.modtemplate.setup.ModEntries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class CCCompatHandler {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CCCompatHandler::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (ModEntry entry : ModEntries.ENTRIES.values()) {
            if (!entry.hasBlockEntity()) continue;
            event.registerBlockEntity(
                    PeripheralCapability.get(),
                    entry.blockEntity().get(),
                    (be, side) -> {
                        if (be instanceof MultiblockControllerBE controller) return new ControllerPerihperal(controller);
                        if (be instanceof UniversalProcessorBE processor) return new ProcessorPeripheral(processor);
                        return (IPeripheral) null;
                    }
            );
        }
    }
}
