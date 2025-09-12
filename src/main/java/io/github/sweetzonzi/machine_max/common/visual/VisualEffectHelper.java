package io.github.sweetzonzi.machine_max.common.visual;

import com.jme3.bullet.objects.PhysicsRigidBody;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <p>做好与实际渲染器的隔离，避免服务端获取到仅客户端的对象，这里只作存储</p>
 * <p>This class is used to store objects that are only used for rendering, and to avoid server-side objects to get client-side objects.</p>
 */
public class VisualEffectHelper {
    public static AnimatableParams partToPlace = null;
    public static RenderableBoundingBox boundingBox = null;
    public static ConcurrentMap<AbstractConnector, PhysicsRigidBody> attachPoints = new ConcurrentHashMap<>();
}
