package io.github.sweetzonzi.machine_max.common.visual;

/**
 * HUD 所处的视角。
 * <p>
 * 宿主的每个视角字段对应一个值：{@code first} / {@code third} 由座椅读取，
 * {@code scope} 由相机/瞄具与武器控制器读取。运行期只会处于三者之一：
 * 炮镜模式强制第一人称，其余情况按玩家当前相机类型取 {@code first} 或 {@code third}。
 * <p>
 * 视角是宿主的属性，不是布局的属性：一份 {@link HudLayout} 可以被不同宿主的
 * 不同视角引用，布局本身不记录自己用于哪个视角。
 */
public enum HudView {
    /** 第一人称 */
    FIRST,
    /** 第三人称（含 {@code THIRD_PERSON_FRONT}） */
    THIRD,
    /** 炮镜/摄像机视角 */
    SCOPE
}
