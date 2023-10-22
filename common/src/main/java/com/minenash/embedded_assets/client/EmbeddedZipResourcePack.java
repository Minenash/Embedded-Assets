package com.minenash.embedded_assets.client;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import net.minecraft.resource.AbstractFileResourcePack;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;
import net.minecraft.util.PathUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

public class EmbeddedZipResourcePack implements ResourcePack {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Splitter TYPE_NAMESPACE_SPLITTER = Splitter.on('/').omitEmptyStrings().limit(3);

    private final String name;
    private final Map<String,byte[]> data;

    public EmbeddedZipResourcePack(String name, Map<String,byte[]> data) {
        this.data = data;
        this.name = name;
    }

    @Nullable
    @Override
    public InputSupplier<InputStream> openRoot(String... segments) {
        PathUtil.validatePath(segments);

        var bytes = data.get(String.join("/", segments));

        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
        var bytes = data.get(getFilename(type, id));

        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
        String string = type.getDirectory() + "/" + namespace + "/";
        String string2 = string + prefix + "/";
        for (String name : data.keySet()) {
            if (name.endsWith("/") || name.endsWith(".mcmeta") || !name.startsWith(string2)) continue;
            String string4 = name.substring(string.length());
            Identifier identifier = Identifier.of(namespace, string4);
            if (identifier == null) {
                LOGGER.warn("Invalid path in datapack: {}:{}, ignoring", namespace, string4);
                continue;
            }

            consumer.accept(identifier, open(type, identifier));
        }
    }

    private static String getFilename(ResourceType type, Identifier id) {
        return String.format(Locale.ROOT, "%s/%s/%s", type.getDirectory(), id.getNamespace(), id.getPath());
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        HashSet<String> set = Sets.newHashSet();
        for (String name : data.keySet()) {
            ArrayList<String> list;
            if (!name.startsWith(type.getDirectory() + "/") || (list = Lists.newArrayList(TYPE_NAMESPACE_SPLITTER.split(name))).size() <= 1)
                continue;
            String namespace = list.get(1);
            if (namespace.equals(namespace.toLowerCase(Locale.ROOT))) {
                set.add(namespace);
                continue;
            }
            LOGGER.warn("ResourcePack: ignored non-lowercase namespace: {} in {}", namespace, name);
        }

        return set;
    }

    @Nullable
    @Override
    public <T> T parseMetadata(ResourceMetadataReader<T> metaReader) {
        return AbstractFileResourcePack.parseMetadata(metaReader, new ByteArrayInputStream(data.get("pack.mcmeta")));
    }


    @Override
    public String getName() {
        return name;
    }

    @Override
    public void close() {}

}
