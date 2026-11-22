package io.github.sweetzonzi.machine_max.control;

import net.minecraft.world.phys.Vec2;

import io.github.sweetzonzi.machine_max.client.input.ThirdPersonViewpoint;
import io.github.sweetzonzi.machine_max.client.input.AimPointResolver;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.*;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import org.junit.jupiter.api.Test;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import net.minecraft.world.phys.Vec3;
import jme3utilities.math.MyQuaternion;
import org.joml.Quaternionf;
import static io.github.sweetzonzi.machine_max.control.AimGeometryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ViewMathTest {
    private static final long START = 1_000_000_000L;

    @Test void t10FollowIncludesSeatMotionExactlyOnce() {
        var view=new ThirdPersonViewpoint();
        var initial=ViewMath.follow(Quaternion.IDENTITY,deg(0, 20),Vec3.ZERO).focusPoint();
        view.enter(true,Quaternion.IDENTITY,Vec3.ZERO,initial,0);
        var first=view.update(Quaternion.IDENTITY,Vec3.ZERO,Vec2.ZERO,AimAxisLimit.CONTINUOUS,AimAxisLimit.CONTINUOUS);
        var seat=ViewMath.lookRotation(deg(0, 10));
        var moved=view.update(seat,Vec3.ZERO,Vec2.ZERO,AimAxisLimit.CONTINUOUS,AimAxisLimit.CONTINUOUS);
        var input=view.update(seat,Vec3.ZERO,deg(0, 5),AimAxisLimit.CONTINUOUS,AimAxisLimit.CONTINUOUS);
        assertEquals(Math.toRadians(20),ViewMath.directionAngles(first.pose().forward()).y,1e-5);
        assertEquals(Math.toRadians(30),ViewMath.directionAngles(moved.pose().forward()).y,1e-5);
        assertEquals(Math.toRadians(35),ViewMath.directionAngles(input.pose().forward()).y,1e-5);
        assertEquals(Math.toRadians(25),view.relativeLook().y,1e-5);
        assertEquals(1000,input.focusPoint().length(),1e-9);
    }
    @Test void t13FreeRetainsPointThroughSeatRotationAndTranslation() {
        var view=new ThirdPersonViewpoint(); var p=new Vec3(300,50,-900);
        view.enter(false,Quaternion.IDENTITY,Vec3.ZERO,p,0);
        var origin=new Vec3(10,2,15);
        var a=view.update(ViewMath.lookRotation(deg(25, 70)),origin,Vec2.ZERO,AimAxisLimit.CONTINUOUS,AimAxisLimit.CONTINUOUS);
        assertEquals(p,a.focusPoint());
        assertEquals(0,a.pose().forward().subtract(p.subtract(origin).normalize()).length(),1e-5);
        var b=view.update(Quaternion.IDENTITY,origin,deg(2, 5),AimAxisLimit.CONTINUOUS,AimAxisLimit.CONTINUOUS);
        assertEquals(1000,b.focusPoint().subtract(origin).length(),1e-9);
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
        assertEquals(point,free.focusPoint());
    }
    @Test void thirdPersonPitchBiasChangesOnlyTheResolvedAimAndKeepsInputLimitsAbsolute() {
        float bias = (float) Math.toRadians(15);
        var lower = new ThirdPersonViewpoint();
        lower.enter(true, Quaternion.IDENTITY, Vec3.ZERO, null, bias);
        var neutral = lower.update(Quaternion.IDENTITY, Vec3.ZERO, Vec2.ZERO,
                AimAxisLimit.CONTINUOUS,
                new AimAxisLimit(false, Math.toRadians(-30), Math.toRadians(45)));
        assertEquals(0, ViewMath.directionAngles(neutral.pose().forward()).x, 1e-5);
        assertEquals(0, lower.relativeLook().x, 1e-6);
        assertEquals(1000, neutral.focusPoint().length(), 1e-9);
        Vec3 neutralAim = AimPointResolver.resolveFixedDistance(neutral.pose(), neutral.focusPoint(), Vec3.ZERO, bias);
        assertEquals(Math.toRadians(15), ViewMath.directionAngles(neutralAim).x, 1e-5);

        var upper = new ThirdPersonViewpoint();
        upper.enter(true, Quaternion.IDENTITY, Vec3.ZERO, null, bias);
        var upperLimit = upper.update(Quaternion.IDENTITY, Vec3.ZERO,
                new Vec2((float) Math.toRadians(80), 0), AimAxisLimit.CONTINUOUS,
                new AimAxisLimit(false, Math.toRadians(-30), Math.toRadians(45)));
        assertEquals(Math.toRadians(30), ViewMath.directionAngles(upperLimit.pose().forward()).x, 1e-5);
        Vec3 upperAim = AimPointResolver.resolveFixedDistance(
                upperLimit.pose(), upperLimit.focusPoint(), Vec3.ZERO, bias);
        assertEquals(Math.toRadians(45), ViewMath.directionAngles(upperAim).x, 1e-5);

        var lowerLimit = new ThirdPersonViewpoint();
        lowerLimit.enter(true, Quaternion.IDENTITY, Vec3.ZERO, null, bias);
        var lowerResult = lowerLimit.update(Quaternion.IDENTITY, Vec3.ZERO,
                new Vec2((float) Math.toRadians(-80), 0), AimAxisLimit.CONTINUOUS,
                new AimAxisLimit(false, Math.toRadians(-30), Math.toRadians(45)));
        assertEquals(Math.toRadians(-45), ViewMath.directionAngles(lowerResult.pose().forward()).x, 1e-5);
        Vec3 lowerAim = AimPointResolver.resolveFixedDistance(
                lowerResult.pose(), lowerResult.focusPoint(), Vec3.ZERO, bias);
        assertEquals(Math.toRadians(-30), ViewMath.directionAngles(lowerAim).x, 1e-5);
    }

    @Test void thirdPersonCameraAxisStaysUnbiasedWhileWeaponTargetProjectsAboveIt() {
        float bias = (float) Math.toRadians(15);
        Quaternion reference = ViewMath.lookRotation(deg(11, -23))
                .mult(rotation(new Vector3f(0, 0, 1), Math.toRadians(29)));
        Vec3 anchor = new Vec3(18, 6, -42);
        var view = new ThirdPersonViewpoint();
        view.enter(true, reference, anchor, null, bias);

        var focus = view.update(reference, anchor, Vec2.ZERO,
                AimAxisLimit.CONTINUOUS, AimAxisLimit.CONTINUOUS);
        var camera = focus.pose();
        Vec3 cameraPosition = ViewMath.orbitPosition(anchor, camera.forward(), 4);
        Vec3 target = AimPointResolver.resolveFixedDistance(camera, focus.focusPoint(), anchor, bias);
        Vec2 targetInCamera = ViewMath.inherit(camera.rotation(), cameraPosition, target);
        Quaternion expectedRotation = camera.rotation().mult(
                new Quaternion().fromAngleNormalAxis(bias, Vector3f.UNIT_X));
        Vec3 expectedDirection = new AimPose(anchor, expectedRotation).forward();
        double projectedPitch = Math.atan2(AimMath.AIM_DISTANCE_METERS * Math.sin(bias),
                4 + AimMath.AIM_DISTANCE_METERS * Math.cos(bias));

        assertEquals(0, camera.forward().subtract(ViewMath.follow(reference, Vec2.ZERO, anchor).pose().forward()).length(), 1e-5);
        assertEquals(projectedPitch, targetInCamera.x, 1e-5);
        assertEquals(0, targetInCamera.y, 1e-5);
        assertEquals(0, target.subtract(anchor).normalize().subtract(expectedDirection).length(), 1e-5);

        var zeroBias = new ThirdPersonViewpoint();
        zeroBias.enter(true, reference, anchor, null, 0);
        var zeroFocus = zeroBias.update(reference, anchor, Vec2.ZERO,
                AimAxisLimit.CONTINUOUS, AimAxisLimit.CONTINUOUS);
        var zeroCamera = zeroFocus.pose();
        Vec3 zeroCameraPosition = ViewMath.orbitPosition(anchor, zeroCamera.forward(), 4);
        Vec3 zeroTarget = AimPointResolver.resolveFixedDistance(
                zeroCamera, zeroFocus.focusPoint(), anchor, 0);
        Vec2 zeroTargetInCamera = ViewMath.inherit(zeroCamera.rotation(), zeroCameraPosition, zeroTarget);
        assertEquals(0, zeroTargetInCamera.x, 1e-5);
        assertEquals(0, zeroTargetInCamera.y, 1e-5);

        Vec3 shiftedTrackAnchor = anchor.add(1, 0, 0);
        Vec3 shiftedCameraPosition = ViewMath.orbitPosition(shiftedTrackAnchor, camera.forward(), 4);
        Vec2 offsetParallax = ViewMath.inherit(camera.rotation(), shiftedCameraPosition, target);
        assertTrue(Math.abs(offsetParallax.y) > 1e-8);
        assertTrue(Math.abs(offsetParallax.y) < Math.toRadians(.1));
        assertEquals(focus.focusPoint(), view.focusPoint());
    }

    @Test void inversePitchBiasRebuildsFocusForTheSameWorldAimPoint() {
        Quaternion reference = ViewMath.lookRotation(deg(18, -33))
                .mult(rotation(new Vector3f(0, 0, 1), Math.toRadians(41)));
        Vec3 anchor = new Vec3(120, 35, -80);

        for (float bias : new float[]{0, (float) Math.toRadians(15), (float) Math.toRadians(-8)}) {
            var source = ViewMath.follow(reference, deg(7, 51), anchor, 430);
            Vec3 aimPoint = AimPointResolver.resolveFixedDistance(
                    source.pose(), source.focusPoint(), anchor, bias);
            Vec3 focusPoint = AimPointResolver.resolveFocusFromAim(reference, aimPoint, anchor, bias);
            Vec2 look = ViewMath.inherit(reference, anchor, focusPoint);
            var switched = ViewMath.follow(reference, look, anchor, focusPoint.distanceTo(anchor));
            Vec3 switchedAim = AimPointResolver.resolveFixedDistance(
                    switched.pose(), switched.focusPoint(), anchor, bias);

            assertEquals(430, focusPoint.distanceTo(anchor), 1e-4);
            assertEquals(0, switchedAim.distanceTo(aimPoint), 1e-4);
        }
    }

    @Test void orbitPositionUsesTheDisplayedDirectionAroundOneStableAnchor() {
        Vec3 anchor = new Vec3(28, 9, -13);
        double distance = 6.5;
        var view = new ThirdPersonViewpoint();
        Quaternionf freeDirection = new Quaternionf().rotateYXZ(-.8f, .2f, -.1f);
        Quaternionf desired = new Quaternionf().rotateYXZ(.1f, -.05f, .03f);
        Vec3 initialPosition = orbitPosition(anchor, freeDirection, distance);
        view.beginReturn(freeDirection);

        Quaternionf start = view.sampleDisplayRotation(desired, START);
        assertEquals(0, initialPosition.distanceTo(orbitPosition(anchor, start, distance)), 1e-6);
        Quaternionf middle = view.sampleDisplayRotation(desired, START + 100_000_000);
        Vec3 middlePosition = orbitPosition(anchor, middle, distance);
        assertEquals(distance, middlePosition.distanceTo(anchor), 1e-6);
        assertTrue(initialPosition.distanceTo(middlePosition) > 1e-3);

        Quaternionf end = view.sampleDisplayRotation(desired, START + 200_000_000);
        assertEquals(0, orbitPosition(anchor, end, distance)
                .distanceTo(orbitPosition(anchor, desired, distance)), 1e-6);
    }

    private static Vec3 orbitPosition(Vec3 anchor, Quaternionf rotation, double distance) {
        org.joml.Vector3f forward = rotation.transform(new org.joml.Vector3f(0, 0, -1));
        return ViewMath.orbitPosition(anchor, new Vec3(forward.x, forward.y, forward.z), distance);
    }

    @Test void inheritingFocusDoesNotApplyTheBiasToTheSavedPoint() {
        float bias = (float) Math.toRadians(15);
        Vec2 effective = new Vec2((float) Math.toRadians(22), (float) Math.toRadians(-31));
        Vec3 anchor = new Vec3(40, 12, -90);
        Quaternion reference = ViewMath.lookRotation(deg(8, 27))
                .mult(rotation(new Vector3f(0, 0, 1), Math.toRadians(19)));
        Vec3 directionPoint = ViewMath.follow(reference, effective, anchor).focusPoint();
        Vec3 focusPoint = anchor.add(directionPoint.subtract(anchor).scale(.64));
        Vec3 switchedAnchor = anchor.add(2, -1, 4);
        var view = new ThirdPersonViewpoint();
        view.enter(true, reference, switchedAnchor, focusPoint, bias);

        var inherited = view.sample(reference, switchedAnchor);
        Vec2 expectedLook = ViewMath.inherit(reference, switchedAnchor, focusPoint);
        assertEquals(expectedLook.x, view.relativeLook().x, 1e-5);
        assertEquals(0, inherited.pose().forward().subtract(focusPoint.subtract(switchedAnchor).normalize()).length(), 1e-5);
        assertEquals(0, inherited.focusPoint().subtract(focusPoint).length(), 1e-4);
        Vec3 expectedAim = AimPointResolver.resolveFixedDistance(
                inherited.pose(), inherited.focusPoint(), switchedAnchor, bias);
        assertTrue(expectedAim.distanceTo(inherited.focusPoint()) > 1);
        var unchanged = view.update(reference, switchedAnchor, Vec2.ZERO,
                AimAxisLimit.CONTINUOUS, AimAxisLimit.CONTINUOUS);
        assertEquals(0, unchanged.focusPoint().subtract(focusPoint).length(), 1e-4);
        assertEquals(expectedAim, AimPointResolver.resolveFixedDistance(
                unchanged.pose(), unchanged.focusPoint(), switchedAnchor, bias));
    }

    @Test void firstPersonCanKeepTheExistingThousandMeterFollowDistance() {
        var view = new ThirdPersonViewpoint();
        view.enter(true, Quaternion.IDENTITY, Vec3.ZERO, new Vec3(0, 0, -640), 0, false);
        var result = view.update(Quaternion.IDENTITY, Vec3.ZERO, Vec2.ZERO,
                AimAxisLimit.CONTINUOUS, AimAxisLimit.CONTINUOUS);
        assertEquals(1000, result.focusPoint().length(), 1e-9);
    }

    @Test void nonFollowingSeatKeepsUnbiasedFocusFixedDuringReferenceRotation() {
        float bias = (float) Math.toRadians(15);
        var view = new ThirdPersonViewpoint();
        Vec3 anchor = new Vec3(3, 5, 7);
        view.enter(false, Quaternion.IDENTITY, anchor, null, bias);
        var initialState = view.update(Quaternion.IDENTITY, anchor, Vec2.ZERO,
                AimAxisLimit.CONTINUOUS, new AimAxisLimit(false, Math.toRadians(-70), Math.toRadians(45)));
        Vec3 initial = initialState.focusPoint();
        assertEquals(0, ViewMath.directionAngles(initial.subtract(anchor)).x, 1e-5);
        Vec3 initialAim = AimPointResolver.resolveFixedDistance(initialState.pose(), initial, anchor, bias);
        assertEquals(Math.toRadians(15), ViewMath.directionAngles(initialAim.subtract(anchor)).x, 1e-5);
        Quaternion rotated = ViewMath.lookRotation(deg(-12, 68))
                .mult(rotation(new Vector3f(0, 0, 1), Math.toRadians(31)));
        var afterRotation = view.update(rotated, anchor.add(12, -2, 9), Vec2.ZERO,
                AimAxisLimit.CONTINUOUS, new AimAxisLimit(false, Math.toRadians(-70), Math.toRadians(45)));
        assertEquals(initial, afterRotation.focusPoint());
        Vec3 rotatedAim = AimPointResolver.resolveFixedDistance(
                afterRotation.pose(), afterRotation.focusPoint(), anchor.add(12, -2, 9), bias);
        var repeated = view.update(rotated, anchor.add(12, -2, 9), Vec2.ZERO,
                AimAxisLimit.CONTINUOUS, new AimAxisLimit(false, Math.toRadians(-70), Math.toRadians(45)));
        assertEquals(initial, repeated.focusPoint());
        assertEquals(rotatedAim, AimPointResolver.resolveFixedDistance(
                repeated.pose(), repeated.focusPoint(), anchor.add(12, -2, 9), bias));
    }

    @Test void lookAtFocusesFinitePointFromOffsetCameraAndPreservesReferenceRoll() {
        Quaternion reference = ViewMath.lookRotation(deg(13, -24))
                .mult(rotation(new Vector3f(0, 0, 1), Math.toRadians(37)));
        Vec3 camera = new Vec3(12, 8, -4);
        Vec3 target = new Vec3(-320, 145, -910);
        Quaternion rotation = ViewMath.lookAt(reference, camera, target);
        Vec3 forward = new AimPose(camera, rotation).forward();
        assertEquals(0, forward.subtract(target.subtract(camera).normalize()).length(), 1e-5);
        Quaternion noRoll = ViewMath.lookAt(ViewMath.lookRotation(deg(13, -24)), camera, target);
        Vector3f rolledRight = MyQuaternion.rotate(rotation, Vector3f.UNIT_X, null);
        Vector3f flatRight = MyQuaternion.rotate(noRoll, Vector3f.UNIT_X, null);
        assertTrue(rolledRight.distance(flatRight) > .1f);
    }
    @Test void coincidentFinitePointDoesNotProduceNaN() {
        var p=new Vec3(1,2,3);
        assertEquals(p,ViewMath.free(p,p).focusPoint());
        assertEquals(Vec2.ZERO,ViewMath.inherit(Quaternion.IDENTITY,p,p));
        assertEquals(1000,ViewMath.advanceFree(p,p,deg(1, 1)).subtract(p).length(),1e-9);
    }
    @Test void axisAlongPutsLocalPlusZOnDirection() {
        for(Vec3 direction:new Vec3[]{
                new Vec3(0,0,1),new Vec3(0,0,-1),new Vec3(1,0,0),
                new Vec3(0,1,0),new Vec3(0.3,-0.6,0.74),new Vec3(-2,5,-9)}){
            Vector3f z=MyQuaternion.rotate(ViewMath.axisAlong(direction),Vector3f.UNIT_Z,null);
            assertEquals(0,new Vec3(z.x,z.y,z.z).normalize().subtract(direction.normalize()).length(),1e-5);
        }
    }
}
