package io.github.sweetzonzi.machine_max.common.gametest;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.part.PartFactory;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * K17 炮塔四根导弹管的命中框在运行期闭合：零件能按模型建出碰撞形状，每根管的命中框指向
 * 同一个子部件里的发射器子系统，且命中框条件表达式可求值。
 *
 * <p>内容包 JSON 里命中框的键是模型骨骼名：名字拼错或骨骼不在该子部件的区间内，建形状时才会
 * 抛错；子系统名拼错则在装载期就会被 {@code HitBoxAttr#validate} 拦下。纯 JUnit 只能检查
 * JSON 与 .geo 的文本一致性，建形状与条件编译这一步只能在 GameTest 里跑。</p>
 */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class K17TurretGameTest {
    private static final String BATCH = "mmK17Turret";

    private record Tube(String subPart, String bone, String subsystem) {
    }

    private static final List<Tube> TUBES = List.of(
            new Tube("sub_part.machine_max.left_atgm", "LeftAtgm1HitBox", "subsystem.machine_max.left_atgm_tube_1"),
            new Tube("sub_part.machine_max.left_atgm", "LeftAtgm2HitBox", "subsystem.machine_max.left_atgm_tube_2"),
            new Tube("sub_part.machine_max.right_atgm", "RightAtgm1HitBox", "subsystem.machine_max.right_atgm_tube_1"),
            new Tube("sub_part.machine_max.right_atgm", "RightAtgm2HitBox", "subsystem.machine_max.right_atgm_tube_2"));

    private K17TurretGameTest() {
    }

    @GameTest(template = "part_foundation", batch = BATCH, timeoutTicks = 100)
    public static void fourAtgmTubesBindTheirOwnLauncher(GameTestHelper helper) {
        RegularPartType type = PartDefinitions.regular(false).get(ResourceLocation.parse("machine_max:k17_turret"));
        helper.assertTrue(type != null, "K17 炮塔的部件定义必须已加载");
        // K17 的部件 JSON 用单变体写法（variants 直接是一个配置），解码后的变体名是 "default"。
        RegularPart part = PartFactory.createRegular(type, "default", helper.getLevel());
        try {
            for (Tube tube : TUBES) {
                SubPart host = part.subParts.get(tube.subPart());
                helper.assertTrue(host != null, "子部件必须存在：" + tube.subPart());
                AbstractSubsystem subsystem = host.subsystems.get(tube.subsystem());
                helper.assertTrue(subsystem != null, "发射器子系统必须存在：" + tube.subsystem());

                HitBox hitBox = host.hitBoxes.get(tube.bone());
                helper.assertTrue(hitBox != null, "命中框必须按骨骼名建出来：" + tube.bone());
                helper.assertTrue(hitBox.getSubsystem() == subsystem,
                        "命中框必须接在同一个子部件里的 " + tube.subsystem() + " 上");

                // 条件表达式在 HitBox 构造时编译、每物理刻求值；这里主动求一次，编译或求值失败都会抛出来。
                hitBox.updateActive();
                helper.assertTrue(hitBox.isActive(), "发射器未摧毁时命中框应生效：" + tube.bone());

                // 打掉这根管：命中框应停止拦截，否则残骸会变成一个吸收伤害的空框。
                subsystem.setDurability(0f);
                subsystem.onTick(); // 判毁边沿
                helper.assertTrue(subsystem.isDestroyed(), "零耐久的发射器应判毁：" + tube.subsystem());
                hitBox.updateActive();
                helper.assertTrue(!hitBox.isActive(), "发射器摧毁后命中框应停用：" + tube.bone());
            }
            helper.succeed();
        } finally {
            part.discardUnattached();
        }
    }
}
