package net.fabricmc.example.mixin;

import net.minecraft.resource.AbstractFileResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

@Mixin(AbstractFileResourcePack.class)
public interface AbstractFileResourcePackAccessor {

    @Accessor File getBase();

}
