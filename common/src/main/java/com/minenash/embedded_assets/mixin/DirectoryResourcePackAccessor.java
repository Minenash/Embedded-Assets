package com.minenash.embedded_assets.mixin;

import net.minecraft.resource.DirectoryResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

@Mixin(DirectoryResourcePack.class)
public interface DirectoryResourcePackAccessor {
    @Accessor
    Path getRoot();
}
