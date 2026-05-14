package io.github.sweetzonzi.machine_max.client.render.projectile;

import com.jme3.math.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.RigidProjectile;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public class ClientProjectileRenderer {

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm == null) return;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f cameraPos = new Vector3f((float) camera.getPosition().x, (float) camera.getPosition().y, (float) camera.getPosition().z);

        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(2f);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < pm.count; i++) {
            if (!pm.alive[i]) continue;

            float prevX = pm.posX[i], prevY = pm.posY[i], prevZ = pm.posZ[i];
            float currX = prevX + pm.velX[i] * 0.05f;
            float currY = prevY + pm.velY[i] * 0.05f;
            float currZ = prevZ + pm.velZ[i] * 0.05f;

            float r = 1f, g = 0f, b = 0f;
            if (pm.lifetime[i] < 10) {
                r = 1f;
                g = 1f;
                b = 0f;
            }

            buffer.addVertex(prevX - cameraPos.x, prevY - cameraPos.y, prevZ - cameraPos.z).setColor(r, g, b, 1f);
            buffer.addVertex(currX - cameraPos.x, currY - cameraPos.y, currZ - cameraPos.z).setColor(r, g, b, 1f);
        }

        var objects = ObjectManager.levelDestroyableObjects.get(level);
        if (objects != null) {
            for (DestroyableObject obj : objects.values()) {
                if (obj instanceof RigidProjectile rp && rp.isAlive()) {
                    Vector3f pos = rp.getPosition();
                    float radius = rp.getRadius();
                    drawSphereWireframe(buffer, pos, radius, 0f, 0f, 1f, cameraPos);
                }
            }
        }

        BufferUploader.drawWithShader(buffer.build());
    }

    private static void drawSphereWireframe(BufferBuilder buffer, Vector3f center, float radius,
                                              float r, float g, float b, Vector3f cameraPos) {
        int segments = 12;
        float cx = center.x - cameraPos.x;
        float cy = center.y - cameraPos.y;
        float cz = center.z - cameraPos.z;

        for (int ring = 0; ring < segments; ring++) {
            float phi = (float) (ring * Math.PI * 2 / segments);
            float phiNext = (float) ((ring + 1) * Math.PI * 2 / segments);
            for (int dot = 0; dot < segments; dot++) {
                float theta = (float) (dot * Math.PI / segments);

                float x1 = cx + radius * (float) (Math.sin(theta) * Math.cos(phi));
                float y1 = cy + radius * (float) (Math.cos(theta));
                float z1 = cz + radius * (float) (Math.sin(theta) * Math.sin(phi));
                float x2 = cx + radius * (float) (Math.sin(theta) * Math.cos(phiNext));
                float y2 = cy + radius * (float) (Math.cos(theta));
                float z2 = cz + radius * (float) (Math.sin(theta) * Math.sin(phiNext));

                buffer.addVertex(x1, y1, z1).setColor(r, g, b, 1f);
                buffer.addVertex(x2, y2, z2).setColor(r, g, b, 1f);
            }
        }
    }
}
