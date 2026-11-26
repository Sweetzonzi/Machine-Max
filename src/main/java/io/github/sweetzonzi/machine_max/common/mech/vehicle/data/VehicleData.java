package io.github.sweetzonzi.machine_max.common.mech.vehicle.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jme3.math.Vector3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Matrix3f;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.shapes.infos.ChildCollisionShape;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLinkData;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import lombok.Getter;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    public final UUID uuid;
    public final Vec3 pos;
    /**
     * 旧格式兼容的 AABB 缓存，坐标相对 {@link #pos}；不代表稳定的载具局部边界。
     * 放置、射程与碰撞逻辑必须从各子零件的物理形状按目标位姿重新计算。新代码不要依赖此字段。
     */
    @Deprecated
    public final Vec3 min;
    /** 旧格式兼容的 AABB 缓存；语义与 {@link #min} 相同，不应作为几何权威来源。 */
    @Deprecated
    public final Vec3 max;
    public final float hpRatio;//血量比例（0~1，1 表示满血）
    public final Map<String, RegularPartData> parts;
    public final List<ConnectionData> connections;
    /** 履带持久状态；空列表表示没有履带。 */
    public final List<TrackLinkData> trackLinks;
    /** 设计元信息（作者 / 时间 / 描述），只进 JSON，不参与网络传输与身份判等 */
    public final BlueprintMeta meta;

    /** JSON 存档/蓝图编解码：UUID 字段用 {@link UUIDUtil#STRING_CODEC} 落盘为字符串，兼容既有模板。 */
    private static final Codec<VehicleData> STRUCTURED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "Vehicle").forGetter(VehicleData::getName),
            UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(VehicleData::getUuid),
            Vec3.CODEC.fieldOf("pos").forGetter(VehicleData::getPos),
            Vec3.CODEC.optionalFieldOf("min", Vec3.ZERO).forGetter(VehicleData::getMin),
            Vec3.CODEC.optionalFieldOf("max", Vec3.ZERO).forGetter(VehicleData::getMax),
            Codec.FLOAT.optionalFieldOf("hp_ratio", 1f).forGetter(VehicleData::getHpRatio),
            RegularPartData.MAP_CODEC.fieldOf("parts").forGetter(VehicleData::getParts),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("part_order", List.of())
                    .forGetter(VehicleData::partOrderList),
            ConnectionData.CODEC.listOf().fieldOf("connections").forGetter(VehicleData::getConnections),
            TrackLinkData.CODEC.listOf().optionalFieldOf("track_links", List.of())
                    .forGetter(VehicleData::getTrackLinks),
            BlueprintMeta.CODEC.optionalFieldOf("meta", BlueprintMeta.EMPTY).forGetter(VehicleData::getMeta)
    ).apply(instance, VehicleData::new));

    /**
     * 载具数据的 JSON 编解码。
     *
     * <p>{@code part_order} 是零件安装序的显式承载：{@code parts} 只负责按键查找，JSON 对象与
     * NBT 复合标签的键序都不是可依赖的持久契约，顺序必须单独写出来。解码时按该列表重排
     * {@code parts}，因此下游只读一份有序映射。</p>
     *
     * <p>列表与 {@code parts} 的键集合必须严格相同：重复、引用了不存在的部件、以及列表长度与
     * 条目数不符都在这里拒绝。解码期校验失败以 {@link DataResult} 错误返回，不抛出。</p>
     */
    public static final Codec<VehicleData> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<VehicleData, T>> decode(DynamicOps<T> ops, T input) {
            try {
                return STRUCTURED_CODEC.decode(ops, input);
            } catch (IllegalArgumentException e) {
                return DataResult.error(e::getMessage);
            }
        }

        @Override
        public <T> DataResult<T> encode(VehicleData input, DynamicOps<T> ops, T prefix) {
            return STRUCTURED_CODEC.encode(input, ops, prefix);
        }
    };

    /**
     * 网络编码：载具 UUID 以双 long 传输（16 字节），字段本身即为 UUID，无需字符串转换。
     * JSON 落盘/蓝图走 {@link #CODEC}，保持字符串 UUID 不变。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull VehicleData decode(RegistryFriendlyByteBuf buffer) {
            String name = buffer.readUtf();
            UUID uuid = buffer.readUUID();
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
            Map<String, RegularPartData> parts = RegularPartData.MAP_STREAM_CODEC.decode(buffer);
            List<ConnectionData> connections = buffer.readList(ConnectionData.STREAM_CODEC);
            List<TrackLinkData> trackLinks = buffer.readList(TrackLinkData.STREAM_CODEC);
            // meta 不参与网络编码，跨网络时一律为空；需要时由载荷以独立字段携带
            return new VehicleData(name, uuid, pos, min, max, hpRatio, parts, connections,
                    trackLinks, BlueprintMeta.EMPTY);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, @NotNull VehicleData value) {
            buffer.writeUtf(value.name);
            buffer.writeUUID(value.uuid);
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
            RegularPartData.MAP_STREAM_CODEC.encode(buffer, value.parts);
            buffer.writeCollection(value.connections, ConnectionData.STREAM_CODEC);
            buffer.writeCollection(value.trackLinks, TrackLinkData.STREAM_CODEC);
        }
    };

    public VehicleData(String name, UUID uuid,
                       Vec3 pos, Vec3 min, Vec3 max,
                       float hpRatio, Map<String, RegularPartData> parts, List<ConnectionData> connections) {
        this(name, uuid, pos, min, max, hpRatio, parts, connections, List.of(), BlueprintMeta.EMPTY);
    }

    public VehicleData(String name, UUID uuid,
                       Vec3 pos, Vec3 min, Vec3 max,
                       float hpRatio, Map<String, RegularPartData> parts, List<ConnectionData> connections,
                       BlueprintMeta meta) {
        this(name, uuid, pos, min, max, hpRatio, parts, connections, List.of(), meta);
    }

    public VehicleData(String name, UUID uuid,
                       Vec3 pos, Vec3 min, Vec3 max,
                       float hpRatio, Map<String, RegularPartData> parts, List<ConnectionData> connections,
                       List<TrackLinkData> trackLinks, BlueprintMeta meta) {
        this.name = name;
        this.uuid = uuid;
        this.pos = pos;
        this.min = min;
        this.max = max;
        this.hpRatio = hpRatio;
        this.parts = parts;
        this.connections = connections;
        this.trackLinks = trackLinks == null ? List.of() : List.copyOf(trackLinks);
        this.meta = meta != null ? meta : BlueprintMeta.EMPTY;
    }

    /**
     * 解码入口：按 {@code part_order} 把 {@code parts} 排成安装序。
     *
     * <p>列表为空表示数据没有携带顺序（旧存档、旧模板），此时按 UUID 字符串全序建立一次
     * 确定性基线；该基线只服务旧格式入口，运行时没有第二条隐式重排的路径。</p>
     *
     * @param declaredOrder {@code part_order}；空表示旧格式
     * @throws IllegalArgumentException 列表与 {@code parts} 键集合不一致
     */
    private VehicleData(String name, UUID uuid,
                        Vec3 pos, Vec3 min, Vec3 max,
                        float hpRatio, Map<String, RegularPartData> parts, List<UUID> declaredOrder,
                        List<ConnectionData> connections,
                        List<TrackLinkData> trackLinks, BlueprintMeta meta) {
        this(name, uuid, pos, min, max, hpRatio, orderParts(parts, declaredOrder), connections,
                trackLinks, meta);
    }

    /**
     * 按显式顺序重排零件映射；缺省顺序时退回 UUID 字符串全序。
     *
     * @return 新映射，键序即安装序
     */
    private static Map<String, RegularPartData> orderParts(Map<String, RegularPartData> parts,
                                                           List<UUID> declaredOrder) {
        LinkedHashMap<String, RegularPartData> ordered = LinkedHashMap.newLinkedHashMap(parts.size());
        if (declaredOrder.isEmpty()) {
            List<String> keys = new ArrayList<>(parts.keySet());
            keys.sort(Comparator.naturalOrder());
            for (String key : keys) ordered.put(key, parts.get(key));
            return ordered;
        }
        if (declaredOrder.size() != parts.size()) {
            throw new IllegalArgumentException("part_order 条目数 " + declaredOrder.size()
                    + " 与 parts 条目数 " + parts.size() + " 不一致");
        }
        for (UUID id : declaredOrder) {
            String key = id.toString();
            RegularPartData part = parts.get(key);
            if (part == null) throw new IllegalArgumentException("part_order 引用了不存在的部件: " + key);
            if (ordered.put(key, part) != null) throw new IllegalArgumentException("part_order 重复的部件: " + key);
        }
        return ordered;
    }

    /**
     * 当前零件安装序，以 UUID 列表形式落盘为 {@code part_order}。
     *
     * @return 与 {@link #parts} 键序一致的 UUID 列表
     */
    public List<UUID> partOrderList() {
        List<UUID> order = new ArrayList<>(parts.size());
        for (String key : parts.keySet()) order.add(UUID.fromString(key));
        return order;
    }

    /**
     * 将载具数据打包为方便传输与读取的VehicleData
     *
     * @param vehicle 载具实例
     */
    public VehicleData(VehicleCore vehicle) {
        this.name = vehicle.name;
        this.uuid = vehicle.getUuid();
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
        this.trackLinks = vehicle.trackLinkData();
        // 由载具实例构造的数据不带元信息，只有抄录 / 取出时才写入
        this.meta = BlueprintMeta.EMPTY;
    }

    /**
     * 查找放置锚点与朝向的参考根子零件（主线程）。总质量相同时取 UUID 自然顺序最小的部件。
     *
     * @param level 用于解析部件类型、变体与子零件质量的世界
     * @return 最大质量部件的根子零件数据
     * @throws IllegalArgumentException 当找不到有效的最大质量部件根子零件时
     */
    public SubPartData findPlacementReferenceRoot(Level level) {
        RegularPartData rootPart = null;
        String rootName = null;
        float maxMass = Float.NEGATIVE_INFINITY;
        for (RegularPartData part : parts.values()) {
            RegularPartType type = RegularPartType.get(level, part.registryKey);
            VariantAttr variant = type == null ? null : type.getVariant(part.variant);
            if (variant == null) continue;
            float mass = 0f;
            for (SubPartAttr attr : variant.getSubParts().values()) mass += attr.getMass();
            if (rootPart == null || mass > maxMass || (mass == maxMass
                    && UUID.fromString(part.uuid).compareTo(UUID.fromString(rootPart.uuid)) < 0)) {
                maxMass = mass;
                rootPart = part;
                rootName = variant.getRootSubPartName();
            }
        }
        if (rootPart == null || rootName == null || !rootPart.subParts.containsKey(rootName)) {
            throw new IllegalArgumentException("找不到有效的载具放置参考根子零件");
        }
        return rootPart.subParts.get(rootName);
    }

    /**
     * 在指定坐标系中合成整车物理碰撞形状的 AABB（主线程）。
     * 子零件使用数据中记录的位姿，与是否调用过 {@link #rebased()} 无关。
     *
     * @param level 用于解析部件类型、变体及物理碰撞形状的世界
     * @param sourceOrigin 所求坐标系的原点，在载具数据坐标系中的位置
     * @param sourceToFrame 将载具数据坐标系的方向旋转到所求坐标系的四元数
     * @return 在所求坐标系的 XYZ 轴上对齐的整车碰撞包围盒
     */
    public AABB computeCollisionBounds(Level level, Vector3f sourceOrigin, Quaternion sourceToFrame) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
        BoundingBox box = new BoundingBox();
        Vector3f boxMin = new Vector3f();
        Vector3f boxMax = new Vector3f();
        Vector3f childPosition = new Vector3f();
        Quaternion childRotation = new Quaternion();
        Matrix3f frameAxes = sourceToFrame.toRotationMatrix();
        for (RegularPartData part : parts.values()) {
            RegularPartType type = RegularPartType.get(level, part.registryKey);
            VariantAttr variant = type.getVariant(part.variant);
            for (Map.Entry<String, SubPartData> subEntry : part.subParts.entrySet()) {
                SubPartAttr attr = variant.getSubParts().get(subEntry.getKey());
                PosRotVelVel pose = subEntry.getValue().posRotVelVel;
                Vector3f position = frameAxes.mult(pose.position().subtract(sourceOrigin), null);
                Quaternion rotation = sourceToFrame.mult(SparkMathKt.toBQuaternion(pose.rotation()));
                Matrix3f subPartAxes = rotation.toRotationMatrix();
                // 逐个子形状换基；旋转 compound 的局部总盒会把空隙也算进接地高度。
                for (ChildCollisionShape child : attr.getCollisionShape(variant).listChildren()) {
                    subPartAxes.mult(child.copyOffset(childPosition), childPosition).addLocal(position);
                    rotation.mult(child.copyRotation(childRotation), childRotation);
                    child.getShape().boundingBoxWithoutRecalculate(childPosition, childRotation.toRotationMatrix(), box);
                    box.getMin(boxMin);
                    box.getMax(boxMax);
                    min.set(Math.min(min.x, boxMin.x), Math.min(min.y, boxMin.y), Math.min(min.z, boxMin.z));
                    max.set(Math.max(max.x, boxMax.x), Math.max(max.y, boxMax.y), Math.max(max.z, boxMax.z));
                }
            }
        }
        return new AABB(min.x, min.y, min.z, max.x, max.y, max.z);
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
        Map<String, RegularPartData> rebasedParts = LinkedHashMap.newLinkedHashMap(parts.size());
        for (Map.Entry<String, RegularPartData> entry : parts.entrySet()) {
            RegularPartData part = entry.getValue();
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
            rebasedParts.put(entry.getKey(), new RegularPartData(
                    part.registryKey,
                    part.name,
                    part.uuid,
                    part.variant,
                    part.assemblingProgress,
                    part.sharedDurabilityRatio,
                    part.materialAssemblingProgress,
                    part.assembling,
                    part.destroyed,
                    part.destroyTime,
                    part.textureName,
                    rebasedSubParts,
                    part.staleness
            ));
        }
        return new VehicleData(name, uuid, Vec3.ZERO, min, max, hpRatio, rebasedParts, connections,
                trackLinks, meta);
    }

    /**
     * 蓝图专用：在 {@link #rebased()} 基础上把血量比例归满，并清空履带的内容物状态。
     *
     * <p>「骨架」语义由放置时的 {@code readAdditionalData=false} 保证（进度、材料进度、耐久比例与
     * 连接器 / 子系统数据都不读），因此这里不再重复清零这些字段。</p>
     *
     * <p>履带走 {@link TrackLinkData#asPlan()}：材料状态清空、相位归零，放出来是 0/N 的方案，
     * 装配完成后由焊枪扣 N 节建成。装配体物品（收纳）走 {@link #rebased()}，原样保留真实状态。</p>
     *
     * @return 归一化后的新实例
     */
    public VehicleData normalized() {
        VehicleData rebased = rebased();
        List<TrackLinkData> plannedTracks = rebased.trackLinks.stream()
                .map(TrackLinkData::asPlan)
                .toList();
        return new VehicleData(rebased.name, rebased.uuid, rebased.pos, rebased.min, rebased.max,
                1f, rebased.parts, rebased.connections, plannedTracks, rebased.meta);
    }

    /**
     * 现场统计设计质量（满配口径，不含进度折算）。
     *
     * <p>按 {@code RegularPartData.registryKey + variant} 查 {@link RegularPartType} → {@link VariantAttr}，
     * 遍历其子零件累加 {@link SubPartAttr#getMass()}。纯查表，<b>不实例化物理体</b>，服务端可用。</p>
     *
     * @param level 用于区分客户端 / 服务端注册表
     * @return 设计总质量；零件缺失时跳过该项
     */
    public float computeDesignMass(Level level) {
        float mass = 0f;
        for (RegularPartData part : parts.values()) {
            RegularPartType partType = RegularPartType.get(level, part.registryKey);
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

        // 存储的 min/max 是兼容缓存，不参与蓝图有效性判定。
        checkFiniteVec(problems, "pos", pos);

        Set<UUID> partUuids = new HashSet<>(parts.size());
        // 结构：parts 的键必须可解析为 UUID——part_order 与蓝图往返都以它为部件身份
        for (String key : parts.keySet()) {
            try {
                UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                problems.add(BlueprintProblem.invalidValue("非法 parts 键: " + key));
            }
        }
        for (Map.Entry<String, RegularPartData> entry : parts.entrySet()) {
            RegularPartData part = entry.getValue();

            // UUID 可解析
            try {
                partUuids.add(UUID.fromString(part.uuid));
            } catch (IllegalArgumentException e) {
                problems.add(BlueprintProblem.invalidValue("非法 uuid: " + part.uuid));
            }

            // 注册表：零件类型与变体必须存在
            RegularPartType partType = RegularPartType.get(level, part.registryKey);
            if (partType == null) {
                problems.add(BlueprintProblem.missingPartType(part.registryKey.toString()));
                continue;
            }
            if (partType.getVariant(part.variant) == null) {
                problems.add(BlueprintProblem.missingVariant(part.registryKey + "#" + part.variant));
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
        return new VehicleData(name, uuid, pos, min, max, hpRatio, parts, connections, trackLinks, meta);
    }

    public VehicleData withNewUUID(UUID uuid) {
        return new VehicleData(name, uuid, pos, min, max, hpRatio, parts, connections,
                trackLinks, meta);
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
        return new VehicleData(name, uuid, pos, min, max, hpRatio, parts, connections, trackLinks, meta);
    }
}
