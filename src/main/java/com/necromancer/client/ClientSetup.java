package com.necromancer.client;

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
    }

    private ClientSetup() {}
}
