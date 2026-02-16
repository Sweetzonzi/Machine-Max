package io.github.sweetzonzi.machine_max.mixin;

import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {
    @Shadow @Final private Entity entity;

    /**
     * 来自<a href="https://www.mcmod.cn/class/18751.html">阿辰的直升机模组</a>
     * <p>
     * 关闭服务器对客户端部件实体位置验证，自行同步部件位置，避免二者冲突导致的部件位置跳变
     * @author ArcherLee
     * */
    @Redirect(method = "sendChanges", at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
    ))
    private void filterPackets(Consumer<Packet<?>> consumer, Object packet) {
        //关闭服务器对客户端部件实体位置验证
        if (this.entity instanceof MMPartEntity &&
                (packet instanceof ClientboundMoveEntityPacket
                        || packet instanceof ClientboundTeleportEntityPacket
                )) {
            return; // 阻止同步包发送
        }
        consumer.accept((Packet<?>) packet);
    }
}
