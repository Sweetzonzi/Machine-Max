package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang

import cn.solarmoon.spark_core.animation.anim.origin.OAnimation
import cn.solarmoon.spark_core.molang.MolangContextRegistry

object HudAnimationExpressions {
    @JvmStatic
    fun validate(animation: OAnimation, context: HudMolangContext) {
        animation.bones.values.forEach { bone ->
            listOf(bone.rotation, bone.position, bone.scale).forEach { keys ->
                keys.values.forEach { key ->
                    listOf(key.pre, key.post).forEach { vector ->
                        listOf(vector.x, vector.y, vector.z).forEach { expression ->
                            HudExpression.validate(expression.value, context, null)
                            MolangContextRegistry.compile(expression.value, context)
                        }
                    }
                }
            }
        }
        animation.timeline.values.forEach { values ->
            val expression = values.joinToString("") { it.value }
            HudExpression.validate(expression, context, null)
            MolangContextRegistry.compile(expression, context)
        }
    }
}
