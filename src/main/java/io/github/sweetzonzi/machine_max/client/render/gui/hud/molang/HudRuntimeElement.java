package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.visual.HudElement;
import io.github.sweetzonzi.machine_max.common.visual.IHudHost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 一个布局条目的数据与动画身份；模型和 AUI 载体都持有此对象。 */
public class HudRuntimeElement implements IAnimatable<HudElement> {
    public record Frame(HudViewSnapshot view, HudPartSnapshot part, HudWeaponSnapshot weapon, boolean selected,
                        HudBallisticTable ballistics) {
        public Frame(HudViewSnapshot view, HudPartSnapshot part, HudWeaponSnapshot weapon, boolean selected) {
            this(view, part, weapon, selected, HudBallisticTable.EMPTY);
        }
    }
    private final IHudHost host;
    private final HudElement element;
    private final @Nullable Level level;
    private ModelIndex modelIndex = new ModelIndex("entity", ResourceLocation.withDefaultNamespace("empty"));
    private final ModelController modelController = new ModelController(this);
    private final AnimController animController = new AnimController(this);
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private final ThreadLocal<HudMolangContext> contexts = ThreadLocal.withInitial(this::createContext);
    private final ThreadLocal<Frame> evaluationFrames = new ThreadLocal<>();
    private volatile Frame frame = new Frame(HudViewSnapshot.EMPTY, HudPartSnapshot.EMPTY, HudWeaponSnapshot.EMPTY, false);

    public HudRuntimeElement(IHudHost host, HudElement element) {
        this.host = host;
        this.element = element;
        this.level = host instanceof AbstractSubsystem subsystem ? subsystem.getLevel() : null;
    }

    protected HudMolangContext createContext() { return new HudMolangContext(); }
    public IHudHost host() { return host; }
    public Frame frame() {
        Frame pinned = evaluationFrames.get();
        return pinned != null ? pinned : frame;
    }

    /** 固定当前消费线程的一轮输入；JSMolangValue 多次 reset 时读取同一份快照。 */
    public FrameScope pinFrame() {
        Frame previous = evaluationFrames.get();
        evaluationFrames.set(previous != null ? previous : frame);
        return new FrameScope(previous);
    }

    public final class FrameScope implements AutoCloseable {
        private final Frame previous;
        private boolean closed;
        private FrameScope(Frame previous) { this.previous = previous; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) evaluationFrames.remove();
            else evaluationFrames.set(previous);
        }
    }

    public void publish(HudPartSnapshot part, HudWeaponSnapshot weapon, boolean selected) {
        frame = new Frame(frame.view(), part, weapon, selected, frame.ballistics());
    }

    public void publishView(HudViewSnapshot view) {
        Frame previous = frame;
        frame = new Frame(view, previous.part(), previous.weapon(), previous.selected(), previous.ballistics());
    }

    public void setBallistics(HudBallisticTable table) {
        var previous = frame;
        frame = new Frame(previous.view(), previous.part(), previous.weapon(), previous.selected(), table);
    }

    public boolean visible() { return element.selectionVisibility().visible(this instanceof WeaponHudElement, frame.selected()); }

    @Override
    public HudMolangContext getMolangContext() {
        var context = contexts.get();
        context.reset(this, context.getTimeS());
        return context;
    }

    public void setModelIndex(ModelIndex index) { modelIndex = index; }

    @Override public HudElement getAnimatable() { return element; }
    /** 与宿主绑定的客户端 Level，供 Spark 获取对应 PhysicsLevel 及安排动画任务。 */
    @Override public @Nullable Level getAnimLevel() { return level; }
    /** 屏幕 HUD 以视角快照中的相机位置作为 LOD 参考点。 */
    @Override public Vec3 getRenderPosition(Number partialTick) {
        var view = frame().view();
        return new Vec3(view.x(), view.y(), view.z());
    }
    @Override public ModelIndex getDefaultModelIndex() { return modelIndex; }
    @Override public ModelController getModelController() { return modelController; }
    @Override public AnimController getAnimController() { return animController; }
    @Override public Map<String, Object> getVariables() { return variables; }
    @Override public Matrix4f getWorldPositionMatrix(Number partialTick) { return new Matrix4f(); }
}
