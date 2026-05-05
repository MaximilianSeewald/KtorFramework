const CARD_VERSION = '1.0.0';
const TOKEN_STORAGE_KEY = 'ktor-lovelace-token';
const DEFAULT_REFRESH_INTERVAL = 30000;

const cardStyles = `
  <style>
    :host {
      display: block;
      color: var(--primary-text-color);
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

    .header {
      align-items: flex-start;
      display: flex;
      gap: 12px;
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

    .refresh {
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

    .refresh:hover {
      background: var(--secondary-background-color);
      color: var(--primary-text-color);
    }

    .refresh svg {
      height: 20px;
      width: 20px;
    }

    .list {
      display: flex;
      flex-direction: column;
      gap: 8px;
      margin: 0;
      padding: 0;
    }

    .row {
      align-items: center;
      background: var(--secondary-background-color);
      border-radius: 8px;
      display: flex;
      gap: 10px;
      min-height: 42px;
      padding: 8px 10px;
    }

    .row-main {
      flex: 1 1 auto;
      min-width: 0;
    }

    .row-title {
      font-size: 14px;
      font-weight: 500;
      line-height: 1.25;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .row-detail {
      color: var(--secondary-text-color);
      font-size: 12px;
      line-height: 1.35;
      margin-top: 2px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
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

    .shopping-check {
      accent-color: var(--primary-color);
      flex: 0 0 auto;
      height: 18px;
      width: 18px;
    }

    .retrieved .row-title,
    .retrieved .row-detail {
      opacity: 0.62;
      text-decoration: line-through;
    }

    .count-pill {
      background: var(--primary-color);
      border-radius: 999px;
      color: var(--text-primary-color);
      flex: 0 0 auto;
      font-size: 12px;
      font-weight: 600;
      min-width: 24px;
      padding: 4px 8px;
      text-align: center;
    }
  </style>
`;

function resolveIngressUrl(path) {
  return new URL(path.replace(/^\/+/, ''), import.meta.url).toString();
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

async function fetchKtorJson(path) {
  const token = await requestKtorToken();
  const response = await fetch(resolveIngressUrl(path), {
    credentials: 'include',
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    }
    throw new Error(`Request failed (${response.status})`);
  }

  return response.json();
}

async function sendKtorJson(path, method, body) {
  const token = await requestKtorToken();
  const response = await fetch(resolveIngressUrl(path), {
    body: JSON.stringify(body),
    credentials: 'include',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    method,
  });

  if (!response.ok) {
    throw new Error(`Request failed (${response.status})`);
  }
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function refreshIcon() {
  return `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path fill="currentColor" d="M17.7 6.3A7.95 7.95 0 0 0 12 4a8 8 0 1 0 7.47 10.86 1 1 0 1 0-1.87-.72A6 6 0 1 1 16.24 7.76L14 10h6V4l-2.3 2.3Z"></path>
    </svg>
  `;
}

class KtorBaseCard extends HTMLElement {
  config = {};
  data = [];
  error = '';
  loading = true;
  refreshTimer = undefined;

  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
  }

  connectedCallback() {
    this.load();
  }

  disconnectedCallback() {
    window.clearTimeout(this.refreshTimer);
  }

  setConfig(config) {
    this.config = {
      refresh_interval: DEFAULT_REFRESH_INTERVAL,
      ...config,
    };
    this.render();
    this.load();
  }

  getCardSize() {
    return Math.min(Math.max(this.data.length + 1, 2), 6);
  }

  getGridOptions() {
    return {
      columns: 6,
      min_columns: 3,
      rows: Math.min(Math.max(this.data.length + 1, 2), 6),
      min_rows: 2,
    };
  }

  scheduleRefresh() {
    window.clearTimeout(this.refreshTimer);
    const interval = Number(this.config.refresh_interval ?? DEFAULT_REFRESH_INTERVAL);
    if (interval > 0) {
      this.refreshTimer = window.setTimeout(() => this.load(), interval);
    }
  }

  async load() {
    window.clearTimeout(this.refreshTimer);
    try {
      this.error = '';
      this.loading = this.data.length === 0;
      this.render();
      this.data = await fetchKtorJson(this.endpoint);
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'Could not load data';
    } finally {
      this.loading = false;
      this.render();
      this.scheduleRefresh();
    }
  }

  title(defaultTitle) {
    return escapeHtml(this.config.title || defaultTitle);
  }

  maxItems(defaultValue) {
    const value = Number(this.config.max_items ?? this.config.max_recipes ?? defaultValue);
    return Number.isFinite(value) && value > 0 ? value : defaultValue;
  }
}

class KtorShoppingListCard extends KtorBaseCard {
  endpoint = 'api/shoppingList';

  static getConfigForm() {
    return {
      schema: [
        { name: 'title', selector: { text: {} } },
        { name: 'max_items', selector: { number: { min: 1, max: 20, mode: 'box' } } },
        { name: 'show_completed', selector: { boolean: {} } },
        { name: 'refresh_interval', selector: { number: { min: 0, max: 300000, mode: 'box' } } },
      ],
    };
  }

  static getStubConfig() {
    return {
      title: 'Shopping List',
      max_items: 8,
      show_completed: true,
    };
  }

  setConfig(config) {
    super.setConfig({
      max_items: 8,
      show_completed: true,
      ...config,
    });
  }

  async toggleItem(item) {
    const nextItem = {
      ...item,
      retrieved: !item.retrieved,
    };
    this.data = this.data.map((entry) => entry.id === item.id ? nextItem : entry);
    this.render();

    try {
      await sendKtorJson('api/shoppingList', 'PUT', nextItem);
      await this.load();
    } catch (error) {
      this.error = error instanceof Error ? error.message : 'Could not update item';
      await this.load();
    }
  }

  render() {
    const showCompleted = this.config.show_completed !== false;
    const visible = this.data
      .filter((item) => showCompleted || !item.retrieved)
      .sort((a, b) => Number(a.retrieved) - Number(b.retrieved))
      .slice(0, this.maxItems(8));
    const openCount = this.data.filter((item) => !item.retrieved).length;

    this.shadowRoot.innerHTML = `
      ${cardStyles}
      <ha-card>
        <div class="card">
          <div class="header">
            <div>
              <h2 class="title">${this.title('Shopping List')}</h2>
              <div class="meta">${openCount} open${this.data.length ? ` · ${this.data.length} total` : ''}</div>
            </div>
            <button class="refresh" type="button" title="Refresh" aria-label="Refresh">${refreshIcon()}</button>
          </div>
          ${this.renderBody(visible)}
        </div>
      </ha-card>
    `;

    this.shadowRoot.querySelector('.refresh')?.addEventListener('click', () => this.load());
    this.shadowRoot.querySelectorAll('.shopping-check').forEach((input) => {
      input.addEventListener('change', () => {
        const item = this.data.find((entry) => entry.id === input.dataset.id);
        if (item) {
          this.toggleItem(item);
        }
      });
    });
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
          <label class="row ${item.retrieved ? 'retrieved' : ''}">
            <input class="shopping-check" type="checkbox" data-id="${escapeHtml(item.id)}" ${item.retrieved ? 'checked' : ''}>
            <span class="row-main">
              <span class="row-title">${escapeHtml(item.name)}</span>
              ${item.amount ? `<span class="row-detail">${escapeHtml(item.amount)}</span>` : ''}
            </span>
          </label>
        `).join('')}
      </div>
    `;
  }
}

class KtorRecipeListCard extends KtorBaseCard {
  endpoint = 'api/recipe';

  static getConfigForm() {
    return {
      schema: [
        { name: 'title', selector: { text: {} } },
        { name: 'max_recipes', selector: { number: { min: 1, max: 20, mode: 'box' } } },
        { name: 'show_ingredients', selector: { boolean: {} } },
        { name: 'refresh_interval', selector: { number: { min: 0, max: 300000, mode: 'box' } } },
      ],
    };
  }

  static getStubConfig() {
    return {
      title: 'Recipes',
      max_recipes: 6,
      show_ingredients: true,
    };
  }

  setConfig(config) {
    super.setConfig({
      max_recipes: 6,
      show_ingredients: true,
      ...config,
    });
  }

  render() {
    const recipes = [...this.data]
      .sort((a, b) => String(a.name).localeCompare(String(b.name)))
      .slice(0, this.maxItems(6));
    const ingredientCount = this.data.reduce((sum, recipe) => sum + (recipe.items?.length ?? 0), 0);

    this.shadowRoot.innerHTML = `
      ${cardStyles}
      <ha-card>
        <div class="card">
          <div class="header">
            <div>
              <h2 class="title">${this.title('Recipes')}</h2>
              <div class="meta">${this.data.length} recipes${ingredientCount ? ` · ${ingredientCount} ingredients` : ''}</div>
            </div>
            <button class="refresh" type="button" title="Refresh" aria-label="Refresh">${refreshIcon()}</button>
          </div>
          ${this.renderBody(recipes)}
        </div>
      </ha-card>
    `;

    this.shadowRoot.querySelector('.refresh')?.addEventListener('click', () => this.load());
  }

  renderBody(recipes) {
    if (this.loading) {
      return '<div class="empty">Loading recipes...</div>';
    }
    if (this.error) {
      return `<div class="error">${escapeHtml(this.error)}</div>`;
    }
    if (recipes.length === 0) {
      return '<div class="empty">No recipes yet.</div>';
    }

    const showIngredients = this.config.show_ingredients !== false;

    return `
      <div class="list">
        ${recipes.map((recipe) => {
          const items = recipe.items ?? [];
          const preview = items.slice(0, 3).map((item) => `${item.name}${item.value ? ` ${item.value}` : ''}`).join(', ');
          return `
            <div class="row">
              <div class="row-main">
                <div class="row-title">${escapeHtml(recipe.name)}</div>
                ${showIngredients && preview ? `<div class="row-detail">${escapeHtml(preview)}</div>` : ''}
              </div>
              <div class="count-pill">${items.length}</div>
            </div>
          `;
        }).join('')}
      </div>
    `;
  }
}

customElements.define('ktor-shopping-list-card', KtorShoppingListCard);
customElements.define('ktor-recipe-list-card', KtorRecipeListCard);

window.customCards = window.customCards || [];
window.customCards.push(
  {
    type: 'ktor-shopping-list-card',
    name: 'Ktor Shopping List',
    preview: true,
    description: 'Native Lovelace card for the Ktor App shopping list.',
  },
  {
    type: 'ktor-recipe-list-card',
    name: 'Ktor Recipes',
    preview: true,
    description: 'Native Lovelace card for the Ktor App recipe list.',
  },
);

console.info(`Ktor Lovelace cards ${CARD_VERSION} loaded`);
