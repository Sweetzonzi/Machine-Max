package io.github.sweetzonzi.machine_max.common.visual;

import com.jme3.bullet.objects.PhysicsRigidBody;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LightingSubsystem;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <p>做好与实际渲染器的隔离，避免服务端获取到仅客户端的对象，这里只作存储</p>
 * <p>This class is used to store objects that are only used for rendering, and to avoid server-side objects to get client-side objects.</p>
 */
public class VisualEffectHelper {
    public static PartAnimatable partToPlace = null;
    public static RenderableBoundingBox boundingBox = null;
    public static ConcurrentMap<AbstractConnector, PhysicsRigidBody> attachPoints = new ConcurrentHashMap<>();
    public static Set<LightingSubsystem> lightingSubsystems = ConcurrentHashMap.newKeySet();
    /**
     * 载具蓝图/装配体放置预览的3D投影动画体，用于显示即将放置的载具模型。
     * <p>与{@link #boundingBox}配合使用，提供更直观的放置预览。</p>
     */
    public static VehicleAnimatable vehicleProjection = null;

    // ---- 后处理特效状态值（服务端子系统写入，客户端渲染只读） ----

    /** 失色程度 [0, 1]，0=全彩，1=全灰度，由伤害/环境系统写入 */
    public static volatile float desaturationLevel = 0f;

    /** 过载程度 [-1, 1]，正=黑视，负=红视，由子系统过载状态写入 */
    public static volatile float overloadLevel = 0f;
}
