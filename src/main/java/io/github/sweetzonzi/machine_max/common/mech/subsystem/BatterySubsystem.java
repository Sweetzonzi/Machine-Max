package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.energy.IEnergyStorage;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BatterySubsystemAttr;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.jetbrains.annotations.NotNull;

public class BatterySubsystem extends BasicSubsystem implements IEnergyStorage {

    public final BatterySubsystemAttr attr;

    protected static final EntityDataAccessor<Float> STORED_ENERGY_ID =
            SynchedEntityData.defineId(BatterySubsystem.class, EntityDataSerializers.FLOAT);

    public BatterySubsystem(ISubsystemHost owner, String name, BatterySubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        setStoredEnergy(attr.getStaticAttribute().getMaxStoredEnergy() * 0.5f);
    }

    @Override
    public float getPowerDemand() {
        if (!isElectricActive() || isDestroyed()) return 0;
        float current = getStoredEnergy();
        float max = getMaxStoredEnergy();
        if (current < max * 0.99f) {
            return getMaxChargeRate();
        }
        return 0;
    }

    @Override
    public void onPowerSupplied(float watts) {
    }

    @Override
    public boolean isElectricActive() {
        return isActive();
    }

    @Override
    public float getProductionCapacity() {
        if (!isElectricActive() || isDestroyed()) return 0;
        float current = getStoredEnergy();
        if (current > 0) {
            return getMaxDischargeRate();
        }
        return 0;
    }

    @Override
    public void onPowerProduced(float watts) {
    }

    @Override
    public float getStoredEnergy() {
        return Math.clamp(
                this.synchedData.get(STORED_ENERGY_ID),
                0,
                getMaxStoredEnergy()
        );
    }

    @Override
    public float getMaxStoredEnergy() {
        return attr.getStaticAttribute().getMaxStoredEnergy();
    }

    @Override
    public float getMaxChargeRate() {
        return attr.getStaticAttribute().getMaxChargeRate();
    }

    @Override
    public float getMaxDischargeRate() {
        return attr.getStaticAttribute().getMaxDischargeRate();
    }

    @Override
    public void onEnergyStored(float deltaEnergy) {
        float current = getStoredEnergy();
        float newVal = Math.clamp(current + deltaEnergy, 0, getMaxStoredEnergy());
        setStoredEnergy(newVal);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STORED_ENERGY_ID, 0f);
    }

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        if (data.contains("stored_energy")) {
            setStoredEnergy(data.getFloat("stored_energy"));
        }
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        data.putFloat("stored_energy", getStoredEnergy());
        return data;
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
    }

    private void setStoredEnergy(float energy) {
        this.synchedData.set(STORED_ENERGY_ID, Math.clamp(energy, 0, getMaxStoredEnergy()));
    }
}
