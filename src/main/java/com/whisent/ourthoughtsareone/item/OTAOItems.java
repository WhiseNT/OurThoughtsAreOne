package com.whisent.ourthoughtsareone.item;

import com.whisent.ourthoughtsareone.OurThoughtsAreOne;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OTAOItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, OurThoughtsAreOne.MODID);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }


}
