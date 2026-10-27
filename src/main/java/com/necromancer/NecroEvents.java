package com.necromancer;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = NecromancerMod.MODID)
public final class NecroEvents {

    // ---------------------------------------------------------------- commands (called by the Origins powers)

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("necromancer")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("summon")
                        .executes(ctx -> NecroSkills.summon(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("darkmagic")
                        .executes(ctx -> NecroSkills.darkMagic(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("ping")
                        .executes(ctx -> {
                            NecroState.ping(ctx.getSource().getPlayerOrException());
                            return 1;
                        })));
    }

    // ---------------------------------------------------------------- tracking minions

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (!event.getLevel().isClientSide() && (NecroState.isMinion(e) || NecroState.isMount(e))) {
            NecroState.track(e);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            NecroState.untrack(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        NecroMinions.tick(server);
        MarkSystem.tick(server);
    }

    // ---------------------------------------------------------------- combat rules

    /** Minions never pick the owner, marked players or their own army as a target. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        LivingEntity target = event.getNewTarget();
        if (target == null || !NecroState.isMinion(mob)) {
            return;
        }
        UUID owner = NecroState.ownerOf(mob);
        MinecraftServer server = mob.getServer();
        if (owner == null || server == null) {
            return;
        }
        if (!NecroMinions.isValidTarget(server, owner, mob, target)) {
            event.setCanceled(true);
        }
    }

    /** Safety net: even a stray hit can't hurt the owner, marked players or other minions of the same owner. */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) {
            return;
        }

        // a necromancer's melee hit on another player is a mark click (covers mods that skip AttackEntityEvent)
        if (attacker instanceof ServerPlayer owner && event.getEntity() instanceof ServerPlayer target
                && owner != target && event.getSource().getDirectEntity() == owner && NecroState.isNecromancer(owner)) {
            markClick(owner, target);
            event.setCanceled(true);
            return;
        }

        if (!NecroState.isMinion(attacker)) {
            return;
        }
        UUID owner = NecroState.ownerOf(attacker);
        MinecraftServer server = attacker.getServer();
        LivingEntity victim = event.getEntity();
        if (owner == null || server == null) {
            return;
        }
        if (!NecroMinions.isValidTarget(server, owner, attacker, victim)) {
            event.setCanceled(true);
        }
    }

    /** Whoever hurts the necromancer is attacked by every minion right away. */
    @SubscribeEvent
    public static void onOwnerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer owner) || !NecroState.isNecromancer(owner)) {
            return;
        }
        MinecraftServer server = owner.getServer();
        if (server != null) {
            NecroMinions.defend(server, owner, event.getSource().getEntity());
        }
    }

    /** Wither skeleton minions do not apply the Wither effect. */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (event.getEffectInstance().getEffect() != MobEffects.WITHER) {
            return;
        }
        Entity source = event.getEffectSource();
        if (source != null && NecroState.isMinion(source)) {
            event.getEntity().removeEffect(MobEffects.WITHER);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        Entity e = event.getEntity();
        if (NecroState.isMinion(e) || NecroState.isMount(e)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onXp(LivingExperienceDropEvent event) {
        Entity e = event.getEntity();
        if (NecroState.isMinion(e) || NecroState.isMount(e)) {
            event.setDroppedExperience(0);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity e = event.getEntity();
        if (NecroState.isMinion(e)) {
            Entity vehicle = e.getVehicle();
            if (vehicle != null && NecroState.isMount(vehicle)) {
                vehicle.discard();
            }
        }
    }

    // ---------------------------------------------------------------- marking (left-click a player)

    @SubscribeEvent
    public static void onLeftClick(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer owner) || !(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }
        if (owner == target || !NecroState.isNecromancer(owner)) {
            return;
        }
        markClick(owner, target);
        event.setCanceled(true);
    }

    private static void markClick(ServerPlayer owner, ServerPlayer target) {
        if (!NecroState.markClickReady(owner.getUUID(), owner.server.getTickCount())) {
            return;
        }
        boolean marked = MarkSystem.toggle(owner, target);
        owner.displayClientMessage(Component.literal(marked
                ? "§5[Mark] §fมาร์ค §d" + target.getName().getString() + " §fแล้ว (Monster จะไม่โจมตี)"
                : "§5[Mark] §fยกเลิกมาร์ค §d" + target.getName().getString() + " §fแล้ว"), true);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            MarkSystem.copyOnClone(event.getOriginal(), event.getEntity());
        }
    }

    // ---------------------------------------------------------------- cleanup

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        MarkSystem.forget(id);
        NecroState.forgetPlayer(id);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NecroState.clearAll();
        MarkSystem.clearAll();
    }

    private NecroEvents() {}
}
