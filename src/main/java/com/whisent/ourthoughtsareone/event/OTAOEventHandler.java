package com.whisent.ourthoughtsareone.event;

import com.whisent.ourthoughtsareone.core.FlightManager;
import com.whisent.ourthoughtsareone.core.ProjectionManager;

import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

import java.util.HashMap;
import java.util.Map;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;


public class OTAOEventHandler {
    private static long lastCooldownTick = -1;
    private static final Map<ResourceKey<Level>, Long> distanceCheckTicks = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("otao_accept_flight")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        return FlightManager.acceptPendingInvite(player) ? 1 : 0;
                    }
                    return 0;
                }));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = player.level();
        long gameTime = level.getGameTime();
        if (lastCooldownTick != gameTime) {
            ProjectionManager.tickCooldowns();
            lastCooldownTick = gameTime;
        }
        if (distanceCheckTicks.getOrDefault(level.dimension(), -1L) != gameTime) {
            ProjectionManager.checkDistance(level);
            distanceCheckTicks.put(level.dimension(), gameTime);
        }

        FlightManager.tick(player);

        if (ProjectionManager.isProjecting(player)) {
            if (player.isShiftKeyDown()) {
                ProjectionManager.endProjection(player, ProjectionManager.DisconnectReason.SHIFT);
                return;
            }
        }

        if (player.isShiftKeyDown()) {
            FlightManager.onSneak(player);
        }

    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        Entity target = event.getTarget();

        if (target instanceof ServerPlayer targetPlayer && ProjectionManager.isProjectionPair(player, targetPlayer)) {
            event.setCanceled(true);
            return;
        }

        if (!ProjectionManager.canAttack(player) || !FlightManager.canAttack(player)) {
            event.setCanceled(true);
            return;
        }

        if (!(target instanceof LivingEntity livingEntity)) return;
        ProjectionManager.triggerBonusAttack(player, livingEntity);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getTarget() instanceof ServerPlayer target)) return;
        if (player.level().isClientSide()) return;

        if (FlightManager.canAcceptInviteWith(player, event.getHand()) && FlightManager.acceptPendingInvite(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (FlightManager.canAcceptInviteWith(player, event.getHand()) && FlightManager.hasInviteFrom(player, target) && FlightManager.declineInvite(player, target)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (!FlightManager.canInteract(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (FlightManager.canAcceptInviteWith(player, event.getHand()) && FlightManager.acceptPendingInvite(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (FlightManager.canInteract(player)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (FlightManager.canAcceptInviteWith(player, event.getHand()) && FlightManager.acceptPendingInvite(player)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (FlightManager.canInteract(player)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (FlightManager.canInteract(player)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof ServerPlayer attacker) {
            if (ProjectionManager.isProjectionPair(attacker, victim)) {
                event.setNewDamage(0.0F);
                return;
            }
        }
        float newDamage = ProjectionManager.handleDamageShare(event.getEntity(), event.getNewDamage());
        event.setNewDamage(newDamage);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ProjectionManager.endProjection(player, ProjectionManager.DisconnectReason.DEATH);
        FlightManager.onLogout(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ProjectionManager.endProjection(player, ProjectionManager.DisconnectReason.OTHER);
        FlightManager.onLogout(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ProjectionManager.onPlayerLogout(player);
        FlightManager.onLogout(player);
    }

    @SubscribeEvent
    public static void onEntityMount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityBeingMounted() instanceof ServerPlayer mount && event.getEntityMounting() instanceof ServerPlayer rider) {
            if (ProjectionManager.isProjecting(rider) || ProjectionManager.isBeingMounted(mount)) {
                event.setCanceled(false);
            }
        }
    }
}
