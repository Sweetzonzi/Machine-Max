package io.github.sweetzonzi.machine_max.common.mech.part;

/**
 * 本次构造路径授予部件的实例权限，适用于新建和存档恢复。
 *
 * <p>权限由宿主选择，构造时写入 {@link AbstractPart#crowbarRemovable}。
 * 该值不属于内容包静态定义，也不从存档读取；同一型号可因构造路径而具有不同拆卸权限。</p>
 *
 * @param crowbarRemovable 是否允许 MM 撬棍拆卸；此开关不禁止伤害销毁或宿主主动移除部件
 */
public record PartCreationContext(boolean crowbarRemovable) {
    /**
     * MM 普通物品与载具路径的默认权限，允许使用撬棍拆卸。
     */
    public static final PartCreationContext REGULAR = new PartCreationContext(true);
}
