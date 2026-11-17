package io.github.sweetzonzi.machine_max.common.item;

import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.item.prop.TrackItem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.TrackType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLink;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.sweetzonzi.machine_max.MachineMax.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class ItemInteractHandler {
    /**
     * 服务端：正在铺设的履带草稿，按玩家索引。
     *
     * <p>草稿只存在于铺设过程中：再次右键起始主动轮闭合成 {@link TrackLink}，
     * 取消或玩家退出时丢弃。草稿阶段不创建 TrackLink，因此半条履带不参与物理、
     * 不参与同步、也不进入载具存档。</p>
     */
    private static final Map<UUID, TrackDraft> TRACK_DRAFTS = new HashMap<>();

    /**
     * 命名牌与载具互动可修改载具名称
     */
    @SubscribeEvent
    private static void interact(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof NameTagItem) {
            if (event.getTarget() instanceof MMPartEntity partEntity && partEntity.subPart != null) {
                SubPart subPart = partEntity.subPart;
                Component component = stack.get(DataComponents.CUSTOM_NAME);
                if (component != null && !player.level().isClientSide) {
                    subPart.part.assembly.setAssemblyName(component.getString());
                    stack.consume(1, player);
                    event.setCancellationResult(InteractionResult.sidedSuccess(false));
                }
            }
        }
    }

    /**
     * 履带物品：右键主动轮开始铺设，依次右键各负重轮，最后再次右键主动轮闭合。
     *
     * <p>闭合分三步：{@link TrackLink#assemble} 建出**未注册**的候选、按候选节数从背包扣
     * 同型号履带节、扣成功才注册并 {@link TrackLink#onAssembled()}。扣料必须发生在注册之前：
     * 注册即拥有身份、约束与同步，撤销就要走返还，等于没付钱却拿回 N 节。
     * 料不够时候选清掉成员的反向引用后丢弃，草稿保留让玩家补料。</p>
     *
     * <p>左/右两条履带只是两次独立的铺设过程，物品本身不按车体坐标分侧。</p>
     *
     * <p>潜行右键：铺设中撤销最后一个负重轮。已闭合的履带只能由撬棍整条拆除，
     * 履带物品不提供逐成员移除。</p>
     *
     * <p>交互事件双端触发；草稿与成员变更只在服务端执行，客户端只取消交互以获得手感反馈。</p>
     */
    @SubscribeEvent
    private static void interactWithTrackItem(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof TrackItem)) return;
        Entity target = event.getTarget();
        if (!(target instanceof MMPartEntity)) return;

        if (!player.level().isClientSide) {
            // 实体交互只作为触发入口；目标 SubPart 由 Eyesight 的物理射线决定，
            // 这样车体等大型实体碰撞箱不会挡住后面的轮子。
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            SubPart subPart = eyesight.getWheelSubPart();
            if (subPart == null) {
                message(player, MMLanguageRegistry.MESSAGE_TRACK_NO_WHEEL);
            } else if (subPart.part.assembly instanceof VehicleCore vehicle) {
                if (player.isShiftKeyDown()) {
                    undo(player, vehicle, subPart);
                } else {
                    advance(player, vehicle, subPart);
                }
            }
        }
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        event.setCanceled(true);
    }

    /** 玩家退出时丢弃未闭合的草稿，避免离线玩家的残稿一直留在内存里。 */
    @SubscribeEvent
    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        TRACK_DRAFTS.remove(event.getEntity().getUUID());
    }

    /**
     * 右键：没有草稿时以目标轮为主动轮开始铺设；有草稿时依次追加履带轮，
     * 再次右键起始主动轮则闭合成一条履带。
     */
    private static void advance(Player player, VehicleCore vehicle, SubPart subPart) {
        ItemStack stack = player.getMainHandItem();
        TrackDraft draft = currentDraft(player);
        if (draft == null) {
            if (!canJoin(player, vehicle, subPart)) return;
            TrackType type = TrackItem.resolveType(player.level(), stack);
            if (type == null) {
                message(player, MMLanguageRegistry.MESSAGE_TRACK_INVALID_TYPE);
                return;
            }
            TRACK_DRAFTS.put(player.getUUID(), new TrackDraft(vehicle, subPart, type));
            message(player, MMLanguageRegistry.MESSAGE_TRACK_DRAFT_STARTED, Component.translatable(subPart.name));
            return;
        }
        if (draft.vehicle() != vehicle) {
            message(player, MMLanguageRegistry.MESSAGE_TRACK_OTHER_VEHICLE);
            return;
        }
        if (subPart == draft.sprocket()) {
            if (draft.memberCount() < TrackLink.MIN_MEMBERS) {
                message(player, MMLanguageRegistry.MESSAGE_TRACK_MIN_MEMBERS, TrackLink.MIN_MEMBERS);
                return;
            }
            closeDraft(player, vehicle, draft);
            return;
        }
        if (draft.contains(subPart)) {
            message(player, MMLanguageRegistry.MESSAGE_TRACK_DUPLICATE_WHEEL);
            return;
        }
        if (!canJoin(player, vehicle, subPart)) return;
        draft.add(subPart);
        message(player, MMLanguageRegistry.MESSAGE_TRACK_WHEEL_ADDED,
                Component.translatable(subPart.name), draft.memberCount());
    }

    /** 闭合草稿：装配候选 → 扣料 → 注册。料不够时保留草稿并丢弃候选。 */
    private static void closeDraft(Player player, VehicleCore vehicle, TrackDraft draft) {
        TrackLink link = TrackLink.assemble(vehicle, draft.sprocket(), draft.wheels(), draft.type());
        if (link == null) {
            // 成员不合格（厚度不成形等）：保留草稿，玩家可以补轮或换型号后再闭合
            message(player, MMLanguageRegistry.MESSAGE_TRACK_CLOSE_FAILED);
            return;
        }
        int cost = link.linkCount();
        if (!TrackLink.takeLinks(player.getInventory(), draft.type().getRegistryKey(), cost)) {
            // 料不够：候选从未注册、没有约束、没有同步，所以只需要清掉成员的反向引用。
            // 不撤销会留下「幽灵履带」——hasLiveTrack 判真，把轮子的命中吸走
            link.abandonCandidate();
            message(player, MMLanguageRegistry.MESSAGE_TRACK_NEED_LINKS, cost,
                    TrackLink.countLinks(player.getInventory(), draft.type().getRegistryKey()));
            return;
        }
        vehicle.registerTrackLink(link);
        link.onAssembled();
        TRACK_DRAFTS.remove(player.getUUID());
        message(player, MMLanguageRegistry.MESSAGE_TRACK_CLOSED, link.members().size(), cost);
    }

    /**
     * 潜行右键：铺设中撤销最后一个履带轮（撤到只剩主动轮再按一次则取消草稿）。
     * 已闭合的履带不在这里改动，拆卸履带请用撬棍。
     */
    private static void undo(Player player, VehicleCore vehicle, SubPart subPart) {
        TrackDraft draft = currentDraft(player);
        if (draft != null) {
            if (draft.vehicle() != vehicle) {
                message(player, MMLanguageRegistry.MESSAGE_TRACK_OTHER_VEHICLE);
                return;
            }
            if (!draft.wheels().isEmpty()) {
                SubPart removed = draft.removeLast();
                message(player, MMLanguageRegistry.MESSAGE_TRACK_WHEEL_UNDONE, Component.translatable(removed.name));
            } else {
                TRACK_DRAFTS.remove(player.getUUID());
                message(player, MMLanguageRegistry.MESSAGE_TRACK_DRAFT_CANCELLED);
            }
            return;
        }
        TrackLink link = subPart.trackLink;
        if (link != null && link.vehicle() == vehicle) {
            message(player, MMLanguageRegistry.MESSAGE_TRACK_USE_CROWBAR);
            return;
        }
        message(player, MMLanguageRegistry.MESSAGE_TRACK_NOT_IN_TRACK);
    }

    /** 草稿与闭合履带共用的成员资格检查。 */
    private static boolean canJoin(Player player, VehicleCore vehicle, SubPart subPart) {
        if (subPart.isDestroyed() || subPart.part.assembly != vehicle) return false;
        if (subPart.getMaxWheelRadius() <= 0f) {
            message(player, MMLanguageRegistry.MESSAGE_TRACK_NO_WHEEL_RADIUS);
            return false;
        }
        if (subPart.trackLink != null) {
            message(player, MMLanguageRegistry.MESSAGE_TRACK_OTHER_TRACK);
            return false;
        }
        return true;
    }

    @Nullable
    private static TrackDraft currentDraft(Player player) {
        TrackDraft draft = TRACK_DRAFTS.get(player.getUUID());
        if (draft == null) return null;
        if (!draft.isValid()) {
            TRACK_DRAFTS.remove(player.getUUID());
            return null;
        }
        return draft;
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

    /** 服务端单线程访问；草稿只持有 SubPart 引用与型号，不持有刚体或约束。 */
    private static final class TrackDraft {
        private final VehicleCore vehicle;
        private final SubPart sprocket;
        private final TrackType type;
        private final List<SubPart> wheels = new ArrayList<>();

        private TrackDraft(VehicleCore vehicle, SubPart sprocket, TrackType type) {
            this.vehicle = vehicle;
            this.sprocket = sprocket;
            this.type = type;
        }

        private boolean isValid() {
            if (vehicle.isRemoved || vehicle.isDestroyed()) return false;
            if (sprocket.isDestroyed() || sprocket.part.assembly != vehicle || sprocket.trackLink != null) {
                return false;
            }
            for (SubPart wheel : wheels) {
                if (wheel.isDestroyed() || wheel.part.assembly != vehicle || wheel.trackLink != null) {
                    return false;
                }
            }
            return true;
        }

        private boolean contains(SubPart subPart) {
            return sprocket == subPart || wheels.contains(subPart);
        }

        private void add(SubPart wheel) {
            wheels.add(wheel);
        }

        private SubPart removeLast() {
            return wheels.remove(wheels.size() - 1);
        }

        /** 本次铺设已包含的履带轮数：已追加的轮子加主动轮，与闭合时报告的成员数同口径。 */
        private int memberCount() {
            return wheels.size() + 1;
        }

        private VehicleCore vehicle() {
            return vehicle;
        }

        private SubPart sprocket() {
            return sprocket;
        }

        private TrackType type() {
            return type;
        }

        private List<SubPart> wheels() {
            return List.copyOf(wheels);
        }
    }
}
