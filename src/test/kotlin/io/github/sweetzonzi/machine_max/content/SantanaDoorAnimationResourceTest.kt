package io.github.sweetzonzi.machine_max.content

import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import cn.solarmoon.spark_core.animation.state.origin.OAnimStateMachineSet
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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

        // 交互区只发 interact 一个频道，含义由对侧连接点解释：门轴那条到达车体后被转译成与控制器
        // 同名的 interact_<名> 并以 local 交给车体状态机；锁扣那条到达车体锁扣连接点时仍是 interact，
        // 直接交给锁扣子系统。事件名只出现在车体侧，可拆的门/盖零件因此不必知道自己在车上触发哪个控制器。
        val expectedBoxPart = mapOf(
            "interact.machine_max.left_front_door" to "santana_door",
            "interact.machine_max.right_front_door" to "santana_door",
            "interact.machine_max.left_back_door" to "santana_door",
            "interact.machine_max.right_back_door" to "santana_door",
            "interact.machine_max.hood" to "santana_hood",
            "interact.machine_max.trunk_door" to "santana_trunk_door"
        )
        val boxesByPart = listOf("santana_hull", "santana_door", "santana_hood", "santana_trunk_door")
            .associateWith { interactBoxesOf(it) }
        val wireTargets = setOf("connector.machine_max.axis", "connector.machine_max.lock")
        expectedBoxPart.forEach { (boxName, partName) ->
            val box = boxesByPart.getValue(partName)[boxName]
            assertNotNull(box, "$boxName 长在 $partName 上——交互区跟着可拆的门/盖零件走")
            val boxTargets = box!!.getAsJsonObject("signal_targets")
            assertEquals(setOf("interact"), boxTargets.keySet(), "$boxName 只发 interact，含义由对侧连接点解释")
            assertEquals(
                wireTargets,
                boxTargets.getAsJsonArray("interact").map { it.asString }.toSet(),
                "$boxName 的两条线：门轴传开合事件、锁扣去解扣"
            )
            assertEquals(
                "accurate",
                box.get("interact_mode").asString,
                "$boxName 用精确交互区，射线命中表面才触发"
            )
        }

        // 车体侧两张表各接一条：门轴连接点把 interact 转译成 interact_<名>，到达频道与控制器事件名逐字相同；
        // 锁扣连接点把 interact 直接指向锁扣子系统。两条缺一条，点击都会静默少掉一半动作。
        val hullConnectors = readJson(
            "/spark_modules/Machine-Max_Civilian/machine_max/parts/santana/santana_hull.json"
        ).asJsonObject.getAsJsonObject("variants")
            .getAsJsonObject("sub_parts")
            .getAsJsonObject("sub_part.machine_max.main")
            .getAsJsonObject("connectors")
            .entrySet().associate { it.key to it.value.asJsonObject }
        controllerSet.animationControllers.keys.forEach { name ->
            val event = "interact_$name"
            val hinges = hullConnectors.filter { (_, connector) ->
                connector.getAsJsonObject("signal_translations")?.get("interact")?.asString == event
            }
            assertEquals(1, hinges.size, "$event 应恰有一枚门轴连接点把 interact 转译成它")
            assertEquals(
                setOf("local"),
                hinges.values.single().getAsJsonObject("signal_targets")
                    .getAsJsonArray(event).map { it.asString }.toSet(),
                "$event 到达车体本地总线，交给车体自己的动画状态机"
            )

            val coupler = "subsystem.machine_max.${name}_coupler"
            val latches = hullConnectors.filter { (_, connector) ->
                connector.getAsJsonObject("signal_targets")
                    ?.getAsJsonArray("interact")?.map { it.asString } == listOf(coupler)
            }
            assertEquals(1, latches.size, "$event 的解扣线应恰有一枚锁扣连接点指向 $coupler")
            assertFalse(
                latches.values.single().has("signal_translations"),
                "锁扣连接点没有转译表：交互区发的到达频道就是 interact"
            )
        }
    }

    /** 一个部件文件里全部变体的交互区：名字 → 交互区对象；单变体与多变体两种写法都收。 */
    private fun interactBoxesOf(partName: String): Map<String, JsonObject> {
        val variants = readJson(
            "/spark_modules/Machine-Max_Civilian/machine_max/parts/santana/$partName.json"
        ).asJsonObject.getAsJsonObject("variants")
        val variantObjects =
            if (variants.has("sub_parts")) listOf(variants) else variants.entrySet().map { it.value.asJsonObject }
        return variantObjects.flatMap { variant ->
            variant.getAsJsonObject("sub_parts")
                ?.getAsJsonObject("sub_part.machine_max.main")
                ?.let { main -> main.getAsJsonObject("interact_boxes") }
                ?.entrySet()?.map { it.key to it.value.asJsonObject }.orEmpty()
        }.toMap()
    }

    private fun readJson(path: String): JsonElement =
        checkNotNull(javaClass.getResourceAsStream(path)) { "Missing resource $path" }
            .bufferedReader()
            .use(JsonParser::parseReader)
}
