package com.whisent.ourthoughtsareone;

import com.mojang.logging.LogUtils;
import com.whisent.ourthoughtsareone.config.OTAOConfig;
import com.whisent.ourthoughtsareone.entity.OTAOEntities;
import com.whisent.ourthoughtsareone.event.OTAOEventHandler;
import com.whisent.ourthoughtsareone.item.OTAOItems;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(OurThoughtsAreOne.MODID)
public class OurThoughtsAreOne {
    public static final String MODID = "ourthoughtsareone";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OurThoughtsAreOne(IEventBus modEventBus, ModContainer modContainer) {
        OTAOItems.register(modEventBus);
        OTAOEntities.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, OTAOConfig.SPEC);
        NeoForge.EVENT_BUS.register(OTAOEventHandler.class);
    }
}
