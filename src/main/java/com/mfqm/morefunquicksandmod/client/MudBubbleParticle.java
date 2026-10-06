package com.mfqm.morefunquicksandmod.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public final class MudBubbleParticle extends SingleQuadParticle {
    private MudBubbleParticle(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,TextureAtlasSprite sprite){
        super(level,x,y,z,dx,dy,dz,sprite);setSize(0.08F,0.08F);quadSize=0.04F;lifetime=20;gravity=-0.005F;
        rCol=0.4F;gCol=0.31F;bCol=0.23F;
    }
    @Override public Layer getLayer(){return Layer.TRANSLUCENT;}
    public static final class Provider implements ParticleProvider<SimpleParticleType>{
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites){this.sprites=sprites;}
        @Override public Particle createParticle(SimpleParticleType type,ClientLevel level,double x,double y,double z,double dx,double dy,double dz,RandomSource random){
            return new MudBubbleParticle(level,x,y,z,dx,dy,dz,sprites.get(random));
        }
    }
}
