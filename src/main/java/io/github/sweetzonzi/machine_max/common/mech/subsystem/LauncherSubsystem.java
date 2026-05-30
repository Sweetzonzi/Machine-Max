package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.LauncherSubsystemAttr;
import jme3utilities.math.MyQuaternion;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发射器子系统。<br>
 * 代表炮闩、导弹挂架、火箭发射管等单个发射口。<br>
 * 从locator位置沿其朝向发射原版Arrow弹丸，初速受子系统属性影响。<br>
 * 对外提供 getMuzzleWorldTransform/getMuzzleWorldPosition/getMuzzleDirection 用于武器控制器瞄准判定。<br>
 * TODO: 弹药消耗逻辑、弹丸类型可配置
 */
public class LauncherSubsystem extends BasicSubsystem {

    public final LauncherSubsystemAttr attr;
    private int fireCooldown = 0;

    /** 基准初速 (m/s)，对应原版弓满弦箭矢速度 */
    private static final float BASE_SPEED_MPS = 50.0f;

    /** Arrow弹丸基础精度 (MIL)，硬编码占位值 */
    private static final float ARROW_BASE_ACCURACY_MIL = 5.0f;

    public LauncherSubsystem(ISubsystemHost owner, String name, LauncherSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        if (attr.locator == null || attr.locator.isEmpty()) {
            MachineMax.LOGGER.error("发射器子系统 {} 未配置发射点locator", name);
        }
    }

    @Override
    public void onTick() {
        super.onTick();
        if (fireCooldown > 0) {
            fireCooldown--;
        }

        if (!isActive() || isDestroyed()) return;

        if (isFiring() && fireCooldown == 0) {
            fire();
            fireCooldown = Math.max(0, calcFireInterval() - 1);
        }
    }

    /**
     * 检测开火信号：轮询配置的信号频道，任一频道有非EmptySignal即视为开火。
     */
    private boolean isFiring() {
        for (String signalKey : attr.staticAttribute.getControlInputs()) {
            SignalChannel channel = getSignalChannel(signalKey);
            if (!channel.isEmpty() && !(channel.getFirstSignal() instanceof EmptySignal)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算发射间隔（tick数）。
     * RPM → 1200 / RPM，最小1tick。
     */
    private int calcFireInterval() {
        return Math.max(1, (int) (1200f / attr.staticAttribute.getFireRate()));
    }

    /**
     * 执行一次发射：从locator位置生成Arrow并施加初速和散布。
     */
    private void fire() {
        Transform muzzleTransform = getMuzzleWorldTransform();
        Vector3f jmePos = muzzleTransform.getTranslation();
        Vec3 spawnPos = new Vec3(jmePos.x, jmePos.y, jmePos.z);

        // 从locator旋转获取发射方向（JME默认前方为-Z）
        Quaternion jmeRot = muzzleTransform.getRotation();
        Vector3f jmeForward = MyQuaternion.rotate(jmeRot, new Vector3f(0, 0, -1), null);
        Vec3 direction = new Vec3(jmeForward.x, jmeForward.y, jmeForward.z).normalize();

        // 计算最终初速 (m/s → blocks/tick)
        float speedMps = BASE_SPEED_MPS * attr.staticAttribute.getVelocityMultiplier() + attr.staticAttribute.getVelocityBonus();
        float speedBpt = speedMps / 20f;

        // 计算有效散布：弹丸基础精度 × 发射器各轴精度乘子 (MIL → 弧度)
        float hRad = ARROW_BASE_ACCURACY_MIL * attr.staticAttribute.getHorizontalAccuracyMultiplier() / 1000f;
        float vRad = ARROW_BASE_ACCURACY_MIL * attr.staticAttribute.getVerticalAccuracyMultiplier() / 1000f;
        Vec3 spreadDir = applyEllipticSpread(direction, hRad, vRad);

        // 生成弹丸并发射（仅服务端）
        if (!getLevel().isClientSide()) {
            Snowball snowball = new Snowball(getLevel(), spawnPos.x, spawnPos.y, spawnPos.z);
            snowball.shoot(spreadDir.x, spreadDir.y, spreadDir.z, speedBpt, 0f);

            // 继承发射平台速度 (m/s → blocks/tick)
            Vector3f platformVel = getSubPart().getLinearVelocity();
            Vec3 inheritVel = new Vec3(platformVel.x, platformVel.y, platformVel.z).scale(1.0 / 20.0);
            snowball.setDeltaMovement(snowball.getDeltaMovement().add(inheritVel));

            // TODO: 设置弹丸的发射者（从座舱玩家获取）
            getLevel().addFreshEntity(snowball);

            // 计算后坐力冲量并提交到物理线程
            float projectileMass = 10.0f; // TODO: 从弹丸类型配置获取
            float absorption = attr.staticAttribute.getRecoilAbsorption();
            float recoilImpulse = projectileMass * speedMps * (1.0f - absorption);
            if (recoilImpulse > 1e-6f) {
                Vector3f impulseWorld = new Vector3f(
                        (float) -direction.x * recoilImpulse,
                        (float) -direction.y * recoilImpulse,
                        (float) -direction.z * recoilImpulse
                );
                Vector3f muzzleWorldPos = jmePos.clone();
                getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
                    var body = getSubPart().getBody();
                    Vector3f bodyWorldPos = body.getPhysicsLocation(new Vector3f());
                    body.applyImpulse(impulseWorld, muzzleWorldPos.subtract(bodyWorldPos));
                    return null;
                });
            }
        }
    }

    /**
     * 获取发射点在世界空间中的位姿。
     */
    public Transform getMuzzleWorldTransform() {
        return getOwner().getSubPart().getLocatorWorldTransform(attr.locator);
    }

    /**
     * 获取发射点在世界空间中的位置。
     */
    public Vec3 getMuzzleWorldPosition() {
        Vector3f pos = getMuzzleWorldTransform().getTranslation();
        return new Vec3(pos.x, pos.y, pos.z);
    }

    /**
     * 获取发射方向在世界空间中的单位向量（JME前方为-Z方向）。
     */
    public Vec3 getMuzzleDirection() {
        Transform tf = getMuzzleWorldTransform();
        Quaternion rot = tf.getRotation();
        Vector3f forward = MyQuaternion.rotate(rot, new Vector3f(0, 0, -1), null);
        return new Vec3(forward.x, forward.y, forward.z).normalize();
    }

    /**
     * 判断发射器当前指向是否已对准目标。<br>
     * 使用发射点枪口位姿直接计算方向偏差，与炮塔关节转角反馈无关。<br>
     * 瞄准偏差角度 = arccos(dot(muzzleDir, toTarget))，单位度。
     *
     * @param target       目标世界坐标
     * @param toleranceDeg 容差角度（度），偏差小于此值时认为已瞄准
     * @return true 表示发射器已对准目标
     */
    public boolean isAimedAt(Vec3 target, float toleranceDeg) {
        Vec3 muzzlePos = getMuzzleWorldPosition();
        Vec3 toTarget = target.subtract(muzzlePos).normalize();
        Vec3 muzzleDir = getMuzzleDirection();
        // 计算两个单位向量的夹角
        double dot = toTarget.dot(muzzleDir);
        double angleRad = Math.acos(Math.clamp(dot, -1.0, 1.0));
        return Math.toDegrees(angleRad) <= toleranceDeg;
    }

    /**
     * 在水平/垂直方向分别应用椭圆锥散布。<br>
     * 沿direction方向构建局部正交基，水平方向散布hRad，垂直方向散布vRad。
     *
     * @param direction 原始发射方向（已归一化）
     * @param hRad      水平方向散布 (弧度)
     * @param vRad      垂直方向散布 (弧度)
     * @return 散布后的方向
     */
    private Vec3 applyEllipticSpread(Vec3 direction, float hRad, float vRad) {
        if (hRad <= 0f && vRad <= 0f) return direction;

        var random = getLevel().random;

        // 构建局部正交基：right(水平) × up(垂直) × forward(发射方向)
        Vec3 up;
        if (Math.abs(direction.y) < 0.99) {
            up = new Vec3(0, 1, 0);
        } else {
            up = new Vec3(1, 0, 0);
        }
        Vec3 right = direction.cross(up).normalize();
        Vec3 localUp = right.cross(direction).normalize();

        // 在水平/垂直椭圆锥内均匀采样
        double theta = random.nextDouble() * 2 * Math.PI;

        // 椭圆锥：水平半径 = hRad, 垂直半径 = vRad
        double hOffset = Math.cos(theta) * hRad;
        double vOffset = Math.sin(theta) * vRad;
        double radialDist = Math.sqrt(hOffset * hOffset + vOffset * vOffset);
        double cosRadial = Math.cos(radialDist);
        double sinRadial = Math.sin(radialDist);

        // 如果散布极小（趋近0），跳过避免除零
        if (radialDist < 1e-10) return direction;

        // 在椭圆锥内旋转原方向
        return direction.scale(cosRadial)
                .add(right.scale((float)(sinRadial * hOffset / radialDist)))
                .add(localUp.scale((float)(sinRadial * vOffset / radialDist)))
                .normalize();
    }

    @Override
    public List<String> getAcceptedChannels() {
        return attr.staticAttribute.getControlInputs();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(2);
        result.putAll(attr.ammoCountOutputs);
        return result;
    }
}
