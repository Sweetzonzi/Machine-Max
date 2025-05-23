package io.github.sweetzonzi.machinemax.common.command

import cn.solarmoon.spark_core.command.BaseCommand

abstract class VehicleCommand(permissionLevel: Int): BaseCommand("vehicle", permissionLevel) {}