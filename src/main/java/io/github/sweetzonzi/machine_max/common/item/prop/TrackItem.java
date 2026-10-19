package io.github.sweetzonzi.machine_max.common.item.prop;

import net.minecraft.world.item.Item;

/**
 * 履带测试物品（写死，不接内容包）。
 *
 * <p>右键主动轮开始铺设，依次右键各负重轮，最后再次右键主动轮闭合成一条履带；
 * 闭合前不创建履带对象。左、右两条履带各做一次铺设即可，物品不按车体坐标分侧。</p>
 *
 * <p>潜行右键：铺设中撤销最后一个负重轮；已闭合时移除目标轮，移除主动轮会解散整条履带。</p>
 *
 * <p>交互处理在 {@code ItemInteractHandler}，实际成员变更在服务端完成。</p>
 */
public class TrackItem extends Item {
    public TrackItem() {
        super(new Properties().stacksTo(1));
    }
}
