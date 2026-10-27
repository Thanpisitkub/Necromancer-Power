package com.necromancer;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/** Behaviour of the summoned monsters (runs on the server). */
public final class NecroMinions {
    private static final double SEARCH_RANGE = 16.0D;
    private static final double THREAT_RANGE = 24.0D;
    private static final double TELEPORT_DISTANCE = 48.0D;
    private static final double FOLLOW_DISTANCE = 10.0D;
    private static final int THREAT_MEMORY_TICKS = 200;

    /**
     * Can a minion owned by ownerId attack e? Never the owner, marked players or the owner's own minions.
     * Hostile mobs and players are fair game; anything else (golems, wolves, endermen...) only when it attacks the owner.
     */
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
        if (e instanceof Enemy) {
            return true;
        }
        return isThreatTo(server, ownerId, e);
    }

    /** Is e attacking the owner right now (or did it hit the owner moments ago)? */
    public static boolean isThreatTo(MinecraftServer server, UUID ownerId, LivingEntity e) {
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            return false;
        }
        if (NecroState.isThreat(ownerId, e.getUUID(), server.getTickCount())) {
            return true;
        }
        if (e instanceof Mob m && m.getTarget() == owner) {
            return true;
        }
        return owner.getLastHurtByMob() == e && owner.tickCount - owner.getLastHurtByMobTimestamp() < THREAT_MEMORY_TICKS;
    }

    /** Dark Magic hits everyone around the caster except the caster (optional exceptions in the config). */
    public static boolean isDarkMagicTarget(ServerPlayer caster, LivingEntity e) {
        if (e == caster || !e.isAlive() || e instanceof ArmorStand) {
            return false;
        }
        if (e instanceof ServerPlayer sp && sp.isSpectator()) {
            return false;
        }
        if (NecroConfig.SPARE_MINIONS.get() && caster.getUUID().equals(NecroState.ownerOf(e))) {
            return false;
        }
        if (NecroConfig.SPARE_MARKED.get() && e instanceof ServerPlayer marked
                && MarkSystem.isMarked(caster.server, caster.getUUID(), marked.getUUID())) {
            return false;
        }
        return true;
    }

    /** Called the moment something hurts the owner: every minion turns on the attacker immediately. */
    public static void defend(MinecraftServer server, ServerPlayer owner, Entity source) {
        if (!(source instanceof LivingEntity attacker) || attacker == owner) {
            return;
        }
        UUID ownerId = owner.getUUID();
        if (ownerId.equals(NecroState.ownerOf(attacker))) {
            return;
        }
        NecroState.addThreat(ownerId, attacker.getUUID(), server.getTickCount() + THREAT_MEMORY_TICKS);
        for (Entity e : NecroState.snapshot()) {
            if (e instanceof Mob m && NecroState.isMinion(m) && m.isAlive() && ownerId.equals(NecroState.ownerOf(m))
                    && isValidTarget(server, ownerId, m, attacker)) {
                m.setTarget(attacker);
            }
        }
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

    /** 3D purple fireball burning on the head of every minion. */
    private static void flames() {
        for (Entity e : NecroState.snapshot()) {
            if (NecroState.isMinion(e) && e.isAlive() && e.level() instanceof ServerLevel level) {
                level.sendParticles(ModParticles.FIREBALL_HEAD.get(), e.getX(), e.getEyeY() + 0.2D, e.getZ(),
                        1, 0.05D, 0.02D, 0.05D, 0.01D);
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

        // 1) anyone who is attacking the owner comes first, even over the current target
        LivingEntity threat = null;
        double td = Double.MAX_VALUE;
        for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(THREAT_RANGE),
                x -> isThreatTo(server, ownerId, x) && isValidTarget(server, ownerId, mob, x))) {
            double d = o.distanceToSqr(mob);
            if (d < td) {
                td = d;
                threat = o;
            }
        }
        if (threat != null) {
            if (mob.getTarget() != threat) {
                mob.setTarget(threat);
            }
            return;
        }

        // 2) keep a valid current target
        LivingEntity target = mob.getTarget();
        if (target != null && !isValidTarget(server, ownerId, mob, target)) {
            mob.setTarget(null);
            target = null;
        }
        if (target != null) {
            return;
        }

        // 3) whatever the owner was fighting, otherwise the nearest enemy
        LivingEntity best = null;
        LivingEntity victim = owner.getLastHurtMob();
        if (victim != null && owner.tickCount - owner.getLastHurtMobTimestamp() < THREAT_MEMORY_TICKS
                && isValidTarget(server, ownerId, mob, victim) && victim.distanceToSqr(mob) < 32.0D * 32.0D) {
            best = victim;
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
