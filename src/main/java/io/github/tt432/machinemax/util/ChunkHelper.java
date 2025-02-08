package io.github.tt432.machinemax.util;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class ChunkHelper {
    public static ChunkPos getChunkPos(Vec3 pos) {
        return getChunkPos((int) pos.x, (int) pos.z);
    }

    public static ChunkPos getChunkPos(Vector3f pos){
        return getChunkPos((int) pos.x, (int) pos.z);
    }

    public static ChunkPos getChunkPos(float x, float z){
        return getChunkPos((int) x, (int) z);
    }

    public static ChunkPos getChunkPos(double x, double z){
        return getChunkPos((int) x, (int) z);
    }

    public static ChunkPos getChunkPos(int x, int z){
        return new ChunkPos(x >> 4, z >> 4);
    }
}
