package io.github.sweetzonzi.machine_max.common.mech.vehicle.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Getter
public class VehicleData {
    /** 坐标绝对值上限：超出视为非法数据（Minecraft 世界边界为 3000 万） */
    private static final double MAX_COORDINATE = 3.0E7;

    public final String name;
    public final String uuid;
    public final Vec3 pos;
    public final Vec3 min;
    public final Vec3 max;
    public final float hpRatio;//血量比例（0~1，1 表示满血）
    public final Map<String, PartData> parts;
    public final List<ConnectionData> connections;
    /** 设计元信息（作者 / 时间 / 描述），只进 JSON，不参与网络传输与身份判等 */
    public final BlueprintMeta meta;

    public static final Codec<VehicleData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "Vehicle").forGetter(VehicleData::getName),
            Codec.STRING.fieldOf("uuid").forGetter(VehicleData::getUuid),
            Vec3.CODEC.fieldOf("pos").forGetter(VehicleData::getPos),
            Vec3.CODEC.optionalFieldOf("min", Vec3.ZERO).forGetter(VehicleData::getMin),
            Vec3.CODEC.optionalFieldOf("max", Vec3.ZERO).forGetter(VehicleData::getMax),
            Codec.FLOAT.optionalFieldOf("hp_ratio", 1f).forGetter(VehicleData::getHpRatio),
            PartData.MAP_CODEC.fieldOf("parts").forGetter(VehicleData::getParts),
            ConnectionData.CODEC.listOf().fieldOf("connections").forGetter(VehicleData::getConnections),
            BlueprintMeta.CODEC.optionalFieldOf("meta", BlueprintMeta.EMPTY).forGetter(VehicleData::getMeta)
    ).apply(instance, VehicleData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull VehicleData decode(RegistryFriendlyByteBuf buffer) {
            String name = buffer.readUtf();
            String uuid = buffer.readUtf();
            double x = buffer.readFloat();
            double y = buffer.readFloat();
            double z = buffer.readFloat();
            Vec3 pos = new Vec3(x, y, z);
            double minX = buffer.readFloat();
            double minY = buffer.readFloat();
            double minZ = buffer.readFloat();
            Vec3 min = new Vec3(minX, minY, minZ);
            double maxX = buffer.readFloat();
            double maxY = buffer.readFloat();
            double maxZ = buffer.readFloat();
            Vec3 max = new Vec3(maxX, maxY, maxZ);
            float hpRatio = buffer.readFloat();
            Map<String, PartData> parts = PartData.MAP_STREAM_CODEC.decode(buffer);
            List<ConnectionData> connections = buffer.readList(ConnectionData.STREAM_CODEC);
            // meta 不参与网络编码，跨网络时一律为空；需要时由载荷以独立字段携带
            return new VehicleData(name, uuid, pos, min, max, hpRatio, parts, connections, BlueprintMeta.EMPTY);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, @NotNull VehicleData value) {
            buffer.writeUtf(value.name);
            buffer.writeUtf(value.uuid);
            buffer.writeFloat((float) value.pos.x);
            buffer.writeFloat((float) value.pos.y);
            buffer.writeFloat((float) value.pos.z);
            buffer.writeFloat((float) value.min.x);
            buffer.writeFloat((float) value.min.y);
            buffer.writeFloat((float) value.min.z);
            buffer.writeFloat((float) value.max.x);
            buffer.writeFloat((float) value.max.y);
            buffer.writeFloat((float) value.max.z);
            buffer.writeFloat(value.hpRatio);
            PartData.MAP_STREAM_CODEC.encode(buffer, value.parts);
            buffer.writeCollection(value.connections, ConnectionData.STREAM_CODEC);
        }
    };

    public VehicleData(String name, String uuid,
                       Vec3 pos, Vec3 min, Vec3 max,
                       float hpRatio, Map<String, PartData> parts, List<ConnectionData> connections) {
        this(name, uuid, pos, min, max, hpRatio, parts, connections, BlueprintMeta.EMPTY);
    }

    public VehicleData(String name, String uuid,
                       Vec3 pos, Vec3 min, Vec3 max,
                       float hpRatio, Map<String, PartData> parts, List<ConnectionData> connections,
                       BlueprintMeta meta) {
        this.name = name;
        this.uuid = uuid;
        this.pos = pos;
        this.min = min;
        this.max = max;
        this.hpRatio = hpRatio;
        this.parts = parts;
        this.connections = connections;
        this.meta = meta != null ? meta : BlueprintMeta.EMPTY;
    }

    /**
     * 将载具数据打包为方便传输与读取的VehicleData
     *
     * @param vehicle 载具实例
     */
    public VehicleData(VehicleCore vehicle) {
        this.name = vehicle.name;
        this.uuid = vehicle.getUuid().toString();
        this.pos = vehicle.getPosition();
        AABB aabb = vehicle.getAABB();
        this.min = aabb.getMinPosition().subtract(pos);
        this.max = aabb.getMaxPosition().subtract(pos);
        // 血量以比例持久化，避免内容包调整部件耐久上限后存档血量失真
        this.hpRatio = vehicle.getMaxHp() > 0f
                ? Math.clamp(vehicle.getHp() / vehicle.getMaxHp(), 0f, 1f)
                : 1f;
        this.parts = vehicle.getPartData();
        this.connections = vehicle.getConnectionData();
        // 由载具实例构造的数据不带元信息，只有抄录 / 取出时才写入
        this.meta = BlueprintMeta.EMPTY;
    }

    /**
     * 将载具数据序列化为 JSON 字符串
     */
    public static String serializeToJsonString(VehicleData vehicleData) {
        JsonElement encoded = VehicleData.CODEC.encodeStart(JsonOps.INSTANCE, vehicleData)
                .getOrThrow(IllegalArgumentException::new);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(encoded);
    }

    public static void serializeVehicleDataToJson(VehicleData vehicleData, File filePath) throws IOException {
        // 将 JSON 字符串写入文件
        try (FileWriter fileWriter = new FileWriter(filePath)) {
            fileWriter.write(serializeToJsonString(vehicleData));
        }
    }

    /**
     * 从 JSON 字符串解析载具数据。
     *
     * <p>客户端蓝图库读取本地文件时使用。解析失败返回 {@link Optional#empty()}，由调用方降级为
     * 「错误条目」，不向上抛异常。</p>
     *
     * @param json JSON 文本
     * @return 解析结果，失败为空
     */
    public static Optional<VehicleData> parseFromJson(String json) {
        try {
            JsonElement element = JsonParser.parseString(json);
            return CODEC.parse(JsonOps.INSTANCE, element).result();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * 返回坐标归一化副本：载具位置归零、子零件坐标同步平移、线速度与角速度清零。
     *
     * <p>所有载具物品（收纳 / 蓝图）都应经过本方法：数据以原点为基准便于移植，且放出来时静止。</p>
     *
     * <p><b>子零件坐标必须随 {@code pos} 一起平移</b>：{@link SubPartData#getPosRotVelVel()} 记录的是
     * 绝对世界坐标，而 {@code VehicleCore.setPos} 按「目标位置 − 当前 {@code pos}」的相对量移动物理体。
     * 若只把 {@code pos} 归零而不平移子零件，重建后的相对位移会叠加抄录点坐标，载具会被推到错误位置
     * （表现为服务端无碰撞、客户端不可见）。平移后 {@code pos} 与子零件坐标都以原点为基准，相对关系不变。</p>
     *
     * @return 归一化坐标后的新实例，其余状态原样保留
     */
    public VehicleData rebased() {
        Vector3f offset = PhysicsHelperKt.toBVector3f(pos);
        Map<String, PartData> rebasedParts = new HashMap<>(parts.size());
        for (Map.Entry<String, PartData> entry : parts.entrySet()) {
            PartData part = entry.getValue();
            // 子零件坐标平移 + 速度归零（放出来的载具不应带残余动量）
            Map<String, SubPartData> rebasedSubParts = new HashMap<>(part.subParts.size());
            for (Map.Entry<String, SubPartData> subEntry : part.subParts.entrySet()) {
                SubPartData sub = subEntry.getValue();
                PosRotVelVel pose = sub.getPosRotVelVel();
                rebasedSubParts.put(subEntry.getKey(), new SubPartData(
                        sub.getId(),
                        sub.getDurabilityRatio(),
                        new PosRotVelVel(
                                pose.position().subtract(offset, new Vector3f()),
                                pose.rotation(),
                                new Vector3f(),
                                new Vector3f()
                        ),
                        sub.getConnectorData(),
                        sub.getSubsystemData()
                ));
            }
            rebasedParts.put(entry.getKey(), new PartData(
                    part.registryKey,
                    part.name,
                    part.uuid,
                    part.variant,
                    part.customRecipe,
                    part.assemblingProgress,
                    part.sharedDurabilityRatio,
                    part.materialAssemblingProgress,
                    part.renderWireframe,
                    part.textureName,
                    rebasedSubParts
            ));
        }
        return new VehicleData(name, uuid, Vec3.ZERO, min, max, hpRatio, rebasedParts, connections, meta);
    }

    /**
     * 蓝图专用：在 {@link #rebased()} 基础上把血量比例归满。
     *
     * <p>「骨架」语义由放置时的 {@code readAdditionalData=false} 保证（进度、材料进度、耐久比例与
     * 连接器 / 子系统数据都不读），因此这里不再重复清零这些字段。</p>
     *
     * @return 归一化后的新实例
     */
    public VehicleData normalized() {
        VehicleData rebased = rebased();
        return new VehicleData(rebased.name, rebased.uuid, rebased.pos, rebased.min, rebased.max,
                1f, rebased.parts, rebased.connections, rebased.meta);
    }

    /**
     * 现场统计设计质量（满配口径，不含进度折算）。
     *
     * <p>按 {@code PartData.registryKey + variant} 查 {@link PartType} → {@link VariantAttr}，
     * 遍历其子零件累加 {@link SubPartAttr#getMass()}。纯查表，<b>不实例化物理体</b>，服务端可用。</p>
     *
     * @param level 用于区分客户端 / 服务端注册表
     * @return 设计总质量；零件缺失时跳过该项
     */
    public float computeDesignMass(Level level) {
        float mass = 0f;
        for (PartData part : parts.values()) {
            PartType partType = PartType.get(level, part.registryKey);
            if (partType == null) continue;
            VariantAttr variant = partType.getVariant(part.variant);
            if (variant == null) continue;
            for (SubPartAttr subPart : variant.getSubParts().values()) {
                mass += subPart.getMass();
            }
        }
        return mass;
    }

    /**
     * 校验载具数据是否可作为蓝图使用。
     *
     * <p>权威校验清单见设计文档 §7。本方法<b>不抛异常</b>，返回空列表表示通过；
     * 空列表以外一律拒绝，不做「部分生成」。</p>
     *
     * @param level 用于注册表与配方查询
     * @return 问题列表，空表示通过
     */
    public List<BlueprintProblem> validate(Level level) {
        List<BlueprintProblem> problems = new ArrayList<>();

        // 结构：parts 非空
        if (parts.isEmpty()) {
            problems.add(BlueprintProblem.invalidValue("parts 为空"));
            return problems;
        }

        // 数值有限性：pos / min / max 必须为有限值且在坐标上限内
        checkFiniteVec(problems, "pos", pos);
        checkFiniteVec(problems, "min", min);
        checkFiniteVec(problems, "max", max);

        Set<String> partUuids = new HashSet<>(parts.size());
        for (Map.Entry<String, PartData> entry : parts.entrySet()) {
            PartData part = entry.getValue();
            partUuids.add(part.uuid);

            // UUID 可解析
            try {
                UUID.fromString(part.uuid);
            } catch (IllegalArgumentException e) {
                problems.add(BlueprintProblem.invalidValue("非法 uuid: " + part.uuid));
            }

            // 注册表：零件类型与变体必须存在
            PartType partType = PartType.get(level, part.registryKey);
            if (partType == null) {
                problems.add(BlueprintProblem.missingPartType(part.registryKey.toString()));
                continue;
            }
            if (partType.getVariant(part.variant) == null) {
                problems.add(BlueprintProblem.missingVariant(part.registryKey + "#" + part.variant));
            }

            // 配方：EMPTY 或产物 PART_TYPE 与 registryKey 一致
            if (!FabricatingRecipe.EMPTY.equals(part.customRecipe)) {
                RecipeHolder<?> holder = level.getRecipeManager().byKey(part.customRecipe).orElse(null);
                if (holder == null || !(holder.value() instanceof FabricatingRecipe recipe)
                        || !part.registryKey.equals(recipe.getResultItem(level.registryAccess())
                        .get(MMDataComponents.getPART_TYPE()))) {
                    problems.add(BlueprintProblem.invalidValue("非法 customRecipe: " + part.customRecipe));
                }
            }
        }

        // 结构：connections 引用的零件必须存在
        for (ConnectionData connection : connections) {
            if (!partUuids.contains(connection.partUuidA)) {
                problems.add(BlueprintProblem.invalidValue("连接引用了不存在的零件: " + connection.partUuidA));
            }
            if (!partUuids.contains(connection.partUuidS)) {
                problems.add(BlueprintProblem.invalidValue("连接引用了不存在的零件: " + connection.partUuidS));
            }
        }

        return problems;
    }

    /** 检查坐标向量是否为有限值且不超过坐标上限 */
    private static void checkFiniteVec(List<BlueprintProblem> problems, String field, Vec3 vec) {
        if (vec == null) {
            problems.add(BlueprintProblem.invalidValue(field + " 为空"));
            return;
        }
        if (!isFinite(vec.x) || !isFinite(vec.y) || !isFinite(vec.z)) {
            problems.add(BlueprintProblem.invalidValue(field + " 非有限值: " + vec));
        }
    }

    private static boolean isFinite(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAX_COORDINATE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VehicleData that)) return false;
        return Float.compare(hpRatio, that.hpRatio) == 0 && Objects.equals(name, that.name) && Objects.equals(uuid, that.uuid) && Objects.equals(pos, that.pos) && Objects.equals(min, that.min) && Objects.equals(max, that.max) && Objects.equals(parts, that.parts) && Objects.equals(connections, that.connections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, uuid, pos, min, max, hpRatio, parts, connections);
    }

    public VehicleData withNewName(String name) {
        return new VehicleData(name, uuid, pos, min, max, hpRatio, parts, connections, meta);
    }

    public VehicleData withNewUUID(UUID uuid) {
        return new VehicleData(name, uuid.toString(), pos, min, max, hpRatio, parts, connections, meta);
    }

    /**
     * 附加元信息，不可变风格。
     *
     * <p>跨网络载荷以独立字段携带 meta，服务端须先 {@code withMeta(payload.meta())} 再归一化。</p>
     *
     * @param meta 元信息
     * @return 附带该元信息的新实例
     */
    public VehicleData withMeta(BlueprintMeta meta) {
        return new VehicleData(name, uuid, pos, min, max, hpRatio, parts, connections, meta);
    }
}
