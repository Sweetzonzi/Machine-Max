package io.github.sweetzonzi.machinemax.external.js;

import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.external.js.hook.EventToJS;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machinemax.network.payload.MovementInputPayload;
import io.github.sweetzonzi.machinemax.network.payload.ScriptablePayload;
import io.github.sweetzonzi.machinemax.util.MMJoystickHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static io.github.sweetzonzi.machinemax.MachineMax.LOGGER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_RUNNER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_SCOPE;

public class JSUtils {
    private final String location;
    private final String packName;
    private Minecraft client = null;
    public JSUtils(String location, String packName) {
        this.location = location;
        this.packName = packName;
    }

//    public double get(String tag) {
//        double value = 0;
//        if (KeyHooks.SIGNAL_MAP.get(tag) instanceof Double d) {
//            value = d;
//        }
//        return value;
//    }


    public void hook(String channel, org.mozilla.javascript.ArrowFunction arrowFunction) {
        if (!Hook.LISTENING_EVENT.containsKey(channel)) Hook.LISTENING_EVENT.put(channel, new ArrayList<>());
        Hook.LISTENING_EVENT.get(channel)
                    .add((new EventToJS() {
                        @Override
                        public Object call(Object... args) {
                            return (JS_RUNNER != null && JS_SCOPE != null) ?
                                    arrowFunction.call(JS_RUNNER, JS_SCOPE, JS_SCOPE, args) : null;
                        }

                        @Override
                        public String packName() {
                            return packName;
                        }

                        @Override
                        public String location() {
                            return location;
                        }
                    }
                    ));

    }
    public void log(Object object) {
        LOGGER.info(String.valueOf(object));
    }

    public void print(Object... objects) {
        StringBuilder msg = new StringBuilder();
        if (! Objects.equals(Thread.currentThread().getName(), "Server thread")) {
            //不是服务器的都打印，避免物理进程不响应
            if (Minecraft.getInstance().player instanceof Player player) {
                for (Object object : objects) {
                    msg.append(object);
                    msg.append(" ");
                }
                player.sendSystemMessage(Component.literal(msg.toString()));
            }
        } else {
            for (Object object : objects) {
                msg.append(object);
                msg.append(" ");
            }
            log(msg);
        }

    }

    public void payload(String to, CompoundTag nbt){
        sendToServer(new ScriptablePayload(location, to, nbt));
    }

    public CompoundTag nbt() {
        return new CompoundTag();
    }
    public CompoundTag copyNbt(CompoundTag from) {
        return from.copy();
    }

    public Float toFloat(Object number) {
        if (number instanceof Double d) {
            return d.floatValue();
        }
        if (number instanceof Long l) {
            return l.floatValue();
        }
        if (number instanceof Short s) {
            return s.floatValue();
        }
        if (number instanceof Integer i) {
            return i.floatValue();
        }
        if (number instanceof Float f) {
            return f;
        }
        return 0f;
    }

    public void sendToServer(CustomPacketPayload payload, CustomPacketPayload... payloads) {
        PacketDistributor.sendToServer(payload, payloads);
    }
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload, CustomPacketPayload... payloads) {
        PacketDistributor.sendToPlayer(player, payload, payloads);
    }

    public static String getSimpleName(String fullClassName) {
        int lastDotIndex = fullClassName.lastIndexOf('.');
        return (lastDotIndex == -1) ? fullClassName : fullClassName.substring(lastDotIndex + 1);
    }
}
