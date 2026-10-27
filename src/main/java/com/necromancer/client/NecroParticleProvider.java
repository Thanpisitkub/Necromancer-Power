package com.necromancer.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

public class NecroParticleProvider implements ParticleProvider<SimpleParticleType> {
    private final SpriteSet sprites;
    private final ParticleStyle style;

    public NecroParticleProvider(SpriteSet sprites, ParticleStyle style) {
        this.sprites = sprites;
        this.style = style;
    }

    @Override
    public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                   double xd, double yd, double zd) {
        return new NecroParticle(level, x, y, z, xd, yd, zd, this.style, this.sprites);
    }
}
