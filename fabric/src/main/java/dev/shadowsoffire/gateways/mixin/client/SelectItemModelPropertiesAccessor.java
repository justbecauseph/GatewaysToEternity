package dev.shadowsoffire.gateways.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs.LateBoundIdMapper;

@Mixin(SelectItemModelProperties.class)
public interface SelectItemModelPropertiesAccessor {

    @Accessor("ID_MAPPER")
    static LateBoundIdMapper<Identifier, SelectItemModelProperty.Type<?, ?>> gateways$getIdMapper() {
        throw new UnsupportedOperationException();
    }
}
