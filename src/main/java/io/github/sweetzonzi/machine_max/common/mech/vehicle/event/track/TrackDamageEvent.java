package io.github.sweetzonzi.machine_max.common.mech.vehicle.event.track;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLink;
import lombok.Getter;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * 履带受击事件：一笔伤害落到某一个材料扇区上。
 *
 * <p>履带的耐久不再是一条聚合血条，所以事件里带的是**扇区索引**与落到该扇区的伤害量，
 * 不是整条带的总量。效果与音效仍走既有碰撞效果管线，本事件只给记账、统计与调试用。</p>
 *
 * <p>两个时点：{@link Pre} 在命中入队时触发（命中发生线程，可能是物理线程），
 * {@link Post} 在主线程结算之后触发。{@code Pre} 可取消：取消的那笔不再入队。</p>
 */
@Getter
public abstract class TrackDamageEvent extends Event {
    private final TrackLink track;
    /** 命中的材料扇区索引，范围 {@code [0, S)}。 */
    private final int sector;
    /** 落到该扇区的伤害量（点），已含装甲修正。 */
    private final float amount;

    protected TrackDamageEvent(TrackLink track, int sector, float amount) {
        this.track = track;
        this.sector = sector;
        this.amount = amount;
    }

    /** 命中入队时触发；取消表示这笔伤害不再落到该扇区。 */
    public static class Pre extends TrackDamageEvent implements ICancellableEvent {
        public Pre(TrackLink track, int sector, float amount) {
            super(track, sector, amount);
        }
    }

    /** 主线程结算之后触发，扇区材料比例已经在事件之后更新。 */
    public static class Post extends TrackDamageEvent {
        public Post(TrackLink track, int sector, float amount) {
            super(track, sector, amount);
        }
    }
}
