package io.github.sweetzonzi.machine_max.common.mech.part;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.SubPartData;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/**
 * 各部件种类动态存档 DTO 的公共字段。
 *
 * <p>本类只承载状态，不选择实例类型，也不创建物理对象。具体子类提供自己的 Codec，
 * {@link PartFactory#decodeData(boolean, com.mojang.serialization.DynamicOps, Object)} 根据
 * {@link #registryKey} 查到已发布定义后选择专用存档 Codec。</p>
 * <p>字段引用为 final，但子零件映射与其中的 NBT 并非深度不可变。恢复过程必须把输入视为只读，
 * 将 NBT 复制后交给连接点和子系统，保证完整恢复与骨架恢复可以共用同一份模板。</p>
 */
@Getter
public abstract class AbstractPartData {
    /**
     * 型号定义的注册名，供工厂选择种类和静态配置。
     */
    public final ResourceLocation registryKey;
    /**
     * 实例显示名称，不参与型号分派。
     */
    public final String name;
    /**
     * 实例 UUID 的文本形式；解析与重复检查由恢复入口和装配体负责。
     */
    public final String uuid;
    /**
     * 保存时选用的变体名，恢复时必须精确命中定义。
     */
    public final String variant;
    /**
     * 共享耐久比例；共享耐久模式下用于在新上限上恢复耐久，通常位于 [0, 1]。
     */
    public final float sharedDurabilityRatio;
    /**
     * 保存的涂装名称；骨架恢复忽略它并使用新建时的默认涂装。
     */
    public final String textureName;
    /**
     * 子零件名到动态状态的映射；保存的集合允许小于变体定义的集合。
     */
    public final Map<String, SubPartData> subParts;

    /**
     * 保存解码结果的公共字段；不复制映射，也不检查对应定义是否已经发布。
     *
     * <p>该构造器允许先读取 DTO、后集中预检；实例化前应调用
     * {@link PartFactory#validateRestore(AbstractPartData, boolean)}。调用者不得在恢复期间修改传入映射。</p>
     */
    protected AbstractPartData(ResourceLocation registryKey, String name, String uuid, String variant,
                               float sharedDurabilityRatio, String textureName, Map<String, SubPartData> subParts) {
        this.registryKey = registryKey;
        this.name = name;
        this.uuid = uuid;
        this.variant = variant;
        this.sharedDurabilityRatio = sharedDurabilityRatio;
        this.textureName = textureName;
        this.subParts = subParts;
    }

    /**
     * 从运行时部件采集公共动态状态，为每个子零件创建存档 DTO。
     *
     * <p>共享耐久保存为 [0, 1] 比例；未启用共享耐久或上限为零时保存为 1。
     * 本方法不锁定物理步，调用者应在宿主安排的存档阶段调用，不能据此假设所有物理字段是原子快照。</p>
     */
    protected AbstractPartData(AbstractPart<?> part) {
        this.registryKey = part.type.getRegistryKey();
        this.name = part.name;
        this.uuid = part.uuid.toString();
        this.variant = part.variantName;
        float max = part.getSharedMaxDurability();
        this.sharedDurabilityRatio = part.type.shareDurability && max > 0f
                ? Math.clamp(part.getSharedDurability() / max, 0f, 1f) : 1f;
        this.textureName = part.textureName;
        this.subParts = new HashMap<>();
        part.subParts.forEach((key, subPart) -> subParts.put(key, new SubPartData(subPart)));
    }
}
