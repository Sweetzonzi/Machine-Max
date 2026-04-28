package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.client.render.renderer.PartAssemblyRenderer
import io.github.sweetzonzi.machine_max.client.render.renderer.LightSourceRenderer

object MMVisualEffects {

    @JvmStatic
    val PART_ASSEMBLY = PartAssemblyRenderer()

    @JvmStatic
    val LIGHT_SOURCE = LightSourceRenderer()

    @JvmStatic
    fun init() {
    }
}
