package com.necromancer.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class NecroParticle extends TextureSheetParticle {
    private static final int FULL_BRIGHT = 15728880;

    private final ParticleStyle style;
    private final SpriteSet sprites;
    private final float baseSize;
    private final float spinSpeed;
    private float spinAngle;
    private float spinAngleO;

    public NecroParticle(ClientLevel level, double x, double y, double z,
                         double vx, double vy, double vz, ParticleStyle style, SpriteSet sprites) {
        super(level, x, y, z);
        this.style = style;
        this.sprites = sprites;
        this.lifetime = style.lifetime + (style.jitter > 0 ? this.random.nextInt(style.jitter + 1) : 0);
        this.gravity = style.gravity;
        this.hasPhysics = false;
        this.friction = 0.96F;

        if (style.flat) {
            this.xd = 0.0D;
            this.yd = 0.0D;
            this.zd = 0.0D;
            // for flat particles the "x velocity" passed by the server is a size multiplier (radius in blocks)
            float scale = vx > 0.05D ? (float) vx : 1.0F;
            this.baseSize = style.size * scale;
            this.spinSpeed = style.spin;
            this.spinAngle = this.random.nextFloat() * Mth.TWO_PI;
        } else {
            this.xd = vx;
            this.yd = vy;
            this.zd = vz;
            this.baseSize = style.size * (0.8F + this.random.nextFloat() * 0.4F);
            this.spinSpeed = style.spin * (this.random.nextBoolean() ? 1.0F : -1.0F);
            this.roll = this.random.nextFloat() * Mth.TWO_PI;
            this.oRoll = this.roll;
        }
        this.spinAngleO = this.spinAngle;
        this.quadSize = this.baseSize;
        this.alpha = 0.0F;

        if (style.animated) {
            this.setSpriteFromAge(sprites);
        } else {
            this.pickSprite(sprites);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.style.animated && !this.removed) {
            this.setSpriteFromAge(this.sprites);
        }
        this.spinAngleO = this.spinAngle;
        this.spinAngle += this.spinSpeed;
        if (!this.style.flat) {
            this.oRoll = this.roll;
            this.roll += this.spinSpeed;
        }
    }

    private float lifeFraction(float partialTicks) {
        return Mth.clamp((this.age + partialTicks) / (float) this.lifetime, 0.0F, 1.0F);
    }

    @Override
    public float getQuadSize(float partialTicks) {
        return this.baseSize * (1.0F + this.style.growth * lifeFraction(partialTicks));
    }

    private float alphaAt(float partialTicks) {
        float t = lifeFraction(partialTicks);
        float in = this.style.fadeIn > 0.0F ? Mth.clamp(t / this.style.fadeIn, 0.0F, 1.0F) : 1.0F;
        float out = Mth.clamp((1.0F - t) / 0.4F, 0.0F, 1.0F);
        return in * out;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        this.alpha = alphaAt(partialTicks);
        if (this.alpha <= 0.004F) {
            return;
        }
        if (!this.style.flat) {
            super.render(buffer, camera, partialTicks);
            return;
        }

        // flat (ground aligned) quad, drawn twice so it is visible from above and below
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float py = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y()) + 0.03F;
        float pz = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());
        float size = getQuadSize(partialTicks);
        float angle = Mth.lerp(partialTicks, this.spinAngleO, this.spinAngle);
        float cos = Mth.cos(angle) * size;
        float sin = Mth.sin(angle) * size;

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        float[] cx = {-1.0F, -1.0F, 1.0F, 1.0F};
        float[] cz = {-1.0F, 1.0F, 1.0F, -1.0F};
        float[] us = {u1, u1, u0, u0};
        float[] vs = {v1, v0, v0, v1};
        int light = this.getLightColor(partialTicks);

        for (int pass = 0; pass < 2; pass++) {
            for (int n = 0; n < 4; n++) {
                int i = pass == 0 ? n : 3 - n;
                float rx = cx[i] * cos - cz[i] * sin;
                float rz = cx[i] * sin + cz[i] * cos;
                buffer.vertex(px + rx, py, pz + rz)
                        .uv(us[i], vs[i])
                        .color(this.rCol, this.gCol, this.bCol, this.alpha)
                        .uv2(light)
                        .endVertex();
            }
        }
    }
}
