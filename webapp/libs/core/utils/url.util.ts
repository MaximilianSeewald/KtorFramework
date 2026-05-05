export function resolveApiUrl(configuredUrl: string): string {
  if (isAbsoluteUrl(configuredUrl)) {
    return trimTrailingSlash(configuredUrl);
  }

  return trimTrailingSlash(new URL(configuredUrl, document.baseURI).toString());
}

export function resolveWebSocketUrl(configuredUrl: string, path: string): string {
  const base = isAbsoluteUrl(configuredUrl)
    ? `${trimTrailingSlash(configuredUrl)}/`
    : new URL(`${trimTrailingSlash(configuredUrl)}/`, document.baseURI).toString();

  const url = new URL(path, base);
  url.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return url.toString();
}

function isAbsoluteUrl(url: string): boolean {
  return /^[a-z][a-z\d+\-.]*:\/\//i.test(url);
}

function trimTrailingSlash(value: string): string {
  return value.replace(/\/+$/, '');
}
