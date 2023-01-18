package com.minenash.embedded_assets.mixin;

import net.minecraft.resource.ZipResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;
import java.util.zip.ZipFile;

@Mixin(ZipResourcePack.class)
public interface ZipResourcePackAccessor {
    @Accessor
    ZipFile getFile();

    @Accessor
    File getBackingZipFile();
}
