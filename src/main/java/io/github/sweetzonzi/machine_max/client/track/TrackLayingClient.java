package io.github.sweetzonzi.machine_max.client.track;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.item.prop.FittingItem;
import io.github.sweetzonzi.machine_max.common.item.prop.TrackItem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.TrackType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackEnvelope2D;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLink;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLinkData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMemberRef;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.network.payload.assembly.TrackCloseRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 履带铺设草稿：只存在于客户端。
 *
 * <p>铺设的每一步——右键主动轮开始、依次右键其余履带轮、再次右键主动轮闭合、潜行右键撤销——
 * 都是本地决策，逐步提示也是本地 actionbar 文案，因此每一步立刻可见，不依赖往返。草稿持有
 * 真实的 {@link SubPart} 引用与开始铺设时解析出的型号，不写入存档、不参与物理、不同步。</p>
 *
 * <p><b>用料在铺设期就报出来。</b>每追加一个履带轮，提示里带上「闭合这条带要扣多少节」与
 * 「背包里有多少节同型号履带」（{@link #needLinks}、{@link TrackLink#countLinks}）；预览快照
 * 带上用料与够不够，虚影按够不够染色。这个节数与闭合时服务端扣的节数同一口径——都取 tick 位姿
 * 的包络周长，因此提示里的数就是账单上的数。</p>
 *
 * <p><b>闭合是一次请求。</b>把「这一下要哪些轮子」整份交给服务端（{@link TrackCloseRequestPayload}），
 * 由服务端重新判定载具、型号、成员归属与轮半径、轮子是否已被别的履带占用、有无重复、数量上下限，
 * 以及玩家是否仍站在车旁；判定通过后按 {@code assemble → 扣料 → 注册} 建出履带并回同步。
 * 扣料与成功提示都在服务端：请求里只有成员表，节数由服务端按包络推导，客户端预览用同一口径
 * 提前报出来，但不参与扣料。</p>
 *
 * <p>草稿绑在手上的那件履带物品上：换成别的物品、或换成别的型号，草稿即作废（型号取开始铺设时
 * 解析出的那个）。它一直留到履带真的建成：主动轮 {@code trackLink} 落位即视为闭合（服务端同步到达时
 * 生效），期间若任一成员被摧毁、拆卸或占用，草稿也作废。因此服务端拒绝闭合（缺料）后玩家可以补料
 * 再点一次，不必重铺。</p>
 *
 * <p>线程：草稿、事件与 {@link #preview()} 的快照都在客户端主线程更新；渲染线程只读 {@link Preview}。
 * 集成服务器与客户端同在客户端 JVM 里运行，交互事件双端触发，因此入口显式排除服务端侧。</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public final class TrackLayingClient {
    /** 同一份成员表的重发间隔（tick）：服务端闭合并回同步之前，按住右键不会刷请求。 */
    private static final int CLOSE_RETRY_TICKS = 10;

    /** 当前草稿；{@code null} 表示没有正在铺设的履带。 */
    @Nullable
    private static Draft draft;
    /** 当 tick 冻结的预览快照，{@code null} 表示本次不画。 */
    private static volatile Preview preview;
    /** 上一 tick 的客户端世界：换世界时草稿持有的成员都属于旧世界，必须丢弃。 */
    @Nullable
    private static Level lastLevel;
    /** 上一次闭合请求的成员表指纹与发出时刻，用于抑制重复请求。 */
    private static int closeHash;
    private static int closeTick = -CLOSE_RETRY_TICKS;

    private TrackLayingClient() {
    }

    /**
     * 一次预览的输入：成员表（含视线命中的候选轮）、型号、用料与够不够。
     *
     * <p>成员是稳定的 {@link SubPart} 引用，位姿每帧由渲染线程按 {@code partialTick} 重新求值，
     * 与 {@code TrackLink.members()} 的用法同一套。用料在客户端 tick 解析并冻结：渲染线程
     * 不读背包，也不做判定。</p>
     *
     * @param members    按铺设顺序排列的成员，主动轮在首位
     * @param type       型号，取开始铺设时解析出的那个
     * @param need       闭合这条带要扣的节数，口径见 {@link #needLinks}
     * @param affordable 背包里的同型号履带够不够扣（持有数经 {@link TrackLink#countLinks} 统计）。
     *                   创造模式不扣料，因此这一位不能由 {@code need} 与持有数推出，必须在这里冻结
     */
    public record Preview(List<SubPart> members, TrackType type, int need, boolean affordable) {
    }

    /** 当 tick 的预览快照；没有草稿、手上没有履带节或成员不足以成带时为 {@code null}。 */
    @Nullable
    public static Preview preview() {
        return preview;
    }

    /** 丢弃草稿与预览：换世界、断线，或草稿已失效 / 已建成。 */
    private static void clear() {
        draft = null;
        preview = null;
        closeHash = 0;
        closeTick = -CLOSE_RETRY_TICKS;
    }

    /**
     * 客户端主线程每 tick：换世界时先丢弃草稿，草稿失效就丢弃，否则冻结当 tick 的预览快照。
     *
     * <p>失效判据只有「成员不再合格」与「主动轮已进履带」两条，后者就是闭合成功的信号——服务端
     * 建成的履带经载具同步落到客户端，成员的 {@code trackLink} 随之落位。</p>
     */
    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            lastLevel = null;
            clear();
            return;
        }
        if (player.level() != lastLevel) {
            lastLevel = player.level();
            clear();
        }
        // 座位上禁止使用物品时不画预览；草稿保留，离座后仍可继续
        if (!SeatSubsystem.canUseItems(player)) {
            preview = null;
            return;
        }
        Draft current = draft;
        if (current == null) {
            preview = null;
            return;
        }
        if (!current.isValid(player)) {
            clear();
            return;
        }
        preview = current.resolvePreview(player);
    }

    /**
     * 右键履带物品时推进铺设。
     *
     * <p>目标 SubPart 由 Eyesight 的物理射线决定，而不是事件里的实体：车体等大型碰撞箱不该挡住后面的
     * 轮子。交互本身的取消由 {@code ItemInteractHandler} 负责，两端一致。</p>
     */
    @SubscribeEvent
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) return;
        // 座位上禁止使用物品时既不推进铺设，也不给任何逐步提示
        if (!SeatSubsystem.canUseItems(player)) return;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof TrackItem)) return;
        if (!(event.getTarget() instanceof MMPartEntity)) return;
        LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
        SubPart aimed = eyesight.getWheelSubPart();
        if (aimed == null) {
            message(player, MMLanguageRegistry.MESSAGE_TRACK_NO_WHEEL);
            return;
        }
        if (!(aimed.part.assembly instanceof VehicleCore vehicle)) return;
        if (player.isShiftKeyDown()) {
            undo(player, vehicle, aimed);
        } else {
            advance(player, vehicle, aimed);
        }
    }

    /**
     * 右键：没有草稿时以目标轮为主动轮开始铺设；有草稿时依次追加履带轮，
     * 再次右键主动轮则请求闭合。
     */
    private static void advance(Player player, VehicleCore vehicle, SubPart aimed) {
        Draft current = draft;
        if (current == null) {
            if (!canJoin(player, vehicle, aimed)) return;
            TrackType type = TrackItem.resolveType(player.level(), player.getMainHandItem());
            if (type == null) {
                message(player, MMLanguageRegistry.MESSAGE_TRACK_INVALID_TYPE);
                return;
            }
            draft = new Draft(vehicle, aimed, type);
            message(player, MMLanguageRegistry.MESSAGE_TRACK_DRAFT_STARTED, Component.translatable(aimed.name));
            return;
        }
        if (current.vehicle != vehicle) {
            message(player, MMLanguageRegistry.MESSAGE_TRACK_OTHER_VEHICLE);
            return;
        }
        if (aimed == current.sprocket) {
            if (current.memberCount() < TrackLink.MIN_MEMBERS) {
                message(player, MMLanguageRegistry.MESSAGE_TRACK_MIN_MEMBERS, TrackLink.MIN_MEMBERS);
                return;
            }
            close(player, current);
            return;
        }
        if (current.contains(aimed)) {
            message(player, MMLanguageRegistry.MESSAGE_TRACK_DUPLICATE_WHEEL);
            return;
        }
        if (!canJoin(player, vehicle, aimed)) return;
        current.wheels.add(aimed);
        message(player, MMLanguageRegistry.MESSAGE_TRACK_WHEEL_ADDED,
                Component.translatable(aimed.name), current.memberCount(),
                needLinks(current.allMembers(), current.type),
                TrackLink.countLinks(player.getInventory(), current.type.getRegistryKey()));
    }

    /**
     * 请求闭合：把草稿的成员表发给服务端。
     *
     * <p>同一份成员表在 {@link #CLOSE_RETRY_TICKS} 内只发一次：按住右键会在每个 tick 走到这里，
     * 而服务端建成的履带要等它自己的同步到达才会落到客户端。成员表一变就立刻重发，
     * 所以缺料补货后补一个轮再点主动轮仍然是一次请求。</p>
     */
    private static void close(Player player, Draft current) {
        List<SubPart> members = current.allMembers();
        int hash = members.hashCode();
        int now = player.tickCount;
        if (hash == closeHash && now - closeTick < CLOSE_RETRY_TICKS) return;
        closeHash = hash;
        closeTick = now;
        List<TrackMemberRef> refs = new ArrayList<>(members.size());
        for (SubPart member : members) refs.add(TrackMemberRef.of(member));
        PacketDistributor.sendToServer(new TrackCloseRequestPayload(
                current.vehicle.getUuid(), current.type.getRegistryKey(), refs));
    }

    /**
     * 潜行右键：铺设中撤销最后一个履带轮（撤到只剩主动轮再按一次则取消草稿）。
     * 已闭合的履带不在这里改动，拆卸履带请用撬棍。
     */
    private static void undo(Player player, VehicleCore vehicle, SubPart aimed) {
        Draft current = draft;
        if (current != null) {
            if (current.vehicle != vehicle) {
                message(player, MMLanguageRegistry.MESSAGE_TRACK_OTHER_VEHICLE);
                return;
            }
            if (!current.wheels.isEmpty()) {
                SubPart removed = current.removeLast();
                message(player, MMLanguageRegistry.MESSAGE_TRACK_WHEEL_UNDONE, Component.translatable(removed.name));
            } else {
                clear();
                message(player, MMLanguageRegistry.MESSAGE_TRACK_DRAFT_CANCELLED);
            }
            return;
        }
        TrackLink link = aimed.trackLink;
        if (link != null && link.vehicle() == vehicle) {
            message(player, MMLanguageRegistry.MESSAGE_TRACK_USE_CROWBAR);
            return;
        }
        message(player, MMLanguageRegistry.MESSAGE_TRACK_NOT_IN_TRACK);
    }

    /**
     * 成员资格：同属这条载具、未摧毁、有轮半径、还没进别的履带。
     *
     * @param player 用于给出失败原因的提示；{@code null} 表示静默判定（预览的候选轮与草稿复验）
     */
    private static boolean canJoin(@Nullable Player player, VehicleCore vehicle, SubPart subPart) {
        if (subPart.isDestroyed() || subPart.part.assembly != vehicle) return false;
        if (subPart.getMaxWheelRadius() <= 0f) {
            if (player != null) message(player, MMLanguageRegistry.MESSAGE_TRACK_NO_WHEEL_RADIUS);
            return false;
        }
        if (subPart.trackLink != null) {
            if (player != null) message(player, MMLanguageRegistry.MESSAGE_TRACK_OTHER_TRACK);
            return false;
        }
        return true;
    }

    /**
     * actionbar 提示。{@code args} 里已是 {@link Component} 的按组件占位，
     * 其余按数字 / 文本占位。
     */
    private static void message(Player player, MMLanguageRegistry key, Object... args) {
        Object[] translated = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            translated[i] = args[i] instanceof Component component ? component
                    : args[i] instanceof Integer || args[i] instanceof Float ? args[i]
                    : Component.literal(String.valueOf(args[i]));
        }
        player.displayClientMessage(Component.translatable(key.tag, translated), true);
    }

    /** 客户端主线程访问；草稿只持有 SubPart 引用与型号，不持有刚体或约束。 */
    private static final class Draft {
        private final VehicleCore vehicle;
        private final SubPart sprocket;
        private final TrackType type;
        private final List<SubPart> wheels = new ArrayList<>();

        private Draft(VehicleCore vehicle, SubPart sprocket, TrackType type) {
            this.vehicle = vehicle;
            this.sprocket = sprocket;
            this.type = type;
        }

        /**
         * 草稿仍然成立：手上还拿着同型号的履带物品，载具在，且每个成员都还合格
         * （`trackLink` 落位即闭合成功，随之失效）。
         */
        private boolean isValid(Player player) {
            if (!(player.getMainHandItem().getItem() instanceof TrackItem)) return false;
            if (!FittingItem.idOf(player.getMainHandItem()).equals(type.getRegistryKey())) return false;
            if (vehicle.isRemoved || vehicle.isDestroyed()) return false;
            if (!canJoin(null, vehicle, sprocket)) return false;
            for (SubPart wheel : wheels) {
                if (!canJoin(null, vehicle, wheel)) return false;
            }
            return true;
        }

        private boolean contains(SubPart subPart) {
            return sprocket == subPart || wheels.contains(subPart);
        }

        private SubPart removeLast() {
            return wheels.remove(wheels.size() - 1);
        }

        /** 本次铺设已包含的履带轮数：已追加的轮子加主动轮，与闭合时报告的成员数同口径。 */
        private int memberCount() {
            return wheels.size() + 1;
        }

        /**
         * 按铺设顺序排列的成员表：主动轮在首位，随后是已追加的履带轮。
         *
         * <p>顺序与闭合请求交给服务端的同一份，也就是包络的锚定绕行顺序。</p>
         */
        private List<SubPart> allMembers() {
            List<SubPart> members = new ArrayList<>(wheels.size() + 1);
            members.add(sprocket);
            members.addAll(wheels);
            return members;
        }

        /**
         * 当帧预览：草稿成员加上视线命中的候选轮，即「点了这一下之后」的闭合成员表，
         * 连同这次闭合的用料与背包够不够。
         *
         * <p>候选轮不合格、或成员不足以成带时返回 {@code null}。草稿本身不会在没有对应手持物品时
         * 走到这里：那种情况在 {@link #isValid(Player)} 里就被丢弃了。</p>
         */
        @Nullable
        private Preview resolvePreview(LocalPlayer player) {
            List<SubPart> members = allMembers();
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            SubPart aimed = eyesight.getWheelSubPart();
            if (aimed != null && canJoin(null, vehicle, aimed) && !members.contains(aimed)) {
                members.add(aimed);
            }
            if (members.size() < TrackLink.MIN_MEMBERS) return null;
            int need = needLinks(members, type);
            boolean affordable = player.hasInfiniteMaterials()
                    || TrackLink.countLinks(player.getInventory(), type.getRegistryKey()) >= need;
            return new Preview(List.copyOf(members), type, need, affordable);
        }
    }

    /**
     * 闭合这组轮子要扣多少节履带。
     *
     * <p>与 {@code TrackLink.refreshState} 的推导同一口径：包络周长除以型号节距，下限抬到成员数、
     * 向上对齐周期 {@code K}、再截断到上限；包络建不出来时同样退回「成员数 × 8」的保守估计。
     * 位姿取 tick 值（{@code partialTick = 1}）——{@code TrackLink.assemble} 在服务端闭合时取的
     * 也是这一档，所以铺设期报的节数就是闭合时扣的节数。</p>
     *
     * @param members 按铺设顺序排列的成员，主动轮在首位
     */
    private static int needLinks(List<SubPart> members, TrackType type) {
        TrackEnvelope2D.Result envelope = envelope(members, type);
        int period = type.linkPeriod();
        int count = envelope == null ? Math.max(1, members.size() * 8)
                : envelope.path().linkCount(type.attr().lengthPerLink(), period);
        return Math.min(TrackLinkData.MAX_LINK_COUNT,
                TrackLink.normalizeLinkCount(count, members.size(), period));
    }

    /**
     * tick 位姿下的候选包络；绕向用与闭合时同一个判定（{@link TrackEnvelope2D#layingClockwise}）。
     *
     * @return 包络不可用（成员不足、缺轮半径、几何退化）时返回 {@code null}
     */
    @Nullable
    private static TrackEnvelope2D.Result envelope(List<SubPart> members, TrackType type) {
        boolean clockwise = TrackEnvelope2D.layingClockwise(
                members.stream().map(member -> member.getWorldPositionMatrix(1f)).toList());
        return TrackEnvelope2D.build(members, members.getFirst(), 1f, type.attr().thicknessMeters(), clockwise);
    }
}
