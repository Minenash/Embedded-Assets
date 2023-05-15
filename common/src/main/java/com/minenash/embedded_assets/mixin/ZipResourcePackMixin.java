package com.minenash.embedded_assets.mixin;

import java.io.File;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.minenash.embedded_assets.AbstractFileResourcePackAccessor;

import net.minecraft.resource.ZipResourcePack;

@Mixin(ZipResourcePack.class)
public class ZipResourcePackMixin implements AbstractFileResourcePackAccessor {

    @Shadow
    private File backingZipFile;

    @Override
    public File getBase() {
        return this.backingZipFile;
    }
}
