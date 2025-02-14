package io.github.tt432.machinemax.common.vehicle;

import cn.solarmoon.spark_core.SparkCore;
import cn.solarmoon.spark_core.physics.host.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.attr.SubPartAttr;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class SubPart implements PhysicsHost {
    public String name;
    public final Part part;
    public SubPart parent;
    public final HashMap<String, AbstractConnector> connectors = HashMap.newHashMap(1);
    public final PhysicsRigidBody body;
    //TODO:建Shape在SubPartAttr中进行，节约内存
    CompoundCollisionShape collisionShape = new CompoundCollisionShape(1);
    public final String material;
    public final float thickness;

    public SubPart(String name, Part part, SubPartAttr attr) {
        this.part = part;
        this.name = name;
        this.body = new PhysicsRigidBody(name, this, collisionShape, attr.mass());
        this.material = attr.material();
        this.thickness = attr.thickness();
    }

    public void addToLevel() {
        this.bindBody(body, part.level.getPhysicsLevel(), true,
                (body -> {
//                    body.addContactListener();//TODO:写接触规则
                    body.activate();
                    return null;
                }));
    }

    public void destroy() {
        for (AbstractConnector connector : connectors.values()) {
            connector.destroy();
            this.removeAllBodies();
        }
    }

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return part.level.getPhysicsLevel();
    }
}
