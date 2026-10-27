package com.necromancer.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A summoning circle built from real 3D geometry: ground seal, stacked rotating rings, standing rune
 * pillars, orbiting crystals and a flame column. The "x velocity" sent by the server is the radius.
 */
public class MagicCircle3DParticle extends Particle {
    // texture order in particles/magic_circle_3d.json
    private static final int SEAL = 0, RING = 1, ENERGY_RING = 2, GLYPH = 3, CRYSTAL = 4, FLAME = 7, COUNT = 8;

    private final SpriteSet sprites;
    private final float radius;

    public MagicCircle3DParticle(ClientLevel level, double x, double y, double z, double radiusParam, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.radius = radiusParam > 0.05D ? (float) radiusParam : 3.0F;
        this.lifetime = 70;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return NecroRenderTypes.ADDITIVE;
    }

    private TextureAtlasSprite sp(int i) {
        return MeshWriter.sprite(sprites, i, COUNT);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float pt) {
        float time = this.age + pt;
        float t = Mth.clamp(time / this.lifetime, 0.0F, 1.0F);
        float appear = Mth.clamp(t / 0.14F, 0.0F, 1.0F);
        float fade = Mth.clamp((1.0F - t) / 0.28F, 0.0F, 1.0F);
        float alpha = appear * fade;
        if (alpha <= 0.004F) {
            return;
        }
        float grow = 1.0F - (1.0F - appear) * (1.0F - appear) * (1.0F - appear);

        Vec3 cam = camera.getPosition();
        float ox = (float) (Mth.lerp(pt, this.xo, this.x) - cam.x());
        float oy = (float) (Mth.lerp(pt, this.yo, this.y) - cam.y());
        float oz = (float) (Mth.lerp(pt, this.zo, this.z) - cam.z());
        MeshWriter w = new MeshWriter(buffer, ox, oy, oz);

        float sc = this.radius / 3.0F;                       // 1.0 for a 3 block circle
        float vs = Mth.clamp(0.6F + 0.4F * sc, 1.0F, 2.2F);  // vertical scale, so big circles aren't absurdly tall
        float rad = this.radius * grow;

        // 1) ground seal
        w.color(0.85F, 0.55F, 1.0F, alpha * 0.95F);
        w.disc(sp(SEAL), 0.0F, 0.03F, 0.0F, rad, time * 0.03F);

        // 2) rings stacked above the seal, spinning in alternating directions
        for (int k = 0; k < 3; k++) {
            float h = (0.45F + k * 0.6F) * vs * grow;
            float r = this.radius * (0.92F - 0.17F * k) * grow;
            float dir = (k % 2 == 0) ? 1.0F : -1.0F;
            w.color(0.8F, 0.5F, 1.0F, alpha * 0.85F);
            w.disc(sp(k % 2 == 0 ? RING : ENERGY_RING), 0.0F, h, 0.0F, r, dir * time * (0.05F + 0.02F * k));
        }

        // 3) rune pillars standing around the edge
        int pillars = 8;
        for (int k = 0; k < pillars; k++) {
            float ang = time * 0.015F + k * Mth.TWO_PI / pillars;
            float c = Mth.cos(ang), s = Mth.sin(ang);
            float px = c * this.radius * 0.95F * grow, pz = s * this.radius * 0.95F * grow;
            float height = (1.7F + 0.35F * Mth.sin(time * 0.2F + k)) * vs * grow;
            w.color(0.75F, 0.45F, 1.0F, alpha * 0.8F);
            w.plane(sp(GLYPH), px, 0.0F, pz, -s, c, 0.3F * sc, height);
        }

        // 4) crystals orbiting inside the circle
        for (int k = 0; k < 6; k++) {
            float ang = -time * 0.04F + k * Mth.TWO_PI / 6.0F;
            float orbit = this.radius * 0.55F * grow;
            float y = (0.9F + 0.45F * Mth.sin(time * 0.12F + k * 1.3F)) * vs * grow;
            w.color(0.85F, 0.6F, 1.0F, alpha * 0.95F);
            w.crystal(sp(CRYSTAL + k % 3), Mth.cos(ang) * orbit, y, Mth.sin(ang) * orbit,
                    0.34F * sc, 0.15F * sc, time * 0.2F + k);
        }

        // 5) flame column in the middle
        float colH = 2.7F * vs * grow;
        w.color(0.8F, 0.4F, 1.0F, alpha * 0.55F);
        w.cylinder(sp(FLAME), 0.0F, 0.0F, 0.0F, 0.45F * sc, colH, 10, time * 0.08F);
        w.color(1.0F, 0.8F, 1.0F, alpha * 0.45F);
        w.cylinder(sp(FLAME), 0.0F, 0.0F, 0.0F, 0.22F * sc, colH * 1.1F, 8, -time * 0.12F);
    }
}
