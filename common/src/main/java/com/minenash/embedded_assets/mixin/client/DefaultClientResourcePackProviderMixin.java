package com.minenash.embedded_assets.mixin.client;

import com.minenash.embedded_assets.client.EmbeddedAssetsClient;
import net.minecraft.client.resource.DefaultClientResourcePackProvider;
import net.minecraft.resource.ResourcePackProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;
import java.util.function.Function;

@Mixin(DefaultClientResourcePackProvider.class)
public class DefaultClientResourcePackProviderMixin {

    @Inject(method = "forEachProfile", at = @At("RETURN"))
    private void addBuiltinResourcePacks(BiConsumer<String, Function<String, ResourcePackProfile>> consumer, CallbackInfo ci) {
        for (var pack : EmbeddedAssetsClient.packs)
            consumer.accept(pack.getName(), unused -> pack);
    }

}
