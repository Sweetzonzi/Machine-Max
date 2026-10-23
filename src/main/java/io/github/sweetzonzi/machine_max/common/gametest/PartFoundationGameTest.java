package io.github.sweetzonzi.machine_max.common.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.part.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ItemStorageSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.ItemStorageSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.*;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** Real-world construction and restoration checks; bodies stay outside the physics world. */
@GameTestHolder(MachineMax.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PartFoundationGameTest {
    private record Selection(RegularPartType type, String variant) {}

    @GameTest(template = "part_foundation", batch = "mmPartFoundation", timeoutTicks = 100)
    public static void fullStateAndBlueprintSkeleton(GameTestHelper helper) {
        Selection selected = PartDefinitions.regular(false).values().stream()
                .flatMap(type -> type.variants.entrySet().stream()
                        .filter(entry -> entry.getValue().textures.size() > 1 && entry.getValue().subParts.values().stream()
                                .anyMatch(sub -> sub.subsystems.values().stream().anyMatch(attr ->
                                        attr instanceof ItemStorageSubsystemAttr)))
                        .map(entry -> new Selection(type, entry.getKey())))
                .findFirst().orElseThrow(() -> new IllegalStateException("Need a painted regular part with a storage subsystem"));
        Level level = helper.getLevel();
        RegularPart source = PartFactory.createRegular(selected.type, selected.variant, level);
        RegularPart full = null;
        RegularPart skeleton = null;
        VehicleCore fullVehicle = null;
        VehicleCore skeletonVehicle = null;
        try {
            source.assemblingProgress = 0.6f;
            source.setMaterialProgress(7);
            source.renderWireframe = false;
            String defaultTexture = source.textureName;
            String paint = source.variant.textures.keySet().stream().filter(name -> !name.equals(defaultTexture)).findFirst().orElseThrow();
            source.applyTexture(paint);
            for (SubPart child : source.subParts.values()) child.setDurability(child.getMaxDurability() * 0.3f);
            source.getAllSubsystems().forEach(sub -> {
                sub.setDurability(sub.getMaxDurability() * 0.2f);
                SimpleContainer storage = storage(sub);
                if (storage != null) storage.setItem(0, new ItemStack(Items.STICK, 37));
            });
            RegularPartData saved = source.save();
            var before = RegularPartData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow();
            VehicleData vehicleData = new VehicleData("mode comparison", UUID.randomUUID().toString(),
                    net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO, net.minecraft.world.phys.Vec3.ZERO,
                    1f, Map.of(saved.uuid, saved), List.of(), List.of(), BlueprintMeta.EMPTY).normalized();
            fullVehicle = ((io.github.sweetzonzi.machine_max.common.item.prop.BaseVehicleItem)
                    io.github.sweetzonzi.machine_max.common.registry.MMItems.getASSEMBLY_ITEM().get()).createVehicle(level, vehicleData);
            skeletonVehicle = ((io.github.sweetzonzi.machine_max.common.item.prop.BaseVehicleItem)
                    io.github.sweetzonzi.machine_max.common.registry.MMItems.getVEHICLE_BLUEPRINT().get()).createVehicle(level, vehicleData);
            full = fullVehicle.findPart(source.uuid);
            skeleton = skeletonVehicle.findPart(source.uuid);
            helper.assertTrue(full.uuid.equals(source.uuid) && skeleton.uuid.equals(source.uuid), "Both restoration modes preserve structural identity");
            helper.assertTrue(full.assemblingProgress == 0.6f && full.materialProgress == source.materialProgress, "Full state restores manufacturing progress");
            helper.assertTrue(full.textureName.equals(paint) && !full.renderWireframe, "Full state restores paint and wireframe");
            helper.assertTrue(Math.abs(fullVehicle.getMaxHp() - full.getVehicleDurabilityContribution()) < 0.01f, "Regular HP contribution is preserved");
            helper.assertTrue(skeleton.assemblingProgress == 0f && skeleton.materialProgress == 0 && skeleton.renderWireframe, "Blueprint is an unassembled skeleton");
            helper.assertTrue(skeleton.textureName.equals(defaultTexture), "Blueprint uses default paint");
            helper.assertTrue(full.subParts.keySet().equals(source.subParts.keySet()) && skeleton.subParts.keySet().equals(source.subParts.keySet()), "Both modes preserve SubPart structure");
            helper.assertTrue(countSticks(full) == countSticks(source) && countSticks(full) > 0, "Full state restores storage resources");
            helper.assertTrue(countSticks(skeleton) == 0, "Blueprint must not import saved storage resources");
            for (var entry : full.subParts.entrySet()) {
                helper.assertTrue(Math.abs(entry.getValue().getDurability() - source.subParts.get(entry.getKey()).getDurability()) < 0.01f, "Full state restores damage");
            }
            helper.assertTrue(before.equals(RegularPartData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow()), "Restoration must not mutate input DTO");
            helper.succeed();
        } finally {
            clearAndDestroy(source);
            if (fullVehicle != null) clearAndRemove(fullVehicle);
            if (skeletonVehicle != null) clearAndRemove(skeletonVehicle);
        }
    }

    private static SimpleContainer storage(Object subsystem) {
        if (subsystem instanceof ItemStorageSubsystem storage) return storage.container;
        return null;
    }
    private static int countSticks(AbstractPart<?> part) {
        int count = 0;
        for (var sub : part.getAllSubsystems()) {
            SimpleContainer storage = storage(sub);
            if (storage != null) for (int i = 0; i < storage.getContainerSize(); i++) {
                if (storage.getItem(i).is(Items.STICK)) count += storage.getItem(i).getCount();
            }
        }
        return count;
    }
    private static void clearAndDestroy(AbstractPart<?> part) {
        part.getAllSubsystems().forEach(sub -> { SimpleContainer storage = storage(sub); if (storage != null) storage.clearContent(); });
        part.destroy();
    }

    private static void clearAndRemove(VehicleCore vehicle) {
        vehicle.partMap.values().forEach(part -> part.getAllSubsystems().forEach(sub -> {
            SimpleContainer storage = storage(sub);
            if (storage != null) storage.clearContent();
        }));
        vehicle.onRemoveFromLevel();
    }

    @GameTest(template = "part_foundation", batch = "mmPartFoundation", timeoutTicks = 100)
    public static void externalFactoryAndVehicleBoundary(GameTestHelper helper) {
        var regular = PartDefinitions.regular(false).values().stream().findFirst().orElseThrow();
        var variant = regular.variants.values().iterator().next();
        String kindId = "machine_max_test:body";
        PartKinds.register(kindId, new PartKind<>(ProbeType.class, ProbeType.CODEC, ProbeData.class, ProbeData.CODEC,
                ProbePart::new, (type, data, level, mode, context) -> new ProbePart(type, data, level, mode, context),
                (type, context) -> {}));
        var definition = new ProbeType(Map.of("default", variant));
        definition.setRegistryKey(ResourceLocation.parse("machine_max_test:core"));
        Map<ResourceLocation, AbstractPartType> all = new LinkedHashMap<>(PartDefinitions.all(false));
        all.put(definition.getRegistryKey(), definition);
        PartDefinitions.validateAndPublish(false, all);
        ProbePart body = (ProbePart) PartFactory.create(definition, "default", helper.getLevel(), new PartCreationContext(false));
        ProbePart restored = null;
        RegularPart equipment = null;
        RegularPart root = PartFactory.createRegular(regular, regular.variants.keySet().iterator().next(), helper.getLevel());
        VehicleCore vehicle = new VehicleCore(helper.getLevel(), root);
        try {
            body.reserve = 73;
            var json = PartFactory.encodeData(false, com.mojang.serialization.JsonOps.INSTANCE, body.save()).getOrThrow();
            var saved = PartFactory.decodeData(false, com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow();
            restored = (ProbePart) PartFactory.restore(saved, helper.getLevel(), PartRestoreMode.FULL_STATE, new PartCreationContext(false));
            helper.assertTrue(restored.reserve == 73 && !restored.crowbarRemovable && !body.crowbarRemovable, "Extension factory restores dedicated state and construction permission");
            helper.assertTrue(!vehicle.acceptsPartType(definition), "Vehicle must reject external type");
            int size = vehicle.partMap.size();
            try {
                vehicle.addPart(body);
                throw new IllegalStateException("Vehicle accepted body");
            } catch (IllegalArgumentException expected) {
                helper.assertTrue(body.assembly == null && vehicle.partMap.size() == size, "Rejection must not mutate ownership or graph");
            }
            helper.assertTrue(body.animController != null && ((ProbeMolangContext) body.getMolangContext()).reserve() == 73, "Extension uses common animation and dedicated queries");
            var host = new ProbeAssembly(helper.getLevel());
            host.addPart(body);
            body.rootSubPart.refreshPartEntity();
            helper.assertTrue(body.rootSubPart.entity == null && host.findPart(body.uuid) == body, "Host assembly owns its proxy policy and lookup");
            equipment = (RegularPart) PartFactory.create(regular, regular.variants.keySet().iterator().next(), helper.getLevel(), new PartCreationContext(false));
            host.addPart(equipment);
            equipment.rootSubPart.refreshPartEntity();
            helper.assertTrue(equipment.rootSubPart.entity == null && host.parts.size() == 2, "Host proxy policy covers Regular equipment as well as body parts");
            var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
            var eyesight = player.getData(io.github.sweetzonzi.machine_max.common.registry.MMAttachments.getENTITY_EYESIGHT());
            eyesight.getSortedTargetBodyCache().add(equipment.rootSubPart.body);
            ItemStack crowbar = new ItemStack(io.github.sweetzonzi.machine_max.common.registry.MMItems.getCROWBAR_ITEM().get());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, crowbar);
            crowbar.getItem().use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(host.findPart(equipment.uuid) == equipment && crowbar.getDamageValue() == 0, "Crowbar permission blocks removal and tool consumption");
            eyesight.getSortedTargetBodyCache().clear();
            eyesight.getSortedTargetBodyCache().add(body.rootSubPart.body);
            ItemStack torch = new ItemStack(io.github.sweetzonzi.machine_max.common.registry.MMItems.getWELDING_TORCH_ITEM().get());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, torch);
            torch.getItem().onUseTick(helper.getLevel(), player, torch, 20);
            helper.assertTrue(torch.getDamageValue() == 0 && body.assembly == host, "Material tool rejects body before side effects");
            eyesight.getSortedTargetBodyCache().clear();
            helper.succeed();
        } finally {
            clearAndDestroy(body);
            if (restored != null) clearAndDestroy(restored);
            if (equipment != null) clearAndDestroy(equipment);
            vehicle.onRemoveFromLevel();
        }
    }

    private static final class ProbeType extends AbstractPartType {
        static final Codec<ProbeType> CODEC = VARIANT_MAP_CODEC.fieldOf("variants").codec().xmap(ProbeType::new, type -> type.variants);
        ProbeType(Map<String, VariantAttr> variants) { super(ResourceLocation.withDefaultNamespace("missingno"), true, 1, variants); }
        @Override public String kindId() { return "machine_max_test:body"; }
    }
    private static final class ProbeData extends AbstractPartData {
        static final Codec<ProbeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("registry_key").forGetter(data -> data.registryKey),
                Codec.STRING.fieldOf("name").forGetter(data -> data.name),
                Codec.STRING.fieldOf("uuid").forGetter(data -> data.uuid),
                Codec.STRING.fieldOf("variant").forGetter(data -> data.variant),
                Codec.FLOAT.fieldOf("shared_durability_ratio").forGetter(data -> data.sharedDurabilityRatio),
                Codec.STRING.fieldOf("texture_name").forGetter(data -> data.textureName),
                SubPartData.MAP_CODEC.fieldOf("sub_parts").forGetter(data -> data.subParts),
                Codec.INT.fieldOf("reserve").forGetter(data -> data.reserve)
        ).apply(instance, ProbeData::new));
        final int reserve;
        ProbeData(ResourceLocation id, String name, String uuid, String variant, float ratio, String texture, Map<String, SubPartData> subParts, int reserve) {
            super(id, name, uuid, variant, ratio, texture, subParts);
            this.reserve = reserve;
        }
        ProbeData(ProbePart part) { super(part); reserve = part.reserve; }
    }
    private static final class ProbePart extends AbstractPart<ProbeType> {
        int reserve = 17;
        ProbePart(ProbeType type, String variant, Level level, PartCreationContext context) {
            super(type, variant, level, UUID.randomUUID(), "probe", context);
            bindMolangContext(new ProbeMolangContext(this));
            initializeStructure();
        }
        ProbePart(ProbeType type, ProbeData data, Level level, PartRestoreMode mode, PartCreationContext context) {
            super(type, data.variant, level, UUID.fromString(data.uuid), data.name, context);
            reserve = mode == PartRestoreMode.FULL_STATE ? data.reserve : 17;
            bindMolangContext(new ProbeMolangContext(this));
            initializeStructure();
            try { restoreState(data, mode); }
            catch (RuntimeException | Error failure) { abortConstruction(); throw failure; }
        }
        @Override public ProbeData save() { return new ProbeData(this); }
        @Override public boolean permitsSubsystemWork() { return true; }
        @Override public float durabilityLimitRatio() { return 1f; }
        @Override public float repairLimitRatio() { return 1f; }
        @Override public boolean permitsRepair() { return false; }
        @Override public float assemblyDamage(float amount, float remaining, boolean destroyed) { return amount; }
    }

    public static final class ProbeMolangContext extends io.github.sweetzonzi.machine_max.common.mech.molang.MechMolangContext {
        private final ProbePart part;
        ProbeMolangContext(ProbePart part) { super(part); this.part = part; }
        @cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding("body.reserve")
        public double reserve() { return part.reserve; }
    }

    private static final class ProbeAssembly implements IPartAssembly {
        final UUID id = UUID.randomUUID();
        final Level level;
        final Map<UUID, AbstractPart<?>> parts = new HashMap<>();
        final io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController controller;
        ProbeAssembly(Level level) { this.level = level; controller = new io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController(level); }
        @Override public UUID getAssemblyId() { return id; }
        @Override public Level getLevel() { return level; }
        @Override public boolean isInLevel() { return false; }
        @Override public String getAssemblyName() { return "probe"; }
        @Override public void setAssemblyName(String name) {}
        @Override public float getTotalMass() { return 0; }
        @Override public boolean acceptsPartType(AbstractPartType type) { return true; }
        @Override public void addPart(AbstractPart<?> part) { parts.put(part.uuid, part); part.assembly = this; }
        @Override public void removePart(AbstractPart<?> part) { parts.remove(part.uuid); part.assembly = null; }
        @Override public AbstractPart<?> findPart(UUID id) { return parts.get(id); }
        @Override public void connect(io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector a,
                                      io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector b, AbstractPart<?> newPart) { throw new UnsupportedOperationException(); }
        @Override public void disconnect(io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector connector) { throw new UnsupportedOperationException(); }
        @Override public io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController getSubsystemController() { return controller; }
        @Override public void onPartDamage(AbstractPart<?> part, float damage) {}
        @Override public void activatePhysics() {}
    }
}
