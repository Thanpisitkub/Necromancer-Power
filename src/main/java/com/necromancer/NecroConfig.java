package com.necromancer;

import net.minecraftforge.common.ForgeConfigSpec;

/** Edit these in config/necromancer-common.toml (created on first launch). */
public final class NecroConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue ZOMBIES;
    public static final ForgeConfigSpec.IntValue SKELETONS;
    public static final ForgeConfigSpec.IntValue MOUNTED_SKELETONS;
    public static final ForgeConfigSpec.IntValue WITHER_SKELETONS;
    public static final ForgeConfigSpec.IntValue MOUNTED_WITHER_SKELETONS;
    public static final ForgeConfigSpec.IntValue SUMMON_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.DoubleValue MINION_HEALTH;
    public static final ForgeConfigSpec.DoubleValue SPEED_MULT;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULT;
    public static final ForgeConfigSpec.DoubleValue TOUGH_MULT;

    public static final ForgeConfigSpec.IntValue DARK_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue DARK_RADIUS;
    public static final ForgeConfigSpec.IntValue EFFECT_SECONDS;
    public static final ForgeConfigSpec.IntValue SLOW_LEVEL;
    public static final ForgeConfigSpec.IntValue WEAK_LEVEL;
    public static final ForgeConfigSpec.IntValue NAUSEA_LEVEL;
    public static final ForgeConfigSpec.BooleanValue SPARE_MINIONS;
    public static final ForgeConfigSpec.BooleanValue SPARE_MARKED;

    public static final ForgeConfigSpec.IntValue MARK_RANGE;
    public static final ForgeConfigSpec.ConfigValue<String> MARK_COLOR;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Skill 1: Summon").push("summon");
        ZOMBIES = b.comment("Zombies per cast").defineInRange("zombies", 2, 0, 30);
        SKELETONS = b.comment("Skeletons (sword, on foot) per cast").defineInRange("skeletons", 1, 0, 30);
        MOUNTED_SKELETONS = b.comment("Skeletons riding a skeleton horse per cast").defineInRange("mountedSkeletons", 1, 0, 30);
        WITHER_SKELETONS = b.comment("Wither skeletons (on foot) per cast").defineInRange("witherSkeletons", 1, 0, 30);
        MOUNTED_WITHER_SKELETONS = b.comment("Wither skeletons riding a skeleton horse per cast").defineInRange("mountedWitherSkeletons", 1, 0, 30);
        SUMMON_COOLDOWN_SECONDS = b.comment("Cooldown in seconds (counted from the cast)").defineInRange("cooldownSeconds", 60, 1, 3600);
        MINION_HEALTH = b.comment("Max health of every summoned monster").defineInRange("health", 500.0D, 1.0D, 1024.0D);
        SPEED_MULT = b.comment("Movement speed multiplier").defineInRange("speedMultiplier", 1.5D, 0.1D, 10.0D);
        DAMAGE_MULT = b.comment("Attack damage multiplier").defineInRange("damageMultiplier", 1.5D, 0.1D, 20.0D);
        TOUGH_MULT = b.comment("Armor multiplier (toughness)").defineInRange("toughnessMultiplier", 1.1D, 0.1D, 10.0D);
        b.pop();

        b.comment("Skill 2: Dark Magic").push("darkMagic");
        DARK_COOLDOWN_SECONDS = b.defineInRange("cooldownSeconds", 10, 1, 3600);
        DARK_RADIUS = b.comment("Blocks around the caster that get hit").defineInRange("radius", 10, 1, 64);
        EFFECT_SECONDS = b.defineInRange("effectSeconds", 10, 1, 600);
        SLOW_LEVEL = b.comment("Slowness level (1 = I)").defineInRange("slownessLevel", 3, 1, 10);
        WEAK_LEVEL = b.comment("Weakness level (1 = I)").defineInRange("weaknessLevel", 3, 1, 10);
        NAUSEA_LEVEL = b.comment("Nausea level (1 = I)").defineInRange("nauseaLevel", 2, 1, 10);
        SPARE_MINIONS = b.comment("true = your own summoned monsters are NOT affected (default: everyone except you is hit)").define("spareOwnMinions", false);
        SPARE_MARKED = b.comment("true = players you marked are NOT affected (default: everyone except you is hit)").define("spareMarkedPlayers", false);
        b.pop();

        b.comment("Mark (left-click a player)").push("mark");
        MARK_RANGE = b.comment("Blocks within which marked players glow for the necromancer").defineInRange("range", 50, 1, 128);
        MARK_COLOR = b.comment("Glow colour: dark_purple or light_purple (any vanilla colour name works)").define("glowColor", "dark_purple");
        b.pop();

        SPEC = b.build();
    }

    private NecroConfig() {}
}
