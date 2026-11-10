package io.github.sweetzonzi.machine_max.mixin;

import io.github.sweetzonzi.machine_max.common.registry.MMDamageTypes;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给本项目的伤害类型补上"带施加者"的死亡消息。
 *
 * <p>原版 {@link DamageSource#getLocalizedDeathMessage} 只在伤害源<b>没有任何实体</b>时才用
 * {@code death.attack.<msgId>.player}：那一支取的是受害者记忆里的击杀记功者，即"伤害源没写谁打的、
 * 只好用受害者最后挨打的对象"的兜底。伤害源带 causing entity 时它一律用基础键，并把起因实体作为
 * 参数传进去——所以基础键只写 {@code %1$s} 时，攻击者名字会被静默丢弃。归属落地后我们的伤害几乎
 * 全部落在"有实体"这一支，那批 {@code .player} 键因此一条也走不到。</p>
 *
 * <p>这里把两支合流：本项目的三类伤害在带 causing entity 时直接取 {@code .player} 键，其余情形
 * 一律交回原版。三条纪律：</p>
 * <ul>
 *   <li><b>不新增伤害类型。</b>原版用 {@code minecraft:explosion} 与 {@code minecraft:player_explosion}
 *       分型，是因为它要两套措辞：没人可写时"炸死"、有人可写时"被 X 炸死"。我们的选择是"总能写出
 *       施加者"，同一份伤害类型就够，不把 {@code machine_max:projectile} 拆成一族。</li>
 *   <li><b>不为了文案造实体。</b>项目里没有"只为死亡消息而存在"的实体；无归属的伤害保持基础文案，
 *       不做技术性载体。</li>
 *   <li><b>不做占位符兜底。</b>接管只发生在归因存在时，因此基础键继续按 1 参使用，
 *       不会漏出字面的 {@code %2$s}。</li>
 * </ul>
 *
 * <p>命中点只有 {@code getLocalizedDeathMessage} 一处：{@code CombatTracker#getDeathMessage()}
 * 对 {@code DeathMessageType.DEFAULT}（本项目三个类型都是 default）直接返回本方法的结果，
 * 玩家死亡屏、聊天广播与生物死亡因此都走同一分支。键取自 {@link MMLanguageRegistry}，
 * 不在这里拼字符串。</p>
 */
@Mixin(DamageSource.class)
public abstract class DamageSourceMixin {

    @Inject(method = "getLocalizedDeathMessage", at = @At("HEAD"), cancellable = true)
    private void machine_max$attributedDeathMessage(LivingEntity victim, CallbackInfoReturnable<Component> cir) {
        DamageSource source = (DamageSource) (Object) this;
        Entity attacker = source.getEntity();
        if (attacker == null) return;
        String key = machine_max$attributedKey(source);
        if (key == null) return;
        cir.setReturnValue(Component.translatable(key, victim.getDisplayName(), attacker.getDisplayName()));
    }

    /**
     * 该伤害类型是否有"带施加者"的文案。
     *
     * @param source 本次伤害源
     * @return {@code death.attack.<msgId>.player} 的键；不归本项目管时返回 {@code null}
     */
    @Unique
    @Nullable
    private static String machine_max$attributedKey(DamageSource source) {
        if (source.is(MMDamageTypes.PROJECTILE)) return MMLanguageRegistry.DEATH_ATTACK_PROJECTILE_PLAYER.tag;
        if (source.is(MMDamageTypes.BLAST)) return MMLanguageRegistry.DEATH_ATTACK_BLAST_PLAYER.tag;
        if (source.is(MMDamageTypes.COOKOFF)) return MMLanguageRegistry.DEATH_ATTACK_COOKOFF_PLAYER.tag;
        return null;
    }
}
