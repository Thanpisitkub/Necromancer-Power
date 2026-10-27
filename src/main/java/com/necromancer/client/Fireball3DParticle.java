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

/** A purple fireball made of real 3D geometry: glowing core sphere, outer plasma shell and crossed flame tongues. */
public class Fireball3DParticle extends Particle {
    private static final int PLASMA = 0, FLAME = 1, COUNT = 2;

    private final SpriteSet sprites;
    private final float size;
    private final boolean detailed;

    public Fireball3DParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                              float size, int lifetime, boolean detailed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.size = size * (0.85F + this.random.nextFloat() * 0.3F);
        this.detailed = detailed;
        this.lifetime = lifetime + this.random.nextInt(5);
        this.hasPhysics = false;
        this.gravity = -0.02F;
        this.friction = 0.95F;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
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
        float alpha = Mth.clamp(t / 0.1F, 0.0F, 1.0F) * Mth.clamp((1.0F - t) / 0.45F, 0.0F, 1.0F);
        if (alpha <= 0.004F) {
            return;
        }
        Vec3 cam = camera.getPosition();
        float ox = (float) (Mth.lerp(pt, this.xo, this.x) - cam.x());
        float oy = (float) (Mth.lerp(pt, this.yo, this.y) - cam.y());
        float oz = (float) (Mth.lerp(pt, this.zo, this.z) - cam.z());
        MeshWriter w = new MeshWriter(buffer, ox, oy, oz);

        float pulse = 1.0F + 0.12F * Mth.sin(time * 0.9F);
        float r = this.size * pulse * (1.0F - 0.45F * t);

        // flame tongues rising from the ball
        w.color(0.8F, 0.45F, 1.0F, alpha * 0.7F);
        w.cross(sp(FLAME), 0.0F, -r * 0.25F, 0.0F, r * 0.9F, r * 2.6F, time * 0.12F);

        // outer plasma shell
        w.color(0.75F, 0.35F, 1.0F, alpha * 0.65F);
        w.sphere(sp(PLASMA), 0.0F, 0.0F, 0.0F, r, -time * 0.18F, this.detailed ? 10 : 7, this.detailed ? 7 : 5);

        // hot core
        w.color(1.0F, 0.9F, 1.0F, alpha * 0.95F);
        w.sphere(sp(PLASMA), 0.0F, 0.0F, 0.0F, r * 0.55F, time * 0.25F, this.detailed ? 10 : 6, this.detailed ? 7 : 4);
    }
}
