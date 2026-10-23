package io.github.sweetzonzi.machine_max.common.mech.part;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.Level;

/** A mod-registered definition codec, saved-state codec and instance factory. */
public record PartKind<T extends AbstractPartType, D extends AbstractPartData>(
        Class<T> definitionClass, Codec<T> definitionCodec,
        Class<D> dataClass, Codec<D> dataCodec,
        Creator<T> creator, Restorer<T, D> restorer, Validator<T> validator) {
    @FunctionalInterface public interface Creator<T> {
        AbstractPart<?> create(T type, String variant, Level level, PartCreationContext context);
    }
    @FunctionalInterface public interface Restorer<T, D> {
        AbstractPart<?> restore(T type, D data, Level level, PartRestoreMode mode, PartCreationContext context);
    }
    @FunctionalInterface public interface Validator<T> {
        void validate(T type, PartValidationContext context);
    }
    void validate(AbstractPartType type, PartValidationContext context) {
        validator.validate(definitionClass.cast(type), context);
    }
    AbstractPart<?> create(AbstractPartType type, String variant, Level level, PartCreationContext context) {
        return creator.create(definitionClass.cast(type), variant, level, context);
    }
    AbstractPart<?> restore(AbstractPartType type, AbstractPartData data, Level level,
                            PartRestoreMode mode, PartCreationContext context) {
        return restorer.restore(definitionClass.cast(type), dataClass.cast(data), level, mode, context);
    }
}
