package io.github.sweetzonzi.machine_max.mixin;

import io.github.sweetzonzi.machine_max.client.input.KeyBinding;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修改客户端骑乘消息的 Mixin
 * 目标：当玩家骑乘模组特定实体时，显示自定义的提示文本
 */
@OnlyIn(Dist.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    /**
     * 重定向 Component.translatable 调用，仅拦截 "mount.onboard" 消息
     *
     * @return 要显示的 Component 对象
     */
    @Redirect(
            method = "handleSetEntityPassengersPacket",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"
            )
    )
    private MutableComponent redirectOnboardMessage(String key, Object[] args) {
        // 只处理原版骑乘消息，其他翻译调用原样放行
        if (!"mount.onboard".equals(key)) {
            return Component.translatable(key, args);
        }

        // 获取当前玩家（客户端玩家）
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return Component.translatable(key, args);
        }
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof MMPartEntity) {
            // 返回自定义翻译消息
            return Component.translatable("message.machine_max.leaving_vehicle",
                    KeyBinding.generalLeaveVehicleKey.getTranslatedKeyMessage(), 0);
        }
        // 如果不是部件实体，返回原版消息
        return Component.translatable(key, args);
    }
}
