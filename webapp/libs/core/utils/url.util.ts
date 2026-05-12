export function resolveApiUrl(configuredUrl: string): string {
  if (isAbsoluteUrl(configuredUrl)) {
    return trimTrailingSlash(configuredUrl);
  }

  return trimTrailingSlash(new URL(configuredUrl, resolveRelativeBaseUrl()).toString());
}

export function resolveWebSocketUrl(configuredUrl: string, path: string): string {
  const base = isAbsoluteUrl(configuredUrl)
    ? `${trimTrailingSlash(configuredUrl)}/`
    : new URL(`${trimTrailingSlash(configuredUrl)}/`, resolveRelativeBaseUrl()).toString();

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

function resolveRelativeBaseUrl(): string {
  const ingressMatch = window.location.pathname.match(/^(.*\/api\/hassio_ingress\/[^/]+)(?:\/.*)?$/);
  if (ingressMatch) {
    return `${window.location.origin}${ingressMatch[1]}/`;
  }

  return document.baseURI;
}
