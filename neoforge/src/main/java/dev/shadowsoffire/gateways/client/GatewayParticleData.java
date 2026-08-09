package dev.shadowsoffire.gateways.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.gateways.GatewayObjects;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record GatewayParticleData(float red, float green, float blue) implements ParticleOptions {

    public GatewayParticleData(int r, int g, int b) {
        this(r / 255F, g / 255F, b / 255F);
    }

    @Override
    public ParticleType<GatewayParticleData> getType() {
        return GatewayObjects.GLOW.get();
    }

    public static final MapCodec<GatewayParticleData> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        Codec.FLOAT.fieldOf("r").forGetter(GatewayParticleData::red),
        Codec.FLOAT.fieldOf("g").forGetter(GatewayParticleData::green),
        Codec.FLOAT.fieldOf("b").forGetter(GatewayParticleData::blue))
        .apply(inst, GatewayParticleData::new));

    public static final StreamCodec<ByteBuf, GatewayParticleData> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, GatewayParticleData::red,
        ByteBufCodecs.FLOAT, GatewayParticleData::green,
        ByteBufCodecs.FLOAT, GatewayParticleData::blue,
        GatewayParticleData::new);

}
