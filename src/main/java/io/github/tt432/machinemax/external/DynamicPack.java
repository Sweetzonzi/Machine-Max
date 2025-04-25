package io.github.tt432.machinemax.external;


import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Set;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

public class DynamicPack implements PackResources {
    private String content = "";
    private final String packRoot;
    private final ResourceLocation location;
    private final File file;
    private final ByteArrayInputStream inputStream;
    public DynamicPack(ResourceLocation location, String packRoot, File file) {
        this.location = location;
        this.packRoot = packRoot;
        this.file = file;
        this.inputStream = MMDynamicRes.fileStream(file);
        loadContent();
    }

    public void loadContent() {
        try {
            content = new String(Files.readAllBytes(getFile().toPath()));
        } catch (Exception ignored) {}
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        // 获取动态资源的输入流（核心方法）
        if (type == PackType.CLIENT_RESOURCES && location.equals(this.location))
            return () -> inputStream;
        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        // 列出所有动态资源（必须实现）
        if (type == PackType.CLIENT_RESOURCES && path.equals(packRoot))
            output.accept(location, () -> inputStream);
    }

    public File getFile() {
        return file;
    }
    public String getContent() {
        return content;
    }
    public InputStream getInputStream() {
        return inputStream;
    } //文件字符流
    @Override
    public String packId() {
        return String.valueOf(location);
    }    // 返回资源包的名称
    @Override
    public Set<String> getNamespaces(PackType type) {
        return Set.of(MOD_ID);
    }    // 定义该资源包管理的命名空间（例如你的模组ID）
    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... elements) {
        return null;
    }

    @Override
    public @Nullable <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return null;
    }
    @Override
    public void close() {}


}