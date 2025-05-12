package io.github.tt432.machinemax.common.item;

import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.VehicleManager;
import io.github.tt432.machinemax.common.vehicle.data.VehicleData;
import io.github.tt432.machinemax.external.DynamicPack;
import io.github.tt432.machinemax.external.MMDynamicRes;
import io.github.tt432.machinemax.external.htlike.HtNode;
import io.github.tt432.machinemax.external.htlike.HtmlLikeParser;
import io.github.tt432.machinemax.external.htlike.TagHtNode;
import io.github.tt432.machinemax.external.htlike.TextHtNode;
import io.github.tt432.machinemax.external.style.StyleProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

public class MMJavaItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static List<DeferredHolder<Item, Item>> BLUEPRINT_EGGS = new ArrayList<>();
    public static DeferredHolder<Item, Item> getBluePrintSpawner(ResourceLocation location, VehicleData vehicleData) {
//        LOGGER.info("BluePrint SpawnEgg:  "+location+"  has been register.");
        return ITEMS.register(
                location.getPath(),
                () -> new Item(new Item.Properties()) {
                    @Override
                    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
                        if(!level.isClientSide()){
                            VehicleCore vehicle = new VehicleCore(level, vehicleData);
                            vehicle.setUuid(UUID.randomUUID());
                            vehicle.setPos(player.position().add(0, 1, 0));
                            VehicleManager.addVehicle(vehicle);
                        }
                        return super.use(level, player, usedHand);
                    }


                    private static List<TextHtNode> loadTree(HtNode htNode, List<TextHtNode> cache) {
                        if (htNode.isText()) {
                            TextHtNode textNode = (TextHtNode) htNode;
                            cache.add(textNode);
                        } else {
                            TagHtNode tagNode = (TagHtNode) htNode;
                            for (HtNode child : tagNode.getChildren()) {
                                loadTree(child, cache);
                            }
                        }
                        return cache;
                    }

                    @Override
                    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                        //物品栏鼠标自定义信息 （正在筹备）
//                        vehicleData.authors
//                        tooltipComponents.add(Component.translatable("tooltip.%s.%s.details".formatted(MOD_ID, MMDynamicRes.getRealName(location.getPath()).replace("/", ".")))); // 支持本地化
                        String tip = vehicleData.tooltip;
                        if (MMDynamicRes.EXTERNAL_RESOURCE.get(ResourceLocation.parse(tip)) instanceof DynamicPack dynamicPack) {
                            tip = dynamicPack.getContent();
                        }

                        String[] regexList = {"\r\n", "\n"}; //扫描不同类型系统的回车符
                        boolean contains = false;
                        for (String regex : regexList) {
                            contains = tip.contains(regex);
                            if (contains) { //扫到了就替换成mc形式的回车，并且退出匹配
                                for (String span : tip.split(regex)) {
                                    MutableComponent component = Component.empty();
                                    try {
                                        List<TextHtNode> nodeQueue = loadTree(HtmlLikeParser.parse(span), new ArrayList<>());
                                        for (TextHtNode text : nodeQueue) {
                                            MutableComponent newComp = Component.translatable(text.getText());
                                            if (text.getEnclosingTags() instanceof List<String> list) {
                                                for (String tag : list) {
                                                    newComp = StyleProvider.styleFactory(tag, newComp);
                                                }
                                            }
                                            component = component.append(newComp);
                                        }
                                    } catch (Exception e) {
                                        System.err.println("富文本解析失败: " + e.getMessage());
                                    }

                                    tooltipComponents.add(component);
                                }
                                break;
                            }
                        }
                        if (! contains) { //没有任何匹配，全部作为可翻译标题
                            tooltipComponents.add(Component.translatable(tip));
                        }


                    }
                }
        );
    }

    public static void register(IEventBus modEventBus) {
        MMDynamicRes.BLUEPRINTS.forEach((location, vehicleData) -> {
            BLUEPRINT_EGGS.add(getBluePrintSpawner(location,vehicleData));
        });
        ITEMS.register(modEventBus);
//        CREATIVE_MODE_TABS.register(modEventBus);
    }

}
