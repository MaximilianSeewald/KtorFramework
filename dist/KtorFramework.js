const CARD_VERSION = '1.1.15';
const TOKEN_STORAGE_KEY = 'ktor-shopping-list-token';
const DEFAULT_ADDON_SLUG = 'ktor_app';
const REFRESH_INTERVAL_MS = 5000;
const RECONNECT_DELAY_MS = 1000;

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
      box-sizing: border-box;
      flex-wrap: nowrap;
      padding: 8px;
      width: 100%;
    }

    .add-row input[type="text"] {
      flex: 1 1 auto;
    }

    .list {
      display: flex;
      flex-direction: column;
      gap: 8px;
      max-height: min(50vh, 420px);
      overflow-y: auto;
      padding-right: 0;
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

  </style>
`;

function normalizeBaseUrl(url) {
  const normalized = new URL(String(url || '').replace(/\/?$/, '/'), window.location.origin);
  if (!['http:', 'https:'].includes(normalized.protocol)) {
    throw new Error('Backend URL must use http or https');
  }
  if (normalized.username || normalized.password) {
    throw new Error('Backend URL must not include credentials');
  }
  if (normalized.origin !== window.location.origin) {
    throw new Error('Backend URL must be on the same origin as Home Assistant');
  }
  return normalized.toString();
}

function normalizeIngressEntry(entry) {
  const normalizedEntry = String(entry || '').trim().replace(/^\/+|\/+$/g, '');
  return normalizedEntry ? `/api/hassio_ingress/${normalizedEntry}/` : '';
}

function inferIngressBaseUrl() {
  const paths = [
    window.location.pathname,
    window.location.href,
    document.referrer,
  ];
  const ingressPattern = /\/api\/hassio_ingress\/[^/?#]+\/?/;
  const matchingPath = paths.find((path) => ingressPattern.test(path));
  const match = matchingPath?.match(ingressPattern)?.[0];
  return match ? normalizeBaseUrl(match) : '';
}

function unwrapSupervisorResponse(response) {
  const unwrapped = response?.body ?? response?.data ?? response?.result ?? response;
  return unwrapped?.data ?? unwrapped?.result ?? unwrapped;
}

async function callSupervisorApi(hass, endpoint, method = 'get') {
  if (!hass?.callWS) {
    throw new Error('Home Assistant websocket API is not available');
  }

  const request = {
    type: 'supervisor/api',
    endpoint,
    method,
  };

  return unwrapSupervisorResponse(await hass.callWS(request));
}

function ingressCookiePath(baseUrl) {
  const pathname = new URL(baseUrl, window.location.origin).pathname;
  const match = pathname.match(/^(.*\/api\/hassio_ingress\/)/);
  return match?.[1] || '/api/hassio_ingress/';
}

async function refreshIngressSession(card) {
  if (!card?._hass?.callWS) {
    return;
  }

  const response = await callSupervisorApi(card._hass, '/ingress/session', 'post');
  const session = response?.session;
  if (!session) {
    return;
  }

  const cookie = [
    `ingress_session=${encodeURIComponent(session)}`,
    `path=${ingressCookiePath(await card.resolveBackendBaseUrl())}`,
    'SameSite=Strict',
    window.location.protocol === 'https:' ? 'Secure' : '',
  ].filter(Boolean).join('; ');
  document.cookie = cookie;
}

function findAddonSlug(addons, configuredSlug) {
  const normalizedSlug = String(configuredSlug || '').trim();
  const installedAddons = Array.isArray(addons) ? addons : [];
  return installedAddons.find((addon) => addon?.slug === normalizedSlug)?.slug
    || installedAddons.find((addon) => String(addon?.slug || '').endsWith(`_${normalizedSlug}`))?.slug
    || normalizedSlug;
}

async function resolveAddonIngressBaseUrl(hass, addonSlug) {
  const normalizedSlug = String(addonSlug || DEFAULT_ADDON_SLUG).trim();
  const inferredIngressBaseUrl = inferIngressBaseUrl();
  if (inferredIngressBaseUrl) {
    return inferredIngressBaseUrl;
  }

  let resolvedSlug = normalizedSlug;
  try {
    const addonsResponse = await callSupervisorApi(hass, '/addons');
    resolvedSlug = findAddonSlug(addonsResponse?.addons, normalizedSlug);
  } catch (error) {
    // Older/mobile frontends may block this call; fall back to the configured slug.
  }

  const endpoints = [
    `/addons/${resolvedSlug}/info`,
    `addons/${resolvedSlug}/info`,
    `/addons/${normalizedSlug}/info`,
    `addons/${normalizedSlug}/info`,
  ];
  let lastError;

  for (const endpoint of endpoints) {
    try {
      const addonInfo = await callSupervisorApi(hass, endpoint);
      if (addonInfo?.ingress_entry) {
        return normalizeBaseUrl(normalizeIngressEntry(addonInfo.ingress_entry));
      }
      if (addonInfo?.ingress_url) {
        return normalizeBaseUrl(addonInfo.ingress_url);
      }
    } catch (error) {
      lastError = error;
    }
  }

  throw new Error(
    `Could not resolve ingress URL for add-on "${normalizedSlug}"${resolvedSlug !== normalizedSlug ? ` (${resolvedSlug})` : ''}${lastError instanceof Error ? `: ${lastError.message}` : ''}`
  );
}

async function requestKtorToken(card) {
  const cached = sessionStorage.getItem(card.tokenStorageKey());
  if (cached && card.backendUrlConfig()) {
    return cached;
  }

  try {
    await refreshIngressSession(card);
  } catch (error) {
    // If Supervisor is unavailable, continue with the browser's existing ingress session.
  }

  const response = await fetch(await card.resolveBackendUrl('api/ha/session'), {
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error(`Could not start Ktor session (${response.status})`);
  }

  const body = await response.json();
  sessionStorage.setItem(card.tokenStorageKey(), body.token);
  return body.token;
}

async function ktorRequest(card, path, options = {}) {
  const token = await requestKtorToken(card);
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: 'application/json',
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...options.headers,
  };

  const response = await fetch(await card.resolveBackendUrl(path), {
    credentials: 'include',
    ...options,
    headers,
  });

  if (!response.ok) {
    if (response.status === 401 || response.status === 403) {
      sessionStorage.removeItem(card.tokenStorageKey());
    }
    throw new Error(`Request failed (${response.status})`);
  }

  return response;
}

async function fetchShoppingList(card) {
  const response = await ktorRequest(card, 'api/shoppingList');
  return normalizeShoppingItems(await response.json());
}

async function saveShoppingItem(card, item) {
  await ktorRequest(card, 'api/shoppingList', {
    body: JSON.stringify(item),
    method: 'PUT',
  });
}

async function addShoppingItem(card, item) {
  await ktorRequest(card, 'api/shoppingList', {
    body: JSON.stringify(item),
    method: 'POST',
  });
}

async function deleteShoppingItem(card, id) {
  await ktorRequest(card, `api/shoppingList?id=${encodeURIComponent(id)}`, {
    method: 'DELETE',
  });
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

const iconPaths = {
  add: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2Z',
  refresh: 'M17.7 6.3A7.95 7.95 0 0 0 12 4a8 8 0 1 0 7.47 10.86 1 1 0 1 0-1.87-.72A6 6 0 1 1 16.24 7.76L14 10h6V4l-2.3 2.3Z',
  trash: 'M9 3h6l1 2h4v2H4V5h4l1-2Zm1 6h2v9h-2V9Zm4 0h2v9h-2V9ZM7 9h2v10h6V9h2v10a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V9Z',
};

function safeText(value, maxLength = 255) {
  return String(value ?? '').slice(0, maxLength);
}

function safeBoolean(value) {
  return value === true;
}

function normalizeShoppingItem(item) {
  return {
    amount: safeText(item?.amount),
    id: safeText(item?.id, 128),
    name: safeText(item?.name),
    retrieved: safeBoolean(item?.retrieved),
  };
}

function normalizeShoppingItems(items) {
  return Array.isArray(items) ? items.map(normalizeShoppingItem) : [];
}

function parseShoppingItems(text) {
  try {
    return normalizeShoppingItems(JSON.parse(String(text || '[]')));
  } catch (error) {
    return [];
  }
}

function buildShoppingListWebSocketPath(token) {
  const params = new URLSearchParams();
  params.set('token', safeText(token, 4096));
  return `api/shoppingListWS?${params.toString()}`;
}

function appendChildren(parent, ...children) {
  children.forEach((child) => {
    if (child) {
      parent.appendChild(child);
    }
  });
  return parent;
}

function createElement(tagName, options = {}) {
  const element = document.createElement(tagName);
  if (options.className) {
    element.className = options.className;
  }
  if (options.text !== undefined) {
    element.textContent = String(options.text);
  }
  Object.entries(options.attributes || {}).forEach(([name, value]) => {
    if (value !== undefined && value !== null) {
      element.setAttribute(name, String(value));
    }
  });
  return element;
}

function createIcon(name) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('fill', 'currentColor');
  path.setAttribute('d', iconPaths[name]);
  svg.appendChild(path);
  return svg;
}

function createIconButton(iconName, label, action, extraClass = '') {
  const button = createElement('button', {
    className: `icon-button${extraClass ? ` ${extraClass}` : ''}`,
    attributes: {
      'aria-label': label,
      title: label,
      type: 'button',
      ...(action ? { 'data-action': action } : {}),
    },
  });
  button.appendChild(createIcon(iconName));
  return button;
}

function createStyleElement() {
  const style = document.createElement('style');
  style.textContent = cardStyles
    .replace(/^\s*<style>\s*/, '')
    .replace(/\s*<\/style>\s*$/, '');
  return style;
}

class KtorShoppingListCard extends HTMLElement {
  config = {};
  data = [];
  error = '';
  loading = true;
  socket = undefined;
  addName = '';
  _hass = undefined;
  backendBaseUrlPromise = undefined;
  reconnectTimer = undefined;
  refreshTimer = undefined;
  loadingPromise = undefined;
  disconnected = true;

  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
  }

  static getConfigForm() {
    return {
      schema: [
        { name: 'title', selector: { text: {} } },
        { name: 'addon_slug', selector: { text: {} } },
        { name: 'backend_url', selector: { text: {} } },
        { name: 'show_completed', selector: { boolean: {} } },
      ],
    };
  }

  static getStubConfig() {
    return {
      title: 'Shopping List',
      addon_slug: DEFAULT_ADDON_SLUG,
      show_completed: true,
    };
  }

  set hass(hass) {
    this._hass = hass;
    if (!this.backendBaseUrlPromise) {
      this.load();
    }
  }

  connectedCallback() {
    this.disconnected = false;
    this.startRefreshTimer();
    this.load();
  }

  disconnectedCallback() {
    this.disconnected = true;
    this.stopRefreshTimer();
    this.clearReconnectTimer();
    this.closeSocket();
  }

  setConfig(config) {
    this.config = {
      addon_slug: DEFAULT_ADDON_SLUG,
      show_completed: true,
      ...config,
    };
    this.clearReconnectTimer();
    this.closeSocket();
    this.backendBaseUrlPromise = undefined;
    sessionStorage.removeItem(this.tokenStorageKey());
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
    if (this.loadingPromise) {
      return this.loadingPromise;
    }

    this.loadingPromise = this.loadData();
    try {
      await this.loadingPromise;
    } finally {
      this.loadingPromise = undefined;
    }
  }

  async loadData() {
    if (!this.backendUrlConfig() && !this._hass) {
      this.loading = true;
      this.render();
      return;
    }

    try {
      this.error = '';
      this.loading = this.data.length === 0;
      this.render();
      this.setItems(await fetchShoppingList(this));
      await this.connectWebSocket();
    } catch (error) {
      this.backendBaseUrlPromise = undefined;
      this.error = error instanceof Error ? error.message : 'Could not load shopping list';
    } finally {
      this.loading = false;
      this.render();
    }
  }

  async connectWebSocket() {
    if (
      this.disconnected
      || (this.socket && [WebSocket.CONNECTING, WebSocket.OPEN].includes(this.socket.readyState))
    ) {
      return;
    }

    const webSocketUrl = await this.resolveShoppingListWebSocketUrl(await requestKtorToken(this));
    this.socket = new WebSocket(webSocketUrl);
    this.socket.onmessage = (event) => {
      this.setItems(parseShoppingItems(event.data));
      this.error = '';
      this.loading = false;
      this.render();
    };
    this.socket.onerror = () => {
      this.scheduleReconnect();
    };
    this.socket.onclose = () => {
      this.socket = undefined;
      this.scheduleReconnect();
    };
  }

  closeSocket() {
    if (this.socket && [WebSocket.CONNECTING, WebSocket.OPEN].includes(this.socket.readyState)) {
      this.socket.close();
    }
    this.socket = undefined;
  }

  clearReconnectTimer() {
    if (this.reconnectTimer) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = undefined;
    }
  }

  scheduleReconnect() {
    if (this.disconnected || this.reconnectTimer) {
      return;
    }

    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = undefined;
      this.connectWebSocket().catch(() => this.scheduleReconnect());
    }, RECONNECT_DELAY_MS);
  }

  startRefreshTimer() {
    if (this.refreshTimer) {
      return;
    }

    this.refreshTimer = window.setInterval(() => {
      if (!document.hidden) {
        this.load();
      }
    }, REFRESH_INTERVAL_MS);
  }

  stopRefreshTimer() {
    if (this.refreshTimer) {
      window.clearInterval(this.refreshTimer);
      this.refreshTimer = undefined;
    }
  }

  sortItems(items) {
    return normalizeShoppingItems(items).sort((a, b) => Number(a.retrieved) - Number(b.retrieved));
  }

  setItems(items) {
    this.data = this.sortItems(items);
  }

  visibleItems() {
    const showCompleted = this.config.show_completed !== false;
    return this.data
      .filter((item) => showCompleted || !item.retrieved);
  }

  title() {
    return String(this.config.title || 'Shopping List');
  }

  tokenStorageKey() {
    return `${TOKEN_STORAGE_KEY}:${this.backendUrlConfig() || this.addonSlug()}`;
  }

  addonSlug() {
    return String(this.config.addon_slug || DEFAULT_ADDON_SLUG).trim() || DEFAULT_ADDON_SLUG;
  }

  backendUrlConfig() {
    return String(this.config.backend_url || '').trim();
  }

  async resolveBackendBaseUrl() {
    if (!this.backendBaseUrlPromise) {
      this.backendBaseUrlPromise = this.backendUrlConfig()
        ? Promise.resolve(normalizeBaseUrl(this.backendUrlConfig()))
        : resolveAddonIngressBaseUrl(this._hass, this.addonSlug());
    }

    return this.backendBaseUrlPromise;
  }

  async resolveBackendUrl(path) {
    return new URL(String(path || '').replace(/^\/+/, ''), await this.resolveBackendBaseUrl()).toString();
  }

  async resolveShoppingListWebSocketUrl(token) {
    const baseUrl = new URL(await this.resolveBackendBaseUrl());
    if (!['http:', 'https:'].includes(baseUrl.protocol) || baseUrl.username || baseUrl.password) {
      throw new Error('Backend WebSocket URL must be derived from a safe http or https URL');
    }
    if (baseUrl.origin !== window.location.origin) {
      throw new Error('Backend WebSocket URL must stay on the Home Assistant origin');
    }
    const url = new URL(buildShoppingListWebSocketPath(token), baseUrl);
    url.protocol = baseUrl.protocol === 'https:' ? 'wss:' : 'ws:';
    return url.toString();
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
    this.setItems([...this.data, item]);
    this.addName = '';
    this.render();

    try {
      await addShoppingItem(this, item);
    } catch (error) {
      this.setItems(previous);
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
    this.setItems(this.data.map((entry) => entry.id === item.id ? item : entry));
    this.error = '';
    this.render();

    try {
      await saveShoppingItem(this, item);
    } catch (error) {
      this.setItems(previous);
      this.error = error instanceof Error ? error.message : 'Could not update item';
      this.render();
    }
  }

  async removeItem(id) {
    const previous = this.data;
    this.setItems(this.data.filter((entry) => entry.id !== id));
    this.error = '';
    this.render();

    try {
      await deleteShoppingItem(this, id);
    } catch (error) {
      this.setItems(previous);
      this.error = error instanceof Error ? error.message : 'Could not remove item';
      this.render();
    }
  }

  render() {
    const openCount = this.data.filter((item) => !item.retrieved).length;
    const items = this.visibleItems();
    this.shadowRoot.replaceChildren();
    const headerText = createElement('div');
    appendChildren(
      headerText,
      createElement('h2', { className: 'title', text: this.title() }),
      createElement('div', {
        className: 'meta',
        text: `${openCount} open${this.data.length ? ` - ${this.data.length} total` : ''}`,
      })
    );

    const header = createElement('div', { className: 'header' });
    appendChildren(header, headerText, createIconButton('refresh', 'Refresh', 'refresh'));

    const nameInput = createElement('input', {
      attributes: {
        autocomplete: 'off',
        name: 'name',
        placeholder: 'Item',
        type: 'text',
      },
    });
    nameInput.value = this.addName;

    const addButton = createIconButton('add', 'Add item');
    addButton.type = 'submit';

    const addForm = createElement('form', { className: 'add-row' });
    appendChildren(addForm, nameInput, addButton);

    const cardBody = createElement('div', { className: 'card' });
    appendChildren(cardBody, header, addForm, this.renderBody(items));

    const haCard = createElement('ha-card');
    haCard.appendChild(cardBody);
    appendChildren(this.shadowRoot, createStyleElement(), haCard);
    this.bindEvents();
  }

  renderBody(items) {
    if (this.loading) {
      return createElement('div', { className: 'empty', text: 'Loading shopping list...' });
    }

    if (this.error) {
      return createElement('div', { className: 'error', text: this.error });
    }

    if (items.length === 0) {
      return createElement('div', { className: 'empty', text: 'No shopping items.' });
    }

    const list = createElement('div', { className: 'list' });
    items.forEach((item) => {
      const row = createElement('div', {
        className: `item-row${item.retrieved ? ' retrieved' : ''}`,
      });
      row.dataset.id = String(item.id ?? '');

      const checkbox = createElement('input', {
        className: 'shopping-check',
        attributes: {
          'aria-label': 'Toggle item',
          type: 'checkbox',
        },
      });
      checkbox.checked = Boolean(item.retrieved);

      const nameInput = createElement('input', {
        attributes: {
          'aria-label': 'Item name',
          'data-field': 'name',
          type: 'text',
        },
      });
      nameInput.value = String(item.name ?? '');

      const fields = createElement('div', { className: 'item-fields' });
      fields.appendChild(nameInput);

      const actions = createElement('div', { className: 'actions' });
      actions.appendChild(createIconButton('trash', 'Remove item', 'delete', 'danger'));

      appendChildren(row, checkbox, fields, actions);
      list.appendChild(row);
    });
    return list;
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

const customCardTypes = new Set(['ktor-shopping-list-card', 'custom:ktor-shopping-list-card', 'ktor-recipe-list-card']);
const customCardMetadata = {
  type: 'ktor-shopping-list-card',
  name: `Ktor Shopping List ${CARD_VERSION}`,
  preview: true,
  description: `Native Lovelace card for the Ktor App shopping list. Version ${CARD_VERSION}.`,
  documentationURL: 'https://github.com/Loudless/KtorFramework',
};

window.customCards = window.customCards || [];
const existingCardIndex = window.customCards.findIndex((card) => customCardTypes.has(card.type));
if (existingCardIndex >= 0) {
  window.customCards.splice(existingCardIndex, 1, customCardMetadata);
  for (let index = window.customCards.length - 1; index > existingCardIndex; index -= 1) {
    if (customCardTypes.has(window.customCards[index]?.type)) {
      window.customCards.splice(index, 1);
    }
  }
} else {
  window.customCards.push(customCardMetadata);
}

window.ktorLovelaceCards = {
  loaded: true,
  types: ['ktor-shopping-list-card'],
  version: CARD_VERSION,
};

console.info(`Ktor Lovelace cards ${CARD_VERSION} loaded`, window.ktorLovelaceCards);
