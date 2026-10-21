package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang

import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.animation.anim.origin.*
import cn.solarmoon.spark_core.animation.model.ModelIndex
import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.animation.model.origin.OModel
import cn.solarmoon.spark_core.animation.model.BonePose
import cn.solarmoon.spark_core.js.molang.JSMolangValue
import cn.solarmoon.spark_core.js.molang.Vector3js
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

class HudAnimationSamplingTest {
    @Test
    fun physicsPosePublishesOnClientTickAndInterpolatesAtRenderTime() {
        val index = ModelIndex("entity", ResourceLocation.parse("test:physics_hud"))
        val bone = OBone("needle", null, Vec3.ZERO, Vec3.ZERO, linkedMapOf(), listOf(), null)
        OModel.ORIGINS[index] = OModel(16, 16, linkedMapOf("needle" to bone))
        val vector = Vector3js(JSMolangValue("hud.view_heading_deg"), JSMolangValue("1"), JSMolangValue("1"))
        val scale = OKeyFrame(vector, vector, InterpolationType.LINEAR)
        val animation = OAnimation(Loop.TRUE, 10f, linkedMapOf(
            "needle" to OBoneAnimation(linkedMapOf(), linkedMapOf(), linkedMapOf(0f to scale))
        ), linkedMapOf())
        OAnimationSet.ORIGINS[index] = OAnimationSet(linkedMapOf("hud" to animation))
        val physics = Executors.newSingleThreadExecutor()
        try {
            val runtime = HudRuntimeElement(null, HudMolangContextTest.element())
            runtime.setModelIndex(index)
            runtime.modelController.setModel(index)
            val instance = AnimInstance(runtime, AnimIndex(index, "hud"))
            instance.inTransitionTime = 0f
            instance.enter()
            val pose = runtime.modelController.model!!.pose.bonePoseList.first()
            val mainContext = runtime.getMolangContext()
            // JUnit 无游戏 Level；通过内部写入口验证混合结果的跨线程发布。
            val updateInternal = BonePose::class.java.declaredMethods.single {
                it.name.startsWith("updateInternal")
            }.apply { isAccessible = true }
            fun physicsStep() = physics.submit {
                runtime.pinFrame().use {
                    assertNotSame(mainContext, runtime.getMolangContext())
                    runtime.animController.layers.values.forEach { it.physicsTick(1f) }
                    updateInternal.invoke(pose, runtime.animController.blendBone(pose.name))
                }
            }.get()

            runtime.publishView(HudViewSnapshot(0.0, 0.0, 0.0, 10.0, 0.0, 0.0, 70.0, 1.0, 800, 600))
            physicsStep()
            assertEquals(1.0, pose.getLocalScale(1f).x, 0.0001)
            runtime.animController.tick()
            assertEquals(10.0, pose.getLocalScale(1f).x, 0.0001)
            assertEquals(1.0, pose.getLocalScale(0f).x, 0.0001)
            assertEquals(5.5, pose.getLocalScale(0.5f).x, 0.0001)

            runtime.publishView(HudViewSnapshot(0.0, 0.0, 0.0, 20.0, 0.0, 0.0, 70.0, 1.0, 800, 600))
            assertEquals(10.0, pose.getLocalScale(1f).x, 0.0001)
            physicsStep()
            assertEquals(10.0, pose.getLocalScale(1f).x, 0.0001)
            runtime.animController.tick()
            assertEquals(20.0, pose.getLocalScale(1f).x, 0.0001)
            assertEquals(10.0, pose.getLocalScale(0f).x, 0.0001)
            assertEquals(15.0, pose.getLocalScale(0.5f).x, 0.0001)
        } finally {
            physics.shutdownNow()
            OModel.ORIGINS.remove(index)
            OAnimationSet.ORIGINS.remove(index)
        }
    }
}
