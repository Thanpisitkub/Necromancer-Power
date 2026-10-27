package com.necromancer;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Players the necromancer has marked. Minions never attack them, and the necromancer (and only the
 * necromancer) sees them glowing purple: we send that player's client a private scoreboard team and
 * a private entity-flags packet, so nobody else ever receives the glow.
 */
public final class MarkSystem {
    private static final String KEY = "NecroMarks";
    private static final EntityDataAccessor<Byte> SHARED_FLAGS = new EntityDataAccessor<>(0, EntityDataSerializers.BYTE);

    /** owner -> (marked player -> scoreboard name) currently glowing on the owner's screen */
    private static final Map<UUID, Map<UUID, String>> SHOWN = new HashMap<>();
    private static final Set<UUID> TEAM_SENT = new HashSet<>();
    private static PlayerTeam team;

    public static List<UUID> marks(ServerPlayer p) {
        List<UUID> out = new ArrayList<>();
        ListTag list = p.getPersistentData().getList(KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                out.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // skip corrupt entry
            }
        }
        return out;
    }

    public static boolean isMarked(MinecraftServer server, UUID owner, UUID target) {
        ServerPlayer o = server.getPlayerList().getPlayer(owner);
        return o != null && marks(o).contains(target);
    }

    /** @return true when the target is marked after the call. */
    public static boolean toggle(ServerPlayer owner, ServerPlayer target) {
        List<UUID> current = marks(owner);
        boolean nowMarked;
        if (current.contains(target.getUUID())) {
            current.remove(target.getUUID());
            nowMarked = false;
        } else {
            current.add(target.getUUID());
            nowMarked = true;
        }
        ListTag list = new ListTag();
        for (UUID id : current) {
            list.add(StringTag.valueOf(id.toString()));
        }
        owner.getPersistentData().put(KEY, list);
        return nowMarked;
    }

    public static void copyOnClone(Player oldPlayer, Player newPlayer) {
        Tag t = oldPlayer.getPersistentData().get(KEY);
        if (t != null) {
            newPlayer.getPersistentData().put(KEY, t.copy());
        }
    }

    private static PlayerTeam team() {
        if (team == null) {
            team = new PlayerTeam(new Scoreboard(), "necro_marks");
            ChatFormatting color = ChatFormatting.getByName(NecroConfig.MARK_COLOR.get());
            team.setColor(color != null && color.isColor() ? color : ChatFormatting.DARK_PURPLE);
        }
        return team;
    }

    private static void ensureTeam(ServerPlayer owner) {
        if (TEAM_SENT.add(owner.getUUID())) {
            owner.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team(), true));
        }
    }

    private static void sendFlags(ServerPlayer owner, ServerPlayer target, boolean glow) {
        int f = 0;
        if (target.isOnFire()) f |= 0x01;
        if (target.isShiftKeyDown()) f |= 0x02;
        if (target.isSprinting()) f |= 0x08;
        if (target.isSwimming()) f |= 0x10;
        if (target.isInvisible()) f |= 0x20;
        if (glow || target.hasEffect(MobEffects.GLOWING)) f |= 0x40;
        if (target.isFallFlying()) f |= 0x80;
        List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
        values.add(SynchedEntityData.DataValue.create(SHARED_FLAGS, (byte) f));
        owner.connection.send(new ClientboundSetEntityDataPacket(target.getId(), values));
    }

    private static void startGlow(ServerPlayer owner, ServerPlayer target) {
        ensureTeam(owner);
        owner.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                team(), target.getScoreboardName(), ClientboundSetPlayerTeamPacket.Action.ADD));
    }

    private static void stopGlow(ServerPlayer owner, String name, ServerPlayer targetOrNull) {
        owner.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                team(), name, ClientboundSetPlayerTeamPacket.Action.REMOVE));
        if (targetOrNull != null) {
            if (targetOrNull.getTeam() instanceof PlayerTeam real) {
                owner.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(
                        real, name, ClientboundSetPlayerTeamPacket.Action.ADD));
            }
            sendFlags(owner, targetOrNull, false);
        }
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        int tc = server.getTickCount();
        if (tc % 4 != 0) {
            return;
        }
        double range = NecroConfig.MARK_RANGE.get();
        double rangeSq = range * range;

        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            Map<UUID, String> shown = SHOWN.get(owner.getUUID());
            boolean necro = NecroState.isNecromancer(owner);
            if (!necro && (shown == null || shown.isEmpty())) {
                continue;
            }
            if (shown == null) {
                shown = new HashMap<>();
                SHOWN.put(owner.getUUID(), shown);
            }

            Map<UUID, ServerPlayer> wanted = new HashMap<>();
            if (necro && owner.isAlive()) {
                for (UUID id : marks(owner)) {
                    ServerPlayer t = server.getPlayerList().getPlayer(id);
                    if (t != null && t != owner && t.isAlive() && t.level() == owner.level()
                            && t.distanceToSqr(owner) <= rangeSq) {
                        wanted.put(id, t);
                    }
                }
            }

            for (UUID id : new ArrayList<>(shown.keySet())) {
                if (!wanted.containsKey(id)) {
                    stopGlow(owner, shown.get(id), server.getPlayerList().getPlayer(id));
                    shown.remove(id);
                }
            }

            for (Map.Entry<UUID, ServerPlayer> e : wanted.entrySet()) {
                ServerPlayer t = e.getValue();
                if (!shown.containsKey(e.getKey())) {
                    startGlow(owner, t);
                    shown.put(e.getKey(), t.getScoreboardName());
                }
                sendFlags(owner, t, true);
                if (tc % 12 == 0) {
                    owner.serverLevel().sendParticles(owner, ModParticles.MARK_GLYPH.get(), true,
                            t.getX(), t.getY() + t.getBbHeight() + 0.55D, t.getZ(),
                            0, 0.0D, 0.02D, 0.0D, 1.0D);
                }
            }
        }
    }

    public static void forget(UUID player) {
        SHOWN.remove(player);
        TEAM_SENT.remove(player);
    }

    public static void clearAll() {
        SHOWN.clear();
        TEAM_SENT.clear();
        team = null;
    }

    private MarkSystem() {}
}
