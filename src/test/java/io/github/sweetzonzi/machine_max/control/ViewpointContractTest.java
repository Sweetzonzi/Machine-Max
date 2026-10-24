package io.github.sweetzonzi.machine_max.control;

import com.google.gson.JsonParser;
import com.jme3.math.Quaternion;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.*;
import io.github.sweetzonzi.machine_max.network.payload.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.sweetzonzi.machine_max.control.AimGeometryFixtures.*;

class ViewpointContractTest {
    @BeforeAll static void registerBus() {
        io.github.sweetzonzi.machine_max.MachineMax.REGISTER.register(net.neoforged.bus.api.BusBuilder.builder().build());
    }
    private CameraSubsystemStaticAttr camera(boolean stable) {
        return CameraSubsystemStaticAttr.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"vertical_stabilized\":"+stable+",\"horizontal_stabilized\":"+stable+",\"yaw_limit\":360,\"min_pitch\":-80,\"max_pitch\":80,\"yaw_speed\":20}")).getOrThrow();
    }
    @Test void fixedAndRotatableBranchesArePeersAtAllThreeLayers() {
        assertFalse(CameraSubsystem.class.isAssignableFrom(SightSubsystem.class));
        assertTrue(AbstractViewpointSubsystem.class.isAssignableFrom(SightSubsystem.class));
        assertFalse(CameraSubsystemStaticAttr.class.isAssignableFrom(SightSubsystemStaticAttr.class));
        assertFalse(CameraSubsystemAttr.class.isAssignableFrom(SightSubsystemAttr.class));
        assertThrows(NoSuchFieldException.class,()->SightSubsystemStaticAttr.class.getField("yawLimit"));
    }
    @Test void legacyFixedOpticFieldsAreIgnoredAndRotationDefaultsBelongToCamera() {
        var fixed=SightSubsystemStaticAttr.CODEC.codec().parse(JsonOps.INSTANCE,JsonParser.parseString(
                "{\"min_pitch\":-12,\"yaw_limit\":5,\"horizontal_stabilized\":true}")).getOrThrow();
        assertTrue(fixed.horizontalStabilized);
        var encoded=SightSubsystemStaticAttr.CODEC.codec().encodeStart(JsonOps.INSTANCE,fixed).getOrThrow().getAsJsonObject();
        assertFalse(encoded.has("yaw_limit"));
        assertFalse(encoded.has("min_pitch"));
        assertEquals(90,camera(false).pitchSpeed);
    }
    @Test void inputCodecPreservesFullTargetAndDoublePrecision() {
        var point=new Vec3(20000000.125,-10.25,-20000000.375);
        var input=new ViewInputPayload(12,"seat",14,"optic",1,point,deg(12,25));
        FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            ViewInputPayload.STREAM_CODEC.encode(buffer,input);
            var decoded=ViewInputPayload.STREAM_CODEC.decode(buffer);
            assertEquals(input.aimPoint(),decoded.aimPoint());
            assertEquals(input.subPartId(),decoded.subPartId());
        assertEquals(input.subSystemName(),decoded.subSystemName());
            assertEquals(input.viewPartId(),decoded.viewPartId());
        assertEquals(input.viewName(),decoded.viewName());
            assertEquals(input.controlGroup(),decoded.controlGroup());
            assertTrue(input.relativeAim().equals(decoded.relativeAim()));
            assertTrue(decoded.isFinite());
        assertEquals(0,buffer.readableBytes());
        } finally { buffer.release(); }
        assertFalse(new ViewInputPayload(12,"seat",-1,"seat",0,new Vec3(Double.NaN,0,0),Vec2.ZERO).isFinite());
        assertFalse(new ViewInputPayload(12,"seat",-1,"seat",0,Vec3.ZERO,new Vec2(Float.NaN,0)).isFinite());
    }
}
