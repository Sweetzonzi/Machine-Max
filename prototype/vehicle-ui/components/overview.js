/**
 * Panel 01：载具概览（Vehicle Overview）
 *
 * 布局：控制组条 → 三维预览占位 → 底部三栏（状态/警告/额外）
 */

import { vehicleStatus, warnings } from '../data/mock.js';

/**
 * 渲染载具概览面板
 * @param {HTMLElement} container 容器元素
 * @param {{ name: string, controlMode: string }[]} subGroups 子控制组列表
 * @param {number} activeGroupIndex 当前激活组索引
 * @param {(index: number) => void} onGroupClick 控制组切换回调
 */
export function renderOverview(container, subGroups, activeGroupIndex, onGroupClick) {
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

  // ── 底部信息区 ──
  const infoBar = document.createElement('div');
  infoBar.className = 'info-bar';

  // 左：状态区
  const statusCol = createStatusColumn();
  infoBar.appendChild(statusCol);

  // 中：警告区
  const warnCol = createWarningColumn();
  infoBar.appendChild(warnCol);

  // 右：额外信息区
  const extraCol = createExtraColumn();
  infoBar.appendChild(extraCol);

  container.appendChild(infoBar);
}

/**
 * 创建控制组切换条
 * 三行布局：名称 / 编号(GROUP 00) / 状态(ACTIVE/STAND BY)
 */
function createGroupStrip(groups, activeIndex, onClick) {
  const strip = document.createElement('div');
  strip.className = 'group-strip';

  // BASE 始终存在，编号 00，永远白色
  const baseCard = document.createElement('div');
  baseCard.className = 'group-card group-card-base';
  baseCard.appendChild(createGroupName('BASE'));
  baseCard.appendChild(createGroupNum('GROUP 00'));
  baseCard.appendChild(createGroupStatus('ALWAYS ACTIVE'));
  strip.appendChild(baseCard);

  // 子控制组
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
 * 创建状态信息列
 */
function createStatusColumn() {
  const col = document.createElement('div');
  col.className = 'info-col info-col-status';

  const title = document.createElement('div');
  title.className = 'info-col-title';
  title.textContent = 'VEHICLE STATUS';
  col.appendChild(title);

  const metrics = [
    { label: 'DURABILITY', value: vehicleStatus.durability, unit: '%' },
    { label: 'ENERGY', value: vehicleStatus.energy, unit: '%' },
    { label: 'THRUST', value: vehicleStatus.thrust, unit: '%' },
    { label: 'SPEED', value: vehicleStatus.speed, unit: 'M/S' }
  ];

  metrics.forEach(m => {
    const row = document.createElement('div');
    row.className = 'status-row';

    const lbl = document.createElement('span');
    lbl.className = 'status-label';
    lbl.textContent = m.label;

    const barWrap = document.createElement('div');
    barWrap.className = 'status-bar-wrap';

    const bar = document.createElement('div');
    bar.className = 'status-bar';
    bar.style.width = `${m.value * 100}%`;

    const barBg = document.createElement('div');
    barBg.className = 'status-bar-bg';
    barBg.style.width = '100%';

    barWrap.appendChild(barBg);
    barWrap.appendChild(bar);
    row.appendChild(lbl);
    row.appendChild(barWrap);
    col.appendChild(row);
  });

  // 数字指标：航向/高度/质量
  const extraRow = document.createElement('div');
  extraRow.className = 'status-numbers';
  extraRow.innerHTML = `
    <div class="status-number-item">
      <span class="status-number-label">HEADING</span>
      <span class="status-number-value">${vehicleStatus.heading}°</span>
    </div>
    <div class="status-number-item">
      <span class="status-number-label">ALTITUDE</span>
      <span class="status-number-value">${vehicleStatus.altitude}M</span>
    </div>
    <div class="status-number-item">
      <span class="status-number-label">MASS</span>
      <span class="status-number-value">${(vehicleStatus.mass / 1000).toFixed(1)}T</span>
    </div>
  `;
  col.appendChild(extraRow);

  return col;
}

/**
 * 创建警告信息列
 */
function createWarningColumn() {
  const col = document.createElement('div');
  col.className = 'info-col info-col-warnings';

  const title = document.createElement('div');
  title.className = 'info-col-title';
  title.textContent = 'WARNINGS';
  col.appendChild(title);

  const list = document.createElement('div');
  list.className = 'warnings-list';

  warnings.forEach(w => {
    const item = document.createElement('div');
    item.className = `warning-item warning-item-${w.level}`;

    const symbol = document.createElement('span');
    symbol.className = 'warning-symbol';
    symbol.textContent = w.level === 'critical' ? '■' : '▲';

    const msg = document.createElement('span');
    msg.className = 'warning-message';
    msg.textContent = w.message;

    item.appendChild(symbol);
    item.appendChild(msg);
    list.appendChild(item);
  });

  col.appendChild(list);
  return col;
}

/**
 * 创建额外信息列
 */
function createExtraColumn() {
  const col = document.createElement('div');
  col.className = 'info-col info-col-extra';

  const title = document.createElement('div');
  title.className = 'info-col-title';
  title.textContent = 'CONTROL MODE';
  col.appendChild(title);

  // 当前控制模式
  const modeItem = document.createElement('div');
  modeItem.className = 'extra-mode';

  const modeLabel = document.createElement('span');
  modeLabel.className = 'extra-mode-label';
  modeLabel.textContent = 'GROUND';
  modeItem.appendChild(modeLabel);

  const modeDesc = document.createElement('span');
  modeDesc.className = 'extra-mode-desc';
  modeDesc.textContent = '/// WHEELED VEHICLE CONTROL';
  modeItem.appendChild(modeDesc);
  col.appendChild(modeItem);

  // 连接信息
  const connItem = document.createElement('div');
  connItem.className = 'extra-conn';

  connItem.innerHTML = `
    <div class="extra-conn-row">
      <span class="extra-conn-label">SIGNAL CHANNELS</span>
      <span class="extra-conn-value">12 ACTIVE</span>
    </div>
    <div class="extra-conn-row">
      <span class="extra-conn-label">BINDINGS</span>
      <span class="extra-conn-value">8 CONFIGURED</span>
    </div>
    <div class="extra-conn-row">
      <span class="extra-conn-label">NETWORK SYNC</span>
      <span class="extra-conn-value extra-conn-ok">ONLINE</span>
    </div>
  `;
  col.appendChild(connItem);

  return col;
}
