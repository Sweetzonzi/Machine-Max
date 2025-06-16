package io.github.sweetzonzi.machinemax.mixin;

import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machinemax.common.item.prop.MMPartItem;
import io.github.sweetzonzi.machinemax.common.registry.MMDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RepairItemRecipe.class)
abstract public class RepairItemRecipeMixin {

    @Shadow
    protected abstract Pair<ItemStack, ItemStack> getItemsToCombine(CraftingInput input);

    @Inject(method = "canCombine", at = @At("RETURN"), cancellable = true)
    private static void canCombine(ItemStack stack1, ItemStack stack2, CallbackInfoReturnable<Boolean> cir) {
        if (stack1.getItem() instanceof MMPartItem && stack2.getItem() instanceof MMPartItem) {
            if (!stack1.has(MMDataComponents.getPART_TYPE()) || !stack2.has(MMDataComponents.getPART_TYPE())) {
                cir.setReturnValue(false);
            } else {
                ResourceLocation partType1 = stack1.get(MMDataComponents.getPART_TYPE());
                ResourceLocation partType2 = stack2.get(MMDataComponents.getPART_TYPE());
                //只有对应的部件类型相同的物品才能够通过合成修复
                if (partType1 != null && partType1.equals(partType2)) cir.setReturnValue(true);
                else cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "assemble*", at = @At("HEAD"), cancellable = true)
    private void modifyAssembledItem(CraftingInput input, HolderLookup.Provider registries, CallbackInfoReturnable<ItemStack> cir) {
        Pair<ItemStack, ItemStack> pair = getItemsToCombine(input);
        if (pair != null) {
            ItemStack itemStack = pair.getFirst();
            ItemStack itemStack1 = pair.getSecond();
            if (itemStack.getItem() instanceof MMPartItem && itemStack1.getItem() instanceof MMPartItem) {
                ResourceLocation partType1 = itemStack.get(MMDataComponents.getPART_TYPE());
                ResourceLocation partType2 = itemStack1.get(MMDataComponents.getPART_TYPE());
                if (partType1 != null && partType1.equals(partType2)) {
                    int i = Math.max(itemStack.getMaxDamage(), itemStack1.getMaxDamage());
                    int j = itemStack.getMaxDamage() - itemStack1.getDamageValue();
                    int k = itemStack1.getMaxDamage() - itemStack1.getDamageValue();
                    int l = j + k + i * 5 / 100;
                    ItemStack itemStack2 = new ItemStack(itemStack.getItem());
                    //需要手动赋予partType以避免获得未指定部件类型的部件物品
                    itemStack2.set(MMDataComponents.getPART_TYPE(), partType1);
                    itemStack2.set(DataComponents.MAX_DAMAGE, i);
                    itemStack2.setDamageValue(Math.max(i - l, 0));
                    cir.setReturnValue(itemStack2);
                } else cir.setReturnValue(ItemStack.EMPTY);
            }
        }
    }
}
