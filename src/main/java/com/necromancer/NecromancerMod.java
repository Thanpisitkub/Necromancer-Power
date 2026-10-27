package com.necromancer;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(NecromancerMod.MODID)
public class NecromancerMod {
    public static final String MODID = "necromancer";

    public NecromancerMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModParticles.PARTICLES.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, NecroConfig.SPEC);
    }
}
