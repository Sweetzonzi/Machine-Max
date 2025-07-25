package io.github.sweetzonzi.machinemax.external.js;

import io.github.sweetzonzi.machinemax.external.js.hook.EventToJS;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import static io.github.sweetzonzi.machinemax.MachineMax.LOGGER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_RUNNER;
import static io.github.sweetzonzi.machinemax.external.js.MMInitialJS.JS_SCOPE;

public class JSUtils {
    private final String location;
    private final String packName;
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

    public void attachInteract() {
        //TODO:换成其他获取方式，弄个Helper类？
//        if (Minecraft.getInstance().player instanceof Player player) {
//            player.getData(MMAttachments.getENTITY_EYESIGHT().get()).clientInteract();
//
//        }
    }
    public boolean bytesE(byte[] b1, byte[] b2) {
       return Arrays.equals(b1, b2);
    }

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
