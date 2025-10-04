//package io.github.sweetzonzi.machinemax.common.vehicle.crafting;

//public class FabricatingRecipeSerializer {

//    public static final MapCodec<FabricatingRecipe> STREAM_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
//            NonNullList.codecOf(Ingredient.STREAM_CODEC).fieldOf("materials").forGetter(FabricatingRecipe::getIngredients),
//            ResourceLocation.STREAM_CODEC.fieldOf("result").forGetter(FabricatingRecipe::getResult)
//    ).apply(instance, FabricatingRecipe::new));
//
//    public static final StreamCodec<FriendlyByteBuf, FabricatingRecipe> STREAM_CODEC = new StreamCodec<>() {
//        @Override
//        public @NotNull FabricatingRecipe decode(FriendlyByteBuf buffer) {
//            return buffer.readJsonWithCodec(STREAM_CODEC);
//        }
//
//        @Override
//        public void encode(FriendlyByteBuf buffer, @NotNull FabricatingRecipe value) {
//            buffer.writeJsonWithCodec(STREAM_CODEC, value);
//        }
//    };

//    @Override
//    public MapCodec<FabricatingRecipe> codec() {
//        return STREAM_CODEC;
//    }
//
//    @Override
//    public StreamCodec<RegistryFriendlyByteBuf, FabricatingRecipe> streamCodec() {
//        return STREAM_CODEC;
//    }
//}
