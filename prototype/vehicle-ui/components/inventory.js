/**
 * Panel 04：库存管理（Inventory）
 *
 * 占位面板。保留标题、分类栏、搜索栏、容量信息框架。
 * 内容显示 "NO DATA"。
 */

export function renderInventory(container) {
  container.innerHTML = '';

  const layout = document.createElement('div');
  layout.className = 'inventory-layout';

  // ── 顶部工具栏 ──
  const toolbar = document.createElement('div');
  toolbar.className = 'inventory-toolbar';

  const title = document.createElement('div');
  title.className = 'inventory-title';
  title.textContent = 'CARGO INVENTORY';
  toolbar.appendChild(title);

  const searchWrap = document.createElement('div');
  searchWrap.className = 'inventory-search-wrap';

  const searchInput = document.createElement('input');
  searchInput.className = 'inventory-search';
  searchInput.type = 'text';
  searchInput.placeholder = 'SEARCH CARGO...';
  searchInput.disabled = true;
  searchWrap.appendChild(searchInput);
  toolbar.appendChild(searchWrap);

  layout.appendChild(toolbar);

  // ── 分类栏 ──
  const categories = document.createElement('div');
  categories.className = 'inventory-categories';

  const catItems = ['ALL', 'PARTS', 'SUBSYSTEMS', 'MATERIALS', 'WEAPONS', 'AMMO'];
  catItems.forEach(cat => {
    const item = document.createElement('div');
    item.className = `inventory-cat-item${cat === 'ALL' ? ' inventory-cat-active' : ''}`;
    item.textContent = cat;
    categories.appendChild(item);
  });

  layout.appendChild(categories);

  // ── 容量信息 ──
  const capacity = document.createElement('div');
  capacity.className = 'inventory-capacity';

  const capLabel = document.createElement('span');
  capLabel.className = 'capacity-label';
  capLabel.textContent = 'CAPACITY';
  capacity.appendChild(capLabel);

  const capBar = document.createElement('div');
  capBar.className = 'capacity-bar-bg';

  const capFill = document.createElement('div');
  capFill.className = 'capacity-bar-fill';
  capFill.style.width = '0%';
  capBar.appendChild(capFill);
  capacity.appendChild(capBar);

  const capText = document.createElement('span');
  capText.className = 'capacity-text';
  capText.textContent = '0 / 2,000 ITEMS';
  capacity.appendChild(capText);

  layout.appendChild(capacity);

  // ── 空状态 ──
  const emptyState = document.createElement('div');
  emptyState.className = 'inventory-empty';

  const emptyLine1 = document.createElement('div');
  emptyLine1.className = 'inventory-empty-line';
  emptyLine1.textContent = 'NO DATA';
  emptyState.appendChild(emptyLine1);

  const emptyLine2 = document.createElement('div');
  emptyLine2.className = 'inventory-empty-sub';
  emptyLine2.textContent = 'INVENTORY SYSTEM OFFLINE';
  emptyState.appendChild(emptyLine2);

  layout.appendChild(emptyState);
  container.appendChild(layout);
}
