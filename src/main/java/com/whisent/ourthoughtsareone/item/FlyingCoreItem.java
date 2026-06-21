package com.whisent.ourthoughtsareone.item;

import com.whisent.ourthoughtsareone.core.FlightManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class FlyingCoreItem extends Item {
    public FlyingCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        if (FlightManager.acceptPendingInvite(serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        ServerPlayer target = FlightManager.findLookTarget(serverPlayer, 24.0);
        if (target == null) {
            serverPlayer.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.invite_failed"));
            return InteractionResult.FAIL;
        }

        boolean success = FlightManager.sendInvite(serverPlayer, target);
        if (!success) {
            serverPlayer.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.invite_failed"));
            return InteractionResult.FAIL;
        }

        serverPlayer.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.invite_sent", target.getName().getString()));
        target.sendSystemMessage(Component.translatable("hint.ourthoughtsareone.flight.invite_received", serverPlayer.getName().getString()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (FlightManager.acceptPendingInvite(serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!(target instanceof ServerPlayer targetPlayer)) return InteractionResult.PASS;

        boolean success = FlightManager.sendInvite(serverPlayer, targetPlayer);
        if (!success) {
            serverPlayer.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.invite_failed"));
            return InteractionResult.FAIL;
        }

        serverPlayer.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.invite_sent", targetPlayer.getName().getString()));
        targetPlayer.sendSystemMessage(Component.translatable("hint.ourthoughtsareone.flight.invite_received", serverPlayer.getName().getString()));
        return InteractionResult.SUCCESS;
    }
}
