package io.github.sweetzonzi.machinemax.client.renderer;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.visual.PartProjection;
import io.github.sweetzonzi.machinemax.common.vehicle.visual.RenderableBoundingBox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * <p>做好与实际渲染器的隔离，避免服务端获取到仅客户端的对象，这里只作存储</p>
 * <p>This class is used to store objects that are only used for rendering, and to avoid server-side objects to get client-side objects.</p>
 */
public class VisualEffectHelper {
    public static PartProjection partToAssembly = null;
    public static ConcurrentMap<AbstractConnector, PhysicsRigidBody> attachPoints = new ConcurrentHashMap<>();
    public static ConcurrentMap<Object, RenderableBoundingBox> boundingBoxes = new ConcurrentHashMap<>();
}
