package io.github.sweetzonzi.machinemax.client.input;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface IKeyCategory {
    String getCategory();
}
