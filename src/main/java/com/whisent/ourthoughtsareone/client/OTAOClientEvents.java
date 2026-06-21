package com.whisent.ourthoughtsareone.client;

import com.whisent.ourthoughtsareone.OurThoughtsAreOne;
import com.whisent.ourthoughtsareone.entity.OTAOEntities;
import com.whisent.ourthoughtsareone.item.OTAOItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = OurThoughtsAreOne.MODID, value = Dist.CLIENT)
public class OTAOClientEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(OTAOEntities.PROJECTION_ANCHOR.get(), InvisibleEntityRenderer::new);
        event.registerEntityRenderer(OTAOEntities.FLIGHT_LINK.get(), FlightLinkRenderer::new);
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        ItemStack stack = event.getItemStack();
        if (!stack.isEmpty() && !stack.is(OTAOItems.FLIGHT_CORE.get())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand("otao_accept_flight");
        }
    }

    private static class InvisibleEntityRenderer<T extends Entity> extends EntityRenderer<T, EntityRenderState> {
        protected InvisibleEntityRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public boolean shouldRender(T entity, net.minecraft.client.renderer.culling.Frustum culler, double camX, double camY, double camZ) {
            return false;
        }

        @Override
        public EntityRenderState createRenderState() {
            return new EntityRenderState();
        }
    }
}
