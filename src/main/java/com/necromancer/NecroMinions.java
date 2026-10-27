package com.necromancer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/** Behaviour of the summoned monsters (runs on the server). */
public final class NecroMinions {
    private static final double SEARCH_RANGE = 16.0D;
    private static final double TELEPORT_DISTANCE = 48.0D;
    private static final double FOLLOW_DISTANCE = 10.0D;

    /** Can a minion owned by ownerId attack e? Never the owner, marked players or the owner's own minions. */
    public static boolean isValidTarget(MinecraftServer server, UUID ownerId, Entity self, LivingEntity e) {
        if (e == self || !e.isAlive() || e.getUUID().equals(ownerId)) {
            return false;
        }
        UUID eo = NecroState.ownerOf(e);
        if (eo != null) {
            // another necromancer's minion is fair game, our own and mounts are not
            return NecroState.isMinion(e) && !eo.equals(ownerId);
        }
        if (e instanceof ServerPlayer sp) {
            if (sp.isCreative() || sp.isSpectator()) {
                return false;
            }
            return !MarkSystem.isMarked(server, ownerId, sp.getUUID());
        }
        return e instanceof Enemy;
    }

    /** Who Dark Magic hurts: hostile mobs and other players, never allies or the caster's own army. */
    public static boolean isEnemyOf(ServerPlayer caster, LivingEntity e) {
        if (e == caster || !e.isAlive()) {
            return false;
        }
        UUID eo = NecroState.ownerOf(e);
        if (eo != null) {
            return NecroState.isMinion(e) && !eo.equals(caster.getUUID());
        }
        if (e instanceof ServerPlayer sp) {
            return !sp.isSpectator() && !MarkSystem.isMarked(caster.server, caster.getUUID(), sp.getUUID());
        }
        return e instanceof Enemy;
    }

    public static void tick(MinecraftServer server) {
        int tc = server.getTickCount();
        if (tc % 3 == 0) {
            flames();
        }
        if (tc % 10 == 0) {
            think(server);
        }
    }

    /** Purple fire burning on the head of every minion. */
    private static void flames() {
        for (Entity e : NecroState.snapshot()) {
            if (NecroState.isMinion(e) && e.isAlive() && e.level() instanceof ServerLevel level) {
                level.sendParticles(ModParticles.SOUL_FLAME.get(), e.getX(), e.getEyeY() + 0.1D, e.getZ(),
                        1, 0.12D, 0.04D, 0.12D, 0.01D);
            }
        }
    }

    private static void think(MinecraftServer server) {
        for (Entity e : NecroState.snapshot()) {
            if (!e.isAlive()) {
                continue;
            }
            UUID ownerId = NecroState.ownerOf(e);
            if (ownerId == null) {
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner == null || !owner.isAlive() || !NecroState.isNecromancer(owner)) {
                vanish(e);
                continue;
            }
            if (NecroState.isMount(e)) {
                if (!e.isVehicle() && e.tickCount > 60) {
                    vanish(e);
                }
                continue;
            }
            if (!(e instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
                continue;
            }

            if (mob.level() != owner.level()) {
                vanish(mob);
                continue;
            }

            double distSq = mob.distanceToSqr(owner);
            if (distSq > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
                Entity root = mob.getRootVehicle();
                root.teleportTo(owner.getX() + (level.random.nextDouble() - 0.5D) * 4.0D, owner.getY(),
                        owner.getZ() + (level.random.nextDouble() - 0.5D) * 4.0D);
                continue;
            }

            retarget(server, level, owner, mob);

            if (mob.getTarget() == null && distSq > FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
                mob.getNavigation().moveTo(owner, 1.15D);
            }

            // hostile monsters nearby start hunting the minion
            AABB box = mob.getBoundingBox().inflate(SEARCH_RANGE);
            for (Mob wild : level.getEntitiesOfClass(Mob.class, box,
                    o -> o instanceof Enemy && NecroState.ownerOf(o) == null && o.isAlive() && o.getTarget() == null)) {
                wild.setTarget(mob);
            }
        }
    }

    private static void retarget(MinecraftServer server, ServerLevel level, ServerPlayer owner, Mob mob) {
        UUID ownerId = owner.getUUID();
        LivingEntity target = mob.getTarget();
        if (target != null && !isValidTarget(server, ownerId, mob, target)) {
            mob.setTarget(null);
            target = null;
        }
        if (target != null) {
            return;
        }

        LivingEntity best = null;
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200
                && isValidTarget(server, ownerId, mob, attacker) && attacker.distanceToSqr(mob) < 32.0D * 32.0D) {
            best = attacker;
        }
        if (best == null) {
            LivingEntity victim = owner.getLastHurtMob();
            if (victim != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 200
                    && isValidTarget(server, ownerId, mob, victim) && victim.distanceToSqr(mob) < 32.0D * 32.0D) {
                best = victim;
            }
        }
        if (best == null) {
            List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(SEARCH_RANGE),
                    o -> isValidTarget(server, ownerId, mob, o));
            double bd = Double.MAX_VALUE;
            for (LivingEntity o : near) {
                double d = o.distanceToSqr(mob);
                if (d < bd) {
                    bd = d;
                    best = o;
                }
            }
        }
        if (best != null) {
            mob.setTarget(best);
        }
    }

    public static void vanish(Entity e) {
        if (e.level() instanceof ServerLevel level) {
            level.sendParticles(ModParticles.DARK_SMOKE.get(), e.getX(), e.getY() + e.getBbHeight() * 0.5D, e.getZ(),
                    6, 0.3D, 0.4D, 0.3D, 0.02D);
        }
        Entity vehicle = e.getVehicle();
        if (vehicle != null && NecroState.isMount(vehicle)) {
            vehicle.discard();
        }
        e.discard();
    }

    private NecroMinions() {}
}
