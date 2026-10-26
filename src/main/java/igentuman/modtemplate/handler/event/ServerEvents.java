package igentuman.modtemplate.handler.event;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import static igentuman.modtemplate.Main.TICK_COUNTER;

public class ServerEvents {
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        TICK_COUNTER++;
    }
}
