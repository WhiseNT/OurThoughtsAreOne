package com.whisent.ourthoughtsareone;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;

import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(OurThoughtsAreOne.MODID)
public class OurThoughtsAreOne {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "ourthoughtsareone";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public OurThoughtsAreOne(IEventBus modEventBus) {

//        NeoForge.EVENT_BUS.register(this);
    }

}
