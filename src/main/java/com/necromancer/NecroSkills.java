package com.necromancer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class NecroSkills {
    private static final UUID ID_SPEED = UUID.fromString("5d0f3c1e-9a2b-4c7e-8f10-1a2b3c4d5e01");
    private static final UUID ID_DAMAGE = UUID.fromString("5d0f3c1e-9a2b-4c7e-8f10-1a2b3c4d5e02");
    private static final UUID ID_ARMOR = UUID.fromString("5d0f3c1e-9a2b-4c7e-8f10-1a2b3c4d5e03");

    private record Spec(EntityType<? extends Mob> type, boolean mounted) {}

    private static void msg(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text), true);
    }

    // ------------------------------------------------------------------ Skill 1: Summon

    public static int summon(ServerPlayer p) {
        NecroState.ping(p);
        MinecraftServer server = p.server;
        int now = server.getTickCount();

        if (NecroState.countMinions(p.getUUID()) > 0) {
            msg(p, "§5[Summon] §fยังมี Monster ที่เสกไว้อยู่ ต้องรอให้ตายหมดก่อนถึงจะเสกใหม่ได้");
            return 0;
        }
        int next = NecroState.nextSummon(p.getUUID());
        if (now < next) {
            int sec = (int) Math.ceil((next - now) / 20.0D);
            msg(p, "§5[Summon] §fคูลดาวน์อีก §d" + sec + " §fวินาที");
            return 0;
        }

        List<Spec> specs = new ArrayList<>();
        add(specs, EntityType.ZOMBIE, false, NecroConfig.ZOMBIES.get());
        add(specs, EntityType.SKELETON, false, NecroConfig.SKELETONS.get());
        add(specs, EntityType.SKELETON, true, NecroConfig.MOUNTED_SKELETONS.get());
        add(specs, EntityType.WITHER_SKELETON, false, NecroConfig.WITHER_SKELETONS.get());
        add(specs, EntityType.WITHER_SKELETON, true, NecroConfig.MOUNTED_WITHER_SKELETONS.get());

        ServerLevel level = p.serverLevel();
        int spawned = 0;
        for (Spec s : specs) {
            if (spawn(p, level, s.type(), s.mounted())) {
                spawned++;
            }
        }
        if (spawned == 0) {
            msg(p, "§5[Summon] §fเสกไม่สำเร็จ (ไม่มีที่ว่างหรือตั้งค่าจำนวนเป็น 0)");
            return 0;
        }

        NecroState.setNextSummon(p.getUUID(), now + NecroConfig.SUMMON_COOLDOWN_SECONDS.get() * 20);

        level.sendParticles(ModParticles.RUNE_SEAL.get(), p.getX(), p.getY() + 0.02D, p.getZ(), 0, 3.5D, 0.0D, 0.0D, 1.0D);
        level.sendParticles(ModParticles.SUMMON_CIRCLE.get(), p.getX(), p.getY() + 0.9D, p.getZ(), 0, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ModParticles.DARK_SMOKE.get(), p.getX(), p.getY() + 0.3D, p.getZ(), 14, 1.2D, 0.2D, 1.2D, 0.02D);
        msg(p, "§5[Summon] §fเรียกกองทัพอันเดดแล้ว §d(" + spawned + ")");
        return spawned;
    }

    private static void add(List<Spec> list, EntityType<? extends Mob> type, boolean mounted, int count) {
        for (int i = 0; i < count; i++) {
            list.add(new Spec(type, mounted));
        }
    }

    private static boolean spawn(ServerPlayer owner, ServerLevel level, EntityType<? extends Mob> type, boolean mounted) {
        Mob mob = type.create(level);
        if (mob == null) {
            return false;
        }
        Vec3 spot = findSpot(owner, level, mounted);
        float yaw = level.random.nextFloat() * 360.0F;
        mob.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);

        equip(mob);
        buff(mob);
        claim(mob, owner, NecroState.TAG_MINION);

        SkeletonHorse horse = mounted ? EntityType.SKELETON_HORSE.create(level) : null;
        if (horse != null) {
            horse.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);
            horse.setTamed(true);
            horse.setAge(0);
            buff(horse);
            claim(horse, owner, NecroState.TAG_MOUNT);
            level.addFreshEntity(horse);
            level.addFreshEntity(mob);
            mob.startRiding(horse, true);
        } else {
            level.addFreshEntity(mob);
        }

        level.sendParticles(ModParticles.SUMMON_BURST.get(), spot.x, spot.y + 0.1D, spot.z, 0, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ModParticles.CRYSTAL_SHARD.get(), spot.x, spot.y + 0.4D, spot.z, 6, 0.4D, 0.2D, 0.4D, 0.05D);
        level.sendParticles(ModParticles.DARK_SPARK.get(), spot.x, spot.y + 0.8D, spot.z, 5, 0.3D, 0.5D, 0.3D, 0.04D);
        return true;
    }

    private static void claim(Mob m, ServerPlayer owner, String tag) {
        m.addTag(tag);
        m.getPersistentData().putUUID(NecroState.OWNER_KEY, owner.getUUID());
        m.setPersistenceRequired();
        // keeps zombies/skeletons from burning in daylight
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
    }

    private static Vec3 findSpot(ServerPlayer owner, ServerLevel level, boolean wide) {
        RandomSource r = level.random;
        for (int attempt = 0; attempt < 14; attempt++) {
            double a = r.nextDouble() * Math.PI * 2.0D;
            double d = 2.0D + r.nextDouble() * 2.5D;
            double x = owner.getX() + Math.cos(a) * d;
            double z = owner.getZ() + Math.sin(a) * d;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, owner.getY() + 3.0D, z);
            for (int i = 0; i < 9; i++) {
                BlockPos below = m.below();
                if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                    double half = wide ? 0.85D : 0.4D;
                    AABB box = new AABB(x - half, m.getY(), z - half, x + half, m.getY() + 2.1D, z + half);
                    if (level.noCollision(box)) {
                        return new Vec3(x, m.getY(), z);
                    }
                    break;
                }
                m.move(0, -1, 0);
            }
        }
        return owner.position();
    }

    private static void equip(Mob m) {
        gear(m, EquipmentSlot.MAINHAND, Items.IRON_SWORD);
        gear(m, EquipmentSlot.HEAD, Items.IRON_HELMET);
        gear(m, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE);
        gear(m, EquipmentSlot.LEGS, Items.IRON_LEGGINGS);
        gear(m, EquipmentSlot.FEET, Items.IRON_BOOTS);
    }

    private static void gear(Mob m, EquipmentSlot slot, Item item) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putBoolean("Unbreakable", true);
        m.setItemSlot(slot, stack);
        m.setDropChance(slot, 0.0F);
    }

    private static void buff(Mob m) {
        setBase(m, Attributes.MAX_HEALTH, NecroConfig.MINION_HEALTH.get());
        m.setHealth(m.getMaxHealth());
        setBase(m, Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0D);
        multiply(m, Attributes.MOVEMENT_SPEED, ID_SPEED, "necro_speed", NecroConfig.SPEED_MULT.get() - 1.0D);
        multiply(m, Attributes.ATTACK_DAMAGE, ID_DAMAGE, "necro_damage", NecroConfig.DAMAGE_MULT.get() - 1.0D);
        multiply(m, Attributes.ARMOR, ID_ARMOR, "necro_toughness", NecroConfig.TOUGH_MULT.get() - 1.0D);
    }

    private static void setBase(Mob m, Attribute attribute, double value) {
        AttributeInstance inst = m.getAttribute(attribute);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    private static void multiply(Mob m, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance inst = m.getAttribute(attribute);
        if (inst != null && amount != 0.0D) {
            inst.removeModifier(id);
            inst.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    // ------------------------------------------------------------------ Skill 2: Dark Magic

    public static int darkMagic(ServerPlayer p) {
        NecroState.ping(p);
        int now = p.server.getTickCount();
        int next = NecroState.nextDark(p.getUUID());
        if (now < next) {
            int sec = (int) Math.ceil((next - now) / 20.0D);
            msg(p, "§5[Dark Magic] §fคูลดาวน์อีก §d" + sec + " §fวินาที");
            return 0;
        }
        NecroState.setNextDark(p.getUUID(), now + NecroConfig.DARK_COOLDOWN_SECONDS.get() * 20);

        ServerLevel level = p.serverLevel();
        double radius = NecroConfig.DARK_RADIUS.get();
        double radiusSq = radius * radius;
        int ticks = NecroConfig.EFFECT_SECONDS.get() * 20;

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius),
                e -> NecroMinions.isEnemyOf(p, e) && e.distanceToSqr(p) <= radiusSq);
        for (LivingEntity e : targets) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, NecroConfig.SLOW_LEVEL.get() - 1), p);
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, NecroConfig.WEAK_LEVEL.get() - 1), p);
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks, NecroConfig.NAUSEA_LEVEL.get() - 1), p);
            level.sendParticles(ModParticles.SLASH.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5D, e.getZ(), 0, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ModParticles.DARK_SPARK.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5D, e.getZ(), 8, 0.35D, 0.5D, 0.35D, 0.05D);
        }

        level.sendParticles(ModParticles.RUNE_SEAL.get(), p.getX(), p.getY() + 0.02D, p.getZ(), 0, radius, 0.0D, 0.0D, 1.0D);
        level.sendParticles(ModParticles.ENERGY_RING.get(), p.getX(), p.getY() + 1.0D, p.getZ(), 0, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ModParticles.RUNE_RING.get(), p.getX(), p.getY() + 0.3D, p.getZ(), 0, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ModParticles.DARK_ORB.get(), p.getX(), p.getY() + 0.5D, p.getZ(), 24, radius * 0.4D, 0.4D, radius * 0.4D, 0.03D);
        level.sendParticles(ModParticles.DARK_SMOKE.get(), p.getX(), p.getY() + 0.4D, p.getZ(), 12, radius * 0.3D, 0.2D, radius * 0.3D, 0.02D);
        msg(p, "§5[Dark Magic] §fโจมตีศัตรู §d" + targets.size() + " §fเป้าหมาย");
        return targets.size();
    }

    private NecroSkills() {}
}
