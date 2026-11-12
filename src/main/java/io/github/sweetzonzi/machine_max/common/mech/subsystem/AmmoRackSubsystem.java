package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.physics.PenetrationKey;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.explosion.CookoffComposition;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AmmoRackSubsystemAttr;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * 物品弹药架子系统。<br>
 * 持有物品形式的弹药容器，是"弹药架 → 人力准备 → 装弹机 → Launcher"这条路线的物品侧端点。
 * <p>
 * <b>WIP：</b>物品与 {@link io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt} 的往返映射
 * 尚未实现。本类型当前只提供容器本身：容量、存取与自身的库存殉爆；它不声明供弹发现频道，
 * 也不参与路由，因此不会被当作可交付的供给者。
 */
@ApiStatus.Experimental
public class AmmoRackSubsystem extends BasicSubsystem {

    public final AmmoRackSubsystemAttr attr;

    /** 物品弹药容器（FIFO） */
    private final SimpleContainer container;

    public AmmoRackSubsystem(ISubsystemHost owner, String name, AmmoRackSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.container = new SimpleContainer(attr.staticAttribute.getMagazineCapacity());
    }

    // ==================== 库存 ====================

    /** 容器内的弹药件数：按非空格计。 */
    public int getAmmoCount() {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) count++;
        }
        return count;
    }

    /** 容器格数上限。 */
    public int getCapacity() {
        return attr.staticAttribute.getMagazineCapacity();
    }

    /** 本架此刻能否储存：自已启用、未摧毁（含 0%—30% 的恢复锁存）。 */
    public boolean isOperational() {
        return isActive() && !isDestroyed();
    }

    /**
     * 从 ItemStack 中解析对应的 {@link ProjectileType}，用物品注册名作为投射物类型 ID 查找。
     *
     * @return 对应弹种；物品为空或没有对应投射物时返回 {@code null}
     */
    @Nullable
    private ProjectileType getProjectileTypeFromItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId == null ? null : ProjectileType.get(getLevel(), itemId);
    }

    // ==================== 持久化 ====================

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(data, items, getOwner().getLevel().registryAccess());
        for (int i = 0; i < items.size() && i < container.getContainerSize(); i++) {
            container.setItem(i, items.get(i));
        }
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        ContainerHelper.saveAllItems(data, container.getItems(), true, getOwner().getLevel().registryAccess());
        return data;
    }

    // ==================== 殉爆 ====================

    @Override
    public void onDestroyed() {
        super.onDestroyed();
        if (getLevel().isClientSide() || getSubPart().isDestroyed()) return;
        detonateStoredAmmo();
    }

    /**
     * 库存殉爆：按容器里的实际物品合成一次普通爆炸，并实际清空容器。
     * <p>
     * 每格按 {@code stack.getCount()} 计入该弹种的发射药贡献与各战斗部的被诱爆贡献；
     * 解析不出投射物类型的物品不产生贡献，但一旦判定需要起爆，容器整体清空（含这些物品），
     * 不产生掉落物。{@code P、D、J} 全为零时不消耗库存、不起爆。
     * <p>
     * <b>调用线程：</b>主线程（子系统摧毁边沿）。
     */
    private void detonateStoredAmmo() {
        if (getAmmoCount() <= 0) return;

        CookoffComposition composition = new CookoffComposition();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            composition.addStoredRound(getProjectileTypeFromItem(stack), stack.getCount());
        }
        if (composition.isEmpty()) return;

        // 起爆位置：最近一次有效受击指认的 HitBox 局部形心；无有效来源回退宿主质心
        Vector3f origin = getLastDamageOrigin();
        // 爆点落在自身命中箱内部，该命中箱必须豁免，否则近距离满值穿深会吃掉整发爆炸
        Set<PenetrationKey> ignoredKeys = getCookoffIgnoredKeys();
        // 归属：打坏本架的那一击的 causing entity，殉爆继续算在同一个射手头上
        Entity attacker = getLastDamageAttacker();
        // 先消耗库存再交给普通爆炸服务：本次起爆后该单元不再有弹药
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
        ExplosionManager.get(getLevel()).detonate(
                origin, composition.toExplosionParams(), getLevel().random.nextLong(), ignoredKeys, attacker);
    }
}
