package io.github.sweetzonzi.machine_max.network.payload.physics_test;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 用于快速运行上一次命令调用的测试用例，
 * 或者执行 暂停/继续 播放的操作
 * */
public record PhysicsTestRePlayPayload(CallType callType) implements CustomPacketPayload {
    public enum CallType {
        run,
        resume,

    }

    public static final Type<PhysicsTestRePlayPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "physics_test_payload"));
    public static final StreamCodec<FriendlyByteBuf, PhysicsTestRePlayPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PhysicsTestRePlayPayload decode(FriendlyByteBuf buf) {
            return new PhysicsTestRePlayPayload(buf.readEnum(CallType.class));
        }

        @Override
        public void encode(FriendlyByteBuf buf, PhysicsTestRePlayPayload testPayload) {
            buf.writeEnum(testPayload.callType);
        }
    };
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void clientHandler(final PhysicsTestRePlayPayload payload, final IPayloadContext context) {
    }


    public static void serverHandler(final PhysicsTestRePlayPayload payload, final IPayloadContext context) {
//        EnvironmentWrapper.run(EnvironmentWrapper.Env.DEVELOPMENT, () -> {
//            // 仅在开发环境实现进入游戏一键运行某测试
//            if (PhysicsTestBus.LAST_RUN == null) {
//                PhysicsTest test = PhysicsTestBus.getTests().getFirst();
//                if (test != null) PhysicsTestBus.LAST_RUN = test.path();
//            }
//            BaseJoinPositionPhysicsTest.JOIN_POSITIONS.clear();
//            PhysicsTestCommand.addJoinPosition((ServerPlayer) context.player());
//        });
//
//        if (PhysicsTestBus.LAST_RUN != null) {
//            switch (payload.callType) {
//                case run -> {
//                    context.player().sendSystemMessage(Component.literal("\n重新运行测试用例"));
//                    PhysicsTestBus.get(PhysicsTestBus.LAST_RUN).run(context.player().level());
//                }
//                case resume -> context.player().sendSystemMessage(
//                        PhysicsTestBus.get(PhysicsTestBus.LAST_RUN).onResume());
//            }
//        } else context.player().sendSystemMessage(Component.literal("你还没有通过命令调用过某个测试用例"));
//

        PacketDistributor.sendToPlayersInDimension((ServerLevel) context.player().level(), payload);
    }


}
