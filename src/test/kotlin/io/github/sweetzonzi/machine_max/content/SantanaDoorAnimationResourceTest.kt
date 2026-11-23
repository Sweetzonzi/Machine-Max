package io.github.sweetzonzi.machine_max.content

import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.state.origin.OAnimStateMachineSet
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SantanaDoorAnimationResourceTest {
    @Test
    fun `controller resources decode and match the part animation set`() {
        val controllerSet = OAnimStateMachineSet.CODEC.parse(
            JsonOps.INSTANCE,
            readJson("/spark_modules/Machine-Max_Civilian/machine_max/controllers/part/santana.geo/santana.animation_controllers.json")
        ).result().orElseThrow()
        val animationSet = OAnimationSet.CODEC.parse(
            JsonOps.INSTANCE,
            readJson("/spark_modules/Machine-Max_Civilian/machine_max/animations/part/santana.geo/santana.animation.json")
        ).result().orElseThrow()
        val part = readJson(
            "/spark_modules/Machine-Max_Civilian/machine_max/parts/santana/santana_hull.json"
        ).asJsonObject.getAsJsonObject("variants")

        assertEquals("machine_max:santana.geo", part.get("model").asString)
        assertEquals("machine_max:santana.geo", part.get("animations").asString)
        assertEquals(
            setOf(
                "left_front_door", "right_front_door", "left_back_door", "right_back_door",
                "hood", "trunk_door"
            ),
            controllerSet.animationControllers.keys
        )

        controllerSet.animationControllers.forEach { (name, machine) ->
            assertEquals("default", machine.initialState)
            val transitions = machine.states.values.flatMap { it.transitions }
            assertEquals(2, transitions.size)
            assertTrue(transitions.all { it.second.value == "event.interact_$name" })

            val referencedAnimations = machine.states.values.flatMap { it.animations.keys }
            assertTrue(referencedAnimations.isNotEmpty())
            assertTrue(referencedAnimations.all(animationSet.animations::containsKey))
        }

        // 控制器只在所属零件收到同名一次性事件时才切换：交互区长在被操作的那块零件上——
        // 四扇车门的交互区在车门零件、引擎盖与后备箱盖的在各自盖板零件——事件因此投给 global，
        // 由装配体广播回全车，车体上的状态机才能切换并驱动铰链。
        val targetsByChannel = mutableMapOf<String, Set<String>>()
        val boxesByChannel = mutableMapOf<String, JsonObject>()
        val partByChannel = mutableMapOf<String, String>()
        listOf("santana_hull", "santana_door", "santana_hood", "santana_trunk_door").forEach { partName ->
            interactBoxesOf(partName).forEach { boxObject ->
                boxObject.getAsJsonObject("signal_targets").entrySet().forEach { (channel, targets) ->
                    targetsByChannel[channel] = targets.asJsonArray.map { it.asString }.toSet()
                    boxesByChannel[channel] = boxObject
                    partByChannel[channel] = partName
                }
            }
        }
        controllerSet.animationControllers.keys.forEach { name ->
            assertTrue(
                targetsByChannel.containsKey("interact_$name"),
                "控制器 $name 需要一条 interact_$name 交互区"
            )
        }
        listOf("left_front_door", "right_front_door", "left_back_door", "right_back_door").forEach { name ->
            val channel = "interact_$name"
            assertEquals("santana_door", partByChannel[channel], "车门交互区随车门零件一起拆走，长在车门上")
            assertEquals(setOf("global"), targetsByChannel[channel], "$channel 在车门上，事件投给 global")
            assertEquals(
                "accurate",
                boxesByChannel.getValue(channel).get("interact_mode").asString,
                "$channel 用精确交互区，射线命中车门表面才触发"
            )
        }
        listOf("interact_hood" to "santana_hood", "interact_trunk_door" to "santana_trunk_door").forEach { (channel, partName) ->
            assertEquals(partName, partByChannel[channel], "$channel 长在 $partName 上")
            assertEquals(setOf("global"), targetsByChannel[channel], "$channel 在盖板上，事件投给 global")
            assertEquals(
                "accurate",
                boxesByChannel.getValue(channel).get("interact_mode").asString,
                "$channel 用精确交互区，射线命中盖板表面才触发"
            )
        }
    }

    /** 一个部件文件里全部变体的交互区对象；单变体与多变体两种写法都收。 */
    private fun interactBoxesOf(partName: String): List<JsonObject> {
        val variants = readJson(
            "/spark_modules/Machine-Max_Civilian/machine_max/parts/santana/$partName.json"
        ).asJsonObject.getAsJsonObject("variants")
        val variantObjects =
            if (variants.has("sub_parts")) listOf(variants) else variants.entrySet().map { it.value.asJsonObject }
        return variantObjects.mapNotNull { variant ->
            variant.getAsJsonObject("sub_parts")
                ?.getAsJsonObject("sub_part.machine_max.main")
                ?.getAsJsonObject("interact_boxes")
        }.flatMap { boxes -> boxes.entrySet().map { it.value.asJsonObject } }
    }

    private fun readJson(path: String): JsonElement =
        checkNotNull(javaClass.getResourceAsStream(path)) { "Missing resource $path" }
            .bufferedReader()
            .use(JsonParser::parseReader)
}
