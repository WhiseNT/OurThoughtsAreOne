package com.whisent.ourthoughtsareone.entity;

import com.whisent.ourthoughtsareone.OurThoughtsAreOne;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OTAOEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(OurThoughtsAreOne.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ProjectionAnchorEntity>> PROJECTION_ANCHOR = ENTITIES.registerEntityType("projection_anchor",
            ProjectionAnchorEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(0.0F, 0.0F).clientTrackingRange(64).updateInterval(1));

    public static final DeferredHolder<EntityType<?>, EntityType<FlightLinkEntity>> FLIGHT_LINK = ENTITIES.registerEntityType("flight_link",
            FlightLinkEntity::new,
            MobCategory.MISC,
            builder -> builder.sized(0.0F, 0.0F).clientTrackingRange(96).updateInterval(1));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
