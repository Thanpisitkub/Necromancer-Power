package com.necromancer.client;

import com.necromancer.ModParticles;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.RegistryObject;

/** Look and behaviour of every custom particle. size = half width of the quad in blocks. */
public enum ParticleStyle {
    //            type                        flat   anim   size   life jit gravity growth  spin    fadeIn
    RUNE_SEAL    (ModParticles.RUNE_SEAL,     true,  false, 1.00F, 70,  0,  0.00F,  0.12F, 0.045F, 0.12F),
    SUMMON_CIRCLE(ModParticles.SUMMON_CIRCLE, false, false, 1.70F, 36,  0,  0.00F,  0.25F, 0.000F, 0.20F),
    SUMMON_BURST (ModParticles.SUMMON_BURST,  false, true,  1.30F, 20,  0,  0.00F,  0.30F, 0.000F, 0.10F),
    SOUL_FLAME   (ModParticles.SOUL_FLAME,    false, false, 0.26F, 14,  6, -0.02F, -0.35F, 0.000F, 0.10F),
    DARK_SMOKE   (ModParticles.DARK_SMOKE,    false, false, 0.55F, 30, 10, -0.01F,  0.80F, 0.020F, 0.20F),
    SLASH        (ModParticles.SLASH,         false, false, 0.85F,  9,  0,  0.00F,  0.20F, 0.000F, 0.10F),
    ENERGY_RING  (ModParticles.ENERGY_RING,   false, false, 0.80F, 22,  0,  0.00F,  2.20F, 0.000F, 0.10F),
    RUNE_RING    (ModParticles.RUNE_RING,     false, false, 1.00F, 30,  0,  0.00F,  0.50F, 0.000F, 0.15F),
    MARK_GLYPH   (ModParticles.MARK_GLYPH,    false, false, 0.30F, 14,  0, -0.01F,  0.00F, 0.000F, 0.15F),
    CRYSTAL_SHARD(ModParticles.CRYSTAL_SHARD, false, false, 0.14F, 32, 10, -0.03F, -0.20F, 0.060F, 0.10F),
    DARK_ORB     (ModParticles.DARK_ORB,      false, false, 0.13F, 22, 10, -0.02F, -0.30F, 0.000F, 0.15F),
    DARK_SPARK   (ModParticles.DARK_SPARK,    false, false, 0.15F, 16,  8,  0.00F, -0.20F, 0.080F, 0.10F);

    public final RegistryObject<SimpleParticleType> type;
    public final boolean flat;
    public final boolean animated;
    public final float size;
    public final int lifetime;
    public final int jitter;
    public final float gravity;
    public final float growth;
    public final float spin;
    public final float fadeIn;

    ParticleStyle(RegistryObject<SimpleParticleType> type, boolean flat, boolean animated, float size,
                  int lifetime, int jitter, float gravity, float growth, float spin, float fadeIn) {
        this.type = type;
        this.flat = flat;
        this.animated = animated;
        this.size = size;
        this.lifetime = lifetime;
        this.jitter = jitter;
        this.gravity = gravity;
        this.growth = growth;
        this.spin = spin;
        this.fadeIn = fadeIn;
    }
}
