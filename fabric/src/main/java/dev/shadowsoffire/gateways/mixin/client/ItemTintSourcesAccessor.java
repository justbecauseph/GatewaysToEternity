package dev.shadowsoffire.gateways.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs.LateBoundIdMapper;

@Mixin(ItemTintSources.class)
public interface ItemTintSourcesAccessor {

    @Accessor("ID_MAPPER")
    static LateBoundIdMapper<Identifier, MapCodec<? extends ItemTintSource>> gateways$getIdMapper() {
        throw new UnsupportedOperationException();
    }
}
