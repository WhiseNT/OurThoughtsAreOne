package com.whisent.ourthoughtsareone.item;

import com.whisent.ourthoughtsareone.OurThoughtsAreOne;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OTAOItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OurThoughtsAreOne.MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OurThoughtsAreOne.MODID);

    public static final DeferredItem<ProjectionCoreItem> PROJECTION_CORE = ITEMS.registerItem("projection_core", ProjectionCoreItem::new);
    public static final DeferredItem<FlyingCoreItem> FLIGHT_CORE = ITEMS.registerItem("flight_core", FlyingCoreItem::new);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> OTAO_TAB = CREATIVE_MODE_TABS.register("ourthoughtsareone", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.ourthoughtsareone"))
            .icon(() -> new ItemStack(PROJECTION_CORE.get()))
            .displayItems((parameters, output) -> {
                output.accept(PROJECTION_CORE.get());
                output.accept(FLIGHT_CORE.get());
            })
            .build());

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
