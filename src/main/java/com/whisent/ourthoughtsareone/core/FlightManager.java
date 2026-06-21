package com.whisent.ourthoughtsareone.core;

import com.whisent.ourthoughtsareone.config.OTAOConfig;
import com.whisent.ourthoughtsareone.entity.FlightLinkEntity;
import com.whisent.ourthoughtsareone.entity.OTAOEntities;
import com.whisent.ourthoughtsareone.item.OTAOItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FlightManager {
    private static final Set<UUID> flightOwners = new HashSet<>();
    private static final Set<UUID> grantedFlight = new HashSet<>();
    private static final Map<UUID, Float> originalFlyingSpeeds = new HashMap<>();
    private static final Map<UUID, UUID> inviteMap = new HashMap<>();
    private static final Map<UUID, Integer> inviteTicks = new HashMap<>();
    private static final Map<UUID, Map<UUID, Integer>> inviteCooldowns = new HashMap<>();
    private static final Map<UUID, Set<UUID>> ownerToLinks = new HashMap<>();
    private static final Map<UUID, UUID> targetToOwner = new HashMap<>();
    private static final Map<UUID, Map<UUID, UUID>> linkEntities = new HashMap<>();
    private static final Map<UUID, Integer> blockedTicks = new HashMap<>();
    private static long lastServerGameTime = -1;

    public static void tick(ServerPlayer player) {
        long gameTime = player.level().getGameTime();
        if (gameTime != lastServerGameTime) {
            tickInvites(gameTime);
            lastServerGameTime = gameTime;
        }

        if (hasFlightCore(player)) {
            enableFlight(player);
        } else {
            disableFlight(player, false);
        }

        if (isFlightOwner(player)) {
            tickOwner(player, gameTime);
        }

        if (targetToOwner.containsKey(player.getUUID())) {
            tickTarget(player, gameTime);
        }

        tickInvitePrompt(player, gameTime);
    }

    private static void tickOwner(ServerPlayer player, long gameTime) {
        if (!hasFlightCore(player)) return;
        originalFlyingSpeeds.putIfAbsent(player.getUUID(), player.getAbilities().getFlyingSpeed());
        double dimMult = getDimensionMultiplier(player.level());
        player.getAbilities().setFlyingSpeed((float) (0.05F * OTAOConfig.FLIGHT_SPEED_MULTIPLIER.get() * dimMult));
        player.onUpdateAbilities();
    }

    private static void tickTarget(ServerPlayer target, long gameTime) {
        UUID ownerUUID = targetToOwner.get(target.getUUID());
        if (ownerUUID == null) return;

        ServerPlayer owner = target.level().getServer().getPlayerList().getPlayer(ownerUUID);
        if (owner == null || !owner.isAlive() || !hasFlightCore(owner)) {
            breakLink(target, true);
            return;
        }

        if (!owner.level().dimension().equals(target.level().dimension())) {
            breakLink(target, true);
            return;
        }

        if (isBlocked(owner, target)) {
            int newTick = blockedTicks.getOrDefault(target.getUUID(), 0) + 1;
            blockedTicks.put(target.getUUID(), newTick);
            if (gameTime % 20 == 0) target.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.blocked"));
            if (newTick >= 60) {
                breakLink(target, true);
                return;
            }
        } else {
            blockedTicks.remove(target.getUUID());
        }

        double distance = target.distanceTo(owner);
        if (distance > 32) {
            breakLink(target, true);
            return;
        }

        double chainLength = OTAOConfig.FLIGHT_CHAIN_MAX_LENGTH.get();
        Vec3 dir = owner.position().add(0, 1.0, 0).subtract(target.position().add(0, 0.6, 0));
        if (dir.lengthSqr() > 0.01) {
            if (distance > 5.0) {
                double force = Math.min(0.55, Math.max(0.12, (distance - 5.0) * 0.045));
                if (distance > chainLength) {
                    force = Math.min(0.85, force + (distance - chainLength) * 0.06);
                }
                Vec3 pull = dir.normalize().scale(force);
                target.setDeltaMovement(target.getDeltaMovement().scale(0.55).add(pull));
                target.hurtMarked = true;
            } else if (!target.onGround()) {
                target.setDeltaMovement(target.getDeltaMovement().scale(0.96));
            }
        }

        if (gameTime % 40 == 0) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 3 * 20, 0, false, false));
        }
    }

    private static void tickInvitePrompt(ServerPlayer player, long gameTime) {
        if (gameTime % 20 != 0) return;
        UUID ownerUUID = inviteMap.get(player.getUUID());
        if (ownerUUID == null) return;
        ServerPlayer owner = player.level().getServer().getPlayerList().getPlayer(ownerUUID);
        if (owner == null) return;
        player.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.invite_received", owner.getName().getString()));
    }

    private static boolean isBlocked(ServerPlayer owner, ServerPlayer target) {
        Vec3 from = owner.getEyePosition();
        Vec3 to = target.getEyePosition();
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner);
        return owner.level().clip(context).getType() == HitResult.Type.BLOCK;
    }

    private static void tickInvites(long gameTime) {
        Iterator<Map.Entry<UUID, Integer>> it = inviteTicks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int newTick = entry.getValue() - 1;
            if (newTick <= 0) {
                inviteMap.remove(entry.getKey());
                it.remove();
            } else {
                entry.setValue(newTick);
            }
        }

        if (gameTime % 20 == 0) tickInviteCooldowns();
    }

    private static void tickInviteCooldowns() {
        for (Map<UUID, Integer> map : inviteCooldowns.values()) {
            Iterator<Map.Entry<UUID, Integer>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                int newTick = entry.getValue() - 1;
                if (newTick <= 0) {
                    it.remove();
                } else {
                    entry.setValue(newTick);
                }
            }
        }
    }

    public static boolean sendInvite(ServerPlayer owner, ServerPlayer target) {
        if (owner == null || target == null) return false;
        if (owner.getUUID().equals(target.getUUID())) return false;
        if (!hasFlightCore(owner)) return false;
        if (targetToOwner.containsKey(target.getUUID())) return false;
        if (!owner.level().dimension().equals(target.level().dimension())) return false;
        if (owner.distanceTo(target) > 16) return false;
        if (ownerToLinks.getOrDefault(owner.getUUID(), Set.of()).size() >= OTAOConfig.FLIGHT_MAX_LINKS.get()) return false;
        if (inviteCooldowns.getOrDefault(owner.getUUID(), Map.of()).getOrDefault(target.getUUID(), 0) > 0) return false;

        inviteMap.put(target.getUUID(), owner.getUUID());
        inviteTicks.put(target.getUUID(), OTAOConfig.FLIGHT_INVITE_TIMEOUT_SECONDS.get() * 20);
        inviteCooldowns.computeIfAbsent(owner.getUUID(), k -> new HashMap<>()).put(target.getUUID(), OTAOConfig.FLIGHT_INVITE_COOLDOWN_SECONDS.get());
        return true;
    }

    public static boolean acceptInvite(ServerPlayer target, ServerPlayer owner) {
        UUID ownerUUID = inviteMap.get(target.getUUID());
        if (ownerUUID == null || !ownerUUID.equals(owner.getUUID())) return false;
        if (!hasFlightCore(owner)) return false;
        if (ownerToLinks.getOrDefault(owner.getUUID(), Set.of()).size() >= OTAOConfig.FLIGHT_MAX_LINKS.get()) return false;

        inviteMap.remove(target.getUUID());
        inviteTicks.remove(target.getUUID());
        ownerToLinks.computeIfAbsent(owner.getUUID(), k -> new HashSet<>()).add(target.getUUID());
        targetToOwner.put(target.getUUID(), owner.getUUID());
        blockedTicks.remove(target.getUUID());
        createLinkEntity(owner, target);
        target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 3 * 20, 0));
        owner.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.accepted", target.getName().getString()));
        target.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.link_started", owner.getName().getString()));
        return true;
    }

    public static boolean declineInvite(ServerPlayer target, ServerPlayer owner) {
        UUID ownerUUID = inviteMap.get(target.getUUID());
        if (ownerUUID == null || !ownerUUID.equals(owner.getUUID())) return false;
        inviteMap.remove(target.getUUID());
        inviteTicks.remove(target.getUUID());
        owner.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.declined", target.getName().getString()));
        target.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.decline_done"));
        return true;
    }

    public static boolean hasInviteFrom(ServerPlayer target, ServerPlayer owner) {
        UUID ownerUUID = inviteMap.get(target.getUUID());
        return ownerUUID != null && ownerUUID.equals(owner.getUUID());
    }

    public static boolean hasPendingInvite(ServerPlayer target) {
        return target != null && inviteMap.containsKey(target.getUUID());
    }

    public static boolean canAcceptInviteWith(ServerPlayer target, InteractionHand hand) {
        if (!hasPendingInvite(target)) return false;
        ItemStack stack = target.getItemInHand(hand);
        return stack.isEmpty() || stack.is(OTAOItems.FLIGHT_CORE.get());
    }

    public static boolean acceptPendingInvite(ServerPlayer target) {
        UUID ownerUUID = inviteMap.get(target.getUUID());
        if (ownerUUID == null) return false;
        ServerPlayer owner = target.level().getServer().getPlayerList().getPlayer(ownerUUID);
        if (owner == null) {
            inviteMap.remove(target.getUUID());
            inviteTicks.remove(target.getUUID());
            return false;
        }
        return acceptInvite(target, owner);
    }

    public static boolean isLinked(ServerPlayer owner, ServerPlayer target) {
        if (owner == null || target == null) return false;
        Set<UUID> links = ownerToLinks.get(owner.getUUID());
        return links != null && links.contains(target.getUUID()) && owner.getUUID().equals(targetToOwner.get(target.getUUID()));
    }

    public static ServerPlayer findLookTarget(ServerPlayer owner, double range) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        AABB box = owner.getBoundingBox().expandTowards(look.scale(range)).inflate(1.25);
        ServerPlayer best = null;
        double bestDist = range * range;
        for (ServerPlayer candidate : owner.level().getEntitiesOfClass(ServerPlayer.class, box, player -> player != owner && player.isAlive())) {
            if (!candidate.level().dimension().equals(owner.level().dimension())) continue;
            double dist = candidate.getBoundingBox().inflate(0.5).clip(eye, end).map(hit -> hit.distanceToSqr(eye)).orElse(Double.MAX_VALUE);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    private static void createLinkEntity(ServerPlayer owner, ServerPlayer target) {
        FlightLinkEntity entity = OTAOEntities.FLIGHT_LINK.get().create(owner.level(), net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (entity == null) return;
        entity.setup(owner, target);
        owner.level().addFreshEntity(entity);
        linkEntities.computeIfAbsent(owner.getUUID(), k -> new HashMap<>()).put(target.getUUID(), entity.getUUID());
    }

    private static void removeLinkEntity(ServerPlayer source, UUID ownerUUID, UUID targetUUID) {
        Map<UUID, UUID> links = linkEntities.get(ownerUUID);
        if (links == null) return;
        UUID entityUUID = links.remove(targetUUID);
        if (links.isEmpty()) linkEntities.remove(ownerUUID);
        if (entityUUID == null || !(source.level() instanceof ServerLevel level)) return;
        Entity entity = level.getEntity(entityUUID);
        if (entity != null) entity.discard();
    }

    public static void breakLink(ServerPlayer target, boolean slowFalling) {
        UUID ownerUUID = targetToOwner.remove(target.getUUID());
        if (ownerUUID == null) return;

        Set<UUID> links = ownerToLinks.get(ownerUUID);
        if (links != null) links.remove(target.getUUID());
        removeLinkEntity(target, ownerUUID, target.getUUID());
        blockedTicks.remove(target.getUUID());
        if (slowFalling) target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 3 * 20, 0));
        target.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.link_broken"));
    }

    public static void breakAllLinks(ServerPlayer owner, boolean slowFalling) {
        Set<UUID> links = ownerToLinks.remove(owner.getUUID());
        if (links == null) return;

        List<UUID> targets = new ArrayList<>(links);
        for (UUID targetUUID : targets) {
            ServerPlayer target = owner.level().getServer().getPlayerList().getPlayer(targetUUID);
            targetToOwner.remove(targetUUID);
            removeLinkEntity(owner, owner.getUUID(), targetUUID);
            blockedTicks.remove(targetUUID);
            if (target == null) continue;
            if (slowFalling) target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 3 * 20, 0));
            target.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.flight.link_broken"));
        }
    }

    public static void onSneak(ServerPlayer player) {
        if (targetToOwner.containsKey(player.getUUID())) {
            breakLink(player, true);
            return;
        }
        if (ownerToLinks.containsKey(player.getUUID())) {
            breakAllLinks(player, true);
        }
    }

    public static void onLogout(ServerPlayer player) {
        disableFlight(player, true);
        breakLink(player, false);
        breakAllLinks(player, true);
        flightOwners.remove(player.getUUID());
        grantedFlight.remove(player.getUUID());
        originalFlyingSpeeds.remove(player.getUUID());
        inviteMap.remove(player.getUUID());
        inviteTicks.remove(player.getUUID());
        inviteCooldowns.remove(player.getUUID());
        inviteMap.entrySet().removeIf(entry -> entry.getValue().equals(player.getUUID()));
        blockedTicks.remove(player.getUUID());
    }

    public static boolean isLinkedTarget(Player player) {
        if (player == null) return false;
        return targetToOwner.containsKey(player.getUUID());
    }

    public static boolean isFlightOwner(Player player) {
        if (player == null) return false;
        return flightOwners.contains(player.getUUID());
    }

    public static boolean isLinkOwner(Player player) {
        if (player == null) return false;
        Set<UUID> links = ownerToLinks.get(player.getUUID());
        return links != null && !links.isEmpty();
    }

    public static boolean canAttack(Player player) {
        if (player == null) return true;
        if (isLinkedTarget(player)) return false;
        return !isLinkOwner(player);
    }

    public static boolean canInteract(Player player) {
        if (player == null) return true;
        return !isLinkedTarget(player);
    }

    private static void enableFlight(ServerPlayer player) {
        flightOwners.add(player.getUUID());
        if (!player.getAbilities().mayfly) {
            grantedFlight.add(player.getUUID());
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
    }

    private static void disableFlight(ServerPlayer player, boolean slowFalling) {
        boolean hadLinks = ownerToLinks.containsKey(player.getUUID());
        if (hadLinks) breakAllLinks(player, true);
        boolean wasFlightOwner = flightOwners.remove(player.getUUID());
        boolean grantedByThisMod = grantedFlight.remove(player.getUUID());
        if (!wasFlightOwner && !grantedByThisMod) return;
        restoreFlyingSpeed(player);
        if (player.isCreative() || player.isSpectator()) {
            player.onUpdateAbilities();
            return;
        }
        if (grantedByThisMod) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
        }
        player.onUpdateAbilities();
        if (slowFalling && grantedByThisMod) player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 3 * 20, 0));
    }

    private static void restoreFlyingSpeed(ServerPlayer player) {
        Float originalSpeed = originalFlyingSpeeds.remove(player.getUUID());
        player.getAbilities().setFlyingSpeed(originalSpeed == null ? 0.05F : originalSpeed);
    }

    private static boolean hasFlightCore(ServerPlayer player) {
        ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offhand = player.getItemInHand(InteractionHand.OFF_HAND);
        return mainHand.is(OTAOItems.FLIGHT_CORE.get()) || offhand.is(OTAOItems.FLIGHT_CORE.get());
    }

    private static double getDimensionMultiplier(Level level) {
        if (level.dimension() == Level.NETHER) return OTAOConfig.DIMENSION_NETHER_EFFICIENCY.get();
        if (level.dimension() == Level.END) return OTAOConfig.DIMENSION_END_EFFICIENCY.get();
        return 1.0;
    }
}
