package com.necromancer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared server-side state. Everything here is touched from the server thread only. */
public final class NecroState {
    public static final String TAG_MINION = "necro_minion";
    public static final String TAG_MOUNT = "necro_mount";
    public static final String OWNER_KEY = "NecroOwner";

    private static final Map<UUID, Integer> LAST_PING = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_SUMMON = new HashMap<>();
    private static final Map<UUID, Integer> NEXT_DARK = new HashMap<>();

    /** owner -> (attacker -> tick until which the attacker counts as a threat to the owner) */
    private static final Map<UUID, Map<UUID, Integer>> THREATS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_MARK_CLICK = new HashMap<>();

    /** Currently loaded minions and their mounts. */
    private static final Set<Entity> LOADED = new LinkedHashSet<>();

    /** The Origins power calls "/necromancer ping" every few ticks, so this tells us who has the origin. */
    public static void ping(ServerPlayer p) {
        LAST_PING.put(p.getUUID(), p.server.getTickCount());
    }

    public static boolean isNecromancer(ServerPlayer p) {
        Integer t = LAST_PING.get(p.getUUID());
        return t != null && p.server.getTickCount() - t <= 60;
    }

    public static int nextSummon(UUID id) {
        return NEXT_SUMMON.getOrDefault(id, 0);
    }

    public static void setNextSummon(UUID id, int tick) {
        NEXT_SUMMON.put(id, tick);
    }

    public static int nextDark(UUID id) {
        return NEXT_DARK.getOrDefault(id, 0);
    }

    public static void setNextDark(UUID id, int tick) {
        NEXT_DARK.put(id, tick);
    }

    public static void addThreat(UUID owner, UUID attacker, int untilTick) {
        Map<UUID, Integer> m = THREATS.computeIfAbsent(owner, k -> new HashMap<>());
        m.put(attacker, untilTick);
        if (m.size() > 32) {
            int now = untilTick - 200;
            m.values().removeIf(t -> t < now);
        }
    }

    public static boolean isThreat(UUID owner, UUID attacker, int now) {
        Map<UUID, Integer> m = THREATS.get(owner);
        if (m == null) {
            return false;
        }
        Integer until = m.get(attacker);
        return until != null && until >= now;
    }

    /** Left-click marking can fire several times per combo; only accept one toggle per 8 ticks. */
    public static boolean markClickReady(UUID owner, int now) {
        Integer last = LAST_MARK_CLICK.get(owner);
        if (last != null && now - last < 8) {
            return false;
        }
        LAST_MARK_CLICK.put(owner, now);
        return true;
    }

    public static boolean isMinion(Entity e) {
        return e.getTags().contains(TAG_MINION);
    }

    public static boolean isMount(Entity e) {
        return e.getTags().contains(TAG_MOUNT);
    }

    /** Owner UUID of a minion/mount, or null when the entity is not one of ours. */
    public static UUID ownerOf(Entity e) {
        if (!isMinion(e) && !isMount(e)) {
            return null;
        }
        CompoundTag data = e.getPersistentData();
        return data.hasUUID(OWNER_KEY) ? data.getUUID(OWNER_KEY) : null;
    }

    public static void track(Entity e) {
        LOADED.add(e);
    }

    public static void untrack(Entity e) {
        LOADED.remove(e);
    }

    public static List<Entity> snapshot() {
        return new ArrayList<>(LOADED);
    }

    public static int countMinions(UUID owner) {
        int c = 0;
        for (Entity e : LOADED) {
            if (isMinion(e) && e.isAlive() && owner.equals(ownerOf(e))) {
                c++;
            }
        }
        return c;
    }

    public static void forgetPlayer(UUID id) {
        LAST_PING.remove(id);
        THREATS.remove(id);
        LAST_MARK_CLICK.remove(id);
    }

    public static void clearAll() {
        LAST_PING.clear();
        NEXT_SUMMON.clear();
        NEXT_DARK.clear();
        THREATS.clear();
        LAST_MARK_CLICK.clear();
        LOADED.clear();
    }

    private NecroState() {}
}
