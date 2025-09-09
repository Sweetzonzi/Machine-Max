package io.github.sweetzonzi.machinemax.common.resource.modules

import cn.solarmoon.spark_core.resource2.graph.SparkPackage
import cn.solarmoon.spark_core.resource2.modules.SparkPackModule
import com.mojang.blaze3d.platform.NativeImage
import io.github.sweetzonzi.machinemax.MachineMax
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import net.neoforged.fml.loading.FMLEnvironment

class TextureModule : SparkPackModule {

    override val id: String = "textures"
    override fun onStart() {

    }

    override fun read(
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage
    ) {
        if (FMLEnvironment.dist.isClient && fileName.endsWith(".png")) {
            val nameSpace : String = if (pathSegments.size > 1) {
                pathSegments[0]
            } else {
                MachineMax.MOD_ID
            }
            val path: String = if (pathSegments.size >= 2) {
                "$id/${pathSegments.subList(1, pathSegments.size).joinToString("/")}/$fileName"
            } else {
                "$id/$fileName"
            }
            val image = NativeImage.read(content)
            val texture = DynamicTexture(image)
            Minecraft.getInstance().textureManager.register(
                ResourceLocation.fromNamespaceAndPath(nameSpace, path), texture
            )
        }
    }


    override fun onFinish() {

    }

}