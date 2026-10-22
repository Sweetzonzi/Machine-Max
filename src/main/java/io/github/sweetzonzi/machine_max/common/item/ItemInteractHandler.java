package io.github.sweetzonzi.machine_max.common.item;

import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.item.prop.TrackItem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.TrackType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLink;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
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
     * 履带测试物品：右键主动轮开始铺设，依次右键各负重轮，最后再次右键主动轮闭合。
     *
     * <p>闭合后由 {@link VehicleCore#createTrack} 建立一条 {@link TrackLink}；铺设
     * 顺序即成员顺序（主动轮在首位）。左/右两条履带只是两次独立的铺设过程，
     * 物品本身不按车体坐标分侧。</p>
     *
     * <p>潜行右键：铺设中撤销最后一个负重轮；没有草稿时从已闭合的履带中移除目标轮，
     * 移除主动轮即解散整条履带。</p>
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
                message(player, "履带：准星没有指向可用的轮子");
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
     * 右键：没有草稿时以目标轮为主动轮开始铺设；有草稿时依次追加负重轮，
     * 再次右键起始主动轮则闭合成一条履带。
     */
    private static void advance(Player player, VehicleCore vehicle, SubPart subPart) {
        ItemStack stack = player.getMainHandItem();
        TrackDraft draft = currentDraft(player);
        if (draft == null) {
            if (!canJoin(player, vehicle, subPart)) return;
            TrackType type = TrackItem.resolveType(player.level(), stack);
            if (type == null) {
                message(player, "履带：物品没有有效的履带型号");
                return;
            }
            TRACK_DRAFTS.put(player.getUUID(), new TrackDraft(vehicle, subPart, type));
            message(player, "履带：主动轮 " + subPart.name + "，依次右键负重轮，最后右键主动轮闭合");
            return;
        }
        if (draft.vehicle() != vehicle) {
            message(player, "履带：请先闭合或取消另一辆载具上的履带");
            return;
        }
        if (subPart == draft.sprocket()) {
            if (draft.wheels().size() + 1 < TrackLink.MIN_MEMBERS) {
                message(player, "履带：至少需要 " + TrackLink.MIN_MEMBERS + " 个轮子（含主动轮）");
                return;
            }
            TrackLink link = vehicle.createTrack(draft.sprocket(), draft.wheels(), draft.type());
            if (link == null) {
                // 装配被拒绝（厚度不成形等）：保留草稿，玩家可以补轮或换型号后再闭合
                message(player, "履带：闭合失败，检查轮组与履带厚度");
                return;
            }
            TRACK_DRAFTS.remove(player.getUUID());
            message(player, "履带闭合：" + link.members().size() + " 个轮子");
            return;
        }
        if (draft.contains(subPart)) {
            message(player, "履带：该轮已在本次铺设中");
            return;
        }
        if (!canJoin(player, vehicle, subPart)) return;
        draft.add(subPart);
        message(player, "履带：加入 " + subPart.name + "（" + draft.wheels().size() + " 个负重轮）");
    }

    /**
     * 潜行右键：铺设中撤销最后一个负重轮（撤到只剩主动轮再按一次则取消草稿）；
     * 没有草稿时从已闭合的履带中移除目标轮，移除主动轮会解散整条履带。
     */
    private static void undo(Player player, VehicleCore vehicle, SubPart subPart) {
        TrackDraft draft = currentDraft(player);
        if (draft != null) {
            if (draft.vehicle() != vehicle) {
                message(player, "履带：请先闭合或取消另一辆载具上的履带");
                return;
            }
            if (!draft.wheels().isEmpty()) {
                SubPart removed = draft.removeLast();
                message(player, "履带：撤销 " + removed.name);
            } else {
                TRACK_DRAFTS.remove(player.getUUID());
                message(player, "履带：已取消铺设");
            }
            return;
        }
        TrackLink link = subPart.trackLink;
        if (link != null && link.vehicle() == vehicle) {
            boolean removed = link.removeMember(subPart);
            message(player, removed ? "履带：移除 " + subPart.name : "履带：移除失败");
            return;
        }
        message(player, "履带：该轮不在履带中");
    }

    /** 草稿与闭合履带共用的成员资格检查。 */
    private static boolean canJoin(Player player, VehicleCore vehicle, SubPart subPart) {
        if (subPart.isDestroyed() || subPart.part.assembly != vehicle) return false;
        if (subPart.getMaxWheelRadius() <= 0f) {
            message(player, "履带：该零件没有轮半径，不能作为履带成员");
            return false;
        }
        if (subPart.trackLink != null) {
            message(player, "履带：该轮已属于另一条履带");
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

    private static void message(Player player, String text) {
        player.displayClientMessage(Component.literal(text), true);
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
