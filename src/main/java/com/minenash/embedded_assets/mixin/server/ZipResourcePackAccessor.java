package com.minenash.embedded_assets.mixin.server;

import net.minecraft.resource.ZipResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.zip.ZipFile;

@Mixin(ZipResourcePack.class)
public interface ZipResourcePackAccessor {

    @Invoker("getZipFile") ZipFile invokeGetZipFile();

}
