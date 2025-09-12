package io.github.sweetzonzi.machine_max.common.command

import cn.solarmoon.spark_core.command.BaseCommand

abstract class VehicleCommand(permissionLevel: Int): BaseCommand("vehicle", permissionLevel) {}