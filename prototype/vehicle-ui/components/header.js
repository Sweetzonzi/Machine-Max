/**
 * 折叠式标题导航（Accordion Header Strip）
 *
 * 当前页面展开（白底黑字+大标题），其余页面折叠（仅数字）。
 * 横向连续条带，无圆角，粗边界，强对比。
 */

/**
 * 标签页定义
 */
export const TABS = [
  { id: 'overview', number: '01', title: 'VEHICLE OVERVIEW', subtitle: '/// STATUS' },
  { id: 'device',   number: '02', title: 'DEVICE CONTROL',   subtitle: '/// CONTROL' },
  { id: 'config',   number: '03', title: 'EDIT CONFIG',      subtitle: '/// CONFIGURE' },
  { id: 'inventory',number: '04', title: 'INVENTORY',        subtitle: '/// CARGO' }
];

/**
 * 渲染头部导航
 * @param {HTMLElement} container 容器元素
 * @param {number} activeIndex 当前激活标签索引
 * @param {(index: number) => void} onTabClick 标签点击回调
 * @returns {() => void} 清理函数
 */
export function renderHeader(container, activeIndex, onTabClick) {
  container.innerHTML = '';
  const strip = document.createElement('div');
  strip.className = 'header-strip';

  TABS.forEach((tab, index) => {
    const isActive = index === activeIndex;
    const item = document.createElement('div');
    item.className = `header-item${isActive ? ' active' : ''}`;
    item.dataset.index = index;

    const num = document.createElement('span');
    num.className = 'header-number';
    num.textContent = tab.number;

    const content = document.createElement('div');
    content.className = 'header-content';

    const title = document.createElement('span');
    title.className = 'header-title';
    title.textContent = tab.title;

    const sub = document.createElement('span');
    sub.className = 'header-subtitle';
    sub.textContent = tab.subtitle;

    content.appendChild(title);
    content.appendChild(sub);
    item.appendChild(num);
    item.appendChild(content);

    if (!isActive) {
      item.addEventListener('click', () => onTabClick(index));
    }

    strip.appendChild(item);
  });

  container.appendChild(strip);
}
