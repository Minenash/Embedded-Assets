package com.minenash.embedded_assets.mixin.client;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.minenash.embedded_assets.client.EmbeddedAssetsClient;

import net.minecraft.client.resource.DefaultClientResourcePackProvider;
import net.minecraft.resource.ResourcePackProfile;

@Mixin(DefaultClientResourcePackProvider.class)
public class ClientBuiltinResourcePackProviderMixin {

    @Inject(method = "register", at = @At("RETURN"))
    private void addBuiltinResourcePacks(Consumer<ResourcePackProfile> profileAdder, CallbackInfo ci) {
        for (var pack : EmbeddedAssetsClient.packs)
            profileAdder.accept(pack);
    }

}
