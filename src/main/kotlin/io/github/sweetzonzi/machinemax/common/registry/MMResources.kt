package io.github.sweetzonzi.machinemax.common.registry

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.resource.payload.registry.DynamicRegistrySyncS2CPacket
import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.common.vehicle.PartType
import net.minecraft.resources.ResourceLocation

object MMResources {
//    @JvmStatic
//    val PARTS = VirtualRegistry<PartType>(
//        ResourceLocation.fromNamespaceAndPath(SparkCore.MOD_ID, "parts")
//    ).apply {
//        this.onDynamicRegister = { key, value ->
//            try {
////                val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
////                if (server != null) {
////                    server.execute {
////                        try {
////                            val registryName = this.key().location().toString()
////                            val assignedId = DynamicIdManager.getId(registryName, key.location()) ?: -1
////                            val packet = DynamicRegistrySyncS2CPacket.createForTypedAnimationAdd(key.location().namespace, key.location(), value, assignedId)
////                            net.neoforged.neoforge.network.PacketDistributor.sendToAllPlayers(packet)
////                        } catch (_: Exception) {}
////                    }
////                }
//            } catch (_: Exception) {}
//        }
//        this.onDynamicUnregister = { key, _ ->
//            try {
////                val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
////                if (server != null) {
////                    server.execute {
////                        try {
////                            DynamicRegistrySyncS2CPacket.syncTypedAnimationRemovalToClients(key.location().namespace, key.location())
////                        } catch (_: Exception) {}
////                    }
////                }
//            } catch (_: Exception) {}
//        }
//    }
}