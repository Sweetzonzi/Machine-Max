package io.github.sweetzonzi.machine_max.common.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import lombok.Getter;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

@Getter
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class ResearchAttachment {

    public static final Codec<ResearchAttachment> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("research_point").forGetter(ResearchAttachment::getResearchPoint)
            ).apply(instance, ResearchAttachment::new)
    );

    private int researchPoint;

    public ResearchAttachment(int researchPoint) {
        this.researchPoint = researchPoint;
    }

    public void addRP(Player player, int amount) {
        researchPoint += Math.max(0, amount);
        this.onRPChange(player);
    }

    public boolean hasEnoughRP(int amount) {
        return researchPoint >= amount;
    }

    public void consumeRP(Player player, int amount) {
        this.researchPoint -= Math.min(amount, researchPoint);
        this.onRPChange(player);
    }

    private void onRPChange(Player player) {
        // TODO: 发包同步研发点数据

    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof Player player) {
            if (!player.hasData(MMAttachments.getRESEARCH_AND_BLUEPRINT())) {
                player.setData(MMAttachments.getRESEARCH_AND_BLUEPRINT(), new ResearchAttachment(0));
            }
        }
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (!event.getEntity().level().isClientSide()) {
            Player player = event.getEntity();
            if (!player.hasData(MMAttachments.getRESEARCH_AND_BLUEPRINT())) {
                player.setData(MMAttachments.getRESEARCH_AND_BLUEPRINT(), new ResearchAttachment(0));
            }
            if (player.hasData(MMAttachments.getRESEARCH_AND_BLUEPRINT())) {
                var research = player.getData(MMAttachments.getRESEARCH_AND_BLUEPRINT());
                research.addRP(player, event.getAmount());
            }
        }
    }


}
