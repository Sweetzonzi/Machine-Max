package io.github.sweetzonzi.machine_max.common.mech.part;

/** Instance permissions supplied by the owning assembly for both creation and restoration. */
public record PartCreationContext(boolean crowbarRemovable) {
    public static final PartCreationContext REGULAR = new PartCreationContext(true);
}
