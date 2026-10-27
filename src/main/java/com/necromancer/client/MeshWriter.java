package com.necromancer.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

/**
 * Tiny helper that writes real 3D shapes (discs, planes, crystals, spheres, cylinders) into the
 * particle vertex buffer. Coordinates are relative to the particle origin.
 */
final class MeshWriter {
    private static final int FULL_BRIGHT = 15728880;

    private final VertexConsumer buf;
    private final float ox, oy, oz;
    private float r = 1.0F, g = 1.0F, b = 1.0F, a = 1.0F;

    MeshWriter(VertexConsumer buf, float ox, float oy, float oz) {
        this.buf = buf;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
    }

    void color(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = Mth.clamp(a, 0.0F, 1.0F);
    }

    private void vertex(float x, float y, float z, float u, float v) {
        buf.vertex(ox + x, oy + y, oz + z).uv(u, v).color(r, g, b, a).uv2(FULL_BRIGHT).endVertex();
    }

    /** index-th texture of a particle definition that lists `count` textures. */
    static TextureAtlasSprite sprite(SpriteSet set, int index, int count) {
        return count <= 1 ? set.get(0, 1) : set.get(index, count - 1);
    }

    /** Quad with corners bottom-left, bottom-right, top-right, top-left. */
    void quad(TextureAtlasSprite s,
              float ax, float ay, float az, float bx, float by, float bz,
              float cx, float cy, float cz, float dx, float dy, float dz) {
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        vertex(ax, ay, az, u0, v1);
        vertex(bx, by, bz, u1, v1);
        vertex(cx, cy, cz, u1, v0);
        vertex(dx, dy, dz, u0, v0);
    }

    /** Triangle (sent as a quad with a repeated corner); apex gets the top centre of the texture. */
    void triangle(TextureAtlasSprite s, float ax, float ay, float az, float bx, float by, float bz,
                  float cx, float cy, float cz) {
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        float um = (u0 + u1) * 0.5F;
        vertex(ax, ay, az, um, v0);
        vertex(bx, by, bz, u0, v1);
        vertex(cx, cy, cz, u1, v1);
        vertex(cx, cy, cz, u1, v1);
    }

    /** Horizontal square of half-size `half` rotated by `angle` around Y. */
    void disc(TextureAtlasSprite s, float cx, float cy, float cz, float half, float angle) {
        float c = Mth.cos(angle) * half, sn = Mth.sin(angle) * half;
        // corners (-1,1) (1,1) (1,-1) (-1,-1) rotated
        quad(s,
                cx + (-c - sn), cy, cz + (-sn + c),
                cx + (c - sn), cy, cz + (sn + c),
                cx + (c + sn), cy, cz + (sn - c),
                cx + (-c + sn), cy, cz + (-sn - c));
    }

    /** Vertical plane standing on (cx,cy,cz), spanning along the horizontal unit vector (tx,tz). */
    void plane(TextureAtlasSprite s, float cx, float cy, float cz, float tx, float tz, float halfWidth, float height) {
        float ax = cx - tx * halfWidth, az = cz - tz * halfWidth;
        float bx = cx + tx * halfWidth, bz = cz + tz * halfWidth;
        quad(s, ax, cy, az, bx, cy, bz, bx, cy + height, bz, ax, cy + height, az);
    }

    /** Three vertical planes crossing at 60 degrees: a volumetric flame. */
    void cross(TextureAtlasSprite s, float cx, float cy, float cz, float halfWidth, float height, float rot) {
        for (int i = 0; i < 3; i++) {
            float ang = rot + i * (Mth.PI / 3.0F);
            plane(s, cx, cy, cz, Mth.cos(ang), Mth.sin(ang), halfWidth, height);
        }
    }

    /** Double pyramid (crystal): `half` = half height, `rad` = radius at the middle. */
    void crystal(TextureAtlasSprite s, float cx, float cy, float cz, float half, float rad, float angle) {
        float[] ex = new float[4], ez = new float[4];
        for (int k = 0; k < 4; k++) {
            float ang = angle + k * Mth.HALF_PI;
            ex[k] = cx + Mth.cos(ang) * rad;
            ez[k] = cz + Mth.sin(ang) * rad;
        }
        for (int k = 0; k < 4; k++) {
            int n = (k + 1) % 4;
            triangle(s, cx, cy + half, cz, ex[k], cy, ez[k], ex[n], cy, ez[n]);
            triangle(s, cx, cy - half, cz, ex[n], cy, ez[n], ex[k], cy, ez[k]);
        }
    }

    /** Open cylinder wall (no caps). */
    void cylinder(TextureAtlasSprite s, float cx, float cy, float cz, float radius, float height, int segs, float rot) {
        for (int j = 0; j < segs; j++) {
            float a0 = rot + Mth.TWO_PI * j / segs, a1 = rot + Mth.TWO_PI * (j + 1) / segs;
            float x0 = cx + Mth.cos(a0) * radius, z0 = cz + Mth.sin(a0) * radius;
            float x1 = cx + Mth.cos(a1) * radius, z1 = cz + Mth.sin(a1) * radius;
            quad(s, x0, cy, z0, x1, cy, z1, x1, cy + height, z1, x0, cy + height, z0);
        }
    }

    /** UV sphere, texture wrapped around it. */
    void sphere(TextureAtlasSprite s, float cx, float cy, float cz, float radius, float rot, int segs, int rings) {
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        for (int i = 0; i < rings; i++) {
            float lat0 = -Mth.HALF_PI + Mth.PI * i / rings;
            float lat1 = -Mth.HALF_PI + Mth.PI * (i + 1) / rings;
            float y0 = Mth.sin(lat0) * radius, y1 = Mth.sin(lat1) * radius;
            float r0 = Mth.cos(lat0) * radius, r1 = Mth.cos(lat1) * radius;
            float va = Mth.lerp(i / (float) rings, v1, v0), vb = Mth.lerp((i + 1) / (float) rings, v1, v0);
            for (int j = 0; j < segs; j++) {
                float lon0 = rot + Mth.TWO_PI * j / segs, lon1 = rot + Mth.TWO_PI * (j + 1) / segs;
                float ua = Mth.lerp(j / (float) segs, u0, u1), ub = Mth.lerp((j + 1) / (float) segs, u0, u1);
                vertex(cx + Mth.cos(lon0) * r0, cy + y0, cz + Mth.sin(lon0) * r0, ua, va);
                vertex(cx + Mth.cos(lon1) * r0, cy + y0, cz + Mth.sin(lon1) * r0, ub, va);
                vertex(cx + Mth.cos(lon1) * r1, cy + y1, cz + Mth.sin(lon1) * r1, ub, vb);
                vertex(cx + Mth.cos(lon0) * r1, cy + y1, cz + Mth.sin(lon0) * r1, ua, vb);
            }
        }
    }
}
