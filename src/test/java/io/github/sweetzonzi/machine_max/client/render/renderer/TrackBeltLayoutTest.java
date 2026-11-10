package io.github.sweetzonzi.machine_max.client.render.renderer;

import io.github.sweetzonzi.machine_max.util.geometry.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TrackBeltLayoutTest {
    @Test
    void freeHeightFollowsProjectedGravityAndTheSpanLengthCap() {
        assertEquals(.08, BeltPath2D.freeSagHeight(2, 1), 1e-12);
        assertEquals(.12, BeltPath2D.freeSagHeight(Math.sqrt(6), 1), 1e-12);
        assertEquals(.12, BeltPath2D.freeSagHeight(3, 1), 1e-12);
        assertEquals(.06, BeltPath2D.freeSagHeight(3, -.5), 1e-12);
        assertEquals(0, BeltPath2D.freeSagHeight(3, 0), 1e-12);
    }

    @Test
    void supportFlappingKeepsNonEngagedTravelBelowThreeMillimeters() {
        var base = BeltPath2D.build(List.of(new Circle2D(0, 0, .4), new Circle2D(3, 0, .4)));
        var contact = base.wheelContact(0);
        var buffer = new BeltPath2D.SagBuffer();
        int lower = -1;
        for (int i = 0; i < base.segments().size(); i++) {
            var span = base.segments().get(i);
            if (span.isStraight() && span.start().y() < 0) lower = i;
        }
        assertTrue(lower >= 0);
        int count = base.linkCount(.1875);
        double[] heights = new double[base.segments().size()];
        var fixed = new TrackBeltLayout(base, contact, count, .1875, .4);
        for (int fps : new int[]{30, 60, 120}) {
            for (int phaseMs : new int[]{0, 13, 24}) {
                double[] min = new double[count], max = new double[count];
                java.util.Arrays.fill(min, Double.POSITIVE_INFINITY);
                java.util.Arrays.fill(max, Double.NEGATIVE_INFINITY);
                heights[lower] = 0;
                for (int frame = 0; frame < fps * 3; frame++) {
                    boolean supported = (frame * 1000 + phaseMs * fps) / (50 * fps) % 2 == 0;
                    double target = supported ? 0 : BeltPath2D.freeSagHeight(3, 1);
                    heights[lower] += TrackRenderer.sagBlendFactor(1.0 / fps) * (target - heights[lower]);
                    var path = base.withSag(new Vec2(0, -1), heights, buffer);
                    if (frame < fps) continue;
                    var layout = new TrackBeltLayout(path, contact, count, .1875, .4);
                    assertTrue(layout.isValid());
                    for (int i = 0; i < count; i++) {
                        var sample = layout.sample(i);
                        var baseline = fixed.sample(i);
                        if (baseline.edgeIndex() == -1) {
                            assertEquals(baseline.point(), sample.point());
                            assertEquals(.1875, layout.spacing(sample), 1e-12);
                        } else {
                            double travel = sample.arcLength() - baseline.arcLength();
                            min[i] = Math.min(min[i], travel);
                            max[i] = Math.max(max[i], travel);
                        }
                    }
                }
                for (int i = 0; i < count; i++) {
                    if (fixed.sample(i).edgeIndex() != -1) {
                        assertTrue(max[i] - min[i] < .003, "fps=" + fps + ", phaseMs=" + phaseMs
                                + ", link=" + i + ", travel=" + (max[i] - min[i]));
                    }
                }
            }
        }
    }

    private static List<Circle2D> wheels(double middle) {
        return List.of(new Circle2D(-2, 1, .4), new Circle2D(-1.5, -.6, .35),
                new Circle2D(0, middle, .35), new Circle2D(1.5, -.6, .35), new Circle2D(2, 1, .4));
    }

    private static List<Circle2D> rotate(List<Circle2D> wheels, double angle) {
        Vec2 origin = wheels.getFirst().center();
        return wheels.stream().map(c -> {
            Vec2 d = c.center().sub(origin);
            return new Circle2D(origin.x() + d.x()*Math.cos(angle)-d.y()*Math.sin(angle),
                    origin.y() + d.x()*Math.sin(angle)+d.y()*Math.cos(angle), c.radius());
        }).toList();
    }

    @Test
    void lockedSprocketRetainsMaterialPositionsAfterTravelAndSuspensionChange() {
        for (boolean clockwise : new boolean[]{false, true}) {
            for (double travel : new double[]{0, 10, 100, 1000}) {
                var first = BeltPath2D.build(wheels(-.6), 1e-9, clockwise);
                int count = first.linkCount(.19);
                double pitch = first.length()/count;
                var phase = new TrackBeltPhase();
                phase.advance(0, first.wheelContact(0).startAngle(), 0, false);
                double spin = travel/.4;
                double raw = Math.atan2(Math.sin(spin), Math.cos(spin));
                var a = BeltPath2D.build(rotate(wheels(-.6), -spin), 1e-9, clockwise);
                var b = BeltPath2D.build(rotate(wheels(-.5), -spin), 1e-9, clockwise);
                phase.advance(raw, a.wheelContact(0).startAngle()+raw, spin, false);
                var la = layout(a, phase, raw, pitch, count, 1);
                phase.advance(raw, b.wheelContact(0).startAngle()+raw, 0, false);
                var lb = layout(b, phase, raw, pitch, count, 1);
                int compared = 0;
                for(int i=0;i<count;i++) {
                    var sa=la.sample(i); var sb=lb.sample(i);
                    if(sa.edgeIndex()==-1 && sb.edgeIndex()==-1) {
                        assertEquals(0, sa.point().distance(sb.point()), 1e-10);
                        compared++;
                    }
                }
                assertTrue(compared > 1);
            }
        }
    }

    private static TrackBeltLayout layout(BeltPath2D path, TrackBeltPhase phase,
                                           double wheel, double pitch, int count, float t) {
        var arc=path.wheelContact(0);
        double label=phase.label(wheel, arc.startAngle()+wheel,t,.4/pitch,Math.signum(arc.sweep()));
        var result=new TrackBeltLayout(path,arc,count,pitch,label);
        assertTrue(result.isValid());
        return result;
    }

    @Test
    void movingEntryPointDoesNotDragEngagedLinks() {
        var a=BeltPath2D.build(wheels(-.6));
        var b=BeltPath2D.build(rotate(wheels(-.6),.1));
        int count=a.linkCount(.19); double pitch=a.length()/count;
        var phase=new TrackBeltPhase();
        phase.advance(0,a.wheelContact(0).startAngle(),0,false);
        var la=layout(a,phase,0,pitch,count,1);
        phase.advance(0,b.wheelContact(0).startAngle(),0,false);
        var lb=layout(b,phase,0,pitch,count,1);
        int compared=0;
        for(int i=0;i<count;i++) if(la.sample(i).edgeIndex()==-1 && lb.sample(i).edgeIndex()==-1) {
            assertEquals(0,la.sample(i).point().distance(lb.sample(i).point()),1e-10); compared++;
        }
        assertTrue(compared>1);
    }

    @Test
    void fallbackCarriesExposedSprocketContactAndCanRecover() {
        for (boolean clockwise : new boolean[]{false, true}) {
        var path=BeltPath2D.convexFallback(wheels(-.6),clockwise);
        var arc=path.wheelContact(0);
        assertNotNull(arc);
        assertEquals(clockwise ? -1 : 1,Math.signum(arc.sweep()));
        var phase=new TrackBeltPhase(); phase.advance(0,arc.startAngle(),0,false);
        var before=layout(path,phase,0,path.length()/67,67,1);
        var normal=BeltPath2D.build(wheels(-.6),1e-9,clockwise);
        phase.advance(0,normal.wheelContact(0).startAngle(),0,false);
        var after=layout(normal,phase,0,path.length()/67,67,1);
        int compared=0;
        for(int i=0;i<67;i++) if(before.sample(i).edgeIndex()==-1 && after.sample(i).edgeIndex()==-1) {
            assertEquals(0,before.sample(i).point().distance(after.sample(i).point()),1e-10); compared++;
        }
        assertTrue(compared>1);
        }
    }

    @Test
    void poseDrivesPhaseAcrossAngleWrapAndBrokenTransportStaysFrozen() {
        var phase=new TrackBeltPhase();
        phase.advance(3,1,0,false);
        phase.advance(-3,1,.3,false);
        assertEquals(-(2*Math.PI-6),phase.label(-3,1,1,1,1),1e-12);
        double locked=phase.label(-3,1,1,1,1);
        phase.advance(-2,1,1,true);
        assertEquals(locked,phase.label(-2,1,1,1,1),1e-12);
        phase.advance(-1,1,1,false);
        assertEquals(locked,phase.label(-1,1,1,1,1),1e-12);
        phase.advance(0,1,1,false);
        assertEquals(locked-1,phase.label(0,1,1,1,1),1e-12);
    }

    @Test
    void partialFramesFollowActualPoseRatherThanLinearAngularPrediction() {
        var phase=new TrackBeltPhase();
        phase.advance(0,1,0,false);
        phase.advance(1,1,1,false);
        for(float t:new float[]{0,.2f,.5f,.9f,1}) {
            double actual=t*t;
            assertEquals(-actual,phase.label(actual,1,t,1,1),1e-12);
        }
    }

    @Test
    void engagedLinkRotatesExactlyWithSprocket() {
        var a=BeltPath2D.build(wheels(-.6));
        double spin=.15;
        var b=BeltPath2D.build(rotate(wheels(-.6),-spin));
        int count=a.linkCount(.19); double pitch=a.length()/count;
        var phase=new TrackBeltPhase();
        phase.advance(0,a.wheelContact(0).startAngle(),0,false);
        var la=layout(a,phase,0,pitch,count,1);
        phase.advance(spin,b.wheelContact(0).startAngle()+spin,spin,false);
        var lb=layout(b,phase,spin,pitch,count,1);
        int compared=0;
        for(int i=0;i<count;i++) if(la.sample(i).edgeIndex()==-1 && lb.sample(i).edgeIndex()==-1) {
            assertEquals(0,la.sample(i).point().distance(lb.sample(i).point()),1e-10); compared++;
        }
        assertTrue(compared>1);
    }

    @Test
    void exitAndLoopSeamAreContinuousAndLabelsRepeatOnlyAfterCountLinks() {
        var path=BeltPath2D.build(wheels(-.6)); var arc=path.wheelContact(0);
        double pitch=path.length()/67, engaged=arc.contactLength()/pitch;
        var before=new TrackBeltLayout(path,arc,67,pitch,-engaged+1e-8);
        var after=new TrackBeltLayout(path,arc,67,pitch,-engaged-1e-8);
        assertTrue(before.sample(0).point().distance(after.sample(0).point())<1e-7);
        before=new TrackBeltLayout(path,arc,67,pitch,1e-8);
        after=new TrackBeltLayout(path,arc,67,pitch,-1e-8);
        assertTrue(before.sample(0).point().distance(after.sample(0).point())<1e-7);
        for(int i=0;i<67;i++) {
            assertEquals(0,after.sample(i).point().distance(after.sample(i+67).point()),1e-10);
            for(int j=0;j<i;j++) assertTrue(after.sample(i).point().distance(after.sample(j).point())>1e-6);
        }
    }
}
