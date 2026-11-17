package io.github.sweetzonzi.machine_max.common.item;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.item.prop.FittingItem;
import io.github.sweetzonzi.machine_max.common.item.prop.TrackItem;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.TrackType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLink;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMemberRef;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.assembly.TrackCloseRequestPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static io.github.sweetzonzi.machine_max.MachineMax.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class ItemInteractHandler {
    /**
     * 闭合请求的成员数上限：正常一侧履带的轮数是个位数，超长的成员表只可能来自伪造的客户端。
     */
    private static final int MAX_MEMBERS = 32;
    /**
     * 闭合时玩家与载具的最大距离（米）。
     *
     * <p>取原版实体交互的服务端上限 {@code ServerGamePacketListenerImpl.MAX_INTERACTION_DISTANCE}：
     * 客户端在自己的交互范围内铺设，闭合时应当仍在同一个范围内。判据取**最近的一个成员**，
     * 这样长车体上玩家站在一端时不会因为另一端的轮子远而被拒。</p>
     */
    private static final double MAX_INTERACTION_DISTANCE = 6.0;

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
     * 履带物品：右键取消原版交互，铺设本身在客户端完成。
     *
     * <p>草稿（主动轮、已选履带轮、型号）由 {@code client/track/TrackLayingClient} 持有，开始、追加、
     * 撤销与逐步提示都在客户端本地；闭合是一次 {@link TrackCloseRequestPayload} 请求。本方法两端
     * 执行，只负责让右键不落到 {@link MMPartEntity} 的原版交互上——不取消的话，手里拿着履带节右键
     * 轮子会同时触发该零件的交互区。</p>
     */
    @SubscribeEvent
    private static void interactWithTrackItem(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof TrackItem)) return;
        if (!(event.getTarget() instanceof MMPartEntity)) return;
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        event.setCanceled(true);
    }

    /**
     * 服务端（主线程）：受理一次履带闭合请求。
     *
     * <p>请求只带「这一下要哪些轮子」这个结论，没有可信的判定过程，因此这里把铺设期逐轮做过的
     * 检查一次补齐：手持物品的型号与请求一致、载具与型号可解析、成员按铺设顺序存在且仍属这条载具、
     * 有轮半径、还没进任何履带、没有重复、数量在 {@link TrackLink#MIN_MEMBERS}..{@link #MAX_MEMBERS}
     * 之间，并且玩家就站在车旁。任一条不成立就整包拒绝，**不**交给 {@link TrackLink#assemble}
     * 静默过滤：预览报了 N 个轮子却只闭合 N−1 个，玩家会按错误的外形付费。</p>
     *
     * <p>扣料与注册的次序沿用「装配候选 → 按候选节数扣料 → 注册 + {@code onAssembled}」：注册即
     * 拥有身份、约束与同步，撤销就要走返还，等于没付钱却拿回 N 节。</p>
     */
    public static void closeTrack(Player player, TrackCloseRequestPayload request) {
        // 观察者的 hasInfiniteMaterials 为真（扣料会被跳过），因此单独挡掉
        if (!player.isAlive() || player.isSpectator()) return;
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof TrackItem)) return;
        // 草稿绑在手上的那件物品上：闭合只能用它开始的型号，换型号请重新开始铺设
        if (!FittingItem.idOf(held).equals(request.trackType())) {
            reject(player, "手持型号 {} 与请求型号 {} 不一致", FittingItem.idOf(held), request.trackType());
            return;
        }
        Level level = player.level();
        VehicleCore vehicle = ObjectManager.getVehicle(level, request.vehicleUuid());
        if (vehicle == null || vehicle.isRemoved || vehicle.isDestroyed()) {
            reject(player, "载具 {} 不存在或已销毁", request.vehicleUuid());
            return;
        }
        FittingType fitting = MMDynamicRes.getFittingType(level, request.trackType());
        if (!(fitting instanceof TrackType type)) {
            reject(player, "履带型号 {} 不存在或不是履带类型", request.trackType());
            return;
        }
        List<SubPart> members = resolveMembers(player, vehicle, request.members());
        if (members == null) return;
        if (!nearVehicle(player, members)) {
            reject(player, "离载具 {} 太远（成员表最近处仍超过 {} m）", vehicle.getUuid(), MAX_INTERACTION_DISTANCE);
            return;
        }
        TrackLink link = TrackLink.assemble(vehicle, members.getFirst(), members.subList(1, members.size()), type);
        if (link == null) {
            // 厚度不小于某个成员的轮半径：包络会退化，保留草稿让玩家换轮或换型号后再闭合
            message(player, MMLanguageRegistry.MESSAGE_TRACK_CLOSE_FAILED);
            return;
        }
        int cost = link.linkCount();
        if (!TrackLink.takeLinks(player.getInventory(), type.getRegistryKey(), cost)) {
            // 料不够：候选从未注册、没有约束、没有同步，只需要清掉成员的反向引用。
            // 不撤销会留下「幽灵履带」——hasLiveTrack 判真，把轮子的命中吸走
            link.abandonCandidate();
            message(player, MMLanguageRegistry.MESSAGE_TRACK_NEED_LINKS, cost,
                    TrackLink.countLinks(player.getInventory(), type.getRegistryKey()));
            return;
        }
        vehicle.registerTrackLink(link);
        link.onAssembled();
        message(player, MMLanguageRegistry.MESSAGE_TRACK_CLOSED, link.members().size(), cost);
    }

    /**
     * 把请求里的成员引用还原成这条载具上的零件。
     *
     * <p>顺序原样保留（主动轮在首位）：它同时是包络的锚定绕行顺序与张力对的配对顺序。任一成员
     * 不合格都整包拒绝——这份成员表必须与玩家看到的预览是同一份。</p>
     *
     * @return 校验通过的成员表；不合格时返回 {@code null}（原因已记日志）
     */
    @Nullable
    private static List<SubPart> resolveMembers(Player player, VehicleCore vehicle, List<TrackMemberRef> refs) {
        if (refs.size() < TrackLink.MIN_MEMBERS || refs.size() > MAX_MEMBERS) {
            reject(player, "成员数 {} 不在 {}..{} 之间", refs.size(), TrackLink.MIN_MEMBERS, MAX_MEMBERS);
            return null;
        }
        List<SubPart> members = new ArrayList<>(refs.size());
        for (TrackMemberRef ref : refs) {
            var part = vehicle.partMap.get(ref.partUuid());
            SubPart member = part == null ? null : part.subParts.get(ref.subPartName());
            if (member == null || member.isDestroyed() || member.part.assembly != vehicle) {
                reject(player, "成员 {} 不在载具 {} 上或已摧毁", ref, vehicle.getUuid());
                return null;
            }
            if (member.getMaxWheelRadius() <= 0f) {
                reject(player, "成员 {} 没有轮半径", ref.subPartName());
                return null;
            }
            if (member.trackLink != null) {
                reject(player, "成员 {} 已属于另一条履带", ref.subPartName());
                return null;
            }
            if (members.contains(member)) {
                reject(player, "成员 {} 在请求里重复出现", ref.subPartName());
                return null;
            }
            members.add(member);
        }
        return members;
    }

    /** 闭合时玩家必须仍站在载具旁：任一成员到眼睛的距离不超过原版实体交互上限。 */
    private static boolean nearVehicle(Player player, List<SubPart> members) {
        Vec3 eye = player.getEyePosition();
        double limit = MAX_INTERACTION_DISTANCE * MAX_INTERACTION_DISTANCE;
        for (SubPart member : members) {
            if (member.getPresentationPosition().distanceToSqr(eye) <= limit) return true;
        }
        return false;
    }

    /** 拒绝一次请求并留下审计记录：这些分支只可能来自伪造的客户端或极端的竞争，不打扰玩家。 */
    private static void reject(Player player, String reason, Object... args) {
        MachineMax.LOGGER.warn("拒绝 {} 的履带闭合请求：{}", player.getName().getString(), String.format(reason, args));
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
}
