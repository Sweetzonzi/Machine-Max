package io.github.tt432.machinemax.common.phys.body;

import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import lombok.Getter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

import static net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE;

public class LivingEntityEyesightBody {
//    final LivingEntity owner;
//    public final DRay ray;
//    @Getter
//    volatile private HashMap<DGeom, Double> targets = HashMap.newHashMap(2);
//    volatile private HashMap<DGeom, Integer> hitCount = HashMap.newHashMap(2);
//    private final Comparator<HashMap.Entry<DGeom, Double>> valueComparator = HashMap.Entry.comparingByValue();

    public LivingEntityEyesightBody(String name, LivingEntity entity) {
//        super(name, entity.level());
//        body = OdeHelper.createBody(MMBodyTypes.getLIVING_ENTITY_EYESIGHT().get(), entity, name, false, getPhysLevel().getWorld());
//        body.disable();
//        body.onTick(this::onTick);
//        body.onPhysTick(this::onPhysTick);
//        this.owner = entity;
//        this.ray = OdeHelper.createRay(null, entity.getAttributeValue(ENTITY_INTERACTION_RANGE));
//        geoms.add(ray);
//        ray.setBody(body);
//        ray.setPassFromCollide(true);
//        this.setPosRotVel();
//        ray.onCollide(this::onCollide);
//        getPhysLevel().getWorld().laterConsume(() -> {
//            getPhysLevel().getWorld().getSpace().add(ray);
//            this.enable();
//            return null;
//        });
    }

    protected void onPhysTick() {//物理线程迭代时
//        Iterator<Map.Entry<DGeom, Double>> iterator = targets.entrySet().iterator();
//        while (iterator.hasNext()) {
//            Map.Entry<DGeom, Double> entry = iterator.next();
//            if (hitCount.get(entry.getKey()) == 0) {
//                hitCount.remove(entry.getKey());//计数器移除键值对
//                iterator.remove();//移除5次未命中目标的计数器的目标
//            } else {
//                hitCount.put(entry.getKey(), hitCount.get(entry.getKey()) - 1);//计数器减1
//            }
//        }
    }

    protected void onTick() {//主线程迭代时
//        getPhysLevel().getWorld().laterConsume(() -> {
//            ((DRay) this.geoms.getFirst()).setLength(owner.getAttributeValue(ENTITY_INTERACTION_RANGE));//更新射线长度
//            return null;
//        });
//        this.setPosRotVel();//更新位置姿态和速度
    }

//    protected void onCollide(DGeom dGeom, DContactBuffer dContacts) {//射线与碰撞体碰撞时
//        if (dGeom.getBody().getOwner() instanceof EntityBoundingBoxBody) {//不检测自身的碰撞箱
//            if (((EntityBoundingBoxBody) dGeom.getBody().getOwner()).owner == this.owner) return;
//        }
//        hitCount.put(dGeom, 5);
//        double range = dContacts.get(0).getContactGeom().depth;
//        targets.put(dGeom, range);
//    }

    /**
     * 获取指向的最近的部件对接口，如果没有则返回null
     *
     * @return 线段命中的最近的部件对接口
     */
    public AbstractConnector getConnector() {
//        if (!targets.isEmpty()) {
//            for (Map.Entry<DGeom, Double> entry : getSortedTargets()) {
//                if (entry.getKey().getBody() != null && entry.getKey().getBody().getOwner() instanceof AbstractPortPort port) {
//                    return port;
//                }
//            }
//        }
        return null;
    }

//    // 获取根据值排序后的列表
//    public List<Map.Entry<DGeom, Double>> getSortedTargets() {
//        return targets.entrySet().stream()
//                .sorted(valueComparator)
//                .collect(Collectors.toList());
//    }

}
