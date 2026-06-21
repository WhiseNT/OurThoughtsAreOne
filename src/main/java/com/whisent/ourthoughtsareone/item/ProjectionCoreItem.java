package com.whisent.ourthoughtsareone.item;

import com.whisent.ourthoughtsareone.core.ProjectionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ProjectionCoreItem extends Item {
    public ProjectionCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof ServerPlayer targetPlayer)) return InteractionResult.PASS;
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        boolean success = ProjectionManager.startProjection(serverPlayer, targetPlayer);
        if (!success) {
            serverPlayer.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.projection.failed"));
            return InteractionResult.FAIL;
        }

        serverPlayer.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.projection.mount", targetPlayer.getName().getString()));
        targetPlayer.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.projection.mounted", serverPlayer.getName().getString()));
        return InteractionResult.SUCCESS;
    }
}
