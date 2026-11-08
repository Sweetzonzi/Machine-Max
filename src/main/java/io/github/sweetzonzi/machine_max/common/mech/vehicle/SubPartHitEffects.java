package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import cn.solarmoon.spark_core.api.ParticleEffects;
import cn.solarmoon.spark_core.util.SparkMathKt;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MaterialAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLink;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 零件命中的一次性表现：受击音效 + 命中火花，只在客户端执行。
 *
 * <p>音效与火花共用同一个判据（{@code SubPart#isArmorPenetrated}）与同一个命中点，
 * 因此不可能出现"音效说击穿、火花说没击穿"这类错配。两者都取自<b>材质</b>：
 * 音效走 {@code sounds}，火花走 {@code particles} 里的 {@code hit_pen} / {@code hit_un_pen}，
 * 碰撞箱可以在 {@code overwrite} 里覆写。履带装甲层吸收的命中整套改取履带型号的材质，
 * 与"吸收这件事发生在履带上"一致。</p>
 *
 * <p><b>调用线程：</b>客户端主线程（由命中表现包的处理器调用）。</p>
 *
 * @see io.github.sweetzonzi.machine_max.network.payload.SubPartHitEffectPayload
 */
public final class SubPartHitEffects {

    /** 碰撞箱命中音效的播放音量，与原先服务端 {@code playSound} 的取值一致。 */
    private static final float HIT_BOX_VOLUME = 0.5f;
    /** 履带吸收音效的播放音量，与原先服务端 {@code playSound} 的取值一致。 */
    private static final float TRACK_VOLUME = 1.0f;

    private SubPartHitEffects() {
    }

    /**
     * 播放一次命中表现。
     *
     * <p>非客户端调用直接返回：服务端不播表现，表现一律由客户端按同一份内容包配置重建。</p>
     *
     * @param level         维度（客户端）
     * @param subPart       被命中的子部件
     * @param hitBox        命中的碰撞箱
     * @param offset        命中点相对子部件呈现位置的偏移（世界轴）
     * @param sprayDir      喷射轴（击穿为弹体穿出方向，未击穿与跳弹为命中面法线）
     * @param penetrated    是否击穿
     * @param trackAbsorbed 是否被履带装甲层吸收
     */
    public static void play(Level level, SubPart subPart, HitBox hitBox,
                            Vec3 offset, Vec3 sprayDir,
                            boolean penetrated, boolean trackAbsorbed) {
        if (!level.isClientSide()) return;

        Vec3 pos = subPart.getPresentationPosition().add(offset);

        MaterialAttr material = resolveMaterial(subPart, hitBox, trackAbsorbed);
        MaterialAttr.MaterialSoundAttr sounds = material.sounds();
        SoundEvent sound = penetrated ? sounds.onHitPen() : sounds.onHitUnPen();
        level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.NEUTRAL,
                trackAbsorbed ? TRACK_VOLUME : HIT_BOX_VOLUME, 1.0f, false);

        MaterialAttr.MaterialParticleAttr particles = material.particles();
        ResourceLocation particle = penetrated ? particles.hitPen() : particles.hitUnPen();
        if (particle != null) {
            ParticleEffects.burst(level, particle, pos, SparkMathKt.toQuaternionf(ViewMath.axisAlong(sprayDir)));
        }
    }

    /**
     * 取本次命中的材质：履带吸收时用履带型号解析出来的，其余用碰撞箱的（材质 + 覆写）。
     *
     * <p>履带在本机解析不到（型号缺失导致履带没建起来）时回退到碰撞箱材质——两者都描述
     * "打中了什么"，且失配只影响本机表现。</p>
     */
    private static MaterialAttr resolveMaterial(SubPart subPart, HitBox hitBox, boolean trackAbsorbed) {
        if (trackAbsorbed) {
            TrackLink track = subPart.trackLink;
            if (track != null) return track.attr().material();
        }
        return hitBox.getAttr().getEffectiveMaterial();
    }
}
