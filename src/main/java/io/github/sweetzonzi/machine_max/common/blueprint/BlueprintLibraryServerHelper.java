package io.github.sweetzonzi.machine_max.common.blueprint;

import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintMeta;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintProblem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.assembly.VehicleDataSavedPayload;
import io.github.sweetzonzi.machine_max.network.payload.library.BlueprintExtractResultPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * 服务端蓝图库：取出校验、算价、扣点与发放。
 *
 * <p>处理器<b>不校验菜单 / 方块上下文</b>；是否放行只取决于两项——数据是否合法、研发点是否足够。</p>
 *
 * <p>两类请求共用同一套后续流程：</p>
 * <ol>
 *   <li>校验（{@link VehicleData#validate(net.minecraft.world.level.Level)}）</li>
 *   <li>现场重算质量与费用（忽略客户端上报值）</li>
 *   <li>校验并扣研发点（必须先校验再扣，{@code setRp} 内部会 clamp 到 0）</li>
 *   <li>发放 {@code vehicle_blueprint} 物品</li>
 *   <li>回执 {@link BlueprintExtractResultPayload}</li>
 * </ol>
 */
public final class BlueprintLibraryServerHelper {
    /** 费用系数：cost = clamp(round(k × mass), min, max) */
    public static final float COST_FACTOR = 0.3f;
    /** 费用下限 */
    public static final int MIN_COST = 100;
    /** 费用上限 */
    public static final int MAX_COST = 2000;

    private BlueprintLibraryServerHelper() {
    }

    /**
     * 处理「内容包蓝图」取出请求。
     *
     * <p>不使用任何客户端上传的载具数据：按 {@code blueprintId} 取 {@link BlueprintData}，
     * 再由其 {@code template} 取 {@link VehicleData}。</p>
     *
     * @param player      玩家
     * @param blueprintId 内容包蓝图注册 id
     */
    public static void handleExtractPack(ServerPlayer player, ResourceLocation blueprintId) {
        BlueprintData blueprintData = MMDynamicRes.BLUEPRINTS.get(blueprintId);
        if (blueprintData == null) {
            reply(player, BlueprintExtractResultPayload.failure(
                    BlueprintExtractResultPayload.Reason.UNKNOWN_BLUEPRINT, 0));
            return;
        }
        VehicleData template = MMDynamicRes.TEMPLATES.get(blueprintData.getTemplate());
        if (template == null) {
            reply(player, BlueprintExtractResultPayload.failure(
                    BlueprintExtractResultPayload.Reason.UNKNOWN_BLUEPRINT, 0));
            return;
        }
        extract(player, template, template.getMeta(), blueprintId);
    }

    /**
     * 处理「玩家库蓝图」取出请求。
     *
     * <p>客户端上传的载具数据不可信，先合并独立携带的 meta，再进入统一流程。</p>
     *
     * @param player      玩家
     * @param vehicleData 客户端上传的载具数据（不可信）
     * @param meta        独立携带的元信息
     */
    public static void handleExtractLocal(ServerPlayer player, VehicleData vehicleData, BlueprintMeta meta) {
        extract(player, vehicleData, meta, null);
    }

    /**
     * 处理「存入库」请求：读取玩家背包中的 {@code vehicle_blueprint} 组件后回传。
     *
     * <p>仅对 {@code LOCAL} 蓝图有效（物品须带 {@code VEHICLE_DATA} 组件）；{@code PACK} 蓝图只有
     * {@code VEHICLE_BLUEPRINT_PATH}，不满足条件时静默忽略。</p>
     *
     * @param player 玩家
     * @param slot   背包槽位索引
     */
    public static void handleStoreRequest(ServerPlayer player, int slot) {
        var inventory = player.getInventory();
        if (slot < 0 || slot >= inventory.getContainerSize()) return;
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty() || !stack.has(MMDataComponents.getVEHICLE_DATA())) return;
        VehicleData data = stack.get(MMDataComponents.getVEHICLE_DATA());
        if (data == null) return;
        // 回传归一化数据 + 独立 meta，由客户端写文件
        PacketDistributor.sendToPlayer(player, new VehicleDataSavedPayload(data.normalized(), data.getMeta()));
    }

    /**
     * 两类请求共用的后续流程：校验 → 算价 → 扣点 → 发放 → 回执。
     *
     * @param player      玩家
     * @param raw         原始载具数据
     * @param meta        独立携带的元信息
     * @param packId      非空表示内容包蓝图，物品只写 {@code VEHICLE_BLUEPRINT_PATH}
     */
    private static void extract(ServerPlayer player, VehicleData raw, BlueprintMeta meta,
                                @Nullable ResourceLocation packId) {
        VehicleData data = raw.withMeta(meta);

        // 1. 校验：非空即拒绝，回执原因与缺失零件，不扣研发点
        List<BlueprintProblem> problems = data.validate(player.serverLevel());
        if (!problems.isEmpty()) {
            boolean missingParts = problems.stream()
                    .anyMatch(problem -> problem.kind() != BlueprintProblem.Kind.INVALID_VALUE);
            if (missingParts) {
                List<ResourceLocation> missing = problems.stream()
                        .filter(problem -> problem.kind() == BlueprintProblem.Kind.MISSING_PART_TYPE)
                        .map(problem -> ResourceLocation.tryParse(problem.detail()))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                reply(player, BlueprintExtractResultPayload.missingParts(missing));
            } else {
                reply(player, BlueprintExtractResultPayload.failure(
                        BlueprintExtractResultPayload.Reason.INVALID_STRUCTURE, 0));
            }
            return;
        }

        // 2. 费用服务端重算，忽略客户端上报值
        float mass = data.computeDesignMass(player.serverLevel());
        int cost = computeCost(mass);

        // 3. 先校验余额再扣，避免 setRp 内部 clamp 到 0 造成静默扣光
        BlueprintAttachment attachment = player.getData(MMAttachments.getBLUEPRINT());
        boolean creative = player.isCreative();
        if (!creative && attachment.getFreeResearchPoint() < cost) {
            reply(player, BlueprintExtractResultPayload.failure(
                    BlueprintExtractResultPayload.Reason.INSUFFICIENT_RP, 0));
            return;
        }

        // 4. 发放：LOCAL 只写内联数据，PACK 只写蓝图路径，避免被解析成另一分支
        ItemStack blueprint = new ItemStack(MMItems.getVEHICLE_BLUEPRINT().get());
        if (packId != null) {
            blueprint.set(MMDataComponents.getVEHICLE_BLUEPRINT_PATH(), packId);
        } else {
            blueprint.set(MMDataComponents.getVEHICLE_DATA(), data.normalized());
        }
        // 背包放不下则掉落为物品实体，避免「已扣点但物品丢失」
        if (!player.getInventory().add(blueprint)) {
            player.drop(blueprint, false);
        }

        // 5. 扣点与回执
        if (!creative) {
            attachment.setRp(player, attachment.getFreeResearchPoint() - cost);
        }
        reply(player, BlueprintExtractResultPayload.success(cost));
    }

    /** 按设计质量计算取出费用 */
    public static int computeCost(float mass) {
        return Math.clamp(Math.round(COST_FACTOR * mass), MIN_COST, MAX_COST);
    }

    private static void reply(ServerPlayer player, BlueprintExtractResultPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
