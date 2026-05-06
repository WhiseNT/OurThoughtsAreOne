package com.whisent.ourthoughtsareone.config;

import net.neoforged.neoforge.common.ModConfigSpec;

// 投射核心 & 飞行核心的可配置参数
public class OTAOConfig {

    // ========== 投射核心 ==========
    public static ModConfigSpec.DoubleValue PROJECTION_BONUS_ATTACK_MULTIPLIER;
    public static ModConfigSpec.DoubleValue PROJECTION_DAMAGE_SHARE_RATIO;
    public static ModConfigSpec.DoubleValue PROJECTION_ARMOR_SHARE_RATIO;
    public static ModConfigSpec.IntValue PROJECTION_MAX_CHAIN_LENGTH;
    public static ModConfigSpec.DoubleValue PROJECTION_CHAIN_DAMAGE_DECAY;
    public static ModConfigSpec.IntValue PROJECTION_COOLDOWN_SECONDS;
    public static ModConfigSpec.IntValue PROJECTION_MAX_DISTANCE;
    public static ModConfigSpec.IntValue PROJECTION_EMERGENCY_COOLDOWN_SECONDS;
    public static ModConfigSpec.IntValue PROJECTION_RESIDUAL_DURATION_SECONDS;

    // ========== 飞行核心 ==========
    public static ModConfigSpec.DoubleValue FLIGHT_SPEED_MULTIPLIER;
    public static ModConfigSpec.IntValue FLIGHT_BASE_STAMINA;
    public static ModConfigSpec.IntValue FLIGHT_STAMINA_DRAIN_PER_SECOND;
    public static ModConfigSpec.IntValue FLIGHT_STAMINA_DRAIN_PER_PASSENGER;
    public static ModConfigSpec.IntValue FLIGHT_STAMINA_REGEN_PER_SECOND;
    public static ModConfigSpec.IntValue FLIGHT_MAX_LINKS;
    public static ModConfigSpec.IntValue FLIGHT_CHAIN_MAX_LENGTH;
    public static ModConfigSpec.IntValue FLIGHT_INVITE_TIMEOUT_SECONDS;
    public static ModConfigSpec.IntValue FLIGHT_INVITE_COOLDOWN_SECONDS;

    // ========== 维度衰减 ==========
    public static ModConfigSpec.DoubleValue DIMENSION_NETHER_EFFICIENCY;
    public static ModConfigSpec.DoubleValue DIMENSION_END_EFFICIENCY;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("========== 投射核心配置 ==========").push("projection_core");

        PROJECTION_BONUS_ATTACK_MULTIPLIER = BUILDER
                .comment("追加攻击伤害倍率（基于乘骑者主手武器伤害）")
                .defineInRange("bonus_attack_multiplier", 0.8, 0.0, 10.0);

        PROJECTION_DAMAGE_SHARE_RATIO = BUILDER
                .comment("伤害分摊比例")
                .defineInRange("damage_share_ratio", 0.25, 0.0, 1.0);

        PROJECTION_ARMOR_SHARE_RATIO = BUILDER
                .comment("护甲共享比例（被乘骑者获得乘骑者护甲值的百分比）")
                .defineInRange("armor_share_ratio", 0.25, 0.0, 1.0);

        PROJECTION_MAX_CHAIN_LENGTH = BUILDER
                .comment("最大骑乘链人数")
                .defineInRange("max_chain_length", 4, 1, 10);

        PROJECTION_CHAIN_DAMAGE_DECAY = BUILDER
                .comment("链条每层伤害衰减比例")
                .defineInRange("chain_damage_decay", 0.2, 0.0, 1.0);

        PROJECTION_COOLDOWN_SECONDS = BUILDER
                .comment("投射后的冷却时间（秒）")
                .defineInRange("cooldown_seconds", 30, 0, 300);

        PROJECTION_MAX_DISTANCE = BUILDER
                .comment("最大联结距离（格）")
                .defineInRange("max_distance", 32, 4, 256);

        PROJECTION_EMERGENCY_COOLDOWN_SECONDS = BUILDER
                .comment("超距强制解除后的冷却时间（秒）")
                .defineInRange("emergency_cooldown_seconds", 60, 0, 600);

        PROJECTION_RESIDUAL_DURATION_SECONDS = BUILDER
                .comment("联结因死亡解除后，存活方的共鸣残留效果持续时长（秒）")
                .defineInRange("residual_duration_seconds", 3, 0, 30);

        BUILDER.pop();

        BUILDER.comment("========== 飞行核心配置 ==========").push("flight_core");

        FLIGHT_SPEED_MULTIPLIER = BUILDER
                .comment("飞行速度倍率（相对创造飞行）")
                .defineInRange("flight_speed_multiplier", 0.6, 0.1, 1.5);

        FLIGHT_BASE_STAMINA = BUILDER
                .comment("基础耐力值")
                .defineInRange("base_stamina", 100, 10, 1000);

        FLIGHT_STAMINA_DRAIN_PER_SECOND = BUILDER
                .comment("基础耐力消耗/秒")
                .defineInRange("stamina_drain_per_second", 2, 0, 100);

        FLIGHT_STAMINA_DRAIN_PER_PASSENGER = BUILDER
                .comment("每名被链接者额外消耗/秒")
                .defineInRange("stamina_drain_per_passenger", 1, 0, 100);

        FLIGHT_STAMINA_REGEN_PER_SECOND = BUILDER
                .comment("落地时耐力恢复/秒")
                .defineInRange("stamina_regen_per_second", 5, 0, 100);

        FLIGHT_MAX_LINKS = BUILDER
                .comment("最大同时链接数")
                .defineInRange("max_links", 4, 1, 20);

        FLIGHT_CHAIN_MAX_LENGTH = BUILDER
                .comment("光链最大长度（格）")
                .defineInRange("chain_max_length", 16, 2, 64);

        FLIGHT_INVITE_TIMEOUT_SECONDS = BUILDER
                .comment("邀请超时时间（秒）")
                .defineInRange("invite_timeout_seconds", 8, 1, 60);

        FLIGHT_INVITE_COOLDOWN_SECONDS = BUILDER
                .comment("邀请冷却时间（秒）")
                .defineInRange("invite_cooldown_seconds", 15, 0, 120);

        BUILDER.pop();

        BUILDER.comment("========== 维度衰减 ==========").push("dimension_modifiers");

        DIMENSION_NETHER_EFFICIENCY = BUILDER
                .comment("下界效率衰减（攻击/飞行速度倍率）")
                .defineInRange("nether_efficiency_multiplier", 0.7, 0.1, 1.0);

        DIMENSION_END_EFFICIENCY = BUILDER
                .comment("末地效率衰减（攻击/飞行速度倍率）")
                .defineInRange("end_efficiency_multiplier", 0.7, 0.1, 1.0);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
