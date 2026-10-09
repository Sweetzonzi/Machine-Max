package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.viewport.ApricityViewport;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem.WeaponRole;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierStatus;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenLoaderSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem.LoaderEntry;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 武器状态 HUD 叠层：管理 AUI Overlay Document 的创建、显隐与内容刷新。
 *
 * <p>逐帧绘制由 AUI 自身完成，本类每个客户端 tick 做两件事：按“是否乘坐激活座椅、该座椅是否有
 * 激活的武器控制器、是否处于炮镜模式”同步显隐，以及把武器状态写进已有槽位。</p>
 *
 * <h3>与 AUI 失效机制的分工</h3>
 *
 * <p>元素结构只在载具结构变化时重建，此后每个 tick 只改取值。这一分工的依据是 AUI 对三类写入的
 * 处理方式并不相同：</p>
 *
 * <ul>
 *   <li><b>文本</b>：叶子元素的 {@code innerText} 由 AUI 自己逐帧比对新旧值，只有真正变化才清文本缓存
 *       并标脏，因此可以每 tick 无条件赋值，取值未变时只有一次字符串比较的成本；</li>
 *   <li><b>类名</b>：{@code getClassList()} 的增删在 token 级别有变更守卫，未变时不触发样式重算，
 *       因此状态类按“记住上一次写过的 token”的方式改写；</li>
 *   <li><b>整段 HTML 与属性</b>：{@code setInnerHTML} 会拆掉全部子节点并重新解析，{@code setAttribute}
 *       会无条件排一次子树样式重算，两者都没有内容比较，因此都不放在每 tick 的刷新路径里。</li>
 * </ul>
 *
 * <p>文档一经创建即保留复用：不可见时只切隐藏类，不销毁文档，避免反复创建重建布局。
 * 退出世界时销毁，避免残留上一个服务器的资源。</p>
 *
 * <p><b>调用线程：</b>主线程（客户端 tick）。</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public final class WeaponHudOverlay {

    /** AUI 文档路径（相对 assets/apricityui/apricity/，与 weapon_hud.css 同目录）。 */
    private static final String AUI_DOC_PATH = "machine_max/weapon_hud/weapon_hud.html";
    /** 文档根元素上的隐藏类：样式表据此让叠层整体不参与布局与绘制。 */
    private static final String HIDDEN_CLASS = "weapon-hud-hidden";
    /** 元素结构的挂载点。 */
    private static final String BODY_ID = "weapon-hud";

    // ——— 叠层缩放 ———

    /** 面板右下角到视口下边的留边，与 weapon_hud.css 里 .hud-overlay 的 bottom 一致。 */
    private static final double OVERLAY_BOTTOM_OFFSET = 80.0;
    /** 面板右边到视口右边的留边，与 .hud-overlay 的 right 一致。 */
    private static final double OVERLAY_RIGHT_OFFSET = 4.0;
    /** 缩放后至少给视口上边与左边留出的空白。 */
    private static final double FIT_MARGIN = 4.0;
    /** 面板缩放下限：视口再小也不缩到设计尺寸的 35% 以下。 */
    private static final double MIN_FIT_SCALE = 0.35;
    /** 缩放系数的写回阈值：变化小于此值就不重写内联样式。 */
    private static final double FIT_SCALE_EPSILON = 0.01;

    // ——— 固定文案的语言键 ———

    private static final String KEY_ROLE_TAG_PRIMARY = "gui.machine_max.weapon_hud.role.tag.primary";
    private static final String KEY_ROLE_TAG_SECONDARY = "gui.machine_max.weapon_hud.role.tag.secondary";
    private static final String KEY_ROLE_TITLE_PRIMARY = "gui.machine_max.weapon_hud.role.title.primary";
    private static final String KEY_ROLE_TITLE_SECONDARY = "gui.machine_max.weapon_hud.role.title.secondary";
    private static final String KEY_READY = "gui.machine_max.weapon_hud.ready";
    private static final String KEY_EMPTY = "gui.machine_max.weapon_hud.empty";

    // ——— 状态类与占位文本 ———

    private static final String STATE_READY = "is-ready";
    private static final String STATE_RELOADING = "is-reloading";
    private static final String STATE_EMPTY = "is-empty";
    private static final String SEG_LOADING = "is-loading";
    private static final String SEG_OFF = "is-off";
    private static final String TAB_SELECTED = "is-on";
    /** 弹种槽的"膛内弹种不在供弹弹链上"标（样式表把它染成警示黄）。 */
    private static final String AMMO_MISMATCH = "is-mismatch";
    /** 无供给者时的弹种槽占位文本。 */
    private static final String NO_ROUND = "---";
    /** 无容量概念时的容量文本。 */
    private static final String INFINITE_CAPACITY = "\u221E";
    /** 装填条填充比例按 10% 一档量化，档位对应样式表的 fill-0 ~ fill-100。 */
    private static final int FILL_STEPS = 10;

    @Nullable
    private static Document overlayDocument;
    /** 当前挂载点；与文档里查到的实例不一致时说明文档被重建，槽位句柄全部作废。 */
    @Nullable
    private static Element bodyElement;
    /** 上次建立元素结构时的形状；形状未变则复用已有元素，只改写取值。 */
    private static String shownShape = "";
    /** 与 {@link #shownShape} 对应的槽位句柄，顺序与 {@link #collect} 的产出一致。 */
    private static final List<GroupSlots> groupSlots = new ArrayList<>();
    /** 已写进面板内联样式的缩放系数；-1 表示当前挂载点上还没写过。 */
    private static double appliedFitScale = -1.0;
    /** 上一次输出的诊断日志全文；内容未变时不重复输出（刷新每 tick 一次，不设门槛会刷屏）。 */
    private static String lastDiagnostics = "";

    private WeaponHudOverlay() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        refresh();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        dispose();
    }

    /** 按当前状态同步显隐并刷新内容；首次需要显示时创建文档。 */
    public static void refresh() {
        Map<WeaponRole, List<WeaponControllerSubsystem>> byRole = activeWeaponControllersByRole();
        List<GroupData> groups = collect(byRole);
        // 无内容可显示，或炮镜模式下分划板会被遮挡 → 隐藏
        if (groups.isEmpty() || CameraController.isCameraMode()) {
            if (overlayDocument != null) setHidden(true);
//            logDiagnostics(byRole, groups);
            return;
        }
        if (overlayDocument == null) {
            overlayDocument = ApricityUI.createDocument(AUI_DOC_PATH);
            if (overlayDocument == null) return;
            bodyElement = null;
        }
        setHidden(false);
        // 文档被重建或重载时挂载点会换实例，此时已有槽位句柄全部作废，重新建结构
        Element body = overlayDocument.getElementById(BODY_ID);
        if (body == null) return;
        if (body != bodyElement) {
            bodyElement = body;
            shownShape = "";
            groupSlots.clear();
            appliedFitScale = -1.0;
        }
        String shape = shapeOf(groups);
        if (!shape.equals(shownShape)) {
            shownShape = shape;
            rebuild(groups);
        }
        update(groups);
        applyFitScale();
//        logDiagnostics(byRole, groups);
    }

    /** 移除文档。幂等。 */
    public static void dispose() {
        if (overlayDocument != null) {
            overlayDocument.remove();
            overlayDocument = null;
        }
        bodyElement = null;
        shownShape = "";
        groupSlots.clear();
        appliedFitScale = -1.0;
    }

    // ==================== 诊断日志 ====================

    /**
     * 把 HUD 本帧看到的原始数据打一行日志，用于定位"显示与预期不符"出在取数还是出在别处。
     *
     * <p>内容是逐 tick 的 <b>快照</b>，只在整段文本变化时输出一次，因此进座、开火、装填、切弹链
     * 各会留下若干行，静止时不再输出。它回答的是这些问题：座位与角色分组有没有拿到控制器；
     * 控制器一共认到几根管、几根是激活的；弹药池里有哪些装弹机、余量各是多少；每根管的膛内弹种、
     * 装填条的档位与它当前绑定的供给者（用于分辨"哪根管现在能打"与"绑定有没有摊开"）；
     * 以及弹种槽最终算出的文本与数字。</p>
     */
    private static void logDiagnostics(Map<WeaponRole, List<WeaponControllerSubsystem>> byRole,
                                       List<GroupData> groups) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("seat=").append(seatDescription())
                .append(" camera=").append(CameraController.isCameraMode())
                .append(" roles=").append(describeRoles(byRole))
                .append(" groups=").append(groups.size());
        for (GroupData group : groups) {
            for (WeaponData weapon : group.weapons()) {
                WeaponControllerSubsystem wc = weapon.controller();
                sb.append("\n  [").append(group.role()).append("] wc=").append(wc.getName())
                        .append(" launchers=").append(wc.getLaunchers().size())
                        .append("(active ").append(weapon.launchers().size()).append(')')
                        .append(" segbar=").append(weapon.showsSegbar())
                        .append(" belts=").append(weapon.belts().size())
                        .append(" pool=").append(describePool(wc))
                        .append(" slot=").append(describeSlot(weapon));
                for (LauncherSubsystem launcher : weapon.launchers()) {
                    ProjectileType chambered = launcher.getChamberedType();
                    Segment segment = segmentOf(launcher);
                    sb.append("\n    tube=").append(launcher.getName())
                            .append(" candidates=").append(launcher.getSuppliers().size())
                            .append(" chambered=").append(roundName(chambered))
                            .append(" seg=").append(segment.status())
                            .append("/").append(String.format(Locale.ROOT, "%.2f", segment.progress()))
                            .append(" bound=").append(describeSupplier(launcher.getCurrentSupplier()));
                }
            }
        }
        String diagnostics = sb.toString();
        if (diagnostics.equals(lastDiagnostics)) return;
        lastDiagnostics = diagnostics;
        MachineMax.LOGGER.info("[武器 HUD] {}", diagnostics);
    }

    /** 角色分组的控制器名（只打名字，子系统默认 toString 不带名字）。 */
    private static String describeRoles(Map<WeaponRole, List<WeaponControllerSubsystem>> byRole) {
        if (byRole.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder(64);
        for (Map.Entry<WeaponRole, List<WeaponControllerSubsystem>> entry : byRole.entrySet()) {
            sb.append(entry.getKey()).append('=');
            for (WeaponControllerSubsystem wc : entry.getValue()) {
                sb.append(wc.getName()).append(',');
            }
            if (sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    /** 当前控制本玩家的座位（含激活与否）；未乘座时为 none。 */
    private static String seatDescription() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return "none(player null)";
        Object controlling = ((IEntityMixin) player).machine_Max$getControllingSubsystem();
        if (controlling instanceof SeatSubsystem seat) {
            return seat.getName() + (seat.isActive() ? "(active)" : "(inactive)");
        }
        return "none";
    }

    /** 弹药池全貌：每条弹链一行，列出该弹链的全部装弹机及其余量。 */
    private static String describePool(WeaponControllerSubsystem wc) {
        if (wc.getAmmoPool().isEmpty()) return "empty";
        StringBuilder sb = new StringBuilder(128);
        for (Map.Entry<List<ResourceLocation>, List<LoaderEntry>> entry : wc.getAmmoPool().entrySet()) {
            sb.append(beltDescription(entry.getKey())).append('{');
            for (LoaderEntry loaderEntry : entry.getValue()) {
                sb.append(describeSupplier(loaderEntry.loader())).append(' ');
            }
            sb.append("} ");
        }
        return sb.toString().trim();
    }

    /** 弹种槽最终算出的文本，与 {@link #updateWeapon} 同一套取数：弹种取第一根有膛内弹的管，计数取供弹弹链的池。 */
    private static String describeSlot(WeaponData weapon) {
        List<ResourceLocation> fillingBelt = fillingBeltOf(weapon, weapon.belts());
        List<LoaderEntry> entries = weapon.controller().getAmmoPool().get(fillingBelt);
        ProjectileType chambered = chamberedRound(weapon.launchers());
        Totals totals = entries != null
                ? totalsOf(poolLoaders(entries))
                : totalsOf(boundSuppliers(weapon.launchers()));
        String label = chambered != null
                ? chambered.getDisplayLabel()
                : roundLabelOf(nextRoundSupplier(weapon.launchers()));
        return (label.isEmpty() ? NO_ROUND : label) + " " + totals.remaining() + "/"
                + (totals.infinite() ? INFINITE_CAPACITY : String.valueOf(totals.capacity()))
                + " (chambered=" + roundName(chambered) + ", pool="
                + (entries != null ? entries.size() : 0) + ")";
    }

    /** 弹种的注册键路径，未解码时写 {@code empty} / {@code unregistered}（诊断日志用，不参与显示）。 */
    private static String roundName(@Nullable ProjectileType type) {
        if (type == null) return "empty";
        ResourceLocation key = type.getRegistryKey();
        return key != null ? key.getPath() : "unregistered";
    }

    /** 供给者一行摘要：子系统名 + 余量 / 容量 / 装填耗时。 */
    private static String describeSupplier(@Nullable IAmmoSupplier supplier) {
        if (supplier == null) return "none";
        String name = supplier instanceof AbstractSubsystem subsystem
                ? subsystem.getName() : supplier.getClass().getSimpleName();
        return name + "(rem=" + supplier.getRemainingCount()
                + ",cap=" + supplier.getCapacity()
                + ",reload=" + supplier.getReloadTimeTicks() + ")";
    }

    /** 弹链摘要：`首发弹种×长度`，够看出池是按哪条弹链分组的。 */
    private static String beltDescription(List<ResourceLocation> belt) {
        if (belt.isEmpty()) return "[]";
        return belt.getFirst().getPath() + "x" + belt.size();
    }

    // ==================== 叠层缩放 ====================
    /**
     * 按视口尺寸给面板写一个不超过 1 的缩放系数，让面板整个落在视口内。
     *
     * <p>面板尺寸是 CSS 像素给定的（宽 300，高由内容决定），而 overlay 文档的视口就是
     * GUI 缩放后的窗口尺寸：窗口小于全屏时视口变矮，固定在右下角的面板会从顶边溢出视口并被裁掉。
     * 这里算出能容纳面板的最大缩放，再由 {@code transform: scale()} 施加。</p>
     *
     * <p>可容纳空间直接取 {@code document.getViewport()} 的 layout 尺寸：AUI 解析
     * {@code position: fixed} 时用的就是这一对值（{@code Position.viewportContainingBlockSize}），
     * 所以不必自己按窗口尺寸与 GUI 缩放重算，两者不会算错位。</p>
     *
     * <p>缩放锚点在样式表的 {@code transform-origin: bottom right}；transform 不参与布局，
     * {@link Element#getOffsetHeight()} 量到的始终是设计尺寸，因此“量高度 → 算缩放 → 写样式”
     * 三步不会互相追逐。</p>
     *
     * <p>{@code setInlineStyleProperty} 每次都排一次样式重算，所以只在系数真的变化时写回。</p>
     */
    private static void applyFitScale() {
        if (overlayDocument == null || bodyElement == null) return;
        ApricityViewport viewport = overlayDocument.getViewport();
        double layoutWidth = viewport.layoutWidth();
        double layoutHeight = viewport.layoutHeight();
        double panelWidth = bodyElement.getOffsetWidth();
        double panelHeight = bodyElement.getOffsetHeight();
        // 文档刚创建、布局尚未提交时，视口还是构造期的 1×1 占位、元素尺寸为 0：本 tick 不缩放
        if (layoutWidth < 64 || layoutHeight < 64 || panelWidth < 8 || panelHeight < 8) return;
        double fit = Math.min(
                (layoutHeight - OVERLAY_BOTTOM_OFFSET - FIT_MARGIN) / panelHeight,
                (layoutWidth - OVERLAY_RIGHT_OFFSET - FIT_MARGIN) / panelWidth);
        double scale = Math.max(MIN_FIT_SCALE, Math.min(1.0, fit));
        if (Math.abs(scale - appliedFitScale) < FIT_SCALE_EPSILON) return;
        appliedFitScale = scale;
        bodyElement.setInlineStyleProperty("transform",
                "scale(" + String.format(Locale.ROOT, "%.3f", scale) + ")");
    }

    // ==================== 取数 ====================

    /**
     * 当前座位下按角色分组的激活武器控制器；未乘坐座椅或座椅未激活时返回空表。
     */
    private static Map<WeaponRole, List<WeaponControllerSubsystem>> activeWeaponControllersByRole() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return Map.of();
        if (!(((IEntityMixin) player).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat)) {
            return Map.of();
        }
        if (!seat.isActive()) return Map.of();
        return seat.getActiveWeaponControllersByRole();
    }

    /**
     * 收集本帧的显示数据：角色 → 武器控制器 → 发射器，逐一滤掉已摧毁与未激活者。
     */
    private static List<GroupData> collect(Map<WeaponRole, List<WeaponControllerSubsystem>> byRole) {
        List<GroupData> groups = new ArrayList<>(byRole.size());
        for (Map.Entry<WeaponRole, List<WeaponControllerSubsystem>> entry : byRole.entrySet()) {
            List<WeaponData> weapons = new ArrayList<>(entry.getValue().size());
            for (WeaponControllerSubsystem wc : entry.getValue()) {
                if (wc.isDestroyed() || !wc.isActive()) continue;
                List<LauncherSubsystem> launchers = new ArrayList<>();
                for (LauncherSubsystem launcher : wc.getLaunchers().keySet()) {
                    if (!launcher.isDestroyed() && launcher.isActive()) launchers.add(launcher);
                }
                // 按名字排序：launchers 是无序 Map，装填条的段与弹种槽（取第一根有膛内弹的管）都按本列表
                // 取数，不定序会让同一根管换段位、弹种槽的文字在两根管之间跳
                launchers.sort(Comparator.comparing(LauncherSubsystem::getName));
                weapons.add(new WeaponData(wc, launchers, wc.getAvailableBelts(), showsSegbar(launchers, wc)));
            }
            if (!weapons.isEmpty()) groups.add(new GroupData(entry.getKey(), weapons));
        }
        return groups;
    }

    /**
     * 装填条是否出现：多管时一定出现，单管时只在确有装填机制时出现。
     * <p>
     * 多管必现，是因为它是唯一能表达“哪根管空了”的槽位：K-17 的 ATGM 控制器下是四根发射管，
     * 每根管一台 {@code regen_loader}（`reload_time` 为 0，按 {@code regen_per_minute} 逐发再生），
     * 这时 {@link #hasReloadMechanism} 判为无装填机制，但每根管的“有弹 / 空弹”仍然互不相同，
     * 用一段表示一根管才有意义。
     * <p>
     * 单管且无装填机制时不出现（弹链机枪）：此时一段的填充只等于弹种槽的计数，
     * 没有额外信息，原型用例 C 就是这种形态。
     */
    private static boolean showsSegbar(List<LauncherSubsystem> launchers, WeaponControllerSubsystem wc) {
        return launchers.size() > 1 || hasReloadMechanism(wc);
    }

    /**
     * 该控制器是否有装填机制：判据是弹药池里任一装弹机的装填耗时为正。
     * <p>
     * 取弹药池而不是当前供给者，是因为供给者会随弹链切换瞬时为空，
     * 用它判定会让装填条在结构未变时忽现忽隐。
     * <p>
     * 这是单管武器的判据；多管武器不看它，见 {@link #showsSegbar}。
     */
    private static boolean hasReloadMechanism(WeaponControllerSubsystem wc) {
        for (List<LoaderEntry> entries : wc.getAmmoPool().values()) {
            for (LoaderEntry entry : entries) {
                if (entry.loader().getReloadTimeTicks() > 0) return true;
            }
        }
        return false;
    }

    /**
     * 结构形状：角色、武器块数、每块的发射器数 / 装填条有无 / 选项卡条数。
     * <p>
     * 这些量决定要建哪些元素，其余量（名称、弹种、计数、状态、选中态）都写在已有元素上，
     * 因此它们变化不会触发重建。
     */
    private static String shapeOf(List<GroupData> groups) {
        StringBuilder sb = new StringBuilder(64);
        for (GroupData group : groups) {
            sb.append(group.role().ordinal()).append(':').append(group.weapons().size());
            for (WeaponData weapon : group.weapons()) {
                sb.append('[').append(weapon.launchers().size())
                        .append(',').append(weapon.showsSegbar() ? 1 : 0)
                        .append(',').append(weapon.belts().size() > 1 ? weapon.belts().size() : 0)
                        .append(']');
            }
            sb.append('|');
        }
        return sb.toString();
    }

    // ==================== 元素结构 ====================

    /** 按当前形状重建整棵内容树，并记下可改写的槽位句柄。 */
    private static void rebuild(List<GroupData> groups) {
        if (bodyElement == null) return;
        bodyElement.clearChildren();
        groupSlots.clear();
        for (GroupData group : groups) {
            // 角色组之间画一条分隔线（原型页在两个 .hud-group 之间就是一条 .hud-divider）
            if (!groupSlots.isEmpty()) create(bodyElement, "div", "hud-divider");
            boolean primary = group.role() == WeaponRole.MAIN;
            String variant = primary ? "primary" : "secondary";
            Element groupEl = create(bodyElement, "div", "hud-group hud-group--" + variant);
            Element head = create(groupEl, "div", "group-head");
            GroupSlots slots = new GroupSlots(
                    create(head, "span", "role-tag role-tag--" + variant),
                    create(head, "span", "group-title"));
            for (WeaponData weapon : group.weapons()) {
                slots.weapons.add(buildWeapon(groupEl, weapon));
            }
            groupSlots.add(slots);
        }
    }

    /** 一个武器块 = 一个武器控制器：名称 / 弹种槽 / 计数 / 尾标 / 装填条 / 弹链选项卡。 */
    private static WeaponSlots buildWeapon(Element parent, WeaponData weapon) {
        Element block = create(parent, "div", "w " + STATE_READY);
        Element head = create(block, "div", "w-head");
        Element name = create(head, "span", "w-name");
        Element ammo = create(head, "span", "w-ammo");
        Element count = create(head, "span", "w-count");
        Element current = create(count, "span", "w-cur");
        Element capacity = create(count, "span", "w-cap");
        Element ready = create(head, "span", "w-ready");
        WeaponSlots slots = new WeaponSlots(block, name, ammo, current, capacity, ready);

        // 装填条：一段 = 一发射器，仅在多管或确有装填机制时出现（见 showsSegbar）
        if (weapon.showsSegbar()) {
            Element segbar = create(block, "div", "w-segbar");
            for (int i = 0; i < weapon.launchers().size(); i++) {
                Element seg = create(segbar, "span", "seg");
                Element fill = create(seg, "i", fillClass(0));
                slots.segments.add(seg);
                slots.segStates.add("");
                slots.fills.add(fill);
                slots.fillClasses.add(fillClass(0));
            }
        }

        // 弹链选项卡：每条可选弹链一条，仅当可选弹链数 > 1 时出现
        if (weapon.belts().size() > 1) {
            Element tabs = create(block, "div", "w-tabs");
            for (int i = 0; i < weapon.belts().size(); i++) {
                Element tab = create(tabs, "span", "tab");
                slots.tabs.add(tab);
                slots.tabLabels.add(create(tab, "span", "tab-label"));
                slots.tabCounts.add(create(tab, "b", "tab-count"));
                slots.tabSelected.add(false);
            }
        }
        return slots;
    }

    /**
     * 建一个元素并挂到父节点上，返回挂进树里的那个实例。
     * <p>
     * 必须用 {@code appendChild} 的返回值而不是 {@code createElement} 的返回值：AUI 在挂载时用
     * {@code Element.init()} 把通用 {@code Element} 换成标签对应的子类实例（div → Div、span → Span…），
     * {@code append} 丢弃新实例，只有 {@code appendChild} 把它交回调用方。属性写在挂载之后，
     * 因此写的一定是留在树里的那个实例。
     */
    private static Element create(@Nullable Element parent, String tag, String className) {
        Element element = overlayDocument.createElement(tag);
        if (parent != null) element = parent.appendChild(element);
        element.setAttribute("class", className);
        return element;
    }

    // ==================== 取值写入 ====================

    /** 把本帧的取值写进已有槽位。 */
    private static void update(List<GroupData> groups) {
        for (int g = 0; g < groups.size() && g < groupSlots.size(); g++) {
            GroupData group = groups.get(g);
            GroupSlots slots = groupSlots.get(g);
            boolean primary = group.role() == WeaponRole.MAIN;
            setText(slots.roleTag, text(primary ? KEY_ROLE_TAG_PRIMARY : KEY_ROLE_TAG_SECONDARY));
            setText(slots.title, text(primary ? KEY_ROLE_TITLE_PRIMARY : KEY_ROLE_TITLE_SECONDARY));
            List<WeaponData> weapons = group.weapons();
            for (int w = 0; w < weapons.size() && w < slots.weapons.size(); w++) {
                updateWeapon(weapons.get(w), slots.weapons.get(w));
            }
        }
    }

    private static void updateWeapon(WeaponData data, WeaponSlots slots) {
        List<LauncherSubsystem> launchers = data.launchers();
        List<List<ResourceLocation>> belts = data.belts();
        // 供弹弹链：轮切过取 selectedBelt，没轮切过取第一条可用弹链（见 fillingBeltOf）
        List<ResourceLocation> fillingBelt = fillingBeltOf(data, belts);

        // 弹种槽的口径是"膛内那一发"：取第一根膛内有弹的管。四根管可能各装一种弹，槽位只有一个，
        // 因此按显示顺序取第一根；都没有膛内弹时退回装弹机弹链指针（下一发是什么）
        ProjectileType chambered = chamberedRound(launchers);
        String roundLabel = chambered != null
                ? chambered.getDisplayLabel()
                : roundLabelOf(nextRoundSupplier(launchers));
        setText(slots.ammo, roundLabel.isEmpty() ? NO_ROUND : roundLabel);
        // 膛内那发不属于当前供弹弹链（例如刚轮切了弹链、膛内还是旧链的弹）：打出去的不是选项卡上写的
        // 那种弹，标出来提醒玩家。膛内为空、弹种没有注册键、或该控制器没有可枚举的弹链（物理弹仓）时不标
        ResourceLocation chamberedKey = chambered != null ? chambered.getRegistryKey() : null;
        setAmmoMismatch(slots, chamberedKey != null && !fillingBelt.isEmpty()
                && !fillingBelt.contains(chamberedKey));

        // 计数是该**弹链**的：供弹弹链在弹药池里的全部装弹机之和。取池而不是取"现在被发射器绑定的
        // 那几台"——绑定每 tick 都可能变（只有一台有余弹时多根管会一起挤到它上面、余弹归零的管又会
        // 短暂无主），按绑定求和会让同一批弹药显示成 1/1；池是按弹链建立的快照，与路由无关，两端一致。
        // 无弹链的供给者（AmmoLoaderSubsystem 的物理弹仓）不进池，回退到已绑定供给者（按实例去重）。
        List<LoaderEntry> fillingEntries = data.controller().getAmmoPool().get(fillingBelt);
        Totals totals = fillingEntries != null
                ? totalsOf(poolLoaders(fillingEntries))
                : totalsOf(boundSuppliers(launchers));
        setText(slots.name, text(data.controller().getName()));
        setText(slots.current, String.valueOf(totals.remaining()));
        setText(slots.capacity, "/" + (totals.infinite() ? INFINITE_CAPACITY : String.valueOf(totals.capacity())));

        // 逐发射器读段状态：一段 = 一根管。膛内有弹 → 常亮（这根管现在就能打）；
        // 空膛 → 看它的供给者（装填中按装填进度、逐发再生的型号按再生进度）；没有供给者 → 熄灭
        boolean allEmpty = !launchers.isEmpty();
        boolean anyReloading = false;
        int slowestRemainingTicks = 0;
        float maxReloadingProgress = 0f;
        for (int i = 0; i < launchers.size(); i++) {
            Segment segment = segmentOf(launchers.get(i));
            SupplierStatus status = segment.status();
            if (i < slots.fills.size()) {
                setFill(slots, i, switch (status) {
                    case READY -> FILL_STEPS;
                    case RELOADING -> Math.round(segment.progress() * FILL_STEPS);
                    case EMPTY -> 0;
                });
                setSegState(slots, i, switch (status) {
                    case READY -> "";
                    case RELOADING -> SEG_LOADING;
                    case EMPTY -> SEG_OFF;
                });
            }
            if (status != SupplierStatus.EMPTY) allEmpty = false;
            if (status == SupplierStatus.RELOADING) {
                anyReloading = true;
                slowestRemainingTicks = Math.max(slowestRemainingTicks,
                        Math.round(segment.reloadTicks() * (1f - segment.progress())));
                maxReloadingProgress = Math.max(maxReloadingProgress, segment.progress());
            }
        }

        String stateClass;
        String readyText;
        if (allEmpty) {
            stateClass = STATE_EMPTY;
            readyText = text(KEY_EMPTY);
        } else if (anyReloading) {
            stateClass = STATE_RELOADING;
            // 有装填耗时概念的型号报剩余秒数，其余（批量冷却等）报装填进度百分比
            readyText = slowestRemainingTicks > 0
                    ? String.format(Locale.ROOT, "%.1fs", slowestRemainingTicks / 20f)
                    : String.format(Locale.ROOT, "%d%%", Math.round(maxReloadingProgress * 100f));
        } else {
            stateClass = STATE_READY;
            readyText = text(KEY_READY);
        }
        setWeaponState(slots, stateClass);
        setText(slots.ready, readyText);

        // 弹链选项卡：文本取弹链名，计数取该弹链全部装弹机的余量之和。
        // 与弹种槽读的是同一个 helper，因此"选项卡上的数"与"弹种槽里的数"在供弹那条上必然相等
        for (int i = 0; i < belts.size() && i < slots.tabs.size(); i++) {
            List<ResourceLocation> belt = belts.get(i);
            List<LoaderEntry> entries = data.controller().getAmmoPool().get(belt);
            String label = NO_ROUND;
            Totals tabTotals = Totals.EMPTY;
            if (entries != null && !entries.isEmpty()) {
                label = beltLabelOf(entries.getFirst().loader());
                tabTotals = totalsOf(poolLoaders(entries));
            }
            setText(slots.tabLabels.get(i), label);
            setText(slots.tabCounts.get(i), String.valueOf(tabTotals.remaining()));
            setTabSelected(slots, i, belt.equals(fillingBelt));
        }
    }

    /**
     * 一个计数的三元组：余量 / 上限 / 上限是否无意义。<br>
     * {@code infinite} 表示这一批装弹机里出现了无容量概念的型号（{@code getCapacity() < 0}），
     * 此时上限文本写 {@code ∞}，不再报求和结果。
     */
    private record Totals(int remaining, int capacity, boolean infinite) {
        private static final Totals EMPTY = new Totals(0, 0, false);
    }

    /**
     * 一批装弹机的余量 / 上限之和。
     * <p>
     * 传入的集合必须已经按实例去重：{@code getRemainingCount()} / {@code getCapacity()} 是**装弹机**的
     * 属性，同一台被算两次会把同一批弹药重复计入。两条取数路径都满足——弹药池在重建时按实例去重
     * （{@code rebuildAmmoPool()} 的 {@code visited}），回退路径经 {@link #boundSuppliers}。
     */
    private static Totals totalsOf(Collection<IAmmoSupplier> suppliers) {
        int remaining = 0;
        int capacity = 0;
        boolean infinite = false;
        for (IAmmoSupplier supplier : suppliers) {
            remaining += Math.max(0, supplier.getRemainingCount());
            int supplierCapacity = supplier.getCapacity();
            if (supplierCapacity < 0) infinite = true;
            else capacity += supplierCapacity;
        }
        return new Totals(remaining, capacity, infinite);
    }

    /** 弹药池内条目的装弹机列表（顺序与池一致）；条目为空时为空表。 */
    private static List<IAmmoSupplier> poolLoaders(@Nullable List<LoaderEntry> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        List<IAmmoSupplier> loaders = new ArrayList<>(entries.size());
        for (LoaderEntry entry : entries) loaders.add(entry.loader());
        return loaders;
    }

    /**
     * 一根管的段状态（装填条的一段）。
     *
     * @param status      该段显示的档位
     * @param progress    {@code RELOADING} 时的填充比例 0~1
     * @param reloadTicks 该供给者的装填耗时（tick），供尾标折算剩余秒数；无装填耗时概念时为 0
     */
    private record Segment(SupplierStatus status, float progress, int reloadTicks) {
    }

    /**
     * 一根管的段状态：膛内有弹 → 常亮；空膛 → 问它的供给者；没有供给者 → 熄灭。
     * <p>
     * 膛内那一发不经过供给者，因此单看供给者的状态会把"膛内已压好一发、装弹机却空着"的管
     * 显示成熄灭（K-17 的 ATGM 每根管一台容量 1 的装弹机，打完后装弹机归零而膛内那发由发射器
     * 自己持有）。逐发再生的型号（{@code reload_time=0}、按 {@code regen_per_minute} 产弹）由
     * {@code getStatus()} / {@code getReloadProgress()} 报出再生进度，空膛的段因此是"慢慢涨起来"
     * 而不是一直熄着。
     */
    private static Segment segmentOf(LauncherSubsystem launcher) {
        if (launcher.getChamberedType() != null) return new Segment(SupplierStatus.READY, 1f, 0);
        IAmmoSupplier supplier = launcher.getCurrentSupplier();
        if (supplier == null) return new Segment(SupplierStatus.EMPTY, 0f, 0);
        return new Segment(supplier.getStatus(launcher),
                Math.clamp(supplier.getReloadProgress(launcher), 0f, 1f),
                Math.max(0, supplier.getReloadTimeTicks()));
    }

    /** 显示顺序里第一根膛内有弹的管的膛内弹种；都没有时返回 null。 */
    @Nullable
    private static ProjectileType chamberedRound(List<LauncherSubsystem> launchers) {
        for (LauncherSubsystem launcher : launchers) {
            ProjectileType chambered = launcher.getChamberedType();
            if (chambered != null) return chambered;
        }
        return null;
    }

    /** 显示顺序里第一根有供给者的管的供给者；都没有时返回 null。 */
    @Nullable
    private static IAmmoSupplier nextRoundSupplier(List<LauncherSubsystem> launchers) {
        for (LauncherSubsystem launcher : launchers) {
            IAmmoSupplier supplier = launcher.getCurrentSupplier();
            if (supplier != null) return supplier;
        }
        return null;
    }

    /**
     * 该控制器下已绑定到发射器的供给者，按实例去重。
     * <p>
     * 只在弹药池里没有这条弹链时用作回退——无弹链的物理弹仓武器（{@code AmmoLoaderSubsystem}）
     * 不进弹药池，它的余量只能从绑定关系上读。去重是必须的：{@code getRemainingCount()} /
     * {@code getCapacity()} 是**装弹机**的属性，一台装弹机同时供多根管时，按管累加会把同一批弹药数重复计入。
     */
    private static Set<IAmmoSupplier> boundSuppliers(List<LauncherSubsystem> launchers) {
        Set<IAmmoSupplier> suppliers = new LinkedHashSet<>();
        for (LauncherSubsystem launcher : launchers) {
            IAmmoSupplier supplier = launcher.getCurrentSupplier();
            if (supplier != null) suppliers.add(supplier);
        }
        return suppliers;
    }

    /**
     * 当前负责供弹的那条弹链：取控制器的 {@code selectedBelt}，取不到时退到第一条可用弹链。
     *
     * <p>控制器建起弹药池后总会选中一条（{@code rebuildAmmoPool()} 建池时默认选中排序第一条，
     * 选中的链随结构变化消失时改选），所以这里的回退只覆盖池尚未建起的窗口（刚载入、握手未完成）。
     * <b>不补这个回退，选项卡在那个窗口里会一条都不高亮。</b></p>
     *
     * <p>回退取 {@code getAvailableBelts()} 的第一条，与控制器默认选中、以及 {@code cycleSelectedBelt()}
     * 从"未选中"起步时选中的是同一条（都按第一发弹种排序），因此高亮口径与控制器自身的选择语义一致。</p>
     */
    private static List<ResourceLocation> fillingBeltOf(WeaponData data, List<List<ResourceLocation>> belts) {
        List<ResourceLocation> selected = data.controller().getSelectedBelt();
        if (selected != null) return selected;
        return belts.isEmpty() ? List.of() : belts.getFirst();
    }

    // ==================== 槽位写入原语 ====================

    /**
     * 写叶子元素的文本。
     * <p>
     * 直接赋 {@code innerText} 字段：AUI 在每帧的 {@code Element.tick()} 里比对新旧文本，
     * 只有变化才清文本缓存并标脏，因此这里不需要自己记录上一次的值。
     */
    private static void setText(Element element, String value) {
        element.innerText = value;
    }

    /** 换武器块的状态类：只在状态真的变化时动类名，未变时连 token 都不碰。 */
    private static void setWeaponState(WeaponSlots slots, String stateClass) {
        if (stateClass.equals(slots.stateClass)) return;
        slots.block.getClassList().remove(slots.stateClass);
        slots.block.getClassList().add(stateClass);
        slots.stateClass = stateClass;
    }

    /** 换弹种槽的"弹种与供弹弹链不符"标：同样只在变化时动类名。 */
    private static void setAmmoMismatch(WeaponSlots slots, boolean mismatch) {
        if (mismatch == slots.ammoMismatch) return;
        slots.ammoMismatch = mismatch;
        if (mismatch) {
            slots.ammo.getClassList().add(AMMO_MISMATCH);
        } else {
            slots.ammo.getClassList().remove(AMMO_MISMATCH);
        }
    }

    /** 换装填条的段状态类（空串 = 常态），同样只在变化时动。 */
    private static void setSegState(WeaponSlots slots, int index, String token) {
        String previous = slots.segStates.get(index);
        if (token.equals(previous)) return;
        Element seg = slots.segments.get(index);
        if (!previous.isEmpty()) seg.getClassList().remove(previous);
        if (!token.isEmpty()) seg.getClassList().add(token);
        slots.segStates.set(index, token);
    }

    /** 换填充比例类（fill-0 ~ fill-100），同样只在变化时动。 */
    private static void setFill(WeaponSlots slots, int index, int step) {
        String next = fillClass(step);
        if (next.equals(slots.fillClasses.get(index))) return;
        Element fill = slots.fills.get(index);
        fill.getClassList().remove(slots.fillClasses.get(index));
        fill.getClassList().add(next);
        slots.fillClasses.set(index, next);
    }

    /** 切换选项卡的选中态类。 */
    private static void setTabSelected(WeaponSlots slots, int index, boolean selected) {
        if (selected == slots.tabSelected.get(index)) return;
        slots.tabSelected.set(index, selected);
        if (selected) {
            slots.tabs.get(index).getClassList().add(TAB_SELECTED);
        } else {
            slots.tabs.get(index).getClassList().remove(TAB_SELECTED);
        }
    }

    private static String fillClass(int step) {
        return "fill-" + step * (100 / FILL_STEPS);
    }

    // ==================== 文本 ====================

    /**
     * 弹种槽文本：弹链指针当前指向的那一发。
     * <p>
     * 物品弹药装填机（{@link io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoLoaderSubsystem}）
     * 尚未接入弹药池，因此这里的供给者实际只有再生装弹机；非再生供给者退回其弹链名。
     */
    private static String roundLabelOf(@Nullable IAmmoSupplier supplier) {
        if (supplier instanceof RegenLoaderSubsystem regen) return regen.getCurrentRoundLabel();
        return supplier != null ? supplier.getLabel() : "";
    }

    /**
     * 弹链名：装弹机实例上的 {@code belt_label}，留空时取弹链首发弹种的展示名。
     */
    private static String beltLabelOf(IAmmoSupplier loader) {
        if (loader instanceof RegenLoaderSubsystem regen) return regen.getBeltLabel();
        return loader.getLabel();
    }

    /** 取玩家语言下的固定文案。 */
    private static String text(String key) {
        return Component.translatable(key).getString();
    }

    /** 给文档根元素切换隐藏类；AUI 的 classList 在 token 未变时不动样式，因此可以每 tick 调用。 */
    private static void setHidden(boolean hidden) {
        if (overlayDocument == null) return;
        Element root = overlayDocument.getDocumentElement();
        if (root == null) return;
        if (hidden) {
            root.getClassList().add(HIDDEN_CLASS);
        } else {
            root.getClassList().remove(HIDDEN_CLASS);
        }
    }

    // ==================== 数据结构 ====================

    /** 本帧的显示数据：角色 → 武器控制器。 */
    private record GroupData(WeaponRole role, List<WeaponData> weapons) {
    }

    /** 一个武器控制器及其发射器与可选弹链。 */
    private record WeaponData(WeaponControllerSubsystem controller,
                              List<LauncherSubsystem> launchers,
                              List<List<ResourceLocation>> belts,
                              boolean showsSegbar) {
    }

    /** 角色分组的槽位句柄。 */
    private static final class GroupSlots {
        private final Element roleTag;
        private final Element title;
        private final List<WeaponSlots> weapons = new ArrayList<>();

        private GroupSlots(Element roleTag, Element title) {
            this.roleTag = roleTag;
            this.title = title;
        }
    }

    /**
     * 一个武器块的槽位句柄。<br>
     * 文本槽位不需要记上一次的值（AUI 自己比对），类名槽位各记一个上一次写过的 token，
     * 因为类名写入没有内容比较。
     */
    private static final class WeaponSlots {
        private final Element block;
        private final Element name;
        private final Element ammo;
        private final Element current;
        private final Element capacity;
        private final Element ready;
        private final List<Element> segments = new ArrayList<>();
        private final List<String> segStates = new ArrayList<>();
        private final List<Element> fills = new ArrayList<>();
        private final List<String> fillClasses = new ArrayList<>();
        private final List<Element> tabs = new ArrayList<>();
        private final List<Element> tabLabels = new ArrayList<>();
        private final List<Element> tabCounts = new ArrayList<>();
        private final List<Boolean> tabSelected = new ArrayList<>();
        private String stateClass = STATE_READY;
        private boolean ammoMismatch = false;

        private WeaponSlots(Element block, Element name, Element ammo, Element current,
                            Element capacity, Element ready) {
            this.block = block;
            this.name = name;
            this.ammo = ammo;
            this.current = current;
            this.capacity = capacity;
            this.ready = ready;
        }
    }
}
