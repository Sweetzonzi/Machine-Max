package io.github.tt432.machinemax.common.phys;

import io.github.tt432.machinemax.MachineMax;

public class PhysThread extends Thread{

    @Override
    public void run() {//此乃物理计算的主线程

        MachineMax.LOGGER.info("New phys thread started!");

        while(!isInterrupted()){//物理线程主循环
            step(false);//推进物理模拟计算进程
            try {
                //TODO:根据前文执行用时调整sleep时间？能做到吗？
                Thread.sleep(20);//等待时间步长
            } catch (InterruptedException e) {
                MachineMax.LOGGER.info("Stopping phys thread...");
                break;
            }
        }

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
