package com.minenash.embedded_assets.mixin;

import java.nio.file.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.minenash.embedded_assets.AbstractFileResourcePackAccessor;

import net.minecraft.resource.DirectoryResourcePack;

@Mixin(DirectoryResourcePack.class)
public class DirectoryResourcePackMixin implements AbstractFileResourcePackAccessor {

    @Shadow
    private Path root;

    @Override
    public Path getPath() {
        return this.root;
    }
}