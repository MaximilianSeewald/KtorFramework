const CARD_VERSION = '1.1.3';
const TOKEN_STORAGE_KEY = 'ktor-lovelace-token';
const KTOR_INGRESS_BASE_URL = '__KTOR_INGRESS_BASE_URL__';

const cardStyles = `
  <style>
    :host {
      color: var(--primary-text-color);
      display: block;
    }

    ha-card {
      overflow: hidden;
    }

    .card {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 16px;
    }

    .header,
    .add-row,
    .item-row,
    .actions {
      align-items: center;
      display: flex;
      gap: 8px;
    }

    .header {
      align-items: flex-start;
      justify-content: space-between;
    }

    .title {
      font-size: 18px;
      font-weight: 600;
      line-height: 1.25;
      margin: 0;
    }

    .meta {
      color: var(--secondary-text-color);
      font-size: 13px;
      line-height: 1.35;
      margin-top: 3px;
    }

    .icon-button {
      align-items: center;
      background: transparent;
      border: 0;
      border-radius: 50%;
      color: var(--secondary-text-color);
      cursor: pointer;
      display: inline-flex;
      flex: 0 0 auto;
      height: 36px;
      justify-content: center;
      padding: 0;
      width: 36px;
    }

    .icon-button:hover,
    .icon-button:focus-visible {
      background: var(--secondary-background-color);
      color: var(--primary-text-color);
      outline: none;
    }

    .icon-button.danger:hover,
    .icon-button.danger:focus-visible {
      color: var(--error-color);
    }

    svg {
      height: 20px;
      width: 20px;
    }

    .add-row {
      background: var(--secondary-background-color);
      border-radius: 8px;
      padding: 8px;
    }

    .list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .item-row {
      background: var(--secondary-background-color);
      border-radius: 8px;
      min-height: 46px;
      padding: 8px;
    }

    .item-fields {
      display: block;
      flex: 1 1 auto;
      min-width: 0;
    }

    input[type="text"] {
      background: var(--card-background-color);
      border: 1px solid var(--divider-color);
      border-radius: 6px;
      box-sizing: border-box;
      color: var(--primary-text-color);
      font: inherit;
      min-height: 34px;
      min-width: 0;
      padding: 6px 8px;
      width: 100%;
    }

    input[type="text"]:focus {
      border-color: var(--primary-color);
      outline: none;
    }

    .shopping-check {
      accent-color: var(--primary-color);
      flex: 0 0 auto;
      height: 18px;
      width: 18px;
    }

    .retrieved input[type="text"] {
      opacity: 0.65;
      text-decoration: line-through;
    }

    .empty,
    .error {
      border: 1px dashed var(--divider-color);
      border-radius: 8px;
      color: var(--secondary-text-color);
      font-size: 14px;
      padding: 14px;
      text-align: center;
    }

    .error {
      color: var(--error-color);
    }

    @media (max-width: 420px) {
      .add-row {
        align-items: stretch;
        flex-direction: column;
      }

      .add-row .icon-button {
        align-self: flex-end;
      }
    }
  </style>
`;

function resolveIngressUrl(path) {
  return new URL(path.replace(/^\/+/, ''), resolveKtorBaseUrl()).toString();
}

function resolveIngressWebSocketUrl(path) {
  const url = new URL(path.replace(/^\/+/, ''), resolveKtorBaseUrl());
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  return url.toString();
}

function resolveKtorBaseUrl() {
  if (!KTOR_INGRESS_BASE_URL.startsWith('__')) {
    return KTOR_INGRESS_BASE_URL;
  }

  return new URL('./', import.meta.url).toString();
}

async function requestKtorToken() {
  const cached = sessionStorage.getItem(TOKEN_STORAGE_KEY);
  if (cached) {
    return cached;
  }

  const response = await fetch(resolveIngressUrl('api/ha/session'), {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`Could not start Ktor session (${response.status})`);
  }

  const body = await response.json();
  sessionStorage.setItem(TOKEN_STORAGE_KEY, body.token);
  return body.token;
}

async function ktorRequest(path, options = {}) {
  const token = await requestKtorToken();
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: 'application/json',
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...options.headers,
  };

  const response = await fetch(resolveIngressUrl(path), {
    credentials: 'include',
    ...options,
    headers,
  });

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    }
    throw new Error(`Request failed (${response.status})`);
  }

  return response;
}

async function fetchShoppingList() {
  const response = await ktorRequest('api/shoppingList');
  return response.json();
}

async function saveShoppingItem(item) {
  await ktorRequest('api/shoppingList', {
    body: JSON.stringify(item),
    method: 'PUT',
  });
}

async function addShoppingItem(item) {
  await ktorRequest('api/shoppingList', {
    body: JSON.stringify(item),
    method: 'POST',
  });
}

async function deleteShoppingItem(id) {
  await ktorRequest(`api/shoppingList?id=${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function createId() {
  if (crypto.randomUUID) {
    return crypto.randomUUID();
  }

  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.random() * 16 | 0;
    const value = char === 'x' ? random : (random & 0x3 | 0x8);
    return value.toString(16);
  });
}

function icon(path) {
  return `<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="${path}"></path></svg>`;
}

const icons = {
  add: icon('M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2Z'),
  refresh: icon('M17.7 6.3A7.95 7.95 0 0 0 12 4a8 8 0 1 0 7.47 10.86 1 1 0 1 0-1.87-.72A6 6 0 1 1 16.24 7.76L14 10h6V4l-2.3 2.3Z'),
  trash: icon('M9 3h6l1 2h4v2H4V5h4l1-2Zm1 6h2v9h-2V9Zm4 0h2v9h-2V9ZM7 9h2v10h6V9h2v10a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V9Z'),
};

class KtorShoppingListCard extends HTMLElement {
  config = {};
  data = [];
  error = '';
  loading = true;
  socket = undefined;
  addName = '';

  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
  }

  static getConfigForm() {
    return {
      schema: [
        { name: 'title', selector: { text: {} } },
        { name: 'max_items', selector: { number: { min: 1, max: 50, mode: 'box' } } },
        { name: 'show_completed', selector: { boolean: {} } },
      ],
    };
  }

  static getStubConfig() {
    return {
      title: 'Shopping List',
      max_items: 12,
      show_completed: true,
    };
  }

  connectedCallback() {
    this.load();
  }

  disconnectedCallback() {
    this.socket?.close();
  }

  setConfig(config) {
    this.config = {
      max_items: 12,
      show_completed: true,
      ...config,
    };
    this.render();
    this.load();
  }

  getCardSize() {
    return Math.min(Math.max(this.data.length + 2, 3), 8);
  }

  getGridOptions() {
    return {
      columns: 6,
      min_columns: 3,
      rows: Math.min(Math.max(this.data.length + 2, 3), 8),
      min_rows: 3,
    };
  }

  async load() {
    try {
      this.error = '';
      this.loading = this.data.length === 0;
      this.render();
      this.data = this.sortItems(await fetchShoppingList());
      await this.connectWebSocket();
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'Could not load shopping list';
    } finally {
      this.loading = false;
      this.render();
    }
  }

  async connectWebSocket() {
    if (this.socket && this.socket.readyState !== WebSocket.CLOSED) {
      return;
    }

    const token = await requestKtorToken();
    this.socket = new WebSocket(resolveIngressWebSocketUrl(`api/shoppingListWS?token=${encodeURIComponent(token)}`));
    this.socket.onmessage = (event) => {
      this.data = this.sortItems(JSON.parse(event.data));
      this.error = '';
      this.loading = false;
      this.render();
    };
    this.socket.onerror = () => {
      this.error = 'Live updates are unavailable. Use refresh to retry.';
      this.render();
    };
    this.socket.onclose = () => {
      this.socket = undefined;
    };
  }

  sortItems(items) {
    return [...items].sort((a, b) => Number(a.retrieved) - Number(b.retrieved));
  }

  visibleItems() {
    const showCompleted = this.config.show_completed !== false;
    return this.data
      .filter((item) => showCompleted || !item.retrieved)
      .slice(0, this.maxItems());
  }

  maxItems() {
    const value = Number(this.config.max_items ?? 12);
    return Number.isFinite(value) && value > 0 ? value : 12;
  }

  title() {
    return escapeHtml(this.config.title || 'Shopping List');
  }

  async addItem() {
    const name = this.addName.trim();
    if (!name) {
      return;
    }

    const item = {
      amount: '',
      id: createId(),
      name,
      retrieved: false,
    };
    const previous = this.data;
    this.data = this.sortItems([...this.data, item]);
    this.addName = '';
    this.render();

    try {
      await addShoppingItem(item);
    } catch (error) {
      this.data = previous;
      this.error = error instanceof Error ? error.message : 'Could not add item';
      this.render();
    }
  }

  async toggleItem(id) {
    const item = this.data.find((entry) => entry.id === id);
    if (!item) {
      return;
    }

    await this.updateItem({
      ...item,
      retrieved: !item.retrieved,
    });
  }

  async editItem(id, field, value) {
    const item = this.data.find((entry) => entry.id === id);
    if (!item || item[field] === value) {
      return;
    }

    await this.updateItem({
      ...item,
      [field]: value,
    });
  }

  async updateItem(item) {
    const previous = this.data;
    this.data = this.sortItems(this.data.map((entry) => entry.id === item.id ? item : entry));
    this.error = '';
    this.render();

    try {
      await saveShoppingItem(item);
    } catch (error) {
      this.data = previous;
      this.error = error instanceof Error ? error.message : 'Could not update item';
      this.render();
    }
  }

  async removeItem(id) {
    const previous = this.data;
    this.data = this.data.filter((entry) => entry.id !== id);
    this.error = '';
    this.render();

    try {
      await deleteShoppingItem(id);
    } catch (error) {
      this.data = previous;
      this.error = error instanceof Error ? error.message : 'Could not remove item';
      this.render();
    }
  }

  render() {
    const openCount = this.data.filter((item) => !item.retrieved).length;
    const items = this.visibleItems();

    this.shadowRoot.innerHTML = `
      ${cardStyles}
      <ha-card>
        <div class="card">
          <div class="header">
            <div>
              <h2 class="title">${this.title()}</h2>
              <div class="meta">${openCount} open${this.data.length ? ` - ${this.data.length} total` : ''}</div>
            </div>
            <button class="icon-button" type="button" title="Refresh" aria-label="Refresh" data-action="refresh">${icons.refresh}</button>
          </div>

          <form class="add-row">
            <input type="text" name="name" placeholder="Item" autocomplete="off" value="${escapeHtml(this.addName)}">
            <button class="icon-button" type="submit" title="Add item" aria-label="Add item">${icons.add}</button>
          </form>

          ${this.renderBody(items)}
        </div>
      </ha-card>
    `;

    this.bindEvents();
  }

  renderBody(items) {
    if (this.loading) {
      return '<div class="empty">Loading shopping list...</div>';
    }

    if (this.error) {
      return `<div class="error">${escapeHtml(this.error)}</div>`;
    }

    if (items.length === 0) {
      return '<div class="empty">No shopping items.</div>';
    }

    return `
      <div class="list">
        ${items.map((item) => `
          <div class="item-row ${item.retrieved ? 'retrieved' : ''}" data-id="${escapeHtml(item.id)}">
            <input class="shopping-check" type="checkbox" aria-label="Toggle ${escapeHtml(item.name)}" ${item.retrieved ? 'checked' : ''}>
            <div class="item-fields">
              <input type="text" data-field="name" aria-label="Item name" value="${escapeHtml(item.name)}">
            </div>
            <div class="actions">
              <button class="icon-button danger" type="button" title="Remove item" aria-label="Remove item" data-action="delete">${icons.trash}</button>
            </div>
          </div>
        `).join('')}
      </div>
    `;
  }

  bindEvents() {
    this.shadowRoot.querySelector('[data-action="refresh"]')?.addEventListener('click', () => this.load());

    const addForm = this.shadowRoot.querySelector('.add-row');
    addForm?.addEventListener('input', (event) => {
      const target = event.target;
      if (target.name === 'name') {
        this.addName = target.value;
      }
    });
    addForm?.addEventListener('submit', (event) => {
      event.preventDefault();
      this.addItem();
    });

    this.shadowRoot.querySelectorAll('.item-row').forEach((row) => {
      const id = row.dataset.id;
      row.querySelector('.shopping-check')?.addEventListener('change', () => this.toggleItem(id));
      row.querySelector('[data-action="delete"]')?.addEventListener('click', () => this.removeItem(id));
      row.querySelectorAll('[data-field]').forEach((input) => {
        input.addEventListener('change', () => this.editItem(id, input.dataset.field, input.value.trim()));
        input.addEventListener('keydown', (event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            input.blur();
          }
        });
      });
    });
  }
}

if (!customElements.get('ktor-shopping-list-card')) {
  customElements.define('ktor-shopping-list-card', KtorShoppingListCard);
}

window.customCards = (window.customCards || [])
  .filter((card) => card.type !== 'ktor-shopping-list-card' && card.type !== 'ktor-recipe-list-card');
window.customCards.push({
  type: 'ktor-shopping-list-card',
  name: 'Ktor Shopping List',
  preview: false,
  description: 'Native Lovelace card for the Ktor App shopping list.',
});

console.info(`Ktor Lovelace cards ${CARD_VERSION} loaded`);
