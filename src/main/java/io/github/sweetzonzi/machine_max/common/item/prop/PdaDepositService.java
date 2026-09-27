package io.github.sweetzonzi.machine_max.common.item.prop;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * PDA 蓝图终端的服务端权威写入服务：存入、绑定格位、切换当前格位、开关设计模式。
 *
 * <p>四个方法都在主线程调用，全部以 {@code hand} 定位 PDA 栈；写入数据组件后写回槽位并触发物品同步，
 * 否则客户端界面会停在旧数据。{@code selected} 与 {@code designMode} 的客户端预写（见 {@code PdaItem.use}
 * 与 {@code PdaInputInterceptor}）已用同一段构造逻辑得到同值，因此服务端回显的内容与客户端已持有的一致。</p>
 */
public final class PdaDepositService {
    private PdaDepositService() {
    }

    /** 存入结果：成功并入的蓝图张数与因已收纳无限次而被拒绝的张数。 */
    public record DepositResult(int stored, int rejected) {
    }

    /**
     * 把玩家背包中的蓝图存入 PDA（主线程）。
     *
     * <p>对每个候选物品经 {@link PdaHelper#recipeIdOf} 归一为配方 id，无法归一者跳过并留在原处；
     * 归一成功后并入条目、从背包移除原件。被拒绝（已收纳无限次）者不消耗原件。</p>
     *
     * @param player 服务端玩家
     * @param hand   手持 PDA 的手
     * @param slots  目标背包槽位；{@code all} 为 true 时忽略本参数
     * @param all    true 表示扫描整个背包（主背包 36 格 + 副手）
     * @return 本次存入与被拒绝的张数
     */
    public static DepositResult deposit(ServerPlayer player, InteractionHand hand, @Nullable List<Integer> slots, boolean all) {
        ItemStack pdaStack = player.getItemInHand(hand);
        if (!PdaHelper.isPda(pdaStack)) return new DepositResult(0, 0);
        Inventory inventory = player.getInventory();
        List<Integer> targets = new ArrayList<>();
        if (all) {
            for (int i = 0; i < inventory.items.size(); i++) targets.add(i);
            targets.add(Inventory.SLOT_OFFHAND);
        } else if (slots != null) {
            targets.addAll(slots);
        }
        PdaData data = PdaHelper.getData(pdaStack);
        int stored = 0;
        int rejected = 0;
        for (int slot : targets) {
            if (slot < 0 || slot >= inventory.getContainerSize()) continue;
            ItemStack blueprint = inventory.getItem(slot);
            if (blueprint.isEmpty()) continue;
            ResourceLocation recipeId = PdaHelper.recipeIdOf(blueprint, player.level());
            if (recipeId == null) continue;
            Integer current = data.usesOf(recipeId);
            if (current != null && PdaData.isInfinite(current)) {
                rejected++;
                continue;
            }
            // 蓝图物品当前不以任何组件承载次数，故传入次数恒为无限；有限次蓝图实装后改取该组件
            PdaData merged = data.mergeEntry(recipeId, PdaData.INFINITE_USES);
            if (merged.usesOf(recipeId) == null) continue;
            data = merged;
            inventory.setItem(slot, ItemStack.EMPTY);
            stored++;
        }
        if (stored > 0) writeData(player, hand, pdaStack, data);
        return new DepositResult(stored, rejected);
    }

    /**
     * 绑定或解绑一个格位（主线程）。
     *
     * @param recipeId 目标配方 id；为 {@code null} 表示解绑该格位
     * @return PDA 不在指定手、格位越界、或 recipeId 在 entries 中无对应条目时返回 false
     */
    public static boolean bindShortcut(ServerPlayer player, InteractionHand hand, int shortcutIndex, @Nullable ResourceLocation recipeId) {
        if (shortcutIndex < 0 || shortcutIndex >= PdaData.SHORTCUT_COUNT) return false;
        ItemStack pdaStack = player.getItemInHand(hand);
        if (!PdaHelper.isPda(pdaStack)) return false;
        PdaData data = PdaHelper.getData(pdaStack);
        if (recipeId != null && data.usesOf(recipeId) == null) return false;
        writeData(player, hand, pdaStack, data.withShortcut(shortcutIndex, recipeId));
        return true;
    }

    /**
     * 写入当前格位（主线程）。
     *
     * @return PDA 不在指定手、或 shortcutIndex 不在 0~8 内时返回 false
     */
    public static boolean selectShortcut(ServerPlayer player, InteractionHand hand, int shortcutIndex) {
        if (shortcutIndex < 0 || shortcutIndex >= PdaData.SHORTCUT_COUNT) return false;
        ItemStack pdaStack = player.getItemInHand(hand);
        if (!PdaHelper.isPda(pdaStack)) return false;
        writeData(player, hand, pdaStack, PdaHelper.getData(pdaStack).withSelected(shortcutIndex));
        return true;
    }

    /**
     * 写入设计模式开关（主线程）。
     *
     * @return PDA 不在指定手时返回 false
     */
    public static boolean setDesignMode(ServerPlayer player, InteractionHand hand, boolean on) {
        ItemStack pdaStack = player.getItemInHand(hand);
        if (!PdaHelper.isPda(pdaStack)) return false;
        writeData(player, hand, pdaStack, PdaHelper.getData(pdaStack).withDesignMode(on));
        return true;
    }

    /** 写回数据组件、把栈定位到其槽位并触发物品同步。 */
    private static void writeData(ServerPlayer player, InteractionHand hand, ItemStack pdaStack, PdaData data) {
        PdaHelper.setData(pdaStack, data);
        Inventory inventory = player.getInventory();
        int slot = hand == InteractionHand.MAIN_HAND ? inventory.selected : Inventory.SLOT_OFFHAND;
        inventory.setItem(slot, pdaStack);
        player.inventoryMenu.broadcastChanges();
    }
}
