package io.github.sweetzonzi.machine_max.common.vehicle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import lombok.Getter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.common.Tags;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据驱动的伤害修改器
 */
public final class DamageModifier {

    public final List<ModifierEntry> modifiers;

    public static final List<ModifierEntry> DEFAULT_PEN_DEPTH_MODIFIERS = List.of(
            new ModifierEntry(Operation.MULTIPLY, 0.5f, new Condition.Not(
                    new Condition.DamageTagCondition(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "has_pen_depth"))))
    );

    public static final List<ModifierEntry> DEFAULT_DAMAGE_MODIFIERS = List.of(
            new ModifierEntry(Operation.ADD, -5f, new Condition.DamageTypeCondition(DamageTypes.FLY_INTO_WALL.location())),
            new ModifierEntry(Operation.MULTIPLY, 0.05f, new Condition.DamageTypeCondition(DamageTypes.SWEET_BERRY_BUSH.location())),
            new ModifierEntry(Operation.MULTIPLY, 0.05f, new Condition.EntityTypeCondition(ResourceLocation.withDefaultNamespace("slime"))),
            new ModifierEntry(Operation.MULTIPLY, 0.1f, new Condition.EntityTypeCondition(ResourceLocation.withDefaultNamespace("magma_cube")))
    );

    public DamageModifier(List<ModifierEntry> modifiers) {
        this.modifiers = modifiers;
    }

    /**
     * 顺序应用 modifier
     */
    public float apply(DamageSource source, float amount) {
        float result = amount;
        for (ModifierEntry entry : modifiers) {
            if (entry.condition.matches(source)) {
                result = entry.operation.apply(result, entry.value);
            }
            if (result <= 0.0f) return 0.0f; // 已经为零或负数，不再继续计算，直接返回
        }
        return result;
    }

    /**
     * 数据驱动的伤害修改器
     *
     * @param operation 操作符
     * @param value     值
     * @param condition 条件
     */
    public record ModifierEntry(Operation operation, float value, Condition condition) {
    }

    /**
     * 操作符
     */
    public enum Operation {
        ADD, // 加法
        MULTIPLY; // 乘法

        float apply(float base, float value) {
            return this == ADD ? base + value : base * value;
        }

        static Operation fromString(String s) {
            return switch (s.toLowerCase(Locale.ROOT)) {
                case "add" -> ADD;
                case "multiply" -> MULTIPLY;
                default -> throw new IllegalArgumentException("Unknown operation: " + s);
            };
        }

        String toStringValue() {
            return this == ADD ? "add" : "multiply";
        }
    }

    /**
     * 条件
     */
    public sealed interface Condition
            permits Condition.Always,
            Condition.DamageTypeCondition,
            Condition.DamageTagCondition,
            Condition.EntityTypeCondition,
            Condition.EntityTagCondition,
            Condition.And,
            Condition.Or,
            Condition.Not {

        boolean matches(DamageSource source);

        Type type();

        /* -------------------------- concrete types -------------------------- */

        record Always() implements Condition {
            @Override
            public boolean matches(DamageSource source) {
                return true;
            }

            @Override
            public Type type() {
                return Type.ALWAYS;
            }
        }

        record DamageTypeCondition(ResourceLocation id) implements Condition {
            @Override
            public boolean matches(DamageSource source) {
                return source.typeHolder()
                        .is(ResourceKey.create(Registries.DAMAGE_TYPE, id));
            }

            @Override
            public Type type() {
                return Type.DAMAGE_TYPE;
            }
        }

        record DamageTagCondition(ResourceLocation tag) implements Condition {
            @Override
            public boolean matches(DamageSource source) {
                return source.is(TagKey.create(Registries.DAMAGE_TYPE, tag));
            }

            @Override
            public Type type() {
                return Type.DAMAGE_TAG;
            }
        }

        record EntityTypeCondition(ResourceLocation id) implements Condition {
            @Override
            public boolean matches(DamageSource source) {
                var e = source.getEntity();
                return e != null &&
                        BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(id);
            }

            @Override
            public Type type() {
                return Type.ENTITY_TYPE;
            }
        }

        record EntityTagCondition(ResourceLocation tag) implements Condition {
            @Override
            public boolean matches(DamageSource source) {
                var e = source.getEntity();
                return e != null &&
                        e.getType().is(TagKey.create(Registries.ENTITY_TYPE, tag));
            }

            @Override
            public Type type() {
                return Type.ENTITY_TAG;
            }
        }

        record And(List<Condition> conditions) implements Condition {
            @Override
            public boolean matches(DamageSource source) {
                for (Condition c : conditions) {
                    if (!c.matches(source)) return false;
                }
                return true;
            }

            @Override
            public Type type() {
                return Type.AND;
            }
        }

        record Or(List<Condition> conditions) implements Condition {
            @Override
            public boolean matches(DamageSource source) {
                for (Condition c : conditions) {
                    if (c.matches(source)) return true;
                }
                return false;
            }

            @Override
            public Type type() {
                return Type.OR;
            }
        }

        record Not(Condition condition) implements Condition {
            @Override
            public boolean matches(DamageSource source) {
                return !condition.matches(source);
            }

            @Override
            public Type type() {
                return Type.NOT;
            }
        }

        /* ================================================================== */
        /* Type + Codec dispatch                                              */
        /* ================================================================== */

        @Getter
        enum Type {
            ALWAYS("always"),
            DAMAGE_TYPE("damage_type"),
            DAMAGE_TAG("damage_tag"),
            ENTITY_TYPE("entity_type"),
            ENTITY_TAG("entity_tag"),
            AND("and"),
            OR("or"),
            NOT("not");

            private final String typeId;

            Type(String typeId) {
                this.typeId = typeId;
            }

            static Type fromString(String s) {
                for (Type t : values()) {
                    if (t.typeId.equals(s)) return t;
                }
                throw new IllegalArgumentException("Unknown condition type: " + s);
            }
        }

        /* ------------------------- Codec registry -------------------------- */

        private static Codec<Condition> createCodec() {
            Map<Type, MapCodec<? extends Condition>> codecs = Map.of(
                    Type.ALWAYS, MapCodec.unit(new Always()),

                    Type.DAMAGE_TYPE, ResourceLocation.CODEC
                            .fieldOf("id")
                            .xmap(DamageTypeCondition::new, DamageTypeCondition::id),

                    Type.DAMAGE_TAG, ResourceLocation.CODEC
                            .fieldOf("tag")
                            .xmap(DamageTagCondition::new, DamageTagCondition::tag),

                    Type.ENTITY_TYPE, ResourceLocation.CODEC
                            .fieldOf("id")
                            .xmap(EntityTypeCondition::new, EntityTypeCondition::id),

                    Type.ENTITY_TAG, ResourceLocation.CODEC
                            .fieldOf("tag")
                            .xmap(EntityTagCondition::new, EntityTagCondition::tag),

                    Type.AND, Codec.list(Condition.CODEC)
                            .fieldOf("conditions")
                            .xmap(And::new, And::conditions),

                    Type.OR, Codec.list(Condition.CODEC)
                            .fieldOf("conditions")
                            .xmap(Or::new, Or::conditions),

                    Type.NOT, Condition.CODEC
                            .fieldOf("condition")
                            .xmap(Not::new, Not::condition)
            );

            return TYPE_CODEC.dispatch(
                    Condition::type,
                    codecs::get
            );
        }

        Codec<Type> TYPE_CODEC =
                Codec.STRING.xmap(Type::fromString, Type::getTypeId);

        Codec<Condition> CODEC = Codec.lazyInitialized(Condition::createCodec);
    }

    /* ====================================================================== */
    /* Codec                                                                  */
    /* ====================================================================== */

    private static final Codec<Operation> OPERATION_CODEC =
            Codec.STRING.xmap(Operation::fromString, Operation::toStringValue);

    public static final Codec<ModifierEntry> ENTRY_CODEC =
            RecordCodecBuilder.create(inst -> inst.group(
                    OPERATION_CODEC.fieldOf("operation").forGetter(e -> e.operation),
                    Codec.FLOAT.fieldOf("value").forGetter(e -> e.value),
                    Condition.CODEC.fieldOf("condition").forGetter(e -> e.condition)
            ).apply(inst, ModifierEntry::new));
}
