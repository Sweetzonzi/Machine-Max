package io.github.sweetzonzi.machine_max.control;

import net.minecraft.world.phys.Vec2;

import io.github.sweetzonzi.machine_max.common.mech.control.aim.*;
import org.junit.jupiter.api.Test;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import net.minecraft.world.phys.Vec3;
import static io.github.sweetzonzi.machine_max.control.AimGeometryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class AimMathTest {
    private static void same(Vec2 expected, Vec2 actual, double tolerance) {
        assertEquals(expected.y, actual.y, tolerance);
        assertEquals(expected.x, actual.x, tolerance);
    }

    @Test void t1PersistentTargetT2T3T4FinitePointRoundTripAndInputEquivalence() {
        for (boolean split : new boolean[]{false, true}) for (double roll : new double[]{0, 30, 90})
            for (double position : new double[]{0, 2e7, -2e7}) {
                AimPose root = new AimPose(new Vec3(position, 7, -position),
                        rotation(new Vector3f(0, 0, 1), Math.toRadians(roll)));
                var b = rig(root, split);
                var q = deg(10, 20);
                var relative = q;
                var worldPoint = AimMath.buildAimPoint(b, q);
                for (int i = 0; i < 100; i++) {
                    var input = deg(.03, .05);
                    relative = AimMath.advanceRelativeAim(b, relative, input);
                    Vec2 before = AimMath.solveAim(b, worldPoint, relative).angles();
                    worldPoint = AimMath.buildAimPoint(b, AimMath.advanceRelativeAim(b, before, input));
                    same(relative, AimMath.solveAim(b, worldPoint, relative).angles(), 1e-5);
                }
                same(deg(13, 25), relative, 1e-6);
                for (var target : new Vec2[]{deg(-25, 35),deg(35, 179),deg(-5, -175)}) {
                    var branch = new Vec2((float) (target.x+.03), (float) (target.y-.05));
                    var solved = AimMath.solveAim(b,AimMath.buildAimPoint(b,target),branch);
                    same(target,solved.angles(),1e-5);
                    assertTrue(solved.errorRadians()<1e-5);
                }
            }
    }

    @Test void solveFromDifferentInitialBranchAndAntipodalTarget() {
        var b=rig(AimPose.IDENTITY,true);
        for (var target : new Vec2[]{deg(30, 60),deg(-25, -120),deg(0, 180)}) {
            var solved=AimMath.solveAim(b,AimMath.buildAimPoint(b,target),Vec2.ZERO);
            assertTrue(solved.errorRadians()<1e-5, solved.toString());
        }
    }

    @Test void t5BaseMotionPreservesWorldOrRelativeIntent() {
        var b=rig(AimPose.IDENTITY,true); var q=deg(10, 20); var p=AimMath.buildAimPoint(b,q);
        var moved = rig(new AimPose(new Vec3(10, 0, 0), rotation(new Vector3f(0, -1, 0), .2)), true);
        same(q, AimMath.advanceRelativeAim(moved, q, Vec2.ZERO), 0);
        assertTrue(AimMath.solveAim(moved, p, q).angles().y < q.y);
    }

    @Test void t6ContinuousAndBoundedAxesRespectOrderedInput() {
        var b=rig(AimPose.IDENTITY,false);
        same(deg(0, 181),AimMath.advanceRelativeAim(b,deg(0, 179),deg(0, 2)),1e-6);
        var q=AimMath.advanceRelativeAim(b,deg(79, 0),deg(5, 0));
        assertEquals(Math.toRadians(80),q.x,1e-6);
        q=AimMath.advanceRelativeAim(b,q,deg(-5, 0));
        assertEquals(Math.toRadians(75),q.x,1e-6);
    }

    @Test void t18SingleAxisCompensatesAndKeepsOtherAxis() {
        var b=rig(AimPose.IDENTITY,true); var initial=deg(10, 20); var p=AimMath.buildAimPoint(b,initial);
        for (boolean yaw : new boolean[]{true,false}) {
            var moved=rig(new AimPose(Vec3.ZERO,rotation(yaw ? new Vector3f(0,-1,0) : new Vector3f(1,0,0),.03)),true);
            var solved=AimMath.solveSingleAxisAim(moved,p,initial,yaw,initial);
            assertEquals(yaw ? initial.x : initial.y,yaw ? solved.angles().x : solved.angles().y,0);
            double before=angle(moved.forward(initial).forward(),p.subtract(moved.forward(initial).position()));
            assertTrue(solved.errorRadians()<before*.5);
            Vec2 after = AimMath.advanceRelativeAim(b, initial, yaw ? deg(2, 0) : deg(0, 2));
            assertNotEquals(p, AimMath.buildAimPoint(b, after));
        }
    }

    @Test void t19DegenerateSingleAxisIsFiniteAndDoesNotFlip() {
        var b=rig(AimPose.IDENTITY,false); var q=deg(0, 35);
        var axisPoint=b.forward(q).position().add(new Vec3(0,1000,0));
        var a=AimMath.solveSingleAxisAim(b,axisPoint,q,true,q);
        assertTrue(Double.isFinite(a.errorRadians()));
        assertEquals(0,a.angles().x,0);
        var repeat=AimMath.solveSingleAxisAim(b,axisPoint,q,true,a.angles());
        same(a.angles(),repeat.angles(),1e-5);
    }

    @Test void t24LeadFilterDoesNotClampExistingTargetOrAccumulateRejectedInput() {
        double l=AimMath.MAX_INPUT_LEAD_RAD;
        assertEquals(Math.toRadians(2),AimMath.filterLead(Math.toRadians(5),Math.toRadians(28),0,l),1e-12);
        assertEquals(0,AimMath.filterLead(.1,l,0,l));
        assertEquals(-.1,AimMath.filterLead(-.1,l,0,l));
        assertEquals(0,AimMath.filterLead(0,Math.toRadians(40),0,l));
        assertEquals(0,AimMath.filterLead(.1,Math.toRadians(40),0,l));
        assertEquals(-.1,AimMath.filterLead(-.1,Math.toRadians(40),0,l));
        assertEquals(.1,AimMath.filterLead(.1,l,Math.toRadians(10),l));
    }

    @Test void mouseScalingAndSignsAreShared() {
        var full=AimMath.mouse(5,3,1); var zoom=AimMath.mouse(5,3,8);
        assertEquals(full.y/8,zoom.y,1e-8);
        assertTrue(full.y>0); assertTrue(full.x<0);
    }

    @Test void nonzeroJointZerosAndAxisSignsRoundTripAcrossWorldPositions() {
        var random=new java.util.Random(20261024);
        for(int i=0;i<100;i++) {
            var b=new AimGeometrySnapshot(
                    new AimPose(new Vec3(2e7,70,-2e7),rotation(new Vector3f(1,2,3),.7)),
                    new Transform(new Vector3f(.4f,1.2f,-.3f),rotation(new Vector3f(0,1,0),.25)),
                    new Vector3f(0,i%2==0 ? -1 : 1,0),
                    new Transform(new Vector3f(.5f,.3f,-.9f),rotation(new Vector3f(1,0,0),-.15)),
                    new Vector3f(i%2==0 ? 1 : -1,0,0),
                    new Transform(new Vector3f(.3f,.2f,-2),rotation(new Vector3f(0,0,1),.08)),
                    AimAxisLimit.CONTINUOUS,new AimAxisLimit(false,-1.4,1.4));
            var q=new Vec2((float) (random.nextDouble(-1.1,1.1)), (float) (random.nextDouble(-3,3)));
            var seed=new Vec2((float) (q.x-.09), (float) (q.y+.12));
            var solved=AimMath.solveAim(b,AimMath.buildAimPoint(b,q),seed);
            same(q,solved.angles(),1e-5);
        }
    }
    @Test void snapshotCopiesMutableJmeInputs() {
        var rotation = new Quaternion();
        var root = new AimPose(new Vec3(2e7,70,-2e7),rotation);
        var frame = new Transform(new Vector3f(.3f,.2f,-2),Quaternion.IDENTITY);
        var axis = new Vector3f(0,-1,0);
        var b = new AimGeometrySnapshot(root,frame,axis,new Transform(),Vector3f.UNIT_X,new Transform(),
                AimAxisLimit.CONTINUOUS,AimAxisLimit.CONTINUOUS);
        var expected = b.forward(deg(10, 20));
        rotation.fromAngles(1,2,3);
        root.rotation().fromAngles(2,3,4);
        frame.setTranslation(new Vector3f(100,200,300));
        axis.set(1,0,0);
        assertEquals(expected,b.forward(deg(10, 20)));
        assertEquals(2e7 + .3f,b.forward(Vec2.ZERO).position().x,1e-8);
        assertEquals(70 + (double) .2f,b.forward(Vec2.ZERO).position().y,1e-8);
    }
    private static double angle(Vec3 a,Vec3 b) {
        return Math.acos(Math.clamp(a.normalize().dot(b.normalize()),-1,1));
    }
}
