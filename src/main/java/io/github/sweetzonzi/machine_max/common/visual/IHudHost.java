package io.github.sweetzonzi.machine_max.common.visual;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 能提供 HUD 布局的宿主。
 * <p>
 * 实现者是座椅、相机/瞄具与武器控制器三类子系统。宿主直接返回该视角实际使用的
 * {@link HudLayout}；{@code null} 表示该视角没有 HUD。内容包宿主通常持有布局资源键，
 * 用 {@link #resolve(ResourceLocation)} 把键解析成布局；将来接入玩家自定义布局或
 * 由宿主自己生成布局时，直接返回布局对象即可，不要求存在资源键。
 * <p>
 * 客户端 {@code CustomHud} 每个 tick 解析当前视角的宿主集合，为每个宿主建立一份
 * 运行时元素列表；宿主集合或视角变化时整体重建。
 */
public interface IHudHost {

    /**
     * 该宿主在指定视角实际使用的布局。
     *
     * @param view 当前视角
     * @return 实际布局；该视角没有 HUD 时返回 {@code null}
     */
    @Nullable
    HudLayout hudLayoutFor(HudView view);

    /**
     * 把布局资源键解析成布局对象。
     * <p>
     * 持有内容包资源键的宿主在 {@link #hudLayoutFor(HudView)} 里调用本方法即可；
     * 不依赖资源键的宿主不需要使用它。资源键为 {@link HudLayout#EMPTY} 或布局不存在时
     * 返回 {@code null}，后者记录一次可定位的警告。
     *
     * @param id 布局资源键；{@code null} 与 {@link HudLayout#EMPTY} 都表示没有布局
     * @return 布局对象；没有布局或资源不存在时返回 {@code null}
     */
    @Nullable
    static HudLayout resolve(@Nullable ResourceLocation id) {
        if (id == null || HudLayout.EMPTY.equals(id)) return null;
        HudLayout layout = MMDynamicRes.HUD_LAYOUTS.get(id);
        if (layout == null && Warnings.LAYOUTS.add(id)) {
            MachineMax.LOGGER.warn("HUD 布局不存在，按无 HUD 处理: {}", id);
        }
        return layout;
    }

    /** 已提示过的缺失布局键，避免每次重建都刷同一条警告。 */
    final class Warnings {
        private static final Set<ResourceLocation> LAYOUTS = ConcurrentHashMap.newKeySet();

        private Warnings() {
        }
    }
}
