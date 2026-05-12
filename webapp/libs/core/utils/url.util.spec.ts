import { resolveApiUrl, resolveWebSocketUrl } from './url.util';

describe('url util', () => {
  const originalBaseHref = document.querySelector('base')?.getAttribute('href') ?? null;
  const originalUrl = window.location.href;

  afterEach(() => {
    if (originalBaseHref === null) {
      document.querySelector('base')?.remove();
    } else {
      let base = document.querySelector('base');
      if (!base) {
        base = document.createElement('base');
        document.head.appendChild(base);
      }
      base.setAttribute('href', originalBaseHref);
    }
    history.pushState(null, '', originalUrl);
  });

  it('resolves relative api urls against Home Assistant ingress path', () => {
    history.pushState(null, '', '/api/hassio_ingress/test-token/#/shoppingList');

    expect(resolveApiUrl('api')).toBe(`${window.location.origin}/api/hassio_ingress/test-token/api`);
  });

  it('resolves websocket urls against Home Assistant ingress path', () => {
    history.pushState(null, '', '/api/hassio_ingress/test-token/#/shoppingList');

    expect(resolveWebSocketUrl('api', 'shoppingListWS?token=abc')).toBe(
      `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/api/hassio_ingress/test-token/api/shoppingListWS?token=abc`
    );
  });
});
