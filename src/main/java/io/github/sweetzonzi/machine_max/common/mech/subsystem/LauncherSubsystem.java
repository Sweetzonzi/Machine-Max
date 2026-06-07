package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.LauncherSubsystemAttr;
import jme3utilities.math.MyQuaternion;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 发射器子系统。<br>
 * 代表炮闩、导弹挂架、火箭发射管等单个发射口。<br>
 * 从locator位置沿其朝向发射数据驱动的投射物，初速和精度受子系统属性与投射物类型共同影响。<br>
 * 实现 {@link IAmmoConsumer} 接口以支持弹药消耗与供给，膛内弹药状态由 chamberedType 管理。<br>
 * 投射物类型由当前供给者提供，而非静态属性直接指定。
 */
public class LauncherSubsystem extends BasicSubsystem implements IAmmoConsumer {

    public final LauncherSubsystemAttr attr;

    /** 发射冷却（tick） */
    private int fireCooldown = 0;

    // ——— 弹药状态 ———

    /** 膛内当前弹药类型。null = 空膛 */
    @Nullable
    private ProjectileType chamberedType;

    /** 当前选中的供给来源索引 */
    private int selectedSupplierIndex = 0;

    /** 由供给者通过 {@link #addSupplier(IAmmoSupplier)} 填充的供给者列表 */
    private final List<IAmmoSupplier> suppliers = new ArrayList<>();

    /** 当前是否正在等待装填（requestRound 已调用但弹药未就绪） */
    private boolean reloading = false;

    public LauncherSubsystem(ISubsystemHost owner, String name, LauncherSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        if (attr.locator == null || attr.locator.isEmpty()) {
            MachineMax.LOGGER.error("发射器子系统 {} 未配置发射点locator", name);
        }
    }

    // ——— IAmmoConsumer 实现 ———

    @Override
    public boolean canAcceptAmmo() {
        return chamberedType == null || isActive();
    }

    @Override
    public int getFreeCapacity() {
        return chamberedType == null ? 1 : 0;
    }

    @Override
    public boolean receiveAmmo(ProjectileType type) {
        if (chamberedType == null && isActive()) {
            chamberedType = type;
            reloading = false;
            return true;
        }
        return false;
    }

    @Override
    @Nullable
    public IAmmoSupplier getCurrentSupplier() {
        if (suppliers.isEmpty()) return null;
        if (selectedSupplierIndex < 0 || selectedSupplierIndex >= suppliers.size()) return null;
        return suppliers.get(selectedSupplierIndex);
    }

    @Override
    public List<IAmmoSupplier> getSuppliers() {
        return suppliers;
    }

    @Override
    public void setCurrentSupplier(int index) {
        if (index >= 0 && index < suppliers.size()) {
            selectedSupplierIndex = index;
        }
    }

    @Override
    public void addSupplier(IAmmoSupplier supplier) {
        suppliers.add(supplier);
        if (suppliers.size() == 1) {
            selectedSupplierIndex = 0;
        }
    }

    @Override
    public boolean canAccept(ProjectileType type) {
        return attr.staticAttribute.isAmmoCompatible(type);
    }

    /**
     * 直接装填一发弹药到膛内。<br>
     * 由外部调用（如 AmmoLoader 直接压弹），绕过 requestRound 流程。
     *
     * @return true 表示装填成功
     */
    public boolean loadRound(ProjectileType type) {
        if (chamberedType != null || !isActive()) return false;
        if (!canAccept(type)) return false;
        chamberedType = type;
        reloading = false;
        return true;
    }

    /**
     * 退膛：返回膛内弹药并清空。<br>
     * 若当前供给者可接收退弹则归还，否则返回 null（后续可扩展为生成 ItemEntity）。
     *
     * @return 退出的弹药类型，空膛返回 null
     */
    @Nullable
    public ProjectileType ejectRound() {
        ProjectileType round = chamberedType;
        if (round == null) return null;
        chamberedType = null;
        reloading = false;

        // 尝试归还给当前供给者
        IAmmoSupplier supplier = getCurrentSupplier();
        if (supplier != null && supplier.canEject()) {
            supplier.returnRound(round);
        }
        // TODO: 若无法归还，生成 ItemEntity 掉落于发射器 locator 位置
        return round;
    }

    // ——— 发射逻辑 ———

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
     * 执行一次发射。<br>
     * 使用膛内弹药 chamberedType 发射，弹药不足时从当前供给者取弹。
     * 流程参见设计文档 §5.2：取弹 → 兼容性校验 → 装膛 → 发射 → 清膛。
     */
    private void fire() {
        // ① 如果膛内无弹药，尝试从当前供给者取弹
        if (chamberedType == null) {
            IAmmoSupplier supplier = getCurrentSupplier();
            if (supplier == null) return;

            if (supplier.isRoundReady(this)) {
                // 弹药已就绪，取弹
                ProjectileType offered = supplier.consumeReadyRound(this);
                if (offered != null && canAccept(offered)) {
                    // 弹药兼容，装膛
                    chamberedType = offered;
                    reloading = false;
                } else {
                    // 弹药不兼容 → 归还后处理
                    if (offered != null && supplier.canEject()) {
                        supplier.returnRound(offered);
                    }
                    handleIncompatibleAmmo(supplier);
                    return;
                }
            } else if (!reloading) {
                // 弹药尚未就绪且未在装填中 → 发起请求
                supplier.requestRound(this);
                reloading = true;
                return;
            } else {
                // 装填中，等待下一 tick
                return;
            }
        }

        // ② 发射膛内弹药
        ProjectileType type = chamberedType;
        Transform muzzleTransform = getMuzzleWorldTransform();
        Vector3f jmePos = muzzleTransform.getTranslation();

        // 从locator旋转获取发射方向（JME默认前方为-Z）
        Quaternion jmeRot = muzzleTransform.getRotation();
        Vector3f jmeForward = MyQuaternion.rotate(jmeRot, new Vector3f(0, 0, -1), null);
        Vec3 direction = new Vec3(jmeForward.x, jmeForward.y, jmeForward.z).normalize();

        // 计算最终初速：弹丸基准初速 × 发射器初速乘子 + 发射器初速加成 (m/s)
        float baseVel = type.getBaseVelocity();
        float finalSpeedMps = baseVel * attr.staticAttribute.getVelocityMultiplier() + attr.staticAttribute.getVelocityBonus();

        // 计算有效散布：弹丸基础精度 × 发射器各轴精度乘子 (MIL → 弧度)
        float baseMil = type.getBaseAccuracyMil();
        float hRad = baseMil * attr.staticAttribute.getHorizontalAccuracyMultiplier() / 1000f;
        float vRad = baseMil * attr.staticAttribute.getVerticalAccuracyMultiplier() / 1000f;
        Vec3 spreadDir = applyEllipticSpread(direction, hRad, vRad);

        // 生成投射物并发射（仅服务端）
        if (!getLevel().isClientSide()) {
            // 构建 JME 速度矢量：散布方向 × 最终速率 (m/s)
            Vector3f jmeVel = new Vector3f((float) spreadDir.x, (float) spreadDir.y, (float) spreadDir.z)
                    .multLocal(finalSpeedMps);

            // 继承发射平台速度 (m/s)，速度已在 JME 空间，直接相加
            Vector3f platformVel = getSubPart().getLinearVelocity();
            jmeVel.addLocal(platformVel);

            // 由 ProjectileType 创建投射物
            type.create(getLevel(), jmePos, jmeVel);

            // 计算后坐力冲量并提交到物理线程
            float projectileMass = type.getMass();
            float absorption = attr.staticAttribute.getRecoilAbsorption();
            float recoilImpulse = projectileMass * finalSpeedMps * (1.0f - absorption);
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

        // ③ 发射后清膛
        chamberedType = null;
    }

    /**
     * 处理不兼容弹药的情况。<br>
     * 多供给者时切换到下一个供给者重新请求；仅一个供给者时从同一供给者取下一发。
     */
    private void handleIncompatibleAmmo(IAmmoSupplier supplier) {
        if (suppliers.size() > 1) {
            // 有多个供给者 → 切换到下一个
            selectedSupplierIndex = (selectedSupplierIndex + 1) % suppliers.size();
            reloading = false;
            // 向新供给者请求
            IAmmoSupplier next = getCurrentSupplier();
            if (next != null) {
                next.requestRound(this);
                reloading = true;
            }
        } else {
            // 仅一个供给者 → 继续从同一供给者取下一发
            reloading = false;
            supplier.requestRound(this);
            reloading = true;
        }
    }

    // ——— 公共查询方法 ———

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
     * 判断发射器当前指向是否已对准目标。
     *
     * @param target       目标世界坐标
     * @param toleranceDeg 容差角度（度）
     * @return true 表示发射器已对准目标
     */
    public boolean isAimedAt(Vec3 target, float toleranceDeg) {
        Vec3 muzzlePos = getMuzzleWorldPosition();
        Vec3 toTarget = target.subtract(muzzlePos).normalize();
        Vec3 muzzleDir = getMuzzleDirection();
        double dot = toTarget.dot(muzzleDir);
        double angleRad = Math.acos(Math.clamp(dot, -1.0, 1.0));
        return Math.toDegrees(angleRad) <= toleranceDeg;
    }

    /**
     * 在水平/垂直方向分别应用椭圆锥散布。
     */
    private Vec3 applyEllipticSpread(Vec3 direction, float hRad, float vRad) {
        if (hRad <= 0f && vRad <= 0f) return direction;

        var random = getLevel().random;

        Vec3 up;
        if (Math.abs(direction.y) < 0.99) {
            up = new Vec3(0, 1, 0);
        } else {
            up = new Vec3(1, 0, 0);
        }
        Vec3 right = direction.cross(up).normalize();
        Vec3 localUp = right.cross(direction).normalize();

        double theta = random.nextDouble() * 2 * Math.PI;

        double hOffset = Math.cos(theta) * hRad;
        double vOffset = Math.sin(theta) * vRad;
        double radialDist = Math.sqrt(hOffset * hOffset + vOffset * vOffset);
        double cosRadial = Math.cos(radialDist);
        double sinRadial = Math.sin(radialDist);

        if (radialDist < 1e-10) return direction;

        return direction.scale(cosRadial)
                .add(right.scale((float)(sinRadial * hOffset / radialDist)))
                .add(localUp.scale((float)(sinRadial * vOffset / radialDist)))
                .normalize();
    }

    @Override
    public List<String> getAcceptedChannels() {
        List<String> channels = new ArrayList<>(attr.staticAttribute.getControlInputs());
        channels.addAll(attr.staticAttribute.getAmmoInputs());
        return channels;
    }

    @Override
    public boolean acceptAllBroadcastInput() {
        return acceptAllRoutingInput();
    }

    @Override
    public boolean acceptAllRoutingInput() {
        return attr.staticAttribute.getControlInputs().isEmpty() || attr.staticAttribute.getAmmoInputs().isEmpty();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(2);
        result.putAll(attr.ammoCountOutputs);
        return result;
    }
}
