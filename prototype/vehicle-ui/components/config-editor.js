/**
 * Panel 03：编辑配置（Edit Configuration）
 *
 * 三栏布局：
 *   左栏：控制组列表（含删除按钮、底部重置）
 *   中栏：组详情 + KEY BINDINGS（按键绑定）+ CONFIGURATION ITEMS（GUI 交互元素）
 *   右栏：选中项的详情编辑
 *
 * 数据结构映射 Java：
 *   ControlGroup: name, controlMode, moveTargets, viewTargets, regularTargets, bindings
 *   ControlBinding: trigger, action(PRESS/HOLD/TOGGLE), channel, targets
 *   AbstractGuiAction: label, channel, targets
 *     GuiPulseAction  (type: 'PULSE')
 *     GuiToggleAction (type: 'TOGGLE')
 *     GuiSliderAction (type: 'SLIDER', min, max, step, value)
 */

/** Java ControlMode 枚举值 */
const CONTROL_MODES = ['INHERIT', 'GROUND', 'PLANE', 'SHIP', 'MECH'];

/** Java BindingAction 枚举值 */
const BINDING_ACTIONS = ['PRESS', 'HOLD', 'TOGGLE'];

/** Java GuiActionType 枚举值 */
const GUI_ACTION_TYPES = ['PULSE', 'TOGGLE', 'SLIDER'];

/**
 * 将 trigger 格式化为用户友好的按键名
 */
function formatTrigger(trigger) {
  if (trigger.startsWith('key.')) return trigger.slice(4).toUpperCase();
  if (trigger === 'mouse_left') return 'LMB';
  if (trigger === 'mouse_right') return 'RMB';
  if (trigger === 'mouse_middle') return 'MMB';
  if (trigger.startsWith('gamepad.')) return trigger.slice(8).toUpperCase();
  return trigger.toUpperCase();
}

/**
 * 渲染编辑配置面板
 * @param {HTMLElement} container 容器元素
 * @param {object} state 面板状态
 * @param {object} callbacks 回调函数集
 */
export function renderConfigEditor(container, state, callbacks) {
  container.innerHTML = '';

  const layout = document.createElement('div');
  layout.className = 'config-layout';

  renderLeftPane(layout, state, callbacks);
  renderMiddlePane(layout, state, callbacks);
  renderRightPane(layout, state, callbacks);

  container.appendChild(layout);
}

/**
 * 渲染左栏：控制组列表
 */
function renderLeftPane(layout, state, callbacks) {
  const { groups, selectedGroupIndex } = state;

  const pane = document.createElement('div');
  pane.className = 'config-left';

  // 标题栏 + 新增按钮
  const header = document.createElement('div');
  header.className = 'config-left-header';

  const title = document.createElement('div');
  title.className = 'config-left-title';
  title.textContent = 'CONTROL GROUPS';
  header.appendChild(title);

  const addBtn = document.createElement('button');
  addBtn.className = 'config-add-btn';
  addBtn.textContent = '+';
  addBtn.title = 'ADD GROUP';
  addBtn.addEventListener('click', callbacks.onAddGroup);
  header.appendChild(addBtn);

  pane.appendChild(header);

  // 控制组列表
  const list = document.createElement('div');
  list.className = 'config-group-list';

  // baseGroup 项（编号 00）
  const baseItem = createGroupItem(groups[0], 0, '00', true, false, callbacks.onSelectGroup, null);
  list.appendChild(baseItem);

  // 子控制组项（编号 01, 02, 03...）
  for (let i = 1; i < groups.length; i++) {
    const group = groups[i];
    const isSelected = i === selectedGroupIndex;
    const numStr = String(i).padStart(2, '0');
    const item = createGroupItem(group, i, numStr, false, isSelected, callbacks.onSelectGroup, () => {
      callbacks.onDeleteGroup(i);
    });
    list.appendChild(item);
  }

  pane.appendChild(list);

  // 底部重置按钮
  const footer = document.createElement('div');
  footer.className = 'config-left-footer';

  const resetBtn = document.createElement('button');
  resetBtn.className = 'config-add-group-btn';
  resetBtn.innerHTML = '<span class="btn-icon">↺</span> RESET TO DEFAULT';
  resetBtn.addEventListener('click', callbacks.onResetDefaults);
  footer.appendChild(resetBtn);

  pane.appendChild(footer);
  layout.appendChild(pane);
}

/**
 * 创建单个控制组列表项
 * @param {object} group 控制组数据
 * @param {number} index 在 groups 数组中的实际索引
 * @param {string} numStr 显示编号（"00", "01" 等）
 * @param {boolean} isBase 是否为 baseGroup
 * @param {boolean} isSelected 是否被选中
 * @param {Function} onSelect 选中回调
 * @param {Function|null} onDelete 删除回调（null = 不显示删除按钮）
 */
function createGroupItem(group, index, numStr, isBase, isSelected, onSelect, onDelete) {
  const item = document.createElement('div');
  item.className = `config-group-item${isSelected ? ' config-group-selected' : ''}`;

  // 顶行：编号 + 名称 + 操作
  const topRow = document.createElement('div');
  topRow.className = 'config-group-top-row';

  const numSpan = document.createElement('span');
  numSpan.className = 'config-group-num';
  numSpan.textContent = numStr;

  const name = document.createElement('span');
  name.className = 'config-group-name';
  name.textContent = group.name.toUpperCase();

  topRow.appendChild(numSpan);
  topRow.appendChild(name);

  if (isBase) {
    const badge = document.createElement('span');
    badge.className = 'config-group-always-active';
    badge.textContent = 'ALWAYS ACTIVE';
    topRow.appendChild(badge);
  } else {
    // 模式标签
    const badge = document.createElement('span');
    badge.className = 'config-group-mode-badge';
    badge.textContent = group.controlMode;
    topRow.appendChild(badge);

    // 删除按钮
    if (onDelete) {
      const delBtn = document.createElement('button');
      delBtn.className = 'config-group-del-btn';
      delBtn.textContent = '✕';
      delBtn.title = 'DELETE GROUP';
      delBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        onDelete();
      });
      topRow.appendChild(delBtn);
    }
  }

  item.appendChild(topRow);

  // 描述行
  if (group.description) {
    const desc = document.createElement('div');
    desc.className = 'config-group-desc';
    desc.textContent = group.description;
    item.appendChild(desc);
  }

  // 底行：绑定数量
  if (!isBase) {
    const info = document.createElement('div');
    info.className = 'config-group-index';
    const bindingCount = (group.bindings || []).length;
    info.textContent = `${bindingCount} BINDING${bindingCount !== 1 ? 'S' : ''}`;
    item.appendChild(info);
  }

  if (!isBase || !isSelected) {
    item.addEventListener('click', () => onSelect(index));
  }

  return item;
}

/**
 * 渲染中栏：组详情 + KEY BINDINGS + CONFIGURATION ITEMS
 */
function renderMiddlePane(layout, state, callbacks) {
  const { groups, selectedGroupIndex, selectedBindingIndex, selectedGuiActionIndex, guiActions } = state;
  const groupIndex = selectedGroupIndex >= 0 ? selectedGroupIndex : 0;
  const group = groups[groupIndex];

  const pane = document.createElement('div');
  pane.className = 'config-middle';

  // ── 组详情区（GROUP DETAILS） ──
  const header = document.createElement('div');
  header.className = 'config-middle-header';

  const headerTitle = document.createElement('div');
  headerTitle.className = 'config-middle-header-title';
  headerTitle.textContent = 'GROUP DETAILS';
  header.appendChild(headerTitle);

  const details = document.createElement('div');
  details.className = 'config-group-details';

  details.appendChild(createFieldInput('GROUP NAME', group.name.toUpperCase(), 'text', (val) => {
    callbacks.onUpdateGroup(groupIndex, 'name', val.toLowerCase());
  }));

  details.appendChild(createFieldSelect('MODE', group.controlMode, CONTROL_MODES, (val) => {
    callbacks.onUpdateGroup(groupIndex, 'controlMode', val);
  }));

  // 描述字段
  const descRow = document.createElement('div');
  descRow.className = 'config-field-row';
  const descLabel = document.createElement('span');
  descLabel.className = 'config-field-label';
  descLabel.textContent = 'DESCRIPTION';
  const descInput = document.createElement('input');
  descInput.className = 'config-field-input';
  descInput.type = 'text';
  descInput.value = group.description || '';
  descInput.placeholder = 'Optional description...';
  descInput.addEventListener('change', () => {
    callbacks.onUpdateGroup(groupIndex, 'description', descInput.value);
  });
  descRow.appendChild(descLabel);
  descRow.appendChild(descInput);
  details.appendChild(descRow);

  header.appendChild(details);
  pane.appendChild(header);

  // ── KEY BINDINGS 区（按键绑定） ──
  const bindingsSection = createBindingSection(group, groupIndex, selectedBindingIndex, callbacks);
  pane.appendChild(bindingsSection);

  // ── CONFIGURATION ITEMS 区（GUI 交互元素） ──
  const guiSection = createGuiActionsSection(guiActions, selectedGuiActionIndex, callbacks);
  pane.appendChild(guiSection);

  layout.appendChild(pane);
}

/**
 * 创建 KEY BINDINGS 区域
 */
function createBindingSection(group, groupIndex, selectedIndex, callbacks) {
  const section = document.createElement('div');
  section.className = 'config-items-section';

  const header = document.createElement('div');
  header.className = 'config-items-header';

  const title = document.createElement('div');
  title.className = 'config-items-title';
  title.textContent = 'KEY BINDINGS';
  header.appendChild(title);

  const addBtn = document.createElement('button');
  addBtn.className = 'config-add-btn';
  addBtn.textContent = '+';
  addBtn.title = 'ADD BINDING';
  addBtn.addEventListener('click', () => callbacks.onAddItem(groupIndex));
  header.appendChild(addBtn);

  section.appendChild(header);

  const list = document.createElement('div');
  list.className = 'config-items-list';

  const bindings = group.bindings || [];
  if (bindings.length === 0) {
    const empty = document.createElement('div');
    empty.className = 'config-items-empty';
    empty.textContent = 'NO BINDINGS';
    list.appendChild(empty);
  } else {
    bindings.forEach((binding, i) => {
      const isSelected = i === selectedIndex;
      const row = createBindingRow(binding, i, isSelected, callbacks.onSelectItem, () => {
        callbacks.onDeleteItem(groupIndex, i);
      });
      list.appendChild(row);
    });
  }

  section.appendChild(list);
  return section;
}

/**
 * 创建 CONFIGURATION ITEMS 区域（GUI 交互元素：按钮/开关/滑条）
 */
function createGuiActionsSection(guiActions, selectedIndex, callbacks) {
  const section = document.createElement('div');
  section.className = 'config-items-section config-items-section-gui';

  const header = document.createElement('div');
  header.className = 'config-items-header';

  const title = document.createElement('div');
  title.className = 'config-items-title';
  title.textContent = 'CONFIGURATION ITEMS';
  header.appendChild(title);

  const addBtn = document.createElement('button');
  addBtn.className = 'config-add-btn';
  addBtn.textContent = '+';
  addBtn.title = 'ADD ITEM';
  addBtn.addEventListener('click', callbacks.onAddGuiAction);
  header.appendChild(addBtn);

  section.appendChild(header);

  const list = document.createElement('div');
  list.className = 'config-items-list';

  if (guiActions.length === 0) {
    const empty = document.createElement('div');
    empty.className = 'config-items-empty';
    empty.textContent = 'NO ITEMS';
    list.appendChild(empty);
  } else {
    guiActions.forEach((action, i) => {
      const isSelected = i === selectedIndex;
      const row = createGuiActionRow(action, i, isSelected, callbacks.onSelectGuiAction, () => {
        callbacks.onDeleteGuiAction(i);
      });
      list.appendChild(row);
    });
  }

  section.appendChild(list);
  return section;
}

/**
 * 创建按键绑定行
 */
function createBindingRow(binding, index, isSelected, onSelect, onDelete) {
  const row = document.createElement('div');
  row.className = `config-item-row${isSelected ? ' config-item-selected' : ''}`;

  const name = document.createElement('span');
  name.className = 'config-item-name';
  name.textContent = binding.bindingName || binding.channel.toUpperCase();

  const type = document.createElement('span');
  type.className = 'config-item-type';
  type.textContent = binding.action;

  const bindingKey = document.createElement('span');
  bindingKey.className = 'config-item-binding';
  bindingKey.textContent = formatTrigger(binding.trigger);

  const actions = document.createElement('div');
  actions.className = 'config-item-actions';

  const deleteBtn = document.createElement('button');
  deleteBtn.className = 'config-item-action-btn delete';
  deleteBtn.textContent = '✕';
  deleteBtn.title = 'DELETE';
  deleteBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    onDelete();
  });

  actions.appendChild(deleteBtn);

  row.appendChild(name);
  row.appendChild(type);
  row.appendChild(bindingKey);
  row.appendChild(actions);

  row.addEventListener('click', () => onSelect(index));

  return row;
}

/**
 * 创建 GUI 交互元素行
 */
function createGuiActionRow(action, index, isSelected, onSelect, onDelete) {
  const row = document.createElement('div');
  row.className = `config-item-row config-item-row-gui${isSelected ? ' config-item-selected' : ''}`;

  const name = document.createElement('span');
  name.className = 'config-item-name';
  name.textContent = action.label;

  const type = document.createElement('span');
  type.className = 'config-item-type';
  type.textContent = action.type;

  const channel = document.createElement('span');
  channel.className = 'config-item-binding';
  channel.textContent = action.channel;

  const actions = document.createElement('div');
  actions.className = 'config-item-actions';

  const deleteBtn = document.createElement('button');
  deleteBtn.className = 'config-item-action-btn delete';
  deleteBtn.textContent = '✕';
  deleteBtn.title = 'DELETE';
  deleteBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    onDelete();
  });

  actions.appendChild(deleteBtn);

  row.appendChild(name);
  row.appendChild(type);
  row.appendChild(channel);
  row.appendChild(actions);

  row.addEventListener('click', () => onSelect(index));

  return row;
}

/**
 * 渲染右栏：详情编辑
 * 根据 selectedBindingIndex 和 selectedGuiActionIndex 决定显示哪个
 */
function renderRightPane(layout, state, callbacks) {
  const { groups, selectedGroupIndex, selectedBindingIndex, selectedGuiActionIndex, guiActions } = state;
  const groupIndex = selectedGroupIndex >= 0 ? selectedGroupIndex : 0;
  const group = groups[groupIndex];
  const binding = selectedBindingIndex >= 0 ? group.bindings[selectedBindingIndex] : null;
  const guiAction = selectedGuiActionIndex >= 0 ? guiActions[selectedGuiActionIndex] : null;

  const pane = document.createElement('div');
  pane.className = 'config-right';

  const header = document.createElement('div');
  header.className = 'config-right-header';

  const title = document.createElement('div');
  title.className = 'config-right-title';
  if (binding) title.textContent = 'BINDING DETAILS';
  else if (guiAction) title.textContent = 'ITEM DETAILS';
  else title.textContent = 'DETAILS';
  header.appendChild(title);

  pane.appendChild(header);

  if (binding) {
    renderBindingDetail(pane, binding, groupIndex, selectedBindingIndex, callbacks);
  } else if (guiAction) {
    renderGuiActionDetail(pane, guiAction, selectedGuiActionIndex, callbacks);
  } else {
    const empty = document.createElement('div');
    empty.className = 'config-right-empty';
    empty.textContent = 'SELECT AN ITEM TO EDIT';
    pane.appendChild(empty);
  }

  layout.appendChild(pane);
}

/**
 * 渲染按键绑定详情编辑
 */
function renderBindingDetail(pane, binding, groupIndex, itemIndex, callbacks) {
  const body = document.createElement('div');
  body.className = 'config-right-body';

  const form = document.createElement('div');
  form.className = 'config-detail-form';

  form.appendChild(createDetailField('NAME', () => {
    const input = document.createElement('input');
    input.className = 'config-field-input';
    input.type = 'text';
    input.value = binding.bindingName || binding.channel.toUpperCase();
    input.addEventListener('change', () => {
      callbacks.onUpdateItem(groupIndex, itemIndex, 'bindingName', input.value);
    });
    return input;
  }));

  form.appendChild(createDetailField('TYPE', () => {
    const select = document.createElement('select');
    select.className = 'config-field-select';
    BINDING_ACTIONS.forEach(act => {
      const opt = document.createElement('option');
      opt.value = act;
      opt.textContent = act;
      if (act === binding.action) opt.selected = true;
      select.appendChild(opt);
    });
    select.addEventListener('change', () => {
      callbacks.onUpdateItem(groupIndex, itemIndex, 'action', select.value);
    });
    return select;
  }));

  form.appendChild(createDetailField('BINDING', () => {
    const input = document.createElement('input');
    input.className = 'config-field-input';
    input.type = 'text';
    input.value = binding.trigger;
    input.placeholder = 'e.g. key.w, mouse_left';
    input.addEventListener('change', () => {
      callbacks.onUpdateItem(groupIndex, itemIndex, 'trigger', input.value);
    });
    return input;
  }));

  form.appendChild(createDetailField('CHANNEL', () => {
    const input = document.createElement('input');
    input.className = 'config-field-input';
    input.type = 'text';
    input.value = binding.channel;
    input.addEventListener('change', () => {
      callbacks.onUpdateItem(groupIndex, itemIndex, 'channel', input.value);
    });
    return input;
  }));

  form.appendChild(createDetailField('TARGETS', () => {
    const input = document.createElement('input');
    input.className = 'config-field-input';
    input.type = 'text';
    input.value = (binding.targets || []).join(', ');
    input.placeholder = 'vehicle, weapon.primary';
    input.addEventListener('change', () => {
      const targets = input.value.split(',').map(s => s.trim()).filter(Boolean);
      callbacks.onUpdateItem(groupIndex, itemIndex, 'targets', targets);
    });
    return input;
  }));

  // REVERSE AXIS 开关
  form.appendChild(createReverseAxisToggle(binding.reverseAxis || false, (val) => {
    callbacks.onUpdateItem(groupIndex, itemIndex, 'reverseAxis', val);
  }));

  // SENSITIVITY 滑块
  form.appendChild(createSliderField('SENSITIVITY', binding.sensitivity ?? 100, 0, 100, '%', (val) => {
    callbacks.onUpdateItem(groupIndex, itemIndex, 'sensitivity', val);
  }));

  // DEAD ZONE 滑块
  form.appendChild(createSliderField('DEAD ZONE', binding.deadZone ?? 0, 0, 50, '%', (val) => {
    callbacks.onUpdateItem(groupIndex, itemIndex, 'deadZone', val);
  }));

  // 目标标签
  form.appendChild(createTargetsSection(binding.targets || []));

  body.appendChild(form);
  pane.appendChild(body);

  // 底部删除按钮
  const footer = document.createElement('div');
  footer.className = 'config-right-footer';
  const deleteBtn = document.createElement('button');
  deleteBtn.className = 'config-delete-btn';
  deleteBtn.textContent = 'DELETE BINDING';
  deleteBtn.addEventListener('click', () => {
    callbacks.onDeleteItem(groupIndex, itemIndex);
  });
  footer.appendChild(deleteBtn);
  pane.appendChild(footer);
}

/**
 * 渲染 GUI 交互元素详情编辑
 */
function renderGuiActionDetail(pane, action, actionIndex, callbacks) {
  const body = document.createElement('div');
  body.className = 'config-right-body';

  const form = document.createElement('div');
  form.className = 'config-detail-form';

  // LABEL 字段
  form.appendChild(createDetailField('LABEL', () => {
    const input = document.createElement('input');
    input.className = 'config-field-input';
    input.type = 'text';
    input.value = action.label;
    input.addEventListener('change', () => {
      callbacks.onUpdateGuiAction(actionIndex, 'label', input.value);
    });
    return input;
  }));

  // TYPE 字段
  form.appendChild(createDetailField('TYPE', () => {
    const select = document.createElement('select');
    select.className = 'config-field-select';
    GUI_ACTION_TYPES.forEach(t => {
      const opt = document.createElement('option');
      opt.value = t;
      opt.textContent = t;
      if (t === action.type) opt.selected = true;
      select.appendChild(opt);
    });
    select.addEventListener('change', () => {
      callbacks.onUpdateGuiAction(actionIndex, 'type', select.value);
    });
    return select;
  }));

  // CHANNEL 字段
  form.appendChild(createDetailField('CHANNEL', () => {
    const input = document.createElement('input');
    input.className = 'config-field-input';
    input.type = 'text';
    input.value = action.channel;
    input.addEventListener('change', () => {
      callbacks.onUpdateGuiAction(actionIndex, 'channel', input.value);
    });
    return input;
  }));

  // TARGETS 字段
  form.appendChild(createDetailField('TARGETS', () => {
    const input = document.createElement('input');
    input.className = 'config-field-input';
    input.type = 'text';
    input.value = (action.targets || []).join(', ');
    input.placeholder = 'vehicle, weapon.primary';
    input.addEventListener('change', () => {
      const targets = input.value.split(',').map(s => s.trim()).filter(Boolean);
      callbacks.onUpdateGuiAction(actionIndex, 'targets', targets);
    });
    return input;
  }));

  // TOGGLE 专属：active 状态
  if (action.type === 'TOGGLE') {
    form.appendChild(createReverseAxisToggle(action.active || false, (val) => {
      callbacks.onUpdateGuiAction(actionIndex, 'active', val);
    }));
  }

  // SLIDER 专属字段
  if (action.type === 'SLIDER') {
    form.appendChild(createSliderField('MIN', action.min ?? 0, -100, 100, '', (val) => {
      callbacks.onUpdateGuiAction(actionIndex, 'min', val);
    }));
    form.appendChild(createSliderField('MAX', action.max ?? 100, 0, 200, '', (val) => {
      callbacks.onUpdateGuiAction(actionIndex, 'max', val);
    }));
    form.appendChild(createSliderField('STEP', action.step ?? 1, 0, 10, '', (val) => {
      callbacks.onUpdateGuiAction(actionIndex, 'step', val);
    }));
  }

  // 目标标签
  form.appendChild(createTargetsSection(action.targets || []));

  body.appendChild(form);
  pane.appendChild(body);

  // 底部删除按钮
  const footer = document.createElement('div');
  footer.className = 'config-right-footer';
  const deleteBtn = document.createElement('button');
  deleteBtn.className = 'config-delete-btn';
  deleteBtn.textContent = 'DELETE ITEM';
  deleteBtn.addEventListener('click', () => {
    callbacks.onDeleteGuiAction(actionIndex);
  });
  footer.appendChild(deleteBtn);
  pane.appendChild(footer);
}

// ── 通用表单工具函数 ──

function createFieldInput(labelText, value, inputType, onChange) {
  const row = document.createElement('div');
  row.className = 'config-field-row';
  const label = document.createElement('span');
  label.className = 'config-field-label';
  label.textContent = labelText;
  const input = document.createElement('input');
  input.className = 'config-field-input';
  input.type = inputType;
  input.value = value;
  input.addEventListener('change', () => onChange(input.value));
  row.appendChild(label);
  row.appendChild(input);
  return row;
}

function createFieldSelect(labelText, value, options, onChange) {
  const row = document.createElement('div');
  row.className = 'config-field-row';
  const label = document.createElement('span');
  label.className = 'config-field-label';
  label.textContent = labelText;
  const select = document.createElement('select');
  select.className = 'config-field-select';
  options.forEach(opt => {
    const option = document.createElement('option');
    option.value = opt;
    option.textContent = opt;
    if (opt === value) option.selected = true;
    select.appendChild(option);
  });
  select.addEventListener('change', () => onChange(select.value));
  row.appendChild(label);
  row.appendChild(select);
  return row;
}

function createDetailField(label, inputFactory) {
  const field = document.createElement('div');
  field.className = 'config-detail-field';
  const labelEl = document.createElement('div');
  labelEl.className = 'config-detail-field-label';
  labelEl.textContent = label;
  field.appendChild(labelEl);
  field.appendChild(inputFactory());
  return field;
}

function createReverseAxisToggle(isOn, onChange) {
  const wrap = document.createElement('div');
  wrap.className = 'config-toggle-wrap';
  const label = document.createElement('span');
  label.className = 'config-toggle-label';
  label.textContent = 'REVERSE AXIS';
  const toggle = document.createElement('div');
  toggle.className = `config-toggle-switch${isOn ? ' toggle-on' : ''}`;
  const knob = document.createElement('div');
  knob.className = 'config-toggle-knob';
  toggle.appendChild(knob);
  toggle.addEventListener('click', () => {
    const now = toggle.classList.contains('toggle-on');
    toggle.classList.toggle('toggle-on', !now);
    onChange(!now);
  });
  wrap.appendChild(label);
  wrap.appendChild(toggle);
  return wrap;
}

function createSliderField(labelText, value, min, max, unit, onChange) {
  const field = document.createElement('div');
  field.className = 'config-slider-field';
  const header = document.createElement('div');
  header.className = 'config-slider-header';
  const label = document.createElement('span');
  label.className = 'config-slider-label';
  label.textContent = labelText;
  const valueEl = document.createElement('span');
  valueEl.className = 'config-slider-value';
  valueEl.textContent = `${value}${unit}`;
  header.appendChild(label);
  header.appendChild(valueEl);
  field.appendChild(header);

  const track = document.createElement('div');
  track.className = 'config-slider-track';
  const fill = document.createElement('div');
  fill.className = 'config-slider-fill';
  const thumb = document.createElement('div');
  thumb.className = 'config-slider-thumb';
  track.appendChild(fill);
  track.appendChild(thumb);

  const range = max - min;
  const pct = range > 0 ? ((value - min) / range) * 100 : 0;
  updateHSlider(fill, thumb, pct);

  let dragging = false;
  const onMove = (e) => {
    if (!dragging) return;
    const rect = track.getBoundingClientRect();
    const x = (e.clientX || (e.touches && e.touches[0].clientX)) - rect.left;
    const p = Math.max(0, Math.min(100, (x / rect.width) * 100));
    updateHSlider(fill, thumb, p);
    const val = Math.round(min + (p / 100) * range);
    valueEl.textContent = `${val}${unit}`;
  };

  const onUp = () => {
    if (!dragging) return;
    dragging = false;
    const rect = track.getBoundingClientRect();
    const currentPct = parseFloat(thumb.style.left) || 0;
    const val = Math.round(min + (currentPct / 100) * range);
    onChange(val);
  };

  thumb.addEventListener('mousedown', (e) => { e.preventDefault(); dragging = true; });
  track.addEventListener('mousedown', (e) => {
    e.preventDefault();
    dragging = true;
    onMove(e);
    const rect = track.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const p = Math.max(0, Math.min(100, (x / rect.width) * 100));
    const val = Math.round(min + (p / 100) * range);
    valueEl.textContent = `${val}${unit}`;
    onChange(val);
  });
  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);

  field.appendChild(track);
  return field;
}

function updateHSlider(fill, thumb, pct) {
  const clamped = Math.max(0, Math.min(100, pct));
  fill.style.width = `${clamped}%`;
  thumb.style.left = `${clamped}%`;
}

function createTargetsSection(targets) {
  const section = document.createElement('div');
  section.className = 'config-targets-section';
  const title = document.createElement('div');
  title.className = 'config-targets-title';
  title.textContent = 'OUTPUT TARGETS';
  section.appendChild(title);
  if (targets.length === 0) {
    const empty = document.createElement('span');
    empty.className = 'config-target-tag';
    empty.textContent = 'NONE';
    section.appendChild(empty);
  } else {
    targets.forEach(t => {
      const tag = document.createElement('span');
      tag.className = 'config-target-tag';
      const channelSpan = document.createElement('span');
      channelSpan.className = 'config-target-tag-channel';
      channelSpan.textContent = t;
      tag.appendChild(channelSpan);
      section.appendChild(tag);
    });
  }
  return section;
}
