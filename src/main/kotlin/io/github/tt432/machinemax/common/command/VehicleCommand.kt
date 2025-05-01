package io.github.tt432.machinemax.common.command

import cn.solarmoon.spark_core.command.BaseCommand

abstract class VehicleCommand(permissionLevel: Int): BaseCommand("vehicle", permissionLevel) {}