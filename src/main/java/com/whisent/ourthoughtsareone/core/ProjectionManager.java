package com.whisent.ourthoughtsareone.core;

import com.whisent.ourthoughtsareone.config.OTAOConfig;
import com.whisent.ourthoughtsareone.entity.OTAOEntities;
import com.whisent.ourthoughtsareone.entity.ProjectionAnchorEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProjectionManager {
    private static final Map<UUID, UUID> riderToMount = new HashMap<>();
    private static final Map<UUID, UUID> mountToRider = new HashMap<>();
    private static final Map<UUID, Integer> chainDepth = new HashMap<>();
    private static final Map<UUID, Integer> cooldownTicks = new HashMap<>();
    private static final Map<UUID, Integer> startGraceTicks = new HashMap<>();
    private static final Map<UUID, UUID> riderToAnchor = new HashMap<>();
    private static boolean sharingDamage = false;
    private static boolean bonusAttacking = false;

    public static boolean startProjection(ServerPlayer rider, ServerPlayer mount) {
        if (rider == null || mount == null) return false;
        if (rider.getUUID().equals(mount.getUUID())) return false;
        if (riderToMount.containsKey(rider.getUUID())) return false;
        if (mountToRider.containsKey(mount.getUUID())) return false;
        if (isOnCooldown(rider.getUUID())) return false;
        if (!rider.getRootVehicle().equals(rider)) return false;
        if (!mount.getRootVehicle().equals(mount) && !riderToMount.containsKey(mount.getUUID())) return false;

        int mountDepth = chainDepth.getOrDefault(mount.getUUID(), 0);
        if (mountDepth + 1 >= OTAOConfig.PROJECTION_MAX_CHAIN_LENGTH.get()) return false;

        ProjectionAnchorEntity anchor = OTAOEntities.PROJECTION_ANCHOR.get().create(rider.level(), EntitySpawnReason.TRIGGERED);
        if (anchor == null) return false;

        riderToMount.put(rider.getUUID(), mount.getUUID());
        mountToRider.put(mount.getUUID(), rider.getUUID());
        chainDepth.put(rider.getUUID(), mountDepth + 1);
        anchor.setup(rider, mount);
        rider.level().addFreshEntity(anchor);
        riderToAnchor.put(rider.getUUID(), anchor.getUUID());
        startGraceTicks.put(rider.getUUID(), 20);

        return true;
    }

    public static void endProjection(Player player, DisconnectReason reason) {
        if (player == null) return;
        UUID pid = player.getUUID();
        UUID mountUUID = riderToMount.remove(pid);
        if (mountUUID != null) {
            mountToRider.remove(mountUUID);
            chainDepth.remove(pid);
            Player mount = getPlayer(player, mountUUID);
            removeAnchor(player, pid);
            startGraceTicks.remove(pid);
            player.stopRiding();
            if (reason == DisconnectReason.DISTANCE_EXCEEDED) cooldownTicks.put(pid, OTAOConfig.PROJECTION_EMERGENCY_COOLDOWN_SECONDS.get() * 20);
            if (reason == DisconnectReason.DEATH && mount != null) applyResidualEffect(mount);
            sendDismountMessage(player);
            if (mount != null) sendDismountMessage(mount);
            endRiderChain(player, reason);
        }

        UUID riderUUID = mountToRider.remove(pid);
        if (riderUUID == null) return;

        riderToMount.remove(riderUUID);
        chainDepth.remove(riderUUID);
        Player rider = getPlayer(player, riderUUID);
        removeAnchor(player, riderUUID);
        if (rider == null) return;
        rider.stopRiding();
        if (reason == DisconnectReason.DEATH) applyResidualEffect(rider);
        sendDismountMessage(rider);
        endRiderChain(rider, reason);
    }

    private static void endRiderChain(Player player, DisconnectReason reason) {
        if (player == null) return;

        UUID riderUUID = mountToRider.remove(player.getUUID());
        if (riderUUID == null) return;

        riderToMount.remove(riderUUID);
        chainDepth.remove(riderUUID);
        Player rider = getPlayer(player, riderUUID);
        removeAnchor(player, riderUUID);
        if (rider == null) return;
        rider.stopRiding();
        if (reason == DisconnectReason.DEATH) applyResidualEffect(rider);
        sendDismountMessage(rider);
        endRiderChain(rider, reason);
    }

    private static void sendDismountMessage(Player player) {
        if (player == null) return;
        player.sendOverlayMessage(Component.translatable("hint.ourthoughtsareone.projection.dismount"));
    }

    private static Player getPlayer(Player source, UUID uuid) {
        if (!(source.level() instanceof ServerLevel level)) return null;
        return level.getPlayerInAnyDimension(uuid);
    }

    private static void removeAnchor(Player source, UUID riderUUID) {
        UUID anchorUUID = riderToAnchor.remove(riderUUID);
        if (anchorUUID == null || !(source.level() instanceof ServerLevel level)) return;
        Entity anchor = level.getEntity(anchorUUID);
        if (anchor != null) {
            anchor.discard();
        }
    }

    private static void applyResidualEffect(Player player) {
        int duration = OTAOConfig.PROJECTION_RESIDUAL_DURATION_SECONDS.get() * 20;
        if (duration <= 0) return;
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, duration, 0));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, 0));
    }

    public static boolean isProjecting(Player player) {
        if (player == null) return false;
        return riderToMount.containsKey(player.getUUID());
    }

    public static boolean isBeingMounted(Player player) {
        if (player == null) return false;
        return mountToRider.containsKey(player.getUUID());
    }

    public static boolean canAttack(Player player) {
        if (player == null) return true;
        return !isProjecting(player);
    }

    public static Player getMount(Player rider, ServerLevel level) {
        UUID mountUUID = riderToMount.get(rider.getUUID());
        if (mountUUID == null) return null;
        return level.getPlayerInAnyDimension(mountUUID);
    }

    public static boolean isProjectionPair(Player a, Player b) {
        if (a == null || b == null) return false;
        UUID aMount = riderToMount.get(a.getUUID());
        if (b.getUUID().equals(aMount)) return true;
        UUID bMount = riderToMount.get(b.getUUID());
        return a.getUUID().equals(bMount);
    }

    public static Player getRider(Player mount, ServerLevel level) {
        UUID riderUUID = mountToRider.get(mount.getUUID());
        if (riderUUID == null) return null;
        return level.getPlayerInAnyDimension(riderUUID);
    }

    public static int getChainDepth(Player player) {
        return chainDepth.getOrDefault(player.getUUID(), 0);
    }

    public static List<Player> getChainFrom(Player mount, ServerLevel level) {
        List<Player> chain = new ArrayList<>();
        Player current = mount;
        while (current != null) {
            chain.add(current);
            UUID nextRiderUUID = mountToRider.get(current.getUUID());
            if (nextRiderUUID == null) break;
            current = level.getPlayerInAnyDimension(nextRiderUUID);
        }
        return chain;
    }

    public static void tickCooldowns() {
        tickMap(cooldownTicks);
        tickMap(startGraceTicks);
    }

    private static void tickMap(Map<UUID, Integer> map) {
        Iterator<Map.Entry<UUID, Integer>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int newVal = entry.getValue() - 1;
            if (newVal <= 0) {
                it.remove();
            } else {
                entry.setValue(newVal);
            }
        }
    }

    public static boolean isInStartGrace(Player player) {
        return player != null && startGraceTicks.getOrDefault(player.getUUID(), 0) > 0;
    }

    public static boolean isOnCooldown(UUID playerUUID) {
        return cooldownTicks.getOrDefault(playerUUID, 0) > 0;
    }

    public static float handleDamageShare(LivingEntity victim, float originalDamage) {
        if (sharingDamage) return originalDamage;
        if (!(victim instanceof ServerPlayer victimPlayer)) return originalDamage;
        if (originalDamage <= 0) return originalDamage;

        ServerLevel level = victimPlayer.level();
        float damage = applySharedArmor(victimPlayer, level, originalDamage);
        double shareRatio = OTAOConfig.PROJECTION_DAMAGE_SHARE_RATIO.get();
        if (shareRatio <= 0) return damage;

        Player shareTarget = null;
        if (isProjecting(victimPlayer)) {
            shareTarget = getMount(victimPlayer, level);
        } else if (isBeingMounted(victimPlayer)) {
            shareTarget = getRider(victimPlayer, level);
        }

        if (shareTarget == null || !shareTarget.isAlive()) return damage;

        float shareAmount = (float) (damage * shareRatio);
        float remainingAmount = damage - shareAmount;
        sharingDamage = true;
        shareTarget.hurtServer(level, victimPlayer.damageSources().generic(), shareAmount);
        sharingDamage = false;

        return remainingAmount;
    }

    private static float applySharedArmor(ServerPlayer victimPlayer, ServerLevel level, float damage) {
        if (!isBeingMounted(victimPlayer)) return damage;
        Player rider = getRider(victimPlayer, level);
        if (rider == null) return damage;

        double sharedArmor = rider.getArmorValue() * OTAOConfig.PROJECTION_ARMOR_SHARE_RATIO.get();
        if (sharedArmor <= 0) return damage;

        double reduce = Math.min(0.8, sharedArmor * 0.04);
        return (float) (damage * (1.0 - reduce));
    }

    public static void triggerBonusAttack(Player attacker, LivingEntity target) {
        if (bonusAttacking) return;
        if (!(attacker instanceof ServerPlayer serverPlayer)) return;
        if (target == null || !target.isAlive()) return;
        if (!isBeingMounted(serverPlayer)) return;

        ServerLevel level = serverPlayer.level();
        Player currentMount = serverPlayer;
        int chainStep = 0;
        double baseMultiplier = OTAOConfig.PROJECTION_BONUS_ATTACK_MULTIPLIER.get() * getDimensionMultiplier(level);
        double chainDecay = OTAOConfig.PROJECTION_CHAIN_DAMAGE_DECAY.get();
        bonusAttacking = true;

        while (true) {
            Player rider = getRider(currentMount, level);
            if (!(rider instanceof ServerPlayer serverRider)) break;
            if (!serverRider.isAlive()) break;

            double stepDecay = 1.0 - (chainStep * chainDecay);
            if (stepDecay <= 0) break;

            double weaponDamage = serverRider.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double damage = weaponDamage * baseMultiplier * stepDecay;
            if (damage > 0) {
                target.hurtServer(level, serverRider.damageSources().generic(), (float) damage);
            }

            currentMount = rider;
            chainStep++;
        }

        bonusAttacking = false;
    }

    public static void checkDistance(ServerLevel level) {
        int maxDistance = OTAOConfig.PROJECTION_MAX_DISTANCE.get();
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : riderToMount.entrySet()) {
            Player rider = level.getPlayerInAnyDimension(entry.getKey());
            Player mount = level.getPlayerInAnyDimension(entry.getValue());
            if (rider == null || mount == null) {
                toRemove.add(entry.getKey());
                continue;
            }
            if (!rider.level().dimension().equals(mount.level().dimension())) {
                toRemove.add(entry.getKey());
                continue;
            }
            if (rider.distanceTo(mount) > maxDistance) toRemove.add(entry.getKey());
        }

        for (UUID riderUUID : toRemove) {
            Player rider = level.getPlayerInAnyDimension(riderUUID);
            if (rider == null) {
                cleanRider(riderUUID);
                continue;
            }
            endProjection(rider, DisconnectReason.DISTANCE_EXCEEDED);
        }
    }

    private static void cleanRider(UUID riderUUID) {
        UUID mountUUID = riderToMount.remove(riderUUID);
        if (mountUUID != null) mountToRider.remove(mountUUID);
        chainDepth.remove(riderUUID);
        riderToAnchor.remove(riderUUID);
        startGraceTicks.remove(riderUUID);
    }

    private static double getDimensionMultiplier(ServerLevel level) {
        if (level.dimension() == Level.NETHER) return OTAOConfig.DIMENSION_NETHER_EFFICIENCY.get();
        if (level.dimension() == Level.END) return OTAOConfig.DIMENSION_END_EFFICIENCY.get();
        return 1.0;
    }

    public static void onPlayerLogout(Player player) {
        endProjection(player, DisconnectReason.LOGOUT);
        cooldownTicks.remove(player.getUUID());
        chainDepth.remove(player.getUUID());
        riderToAnchor.remove(player.getUUID());
        startGraceTicks.remove(player.getUUID());
    }

    public enum DisconnectReason {
        SHIFT,
        DEATH,
        DISTANCE_EXCEEDED,
        ITEM_SWITCH,
        LOGOUT,
        OTHER
    }
}
