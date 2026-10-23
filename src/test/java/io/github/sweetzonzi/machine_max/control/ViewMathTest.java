package io.github.sweetzonzi.machine_max.control;

import net.minecraft.world.phys.Vec2;

import io.github.sweetzonzi.machine_max.client.input.ThirdPersonViewpoint;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.*;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import org.junit.jupiter.api.Test;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import net.minecraft.world.phys.Vec3;
import jme3utilities.math.MyQuaternion;
import static io.github.sweetzonzi.machine_max.control.AimGeometryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ViewMathTest {
    @Test void t10FollowIncludesSeatMotionExactlyOnce() {
        var view=new ThirdPersonViewpoint();
        var initial=ViewMath.follow(Quaternion.IDENTITY,deg(0, 20),Vec3.ZERO).worldPoint();
        view.enter(true,Quaternion.IDENTITY,Vec3.ZERO,initial);
        var first=view.update(Quaternion.IDENTITY,Vec3.ZERO,Vec2.ZERO,AimAxisLimit.CONTINUOUS,AimAxisLimit.CONTINUOUS);
        var seat=ViewMath.lookRotation(deg(0, 10));
        var moved=view.update(seat,Vec3.ZERO,Vec2.ZERO,AimAxisLimit.CONTINUOUS,AimAxisLimit.CONTINUOUS);
        var input=view.update(seat,Vec3.ZERO,deg(0, 5),AimAxisLimit.CONTINUOUS,AimAxisLimit.CONTINUOUS);
        assertEquals(Math.toRadians(20),ViewMath.directionAngles(first.pose().forward()).y,1e-5);
        assertEquals(Math.toRadians(30),ViewMath.directionAngles(moved.pose().forward()).y,1e-5);
        assertEquals(Math.toRadians(35),ViewMath.directionAngles(input.pose().forward()).y,1e-5);
        assertEquals(Math.toRadians(25),view.relativeLook().y,1e-5);
        assertEquals(1000,input.worldPoint().length(),1e-9);
    }
    @Test void t13FreeRetainsPointThroughSeatRotationAndTranslation() {
        var view=new ThirdPersonViewpoint(); var p=new Vec3(300,50,-900);
        view.enter(false,Quaternion.IDENTITY,Vec3.ZERO,p);
        var origin=new Vec3(10,2,15);
        var a=view.update(ViewMath.lookRotation(deg(25, 70)),origin,Vec2.ZERO,AimAxisLimit.CONTINUOUS,AimAxisLimit.CONTINUOUS);
        assertEquals(p,a.worldPoint());
        assertEquals(0,a.pose().forward().subtract(p.subtract(origin).normalize()).length(),1e-5);
        var b=view.update(Quaternion.IDENTITY,origin,deg(2, 5),AimAxisLimit.CONTINUOUS,AimAxisLimit.CONTINUOUS);
        assertEquals(1000,b.worldPoint().subtract(origin).length(),1e-9);
    }
    @Test void t14T15FullReferenceCompositionAndRenderReadOnly() {
        for(double roll:new double[]{30,90}) {
            var reference=ViewMath.lookRotation(deg(15, 25)).mult(rotation(new Vector3f(0,0,1),Math.toRadians(roll)));
            var look=deg(10, 20); var origin=new Vec3(2e7,3,-2e7);
            var a=ViewMath.follow(reference,look,origin);
            var expected=reference.mult(ViewMath.lookRotation(look));
            assertEquals(0,MyQuaternion.rotate(expected,Vector3f.UNIT_X,null).subtract(MyQuaternion.rotate(a.pose().rotation(),Vector3f.UNIT_X,null)).length(),1e-5);
            for(int i=0;i<100;i++) assertEquals(a,ViewMath.follow(reference,look,origin));
        }
    }
    @Test void t23ModeSwitchInheritsDesiredPoint() {
        var origin=new Vec3(1,3,7); var point=new Vec3(80,200,-900);
        var reference=ViewMath.lookRotation(deg(15, 40));
        var q=ViewMath.inherit(reference,origin,point);
        var follow=ViewMath.follow(reference,q,origin);
        assertEquals(0,follow.pose().forward().subtract(point.subtract(origin).normalize()).length(),1e-5);
        var free=ViewMath.free(point,origin);
        assertEquals(point,free.worldPoint());
    }
    @Test void coincidentFinitePointDoesNotProduceNaN() {
        var p=new Vec3(1,2,3);
        assertEquals(p,ViewMath.free(p,p).worldPoint());
        assertEquals(Vec2.ZERO,ViewMath.inherit(Quaternion.IDENTITY,p,p));
        assertEquals(1000,ViewMath.advanceFree(p,p,deg(1, 1)).subtract(p).length(),1e-9);
    }
}
