package com.minenash.embedded_assets.mixin;

import java.io.File;
import java.nio.file.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.minenash.embedded_assets.AbstractFileResourcePackAccessor;

import net.minecraft.resource.ZipResourcePack;

@Mixin(ZipResourcePack.class)
public class ZipResourcePackMixin implements AbstractFileResourcePackAccessor {

    @Shadow
    private File backingZipFile;

    @Override
    public Path getPath() {
        return this.backingZipFile.toPath();
    }
}
