/**
 * Panel 02：设备控制（Device Control）
 *
 * 布局与 Panel 01 同构：控制组条 → 三维预览 → 底部三区
 * 底部三区映射 guiActions：PULSE 按钮 / TOGGLE 开关 / SLIDER 滑块
 */

/**
 * 渲染设备控制面板
 * @param {HTMLElement} container 容器元素
 * @param {{ name: string }[]} subGroups 子控制组列表
 * @param {number} activeGroupIndex 当前激活组索引
 * @param {(index: number) => void} onGroupClick 控制组切换回调
 * @param {object[]} guiActions AbstractGuiAction 列表
 */
export function renderDeviceControl(container, subGroups, activeGroupIndex, onGroupClick, guiActions) {
  container.innerHTML = '';

  // ── 控制组切换条 ──
  const groupStrip = createGroupStrip(subGroups, activeGroupIndex, onGroupClick);
  container.appendChild(groupStrip);

  // ── 三维预览区 ──
  const previewArea = document.createElement('div');
  previewArea.className = 'preview-area';

  const previewInner = document.createElement('div');
  previewInner.className = 'preview-inner';

  const img = document.createElement('img');
  img.src = 'assets/placeholder.svg';
  img.className = 'preview-placeholder';
  img.alt = 'Vehicle Wireframe Preview';
  previewInner.appendChild(img);
  previewArea.appendChild(previewInner);
  container.appendChild(previewArea);

  // ── 底部控制区 ──
  const controlBar = document.createElement('div');
  controlBar.className = 'device-bar';

  // 分区 guiActions
  const pulses = guiActions.filter(a => a.type === 'PULSE');
  const toggles = guiActions.filter(a => a.type === 'TOGGLE');
  const sliders = guiActions.filter(a => a.type === 'SLIDER');

  controlBar.appendChild(createButtonSection(pulses));
  controlBar.appendChild(createToggleSection(toggles));
  controlBar.appendChild(createSliderSection(sliders));

  container.appendChild(controlBar);
}

/**
 * 创建控制组切换条（与 overview 共享结构）
 * 三行布局：名称 / 编号(GROUP 00) / 状态(ACTIVE/STAND BY)
 */
function createGroupStrip(groups, activeIndex, onClick) {
  const strip = document.createElement('div');
  strip.className = 'group-strip';

  const baseCard = document.createElement('div');
  baseCard.className = 'group-card group-card-base';
  baseCard.appendChild(createGroupName('BASE'));
  baseCard.appendChild(createGroupNum('GROUP 00'));
  baseCard.appendChild(createGroupStatus('ALWAYS ACTIVE'));
  strip.appendChild(baseCard);

  groups.forEach((group, index) => {
    const isActive = index === activeIndex;
    const card = document.createElement('div');
    card.className = `group-card${isActive ? ' group-card-active' : ''}`;
    card.dataset.index = index;

    card.appendChild(createGroupName(group.name.toUpperCase()));
    card.appendChild(createGroupNum(`GROUP ${String(index + 1).padStart(2, '0')}`));
    card.appendChild(createGroupStatus(isActive ? 'ACTIVE' : 'STAND BY'));

    if (!isActive) {
      card.addEventListener('click', () => onClick(index));
    }
    strip.appendChild(card);
  });

  return strip;
}

function createGroupName(text) {
  const el = document.createElement('span');
  el.className = 'group-card-name';
  el.textContent = text;
  return el;
}

function createGroupNum(text) {
  const el = document.createElement('span');
  el.className = 'group-card-num';
  el.textContent = text;
  return el;
}

function createGroupStatus(text) {
  const el = document.createElement('span');
  el.className = 'group-card-status';
  el.textContent = text;
  return el;
}

/**
 * 创建 PULSE 按钮区
 */
function createButtonSection(actions) {
  const section = document.createElement('div');
  section.className = 'device-section device-section-buttons';

  const title = document.createElement('div');
  title.className = 'device-section-title';
  title.textContent = 'ACTIONS';
  section.appendChild(title);

  const grid = document.createElement('div');
  grid.className = 'button-grid';

  actions.forEach(action => {
    const btn = document.createElement('button');
    btn.className = 'device-btn pulse-btn';
    btn.textContent = action.label;
    btn.addEventListener('click', () => {
      btn.classList.add('pulse-active');
      setTimeout(() => btn.classList.remove('pulse-active'), 120);
    });
    grid.appendChild(btn);
  });

  section.appendChild(grid);
  return section;
}

/**
 * 创建 TOGGLE 开关区
 */
function createToggleSection(actions) {
  const section = document.createElement('div');
  section.className = 'device-section device-section-toggles';

  const title = document.createElement('div');
  title.className = 'device-section-title';
  title.textContent = 'TOGGLES';
  section.appendChild(title);

  const grid = document.createElement('div');
  grid.className = 'toggle-grid';

  actions.forEach(action => {
    const item = document.createElement('div');
    item.className = 'toggle-item';

    const label = document.createElement('span');
    label.className = 'toggle-label';
    label.textContent = action.label;

    const toggle = document.createElement('div');
    toggle.className = `toggle-switch${action.active ? ' toggle-on' : ''}`;
    toggle.dataset.active = action.active;

    const knob = document.createElement('div');
    knob.className = 'toggle-knob';

    toggle.appendChild(knob);
    toggle.addEventListener('click', () => {
      const now = toggle.dataset.active === 'true';
      toggle.dataset.active = !now;
      toggle.classList.toggle('toggle-on', !now);
    });

    item.appendChild(label);
    item.appendChild(toggle);
    grid.appendChild(item);
  });

  section.appendChild(grid);
  return section;
}

/**
 * 创建 SLIDER 滑块区（竖向工业控制杆）
 */
function createSliderSection(actions) {
  const section = document.createElement('div');
  section.className = 'device-section device-section-sliders';

  const title = document.createElement('div');
  title.className = 'device-section-title';
  title.textContent = 'AXES';
  section.appendChild(title);

  const grid = document.createElement('div');
  grid.className = 'slider-grid';

  actions.forEach(action => {
    const item = document.createElement('div');
    item.className = 'slider-item';

    const label = document.createElement('span');
    label.className = 'slider-label';
    label.textContent = action.label;

    const track = document.createElement('div');
    track.className = 'slider-track';

    const fill = document.createElement('div');
    fill.className = 'slider-fill';

    const thumb = document.createElement('div');
    thumb.className = 'slider-thumb';

    track.appendChild(fill);
    track.appendChild(thumb);

    // 初始化位置
    const range = action.max - action.min;
    const pct = range > 0 ? ((action.value - action.min) / range) * 100 : 0;
    updateSlider(track, fill, thumb, pct, action.min);

    // 鼠标拖拽
    let dragging = false;
    const onMove = (e) => {
      if (!dragging) return;
      const rect = track.getBoundingClientRect();
      const y = (e.clientY || (e.touches && e.touches[0].clientY)) - rect.top;
      const pct = Math.max(0, Math.min(100, (1 - y / rect.height) * 100));
      updateSlider(track, fill, thumb, pct, action.min);
      const val = action.min + (pct / 100) * range;
      action.value = action.step > 0
        ? Math.round(val / action.step) * action.step
        : val;
    };

    thumb.addEventListener('mousedown', (e) => { e.preventDefault(); dragging = true; });
    track.addEventListener('mousedown', (e) => { e.preventDefault(); dragging = true; onMove(e); });
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', () => { dragging = false; });

    item.appendChild(label);
    item.appendChild(track);
    grid.appendChild(item);
  });

  section.appendChild(grid);
  return section;
}

function updateSlider(track, fill, thumb, pct, min) {
  const clamped = Math.max(0, Math.min(100, pct));
  fill.style.height = `${clamped}%`;
  thumb.style.bottom = `${clamped}%`;
}
