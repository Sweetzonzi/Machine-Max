package io.github.tt432.machinemax.common.phys;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.utils.bulletphysics.collision.broadphase.BroadphaseInterface;
import io.github.tt432.machinemax.utils.bulletphysics.collision.broadphase.DbvtBroadphase;
import io.github.tt432.machinemax.utils.bulletphysics.collision.dispatch.CollisionDispatcher;
import io.github.tt432.machinemax.utils.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import io.github.tt432.machinemax.utils.bulletphysics.collision.shapes.CollisionShape;
import io.github.tt432.machinemax.utils.bulletphysics.collision.shapes.StaticPlaneShape;
import io.github.tt432.machinemax.utils.bulletphysics.dynamics.DiscreteDynamicsWorld;
import io.github.tt432.machinemax.utils.bulletphysics.dynamics.constraintsolver.ConstraintSolver;
import io.github.tt432.machinemax.utils.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import io.github.tt432.machinemax.utils.bulletphysics.util.ObjectArrayList;

import javax.vecmath.Vector3f;

public class PhysThread extends Thread{

    public static volatile DiscreteDynamicsWorld world;//各个物体所处的世界，不处于同一世界的物体无法交互，或许可以用来做不同维度的处理，但是否会无法利用多线程优势？
    public static volatile ObjectArrayList<CollisionShape> serverCollisionShapes = new ObjectArrayList<CollisionShape>();
    public static volatile ObjectArrayList<CollisionShape> rendererCollisionShapes = new ObjectArrayList<CollisionShape>();
    public DefaultCollisionConfiguration collisionConfiguration;// collision configuration contains default setup for memory, collision setup
    public BroadphaseInterface broadphase;//TODO:看看这个是干什么用的
    public CollisionDispatcher dispatcher;//use the default collision dispatcher. For parallel processing you can use a diffent dispatcher (see Extras/BulletMultiThreaded)
    public ConstraintSolver solver;//the default constraint solver. For parallel processing you can use a different solver (see Extras/BulletMultiThreaded)
    @Override
    public void run() {//此乃物理计算的主线程
        collisionConfiguration = new DefaultCollisionConfiguration();
        dispatcher = new CollisionDispatcher(collisionConfiguration);
        broadphase = new DbvtBroadphase();
        solver = new SequentialImpulseConstraintSolver();
        world = new DiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfiguration);
        //初始化
        world.setGravity(new Vector3f(0,-9.81f,0));//设置重力加速度
        CollisionShape ground = new StaticPlaneShape(new Vector3f(0, 1, 0), -60);
        serverCollisionShapes.add(ground);
        rendererCollisionShapes.add(ground);

        MachineMax.LOGGER.info("New phys thread started!");
        while(!isInterrupted()){//物理线程主循环
            step(false);//推进物理模拟计算进程
            world.stepSimulation(20,20,0.02f);
            try {
                //TODO:根据前文执行用时调整sleep时间？能做到吗？
                Thread.sleep(20);//等待时间步长
            } catch (InterruptedException e) {
                MachineMax.LOGGER.info("Stopping phys thread...");
                break;
            }
        }
        world.destroy();
    }

    /**
     * 在物理仿真未处于暂停状态时推进仿真进程
     * @param paused -是否暂停了物理仿真进程
     */
    public void step(boolean paused){

        if(!paused){
        }

    }

}
