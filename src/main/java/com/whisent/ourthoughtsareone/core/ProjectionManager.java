package com.whisent.ourthoughtsareone.core;

import com.whisent.ourthoughtsareone.config.OTAOConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.*;

// 管理投射核心的联结状态——使用静态 Map 跟踪所有骑乘/被骑乘关系、链深度和冷却
public class  ProjectionManager {

    // rider -> mount
    private static final Map<UUID, UUID> riderToMount = new HashMap<>();
    // mount -> rider
    private static final Map<UUID, UUID> mountToRider = new HashMap<>();
    // 每个玩家的链深度（0 = 自己没有被投射，1 = 直接骑在别人身上，2 = 骑在"骑在别人身上的人"身上……）
    private static final Map<UUID, Integer> chainDepth = new HashMap<>();
    // 投射核心冷却剩余 tick 数
    private static final Map<UUID, Integer> cooldownTicks = new HashMap<>();

    // ========== 联结操作 ==========

    // 玩家 A 投射到玩家 B 身上，返回是否成功
    public static boolean startProjection(Player rider, Player mount) {
        if (rider == null || mount == null) return false;
        if (rider.getUUID().equals(mount.getUUID())) return false;

        // 已经有骑乘关系
        if (riderToMount.containsKey(rider.getUUID())) return false;
        // B 不能同时被两个人骑
        if (mountToRider.containsKey(mount.getUUID())) return false;
        // 冷却中
        if (cooldownTicks.getOrDefault(rider.getUUID(), 0) > 0) return false;
        // 链长限制
        int mountDepth = chainDepth.getOrDefault(mount.getUUID(), 0);
        if (mountDepth >= OTAOConfig.PROJECTION_MAX_CHAIN_LENGTH.get()) return false;

        riderToMount.put(rider.getUUID(), mount.getUUID());
        mountToRider.put(mount.getUUID(), rider.getUUID());
        chainDepth.put(rider.getUUID(), mountDepth + 1);

        // 冷却
        cooldownTicks.put(rider.getUUID(), OTAOConfig.PROJECTION_COOLDOWN_SECONDS.get() * 20);

        // 实际乘骑
        rider.startRiding(mount);

        System.out.println("[投射核心] " + rider.getName().getString()
                + " 投射至 " + mount.getName().getString()
                + "，链深度=" + (mountDepth + 1));

        return true;
    }

    // 从指定玩家出发，解除其投射联结
    public static void endProjection(Player player, DisconnectReason reason) {
        UUID pid = player.getUUID();

        // 玩家是骑乘者——下马
        UUID mountUUID = riderToMount.remove(pid);
        if (mountUUID != null) {
            mountToRider.remove(mountUUID);
            chainDepth.remove(pid);
            player.stopRiding();

            // 如果是超距解除，施加超长冷却
            if (reason == DisconnectReason.DISTANCE_EXCEEDED) {
                cooldownTicks.put(pid, OTAOConfig.PROJECTION_EMERGENCY_COOLDOWN_SECONDS.get() * 20);
            }

            // 死亡解除时给存活方共鸣残留
            if (reason == DisconnectReason.DEATH) {
                applyResidualEffect(player);
            }

            // 递归解除骑在这个玩家上面的人
            endRiderChain(player, reason);
        }

        // 玩家是被骑乘者——甩掉上面的人
        UUID riderUUID = mountToRider.remove(pid);
        if (riderUUID != null) {
            riderToMount.remove(riderUUID);
            chainDepth.remove(riderUUID);
            Player rider = player.level().getPlayerByUUID(riderUUID);
            if (rider != null) {
                rider.stopRiding();
                if (reason == DisconnectReason.DEATH) {
                    applyResidualEffect(rider);
                }
                endRiderChain(rider, reason);
            }
        }
    }

    // 递归解除骑在指定玩家上面的所有骑乘者
    private static void endRiderChain(Player player, DisconnectReason reason) {
        UUID riderUUID = mountToRider.remove(player.getUUID());
        if (riderUUID != null) {
            riderToMount.remove(riderUUID);
            chainDepth.remove(riderUUID);
            Player rider = player.level().getPlayerByUUID(riderUUID);
            if (rider != null) {
                rider.stopRiding();
                if (reason == DisconnectReason.DEATH) {
                    applyResidualEffect(rider);
                }
                endRiderChain(rider, reason); // 递归
            }
        }
    }

    // 死亡后的共鸣残留效果
    private static void applyResidualEffect(Player player) {
        int duration = OTAOConfig.PROJECTION_RESIDUAL_DURATION_SECONDS.get() * 20;
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, duration, 0));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, 0));
    }

    // ========== 查询方法 ==========

    public static boolean isProjecting(Player player) {
        return riderToMount.containsKey(player.getUUID());
    }

    public static boolean isBeingMounted(Player player) {
        return mountToRider.containsKey(player.getUUID());
    }

    // 获取骑乘的目标，没有则返回 null
    public static Player getMount(Player rider, ServerLevel level) {
        UUID mountUUID = riderToMount.get(rider.getUUID());
        if (mountUUID == null) return null;
        return level.getPlayerByUUID(mountUUID);
    }

    // 获取骑在这个玩家上面的人
    public static Player getRider(Player mount, ServerLevel level) {
        UUID riderUUID = mountToRider.get(mount.getUUID());
        if (riderUUID == null) return null;
        return level.getPlayerByUUID(riderUUID);
    }

    public static int getChainDepth(Player player) {
        return chainDepth.getOrDefault(player.getUUID(), 0);
    }

    // 获取从指定玩家开始的完整链条（按深度从小到大：mount -> rider1 -> rider2 -> ...）
    public static List<Player> getChainFrom(Player mount, ServerLevel level) {
        List<Player> chain = new ArrayList<>();
        Player current = mount;
        while (current != null) {
            chain.add(current);
            UUID nextRiderUUID = mountToRider.get(current.getUUID());
            if (nextRiderUUID == null) break;
            current = level.getPlayerByUUID(nextRiderUUID);
        }
        return chain;
    }

    // 获取链条最顶端的骑乘者
    public static Player getTopRider(Player mount, ServerLevel level) {
        Player current = mount;
        while (true) {
            UUID nextUUID = mountToRider.get(current.getUUID());
            if (nextUUID == null) return current;
            Player next = level.getPlayerByUUID(nextUUID);
            if (next == null) return current;
            current = next;
        }
    }

    // ========== 冷却管理 ==========

    public static void tickCooldowns() {
        Iterator<Map.Entry<UUID, Integer>> it = cooldownTicks.entrySet().iterator();
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

    public static int getCooldownTicks(UUID playerUUID) {
        return cooldownTicks.getOrDefault(playerUUID, 0);
    }

    public static boolean isOnCooldown(UUID playerUUID) {
        return cooldownTicks.getOrDefault(playerUUID, 0) > 0;
    }

    // ========== 伤害分摊 ==========

    // 处理伤害分摊——在 LivingHurtEvent 中调用
    // 返回修改后的伤害值（剩余由 victim 自己承受的伤害）
    public static float handleDamageShare(LivingEntity victim, float originalDamage) {
        if (!(victim instanceof Player victimPlayer)) return originalDamage;
        if (originalDamage <= 0) return originalDamage;

        double shareRatio = OTAOConfig.PROJECTION_DAMAGE_SHARE_RATIO.get();
        if (shareRatio <= 0) return originalDamage;

        // 伤害分摊的份额
        float shareAmount = (float) (originalDamage * shareRatio);
        float remainingAmount = originalDamage - shareAmount;

        if (victim.level().isClientSide()) return originalDamage;
        ServerLevel level = (ServerLevel) victim.level();

        if (isProjecting(victimPlayer)) {
            // victim 是骑乘者，把伤害份额向上传给被骑乘者
            Player mount = getMount(victimPlayer, level);
            if (mount != null) {
                mount.hurtServer(level, victimPlayer.damageSources().generic(), shareAmount);
                System.out.println("[投射核心] 伤害分摊: " + victim.getName().getString()
                        + " -> " + mount.getName().getString()
                        + " (" + shareAmount + "/" + originalDamage + ")");
            }
            return remainingAmount;
        }

        if (isBeingMounted(victimPlayer)) {
            // victim 是被骑乘者，把伤害份额向上传给骑乘者
            Player rider = getRider(victimPlayer, level);
            if (rider != null) {
                rider.hurtServer(level, victimPlayer.damageSources().generic(), shareAmount);
                System.out.println("[投射核心] 伤害分摊: " + victim.getName().getString()
                        + " -> " + rider.getName().getString()
                        + " (" + shareAmount + "/" + originalDamage + ")");
            }
            return remainingAmount;
        }

        return originalDamage;
    }

    // ========== 追加攻击 ==========

    // B 攻击目标时，触发所有骑乘者的追加攻击
    public static void triggerBonusAttack(Player attacker, LivingEntity target) {
        if (attacker.level().isClientSide()) return;
        if (!isBeingMounted(attacker)) return;

        ServerLevel level = (ServerLevel) attacker.level();

        // 沿链条向上，每个骑乘者进行一次追加攻击
        Player currentMount = attacker;
        int chainStep = 0;
        double baseMultiplier = OTAOConfig.PROJECTION_BONUS_ATTACK_MULTIPLIER.get();
        double chainDecay = OTAOConfig.PROJECTION_CHAIN_DAMAGE_DECAY.get();

        // 维度衰减
        double dimMult = getDimensionMultiplier(level);
        baseMultiplier *= dimMult;

        while (true) {
            Player rider = getRider(currentMount, level);
            if (rider == null) break;

            double stepDecay = 1.0 - (chainStep * chainDecay);
            if (stepDecay <= 0) break;

            // 乘骑者的武器面板攻击力 × 倍率 × 链层衰减
            double weaponDamage = rider.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double damage = weaponDamage * baseMultiplier * stepDecay;
            if (damage > 0) {
                target.hurtServer(level, rider.damageSources().generic(), (float) damage);
                System.out.println("[投射核心] 追加攻击: " + rider.getName().getString()
                        + " -> " + target.getName().getString()
                        + " 伤害=" + String.format("%.1f", damage)
                        + " (链层=" + chainStep + ")");
            }

            currentMount = rider;
            chainStep++;
        }
    }

    // ========== 护甲共享 ==========

    // 获取被乘骑者应获得的额外护甲值（乘骑者护甲的百分比）
    public static double getSharedArmor(Player mount) {
        if (!isBeingMounted(mount)) return 0;

        Player rider = getRider(mount, (ServerLevel) mount.level());
        if (rider == null) return 0;

        return rider.getArmorValue() * OTAOConfig.PROJECTION_ARMOR_SHARE_RATIO.get();
    }

    // ========== 距离检查 ==========

    // 在 tick 中调用，检查联结双方距离
    public static void checkDistance(ServerLevel level) {
        int maxDistance = OTAOConfig.PROJECTION_MAX_DISTANCE.get();

        // 遍历所有骑乘关系，检查距离
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : riderToMount.entrySet()) {
            UUID riderUUID = entry.getKey();
            UUID mountUUID = entry.getValue();

            Player rider = level.getPlayerByUUID(riderUUID);
            Player mount = level.getPlayerByUUID(mountUUID);
            if (rider == null || mount == null) continue;

            if (rider.distanceTo(mount) > maxDistance) {
                toRemove.add(riderUUID);
            }
        }

        for (UUID riderUUID : toRemove) {
            Player rider = level.getPlayerByUUID(riderUUID);
            if (rider != null) {
                System.out.println("[投射核心] 超距强制解除: " + rider.getName().getString());
                endProjection(rider, DisconnectReason.DISTANCE_EXCEEDED);
            }
        }
    }

    // ========== 维度衰减 ==========

    private static double getDimensionMultiplier(ServerLevel level) {
        var dim = level.dimension();
        if (dim == net.minecraft.world.level.Level.NETHER) {
            return OTAOConfig.DIMENSION_NETHER_EFFICIENCY.get();
        }
        if (dim == net.minecraft.world.level.Level.END) {
            return OTAOConfig.DIMENSION_END_EFFICIENCY.get();
        }
        return 1.0;
    }

    // ========== 清理 ==========

    // 玩家登出时清理
    public static void onPlayerLogout(Player player) {
        endProjection(player, DisconnectReason.LOGOUT);
        cooldownTicks.remove(player.getUUID());
        chainDepth.remove(player.getUUID());
    }

    // ========== 枚举 ==========

    public enum DisconnectReason {
        SHIFT,             // 潜行键主动解除
        DEATH,             // 死亡
        DISTANCE_EXCEEDED, // 超距
        ITEM_SWITCH,       // 切换物品
        LOGOUT,            // 登出
        OTHER
    }
}
