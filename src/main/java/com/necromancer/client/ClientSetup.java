package com.necromancer.client;

import com.necromancer.ModParticles;
import com.necromancer.NecromancerMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NecromancerMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        for (ParticleStyle style : ParticleStyle.values()) {
            event.registerSpriteSet(style.type.get(), sprites -> new NecroParticleProvider(sprites, style));
        }

        // 3D mesh particles
        event.registerSpriteSet(ModParticles.MAGIC_CIRCLE_3D.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new MagicCircle3DParticle(level, x, y, z, xd, sprites));
        event.registerSpriteSet(ModParticles.FIREBALL_3D.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new Fireball3DParticle(level, x, y, z, xd, yd, zd, 0.34F, 22, true, sprites));
        event.registerSpriteSet(ModParticles.FIREBALL_HEAD.get(), sprites ->
                (type, level, x, y, z, xd, yd, zd) -> new Fireball3DParticle(level, x, y, z, xd, yd, zd, 0.17F, 9, false, sprites));
    }

    private ClientSetup() {}
}
