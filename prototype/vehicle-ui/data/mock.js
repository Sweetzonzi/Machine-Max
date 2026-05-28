/**
 * Mock 数据模型，与 Java 后端 ControlGroupSet 等数据结构同构。
 *
 * 核心结构与 Java 端一一对应：
 *   ControlGroupSet
 *   ├── baseGroup: ControlGroup          (对应 Java ControlGroup.java)
 *   ├── groups: ControlGroup[]           (可切换子控制组)
 *   ├── activeIndex: number              (-1 = 无激活子组)
 *   └── guiActions: AbstractGuiAction[]  (对应 Java AbstractGuiAction.java)
 *       ├── GuiPulseAction  (type: 'PULSE')
 *       ├── GuiToggleAction (type: 'TOGGLE')
 *       └── GuiSliderAction (type: 'SLIDER')
 *
 * 每个 ControlGroup 内含:
 *   name, controlMode, moveTargets, viewTargets, regularTargets, bindings
 *
 * 每个 ControlBinding 内含:
 *   trigger, action(PRESS/HOLD/TOGGLE), channel, targets
 *
 * 原型扩展字段（标记 [UI-ONLY]）：
 *   ControlGroup.description   — 前端展示用，Java 端尚无此字段
 *   ControlBinding bindingName — 显示名称，Java 端由 channel 推导
 *   ControlBinding sensitivity — 灵敏度，Java 端尚无此字段
 *   ControlBinding deadZone    — 死区，Java 端尚无此字段
 *   ControlBinding reverseAxis — 反向轴，Java 端尚无此字段
 */

/**
 * 载具状态数据（顶层独立模型，非 Java 类直接映射，用于 Panel 01）
 */
export const vehicleStatus = {
  durability: 0.87,
  energy: 0.65,
  speed: 128,
  heading: 234,
  altitude: 312,
  mass: 12450,
  thrust: 1.26
};

/**
 * 警告信息列表（Panel 01 警告区）
 */
export const warnings = [
  { level: 'warning', message: 'LEFT ENGINE DURABILITY LOW' },
  { level: 'warning', message: 'WEAPON SYSTEM NOT RESPONDING' },
  { level: 'critical', message: 'ENERGY LEVEL CRITICAL' }
];

/**
 * ControlGroupSet 的 Mock 数据，结构与 Java 端完全对应。
 * 字段注释中标注 [UI-ONLY] 的为原型扩展，Java 端尚无对应字段。
 */
export const mockControlGroupSet = {
  /** 始终激活的基础控制组（Java: ControlGroupSet.baseGroup） */
  baseGroup: {
    name: 'base',
    controlMode: 'GROUND',
    /** [UI-ONLY] 描述信息，Java 端无此字段 */
    description: 'Base vehicle controls — always active.',
    moveTargets: {
      'throttle': ['engine.main'],
      'steer': ['wheel.front', 'wheel.rear']
    },
    viewTargets: {
      'look': ['camera.main']
    },
    regularTargets: {
      'gear': ['landing_gear'],
      'lights': ['headlight.left', 'headlight.right'],
      'horn': ['audio.horn']
    },
    bindings: [
      { trigger: 'key.w', action: 'HOLD', channel: 'throttle', targets: ['vehicle'], toggleState: false, bindingName: 'THROTTLE', sensitivity: 100, deadZone: 0, reverseAxis: false },
      { trigger: 'key.s', action: 'HOLD', channel: 'brake', targets: ['vehicle'], toggleState: false, bindingName: 'BRAKE', sensitivity: 100, deadZone: 0, reverseAxis: false },
      { trigger: 'key.a', action: 'HOLD', channel: 'steer.left', targets: ['wheel.front', 'wheel.rear'], toggleState: false, bindingName: 'STEER LEFT', sensitivity: 100, deadZone: 0, reverseAxis: false },
      { trigger: 'key.d', action: 'HOLD', channel: 'steer.right', targets: ['wheel.front', 'wheel.rear'], toggleState: false, bindingName: 'STEER RIGHT', sensitivity: 100, deadZone: 0, reverseAxis: false },
      { trigger: 'key.g', action: 'TOGGLE', channel: 'gear', targets: ['landing_gear'], toggleState: false, bindingName: 'LANDING GEAR', sensitivity: 0, deadZone: 0, reverseAxis: false },
      { trigger: 'key.f', action: 'TOGGLE', channel: 'lights', targets: ['headlight.left', 'headlight.right'], toggleState: false, bindingName: 'HEADLIGHTS', sensitivity: 0, deadZone: 0, reverseAxis: false }
    ]
  },

  /** 可切换的子控制组列表（Java: ControlGroupSet.groups） */
  groups: [
    {
      name: 'combat',
      controlMode: 'INHERIT',
      description: 'Weapons targeting and firing controls.',
      moveTargets: {
        'aim_pitch': ['turret.main.pitch'],
        'aim_yaw': ['turret.main.yaw']
      },
      viewTargets: {
        'aim': ['turret.main']
      },
      regularTargets: {
        'fire': ['weapon.primary'],
        'alt_fire': ['weapon.secondary'],
        'reload': ['weapon.primary', 'weapon.secondary']
      },
      bindings: [
        { trigger: 'mouse_left', action: 'HOLD', channel: 'fire', targets: ['weapon.primary'], toggleState: false, bindingName: 'PRIMARY FIRE', sensitivity: 100, deadZone: 0, reverseAxis: false },
        { trigger: 'mouse_right', action: 'HOLD', channel: 'alt_fire', targets: ['weapon.secondary'], toggleState: false, bindingName: 'ALT FIRE', sensitivity: 100, deadZone: 0, reverseAxis: false },
        { trigger: 'key.r', action: 'PRESS', channel: 'reload', targets: ['weapon.primary', 'weapon.secondary'], toggleState: false, bindingName: 'RELOAD', sensitivity: 0, deadZone: 0, reverseAxis: false },
        { trigger: 'key.q', action: 'TOGGLE', channel: 'weapon_system', targets: ['weapon.primary', 'weapon.secondary'], toggleState: false, bindingName: 'WEAPON SYSTEM', sensitivity: 0, deadZone: 0, reverseAxis: false }
      ]
    },
    {
      name: 'piloting',
      controlMode: 'PLANE',
      description: 'Standard piloting controls for general navigation.',
      moveTargets: {
        'pitch': ['wing.flap.left', 'wing.flap.right'],
        'yaw': ['rudder'],
        'roll': ['wing.aileron.left', 'wing.aileron.right']
      },
      viewTargets: {},
      regularTargets: {
        'afterburner': ['engine.afterburner'],
        'flaps': ['wing.flap.left', 'wing.flap.right'],
        'gear': ['landing_gear']
      },
      bindings: [
        { trigger: 'key.w', action: 'HOLD', channel: 'pitch.up', targets: ['wing.flap.left', 'wing.flap.right'], toggleState: false, bindingName: 'PITCH UP', sensitivity: 100, deadZone: 0, reverseAxis: false },
        { trigger: 'key.s', action: 'HOLD', channel: 'pitch.down', targets: ['wing.flap.left', 'wing.flap.right'], toggleState: false, bindingName: 'PITCH DOWN', sensitivity: 100, deadZone: 0, reverseAxis: true },
        { trigger: 'key.a', action: 'HOLD', channel: 'roll.left', targets: ['wing.aileron.left', 'wing.aileron.right'], toggleState: false, bindingName: 'ROLL LEFT', sensitivity: 100, deadZone: 0, reverseAxis: false },
        { trigger: 'key.d', action: 'HOLD', channel: 'roll.right', targets: ['wing.aileron.left', 'wing.aileron.right'], toggleState: false, bindingName: 'ROLL RIGHT', sensitivity: 100, deadZone: 0, reverseAxis: true },
        { trigger: 'key.space', action: 'HOLD', channel: 'afterburner', targets: ['engine.afterburner'], toggleState: false, bindingName: 'AFTERBURNER', sensitivity: 0, deadZone: 0, reverseAxis: false },
        { trigger: 'key.g', action: 'TOGGLE', channel: 'gear', targets: ['landing_gear'], toggleState: false, bindingName: 'LANDING GEAR', sensitivity: 0, deadZone: 0, reverseAxis: false }
      ]
    },
    {
      name: 'mining',
      controlMode: 'INHERIT',
      description: 'Drilling, scanning and resource extraction controls.',
      moveTargets: {
        'drill': ['drill.arm'],
        'scan': ['scanner.head']
      },
      viewTargets: {},
      regularTargets: {
        'extract': ['collector.intake'],
        'lights': ['mining.lamp']
      },
      bindings: [
        { trigger: 'mouse_left', action: 'HOLD', channel: 'drill', targets: ['drill.arm'], toggleState: false, bindingName: 'DRILL', sensitivity: 100, deadZone: 0, reverseAxis: false },
        { trigger: 'key.r', action: 'PRESS', channel: 'extract', targets: ['collector.intake'], toggleState: false, bindingName: 'EXTRACT', sensitivity: 0, deadZone: 0, reverseAxis: false },
        { trigger: 'key.l', action: 'TOGGLE', channel: 'lights', targets: ['mining.lamp'], toggleState: false, bindingName: 'MINING LAMP', sensitivity: 0, deadZone: 0, reverseAxis: false }
      ]
    },
    {
      name: 'utility',
      controlMode: 'INHERIT',
      description: 'Utility subsystems: shield, repair, countermeasures.',
      moveTargets: {},
      viewTargets: {},
      regularTargets: {
        'shield': ['shield.generator'],
        'repair': ['repair.drone'],
        'flare': ['countermeasure.flare']
      },
      bindings: [
        { trigger: 'key.x', action: 'TOGGLE', channel: 'shield', targets: ['shield.generator'], toggleState: false, bindingName: 'SHIELD', sensitivity: 0, deadZone: 0, reverseAxis: false },
        { trigger: 'key.z', action: 'HOLD', channel: 'repair', targets: ['repair.drone'], toggleState: false, bindingName: 'REPAIR', sensitivity: 0, deadZone: 0, reverseAxis: false },
        { trigger: 'key.c', action: 'PRESS', channel: 'flare', targets: ['countermeasure.flare'], toggleState: false, bindingName: 'COUNTERMEASURE', sensitivity: 0, deadZone: 0, reverseAxis: false }
      ]
    }
  ],

  /** 当前激活的子控制组索引，-1 表示无（Java: ControlGroupSet.activeIndex） */
  activeIndex: -1,

  /** GUI 交互元素列表（Java: ControlGroupSet.guiActions） */
  guiActions: [
    // ── PULSE 按钮区（Java: GuiPulseAction）──
    { type: 'PULSE', label: 'THRUST BOOST', channel: 'boost', targets: ['vehicle'] },
    { type: 'PULSE', label: 'LANDING GEAR', channel: 'gear', targets: ['landing_gear'] },
    { type: 'PULSE', label: 'EMERGENCY BRAKE', channel: 'brake', targets: ['vehicle'] },
    // ── TOGGLE 开关区（Java: GuiToggleAction）──
    { type: 'TOGGLE', label: 'CRUISE MODE', channel: 'cruise', targets: ['vehicle'], active: false },
    { type: 'TOGGLE', label: 'STABILIZER', channel: 'stabilizer', targets: ['vehicle'], active: true },
    { type: 'TOGGLE', label: 'GRAVITY COMP', channel: 'gravity_comp', targets: ['vehicle'], active: false },
    { type: 'TOGGLE', label: 'POWER LIMITER', channel: 'power_limiter', targets: ['vehicle'], active: true },
    // ── SLIDER 滑块区（Java: GuiSliderAction）──
    { type: 'SLIDER', label: 'THRUST', channel: 'throttle', targets: ['vehicle'], min: 0, max: 100, step: 1, value: 0 },
    { type: 'SLIDER', label: 'YAW', channel: 'yaw', targets: ['vehicle'], min: -100, max: 100, step: 1, value: 0 },
    { type: 'SLIDER', label: 'PITCH', channel: 'pitch', targets: ['vehicle'], min: -100, max: 100, step: 1, value: 0 },
    { type: 'SLIDER', label: 'ROLL', channel: 'roll', targets: ['vehicle'], min: -100, max: 100, step: 1, value: 0 },
    { type: 'SLIDER', label: 'LIFT', channel: 'lift', targets: ['vehicle'], min: 0, max: 100, step: 1, value: 0 }
  ]
};
