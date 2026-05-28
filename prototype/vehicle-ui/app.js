/**
 * 应用主入口 —— 状态管理 + 组件编排
 *
 * 职责：
 *   1. 持有全局状态（当前标签页、激活控制组索引、编辑器选中状态）
 *   2. 协调各组件的渲染与事件
 *   3. 提供 CRUD 回调，修改数据并触发重渲染
 */

import { mockControlGroupSet } from './data/mock.js';
import { renderHeader } from './components/header.js';
import { renderOverview } from './components/overview.js';
import { renderDeviceControl } from './components/device-control.js';
import { renderConfigEditor } from './components/config-editor.js';
import { renderInventory } from './components/inventory.js';

/** 深拷贝 mock 默认数据，用于重置 */
function cloneDefaults() {
  return JSON.parse(JSON.stringify(mockControlGroupSet));
}

/**
 * 全局应用状态
 */
const state = {
  activeTab: 0,
  activeGroupIndex: mockControlGroupSet.activeIndex,
  controlSet: mockControlGroupSet,

  // ── Panel 03 编辑器专用状态 ──
  configSelectedGroupIndex: 0,
  /** 按键绑定选中索引（-1 = 无） */
  configSelectedBindingIndex: -1,
  /** GUI 交互元素选中索引（-1 = 无） */
  configSelectedGuiActionIndex: -1
};

function getAllGroups() {
  return [state.controlSet.baseGroup, ...state.controlSet.groups];
}

function switchTab(index) {
  if (index === state.activeTab) return;
  state.activeTab = index;
  render();
}

function switchControlGroup(index) {
  if (index === state.activeGroupIndex) {
    state.activeGroupIndex = -1;
    state.controlSet.activeIndex = -1;
  } else {
    state.activeGroupIndex = index;
    state.controlSet.activeIndex = index;
  }
  renderActivePanel();
}

function render() {
  const headerEl = document.getElementById('header');
  const panelEl = document.getElementById('panel');

  const themeClasses = ['panel-overview', 'panel-device', 'panel-config', 'panel-inventory'];
  panelEl.className = `panel-content ${themeClasses[state.activeTab] || ''}`;

  renderHeader(headerEl, state.activeTab, switchTab);
  renderActivePanel(panelEl);
}

function renderActivePanel(panelEl) {
  const el = panelEl || document.getElementById('panel');

  switch (state.activeTab) {
    case 0:
      renderOverview(el, state.controlSet.groups, state.activeGroupIndex, switchControlGroup);
      break;
    case 1:
      renderDeviceControl(el, state.controlSet.groups, state.activeGroupIndex, switchControlGroup, state.controlSet.guiActions);
      break;
    case 2:
      renderConfigPanel(el);
      break;
    case 3:
      renderInventory(el);
      break;
  }
}

/**
 * 渲染编辑配置面板（Panel 03）
 */
function renderConfigPanel(el) {
  const allGroups = getAllGroups();
  const groupIndex = state.configSelectedGroupIndex;
  const isBaseGroup = groupIndex === 0;

  if (groupIndex >= allGroups.length) {
    state.configSelectedGroupIndex = allGroups.length - 1;
  }
  const actualGroupIndex = state.configSelectedGroupIndex === 0 ? 0 : state.configSelectedGroupIndex;

  renderConfigEditor(el, {
    groups: allGroups,
    selectedGroupIndex: actualGroupIndex,
    selectedBindingIndex: state.configSelectedBindingIndex,
    selectedGuiActionIndex: state.configSelectedGuiActionIndex,
    guiActions: state.controlSet.guiActions,
    isBaseGroup
  }, {
    // ── 控制组 CRUD ──
    onSelectGroup: (index) => {
      state.configSelectedGroupIndex = index;
      state.configSelectedBindingIndex = -1;
      state.configSelectedGuiActionIndex = -1;
      renderConfigPanel(el);
    },

    onAddGroup: () => {
      const newGroup = {
        name: `group_${state.controlSet.groups.length + 1}`,
        controlMode: 'INHERIT',
        description: '',
        moveTargets: {},
        viewTargets: {},
        regularTargets: {},
        bindings: []
      };
      state.controlSet.groups.push(newGroup);
      state.configSelectedGroupIndex = state.controlSet.groups.length;
      state.configSelectedBindingIndex = -1;
      state.configSelectedGuiActionIndex = -1;
      renderConfigPanel(el);
    },

    onDeleteGroup: (index) => {
      if (index === 0) return;
      state.controlSet.groups.splice(index - 1, 1);
      if (state.configSelectedGroupIndex >= getAllGroups().length) {
        state.configSelectedGroupIndex = getAllGroups().length - 1;
      }
      state.configSelectedBindingIndex = -1;
      state.configSelectedGuiActionIndex = -1;
      renderConfigPanel(el);
    },

    onUpdateGroup: (index, field, value) => {
      const target = index === 0
        ? state.controlSet.baseGroup
        : state.controlSet.groups[index - 1];
      target[field] = value;
      renderConfigPanel(el);
    },

    onResetDefaults: () => {
      const defaults = cloneDefaults();
      state.controlSet.baseGroup = defaults.baseGroup;
      state.controlSet.groups = defaults.groups;
      state.controlSet.guiActions = defaults.guiActions;
      state.controlSet.activeIndex = defaults.activeIndex;
      state.configSelectedGroupIndex = 0;
      state.configSelectedBindingIndex = -1;
      state.configSelectedGuiActionIndex = -1;
      state.activeGroupIndex = defaults.activeIndex;
      renderConfigPanel(el);
    },

    // ── 按键绑定 CRUD ──
    onSelectItem: (index) => {
      state.configSelectedBindingIndex = index;
      state.configSelectedGuiActionIndex = -1;
      renderConfigPanel(el);
    },

    onAddItem: (groupIndex) => {
      const group = groupIndex === 0
        ? state.controlSet.baseGroup
        : state.controlSet.groups[groupIndex - 1];
      const newBinding = {
        trigger: 'key.',
        action: 'HOLD',
        channel: 'new_channel',
        targets: ['vehicle'],
        toggleState: false,
        bindingName: 'NEW BINDING',
        sensitivity: 100,
        deadZone: 0,
        reverseAxis: false
      };
      if (!group.bindings) group.bindings = [];
      group.bindings.push(newBinding);
      state.configSelectedBindingIndex = group.bindings.length - 1;
      state.configSelectedGuiActionIndex = -1;
      renderConfigPanel(el);
    },

    onDeleteItem: (groupIndex, itemIndex) => {
      const group = groupIndex === 0
        ? state.controlSet.baseGroup
        : state.controlSet.groups[groupIndex - 1];
      group.bindings.splice(itemIndex, 1);
      if (state.configSelectedBindingIndex >= group.bindings.length) {
        state.configSelectedBindingIndex = group.bindings.length - 1;
      }
      renderConfigPanel(el);
    },

    onUpdateItem: (groupIndex, itemIndex, field, value) => {
      const group = groupIndex === 0
        ? state.controlSet.baseGroup
        : state.controlSet.groups[groupIndex - 1];
      group.bindings[itemIndex][field] = value;
      renderConfigPanel(el);
    },

    // ── GUI 交互元素 CRUD ──
    onSelectGuiAction: (index) => {
      state.configSelectedGuiActionIndex = index;
      state.configSelectedBindingIndex = -1;
      renderConfigPanel(el);
    },

    onAddGuiAction: () => {
      const newAction = {
        type: 'PULSE',
        label: 'NEW ACTION',
        channel: 'new_channel',
        targets: ['vehicle']
      };
      state.controlSet.guiActions.push(newAction);
      state.configSelectedGuiActionIndex = state.controlSet.guiActions.length - 1;
      state.configSelectedBindingIndex = -1;
      renderConfigPanel(el);
    },

    onDeleteGuiAction: (index) => {
      state.controlSet.guiActions.splice(index, 1);
      if (state.configSelectedGuiActionIndex >= state.controlSet.guiActions.length) {
        state.configSelectedGuiActionIndex = state.controlSet.guiActions.length - 1;
      }
      renderConfigPanel(el);
    },

    onUpdateGuiAction: (index, field, value) => {
      state.controlSet.guiActions[index][field] = value;
      renderConfigPanel(el);
    }
  });
}

// ── 启动应用 ──
document.addEventListener('DOMContentLoaded', () => {
  render();
});
