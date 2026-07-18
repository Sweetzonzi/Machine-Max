package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.client.render.renderer.PartAssemblyRenderer
import io.github.sweetzonzi.machine_max.client.render.renderer.LightSourceRenderer
import io.github.sweetzonzi.machine_max.client.render.renderer.ClientProjectileRenderer
import io.github.sweetzonzi.machine_max.client.render.renderer.DistantVehicleRenderer
import io.github.sweetzonzi.machine_max.client.render.renderer.VehicleInspectorRenderer

object MMVisualEffects {

    @JvmStatic
    val PART_ASSEMBLY = PartAssemblyRenderer()

    @JvmStatic
    val LIGHT_SOURCE = LightSourceRenderer()

    @JvmStatic
    val PROJECTILE = ClientProjectileRenderer()

    @JvmStatic
    val DISTANT_VEHICLE = DistantVehicleRenderer()

    @JvmStatic
    val VEHICLE_INSPECTOR = VehicleInspectorRenderer()

    @JvmStatic
    fun init() {
    }
}
