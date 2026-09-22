package io.github.sweetzonzi.machine_max.common.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.common.registry.MMResources;
import lombok.Getter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 零件制造配方：产物为某个零件类型的制造配方。
 *
 * <p>相比基类 {@link FabricatingRecipe}，本类额外承担两件事：</p>
 * <ul>
 *   <li>描述该零件能否用原材料手动装配（{@link #manual}），并据此派生出组装与拆卸两条材料清单；</li>
 *   <li>通过 {@link #partType} 把「配方」与「零件类型」绑起来，装配侧据此按零件 id 反查配方。</li>
 * </ul>
 *
 * <p><b>产物构造：</b>JSON 只声明 {@code result.count}，产物物品、数据组件与数量上限都在索引构建期由
 * {@link #setResolvedProduct(ResourceLocation, int)} 注入；注入前实例持占位产物（物品为
 * {@code machine_max:part}、数量正确、{@code machine_max:part_type} 为空）。</p>
 *
 * <p><b>目录与命名约定：</b>配方文件必须位于内容包的 {@code recipe/part_fabricating/} 下，文件名
 * （只取最后一段，忽略子目录）等于零件 id 的 path，namespace 与零件一致。</p>
 */
@Getter
public class PartFabricatingRecipe extends FabricatingRecipe {
    /** 配方 id 中标记「零件配方」的目录前缀 */
    public static final String DIRECTORY_PREFIX = "part_fabricating/";

    /** {@code result} 的 JSON 形态：只声明数量 */
    private static final Codec<Integer> RESULT_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("count", 1).forGetter(Integer::intValue)
            ).apply(instance, Integer::valueOf)
    );

    public static final MapCodec<PartFabricatingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    IngredientCountPair.CODEC.listOf().fieldOf("ingredients").forGetter(PartFabricatingRecipe::getIngredientPairs),
                    RESULT_CODEC.optionalFieldOf("result", 1).forGetter(PartFabricatingRecipe::getResultCount),
                    Codec.INT.optionalFieldOf("time", 100).forGetter(PartFabricatingRecipe::getProcessingTime),
                    Codec.STRING.optionalFieldOf("description", "").forGetter(PartFabricatingRecipe::getTooltip),
                    Codec.BOOL.optionalFieldOf("manual", true).forGetter(PartFabricatingRecipe::isManual)
            ).apply(instance, PartFabricatingRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PartFabricatingRecipe> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PartFabricatingRecipe decode(@NotNull RegistryFriendlyByteBuf buffer) {
            int ingredientCount = buffer.readVarInt();
            List<IngredientCountPair> ingredients = new ArrayList<>(ingredientCount);
            for (int i = 0; i < ingredientCount; i++) {
                Ingredient ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                int count = buffer.readVarInt();
                ingredients.add(new IngredientCountPair(ingredient, count));
            }
            int resultCount = buffer.readVarInt();
            int processingTime = buffer.readVarInt();
            String descriptionId = buffer.readUtf();
            boolean manual = buffer.readBoolean();
            return new PartFabricatingRecipe(ingredients, resultCount, processingTime, descriptionId, manual);
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, PartFabricatingRecipe recipe) {
            List<IngredientCountPair> ingredients = recipe.getIngredientPairs();
            buffer.writeVarInt(ingredients.size());
            for (IngredientCountPair pair : ingredients) {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, pair.ingredient());
                buffer.writeVarInt(pair.count());
            }
            buffer.writeVarInt(recipe.getResultCount());
            buffer.writeVarInt(recipe.getProcessingTime());
            buffer.writeUtf(recipe.getTooltip());
            buffer.writeBoolean(recipe.isManual());
        }
    };

    /** 是否允许用原材料手动装配；为假时以本配方的产物整件作为唯一装配材料 */
    private final boolean manual;
    /** 制造机单次产出数量，同时参与 manual 模式下的材料折算 */
    private final int resultCount;
    /** 产物对应的零件 id；由配方 id 在索引构建期注入 */
    @Nullable
    private ResourceLocation partType;

    private final List<IngredientCountPair> manualAssembleIngredientPairs = new ArrayList<>();
    private final List<IngredientCountPair> manualDisassembleIngredientPairs = new ArrayList<>();
    private final List<Ingredient> manualAssembleIngredientList = new ArrayList<>();
    private final List<Ingredient> manualDisassembleIngredientList = new ArrayList<>();

    /** 是否已完成产物注入；同一实例被重复建索引时只注入一次，避免非原材料装配的清单被重复追加 */
    private boolean productResolved = false;

    public PartFabricatingRecipe(
            List<IngredientCountPair> ingredientPairs,
            int resultCount,
            int processingTime,
            String tooltip,
            boolean manual) {
        super(ingredientPairs, createPlaceholderResult(resultCount), processingTime, tooltip);
        this.manual = manual;
        this.resultCount = resultCount;
        if (manual) deriveManualListsFromIngredients(ingredientPairs, resultCount);
    }

    /** 构造期占位产物：物品为 {@code machine_max:part}、数量取 {@code resultCount}、不带数据组件 */
    private static ItemStack createPlaceholderResult(int resultCount) {
        return new ItemStack(MMItems.getPART_ITEM(), Math.max(resultCount, 1));
    }

    /**
     * 由原料表派生组装清单（每种原料重复 {@code ceil(数量 / resultCount)} 次）与
     * 拆卸清单（每种原料重复 {@code floor(数量 / resultCount)} 次）。
     */
    private void deriveManualListsFromIngredients(List<IngredientCountPair> ingredientPairs, int resultCount) {
        for (IngredientCountPair pair : ingredientPairs) {
            int assembleCount = Math.ceilDiv(pair.count(), resultCount);
            int disassembleCount = pair.count() / resultCount;
            manualAssembleIngredientPairs.add(new IngredientCountPair(pair.ingredient(), assembleCount));
            manualDisassembleIngredientPairs.add(new IngredientCountPair(pair.ingredient(), disassembleCount));
            for (int i = 0; i < assembleCount; i++) {
                manualAssembleIngredientList.add(pair.ingredient());
            }
            for (int i = 0; i < disassembleCount; i++) {
                manualDisassembleIngredientList.add(pair.ingredient());
            }
        }
    }

    /**
     * 索引构建期注入产物：写入零件 id、补齐产物数据组件与堆叠上限。
     *
     * <p>仅在服务端 {@code DataPackReloader.apply} 或客户端 {@code RecipesUpdatedEvent} 的索引重建中调用一次；
     * 调用完成后本实例视为只读。</p>
     *
     * @param partType     本配方产出的零件 id
     * @param maxStackSize 该零件类型的最大堆叠数量，写入产物的 {@code MAX_STACK_SIZE}
     */
    public void setResolvedProduct(ResourceLocation partType, int maxStackSize) {
        if (productResolved) return;
        productResolved = true;
        this.partType = partType;
        ItemStack result = getResult();
        result.set(MMDataComponents.getPART_TYPE(), partType);
        result.set(DataComponents.MAX_STACK_SIZE, maxStackSize);
        if (!manual) {
            // 非原材料装配：唯一材料就是本配方的产物整件，清单长度恒为 1
            ItemStack single = result.copyWithCount(1);
            Ingredient productIngredient = DataComponentIngredient.of(false, single);
            manualAssembleIngredientPairs.add(new IngredientCountPair(productIngredient, 1));
            manualDisassembleIngredientPairs.add(new IngredientCountPair(productIngredient, 1));
            manualAssembleIngredientList.add(productIngredient);
            manualDisassembleIngredientList.add(productIngredient);
        }
    }

    /**
     * 由配方 id 推导零件 id：取 path 的 {@code part_fabricating/} 之后、最后一段斜杠之后的部分，
     * namespace 保持不变。
     *
     * @param recipeId 零件配方 id，形如 {@code <ns>:part_fabricating/[子目录/]<零件名>}
     * @return 零件 id
     */
    public static ResourceLocation partTypeFromRecipeId(ResourceLocation recipeId) {
        String path = recipeId.getPath();
        if (path.startsWith(DIRECTORY_PREFIX)) path = path.substring(DIRECTORY_PREFIX.length());
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) path = path.substring(lastSlash + 1);
        return ResourceLocation.fromNamespaceAndPath(recipeId.getNamespace(), path);
    }

    /**
     * 由零件 id 推导零件配方 id（与 {@link #partTypeFromRecipeId(ResourceLocation)} 互逆）。
     *
     * @param partType 零件 id
     * @return 该零件的配方 id
     */
    public static ResourceLocation recipeIdFromPartType(ResourceLocation partType) {
        return ResourceLocation.fromNamespaceAndPath(
                partType.getNamespace(), DIRECTORY_PREFIX + partType.getPath());
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return MMResources.getPART_FABRICATION_RECIPE_SERIALIZER().get();
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return MMResources.getPART_FABRICATION_RECIPE_TYPE().get();
    }

    public static class Serializer implements RecipeSerializer<PartFabricatingRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public @NotNull MapCodec<PartFabricatingRecipe> codec() {
            return PartFabricatingRecipe.CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, PartFabricatingRecipe> streamCodec() {
            return PartFabricatingRecipe.STREAM_CODEC;
        }
    }
}
