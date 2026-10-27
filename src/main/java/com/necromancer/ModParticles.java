package com.necromancer;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, NecromancerMod.MODID);

    public static final RegistryObject<SimpleParticleType> RUNE_SEAL = reg("rune_seal");
    public static final RegistryObject<SimpleParticleType> SUMMON_CIRCLE = reg("summon_circle");
    public static final RegistryObject<SimpleParticleType> SUMMON_BURST = reg("summon_burst");
    public static final RegistryObject<SimpleParticleType> SOUL_FLAME = reg("soul_flame");
    public static final RegistryObject<SimpleParticleType> DARK_SMOKE = reg("dark_smoke");
    public static final RegistryObject<SimpleParticleType> SLASH = reg("slash");
    public static final RegistryObject<SimpleParticleType> ENERGY_RING = reg("energy_ring");
    public static final RegistryObject<SimpleParticleType> RUNE_RING = reg("rune_ring");
    public static final RegistryObject<SimpleParticleType> MARK_GLYPH = reg("mark_glyph");
    public static final RegistryObject<SimpleParticleType> CRYSTAL_SHARD = reg("crystal_shard");
    public static final RegistryObject<SimpleParticleType> DARK_ORB = reg("dark_orb");
    public static final RegistryObject<SimpleParticleType> DARK_SPARK = reg("dark_spark");

    private static RegistryObject<SimpleParticleType> reg(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(true));
    }

    private ModParticles() {}
}
