import { createServer, IncomingMessage, request as httpRequest, ServerResponse } from 'node:http';
import { createReadStream, existsSync, mkdirSync, mkdtempSync, rmSync, statSync } from 'node:fs';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { tmpdir } from 'node:os';
import { Duplex } from 'node:stream';
import { createConnection } from 'node:net';
import { execFileSync, spawn, ChildProcessWithoutNullStreams } from 'node:child_process';

const ingressPrefix = '/api/hassio_ingress/test-token/';

type RunningHaIngressApp = {
  baseUrl: string;
  ingressPath: string;
  stop: () => Promise<void>;
};

export async function startHaIngressApp(): Promise<RunningHaIngressApp> {
  const webappRoot = process.cwd();
  const repoRoot = resolve(webappRoot, '..');
  const browserDir = resolve(repoRoot, 'deploy', 'ha-app', 'browser');
  const jarPath = resolve(repoRoot, 'deploy', 'ktor.jar');

  if (!existsSync(join(browserDir, 'index.html'))) {
    throw new Error(`HA app build is missing at ${browserDir}. Run npm run build:ha first.`);
  }
  if (!existsSync(jarPath)) {
    throw new Error(`Backend jar is missing at ${jarPath}. Run ./gradlew deployGithub first.`);
  }

  const backendPort = await getFreePort();
  const backendTempDir = mkdtempSync(join(tmpdir(), 'ktor-ha-ingress-'));
  const backendWorkDir = join(backendTempDir, 'work');
  const backendDataDir = join(backendTempDir, 'data');
  mkdirSync(backendWorkDir);
  const backend = spawnBackend(jarPath, backendPort, backendWorkDir, backendDataDir);

  try {
    await waitForBackend(backendPort);
  } catch (error) {
    await stopBackend(backend);
    await cleanupDirectory(backendTempDir);
    throw error;
  }

  const server = createServer((request, response) => {
    void handleHttpRequest(request, response, browserDir, backendPort);
  });
  server.on('upgrade', (request, socket, head) => {
    handleWebSocketUpgrade(request, socket, head, backendPort);
  });

  const proxyPort = await listen(server);
  return {
    baseUrl: `http://127.0.0.1:${proxyPort}`,
    ingressPath: ingressPrefix,
    stop: async () => {
      await closeServer(server);
      await stopBackend(backend);
      await cleanupDirectory(backendTempDir);
    },
  };
}

function spawnBackend(
  jarPath: string,
  port: number,
  backendWorkDir: string,
  backendDataDir: string,
): ChildProcessWithoutNullStreams {
  const databasePath = join(backendDataDir, 'db');
  const backend = spawn('java', [`-Dktor.database.path=${databasePath}`, '-jar', jarPath], {
    cwd: backendWorkDir,
    env: {
      ...process.env,
      HA_MODE: 'true',
      JWT_SECRET_KEY: 'ha-ingress-test-secret-with-production-length',
      KTOR_HOST: '127.0.0.1',
      KTOR_PORT: String(port),
    },
  });

  backend.stdout.on('data', (chunk) => process.stdout.write(`[ha-backend] ${chunk}`));
  backend.stderr.on('data', (chunk) => process.stderr.write(`[ha-backend] ${chunk}`));
  return backend;
}

async function handleHttpRequest(
  request: IncomingMessage,
  response: ServerResponse,
  browserDir: string,
  backendPort: number,
): Promise<void> {
  const requestUrl = new URL(request.url ?? '/', 'http://127.0.0.1');
  if (!requestUrl.pathname.startsWith(ingressPrefix)) {
    response.writeHead(404);
    response.end('Not found');
    return;
  }

  const strippedPath = requestUrl.pathname.slice(ingressPrefix.length);
  if (strippedPath === 'api' || strippedPath.startsWith('api/')) {
    proxyHttpRequest(request, response, backendPort, `/${strippedPath}${requestUrl.search}`);
    return;
  }

  serveStaticAsset(response, browserDir, strippedPath);
}

function serveStaticAsset(response: ServerResponse, browserDir: string, strippedPath: string): void {
  const assetPath = strippedPath === '' ? 'index.html' : strippedPath;
  const filePath = normalize(resolve(browserDir, assetPath));
  if (!filePath.startsWith(browserDir + sep) && filePath !== browserDir) {
    response.writeHead(400);
    response.end('Invalid path');
    return;
  }

  if (!existsSync(filePath) || !statSync(filePath).isFile()) {
    response.writeHead(404);
    response.end('Not found');
    return;
  }

  response.writeHead(200, {
    'Content-Type': contentTypeFor(filePath),
  });
  createReadStream(filePath).pipe(response);
}

function proxyHttpRequest(
  sourceRequest: IncomingMessage,
  sourceResponse: ServerResponse,
  backendPort: number,
  targetPath: string,
): void {
  const targetRequest = createServerRequest(sourceRequest, backendPort, targetPath, (targetResponse) => {
    sourceResponse.writeHead(targetResponse.statusCode ?? 502, targetResponse.headers);
    targetResponse.pipe(sourceResponse);
  });

  targetRequest.on('error', (error) => {
    sourceResponse.writeHead(502);
    sourceResponse.end(`Backend proxy error: ${error.message}`);
  });

  sourceRequest.pipe(targetRequest);
}

function createServerRequest(
  sourceRequest: IncomingMessage,
  backendPort: number,
  targetPath: string,
  onResponse: (response: IncomingMessage) => void,
) {
  return httpRequest(
    {
      host: '127.0.0.1',
      port: backendPort,
      method: sourceRequest.method,
      path: targetPath,
      headers: {
        ...sourceRequest.headers,
        host: `127.0.0.1:${backendPort}`,
      },
    },
    onResponse,
  );
}

function handleWebSocketUpgrade(request: IncomingMessage, socket: Duplex, head: Buffer, backendPort: number): void {
  const requestUrl = new URL(request.url ?? '/', 'http://127.0.0.1');
  const strippedPath = requestUrl.pathname.startsWith(ingressPrefix)
    ? requestUrl.pathname.slice(ingressPrefix.length)
    : '';

  if (!(strippedPath === 'api' || strippedPath.startsWith('api/'))) {
    socket.destroy();
    return;
  }

  const backendSocket = createConnection({ host: '127.0.0.1', port: backendPort }, () => {
    const targetPath = `/${strippedPath}${requestUrl.search}`;
    backendSocket.write(`GET ${targetPath} HTTP/${request.httpVersion}\r\n`);

    for (const [name, value] of Object.entries(request.headers)) {
      if (!value) continue;
      if (name.toLowerCase() === 'host') {
        backendSocket.write(`host: 127.0.0.1:${backendPort}\r\n`);
      } else if (Array.isArray(value)) {
        value.forEach((entry) => backendSocket.write(`${name}: ${entry}\r\n`));
      } else {
        backendSocket.write(`${name}: ${value}\r\n`);
      }
    }

    backendSocket.write('\r\n');
    if (head.length > 0) {
      backendSocket.write(head);
    }
    socket.pipe(backendSocket).pipe(socket);
  });

  backendSocket.on('error', () => socket.destroy());
  socket.on('error', () => backendSocket.destroy());
}

function contentTypeFor(filePath: string): string {
  switch (extname(filePath)) {
    case '.css':
      return 'text/css; charset=utf-8';
    case '.html':
      return 'text/html; charset=utf-8';
    case '.ico':
      return 'image/x-icon';
    case '.js':
      return 'text/javascript; charset=utf-8';
    default:
      return 'application/octet-stream';
  }
}

async function waitForBackend(port: number): Promise<void> {
  const deadline = Date.now() + 30_000;
  let lastError: unknown;

  while (Date.now() < deadline) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/api/ha/session`);
      if (response.ok) return;
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolveTimeout) => setTimeout(resolveTimeout, 250));
  }

  throw new Error(`Backend did not become ready on port ${port}: ${String(lastError)}`);
}

function getFreePort(): Promise<number> {
  return new Promise((resolvePort, reject) => {
    const server = createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      if (!address || typeof address === 'string') {
        reject(new Error('Could not allocate a local port'));
        return;
      }
      const port = address.port;
      server.close(() => resolvePort(port));
    });
  });
}

function listen(server: ReturnType<typeof createServer>): Promise<number> {
  return new Promise((resolvePort, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      if (!address || typeof address === 'string') {
        reject(new Error('Could not start ingress proxy'));
        return;
      }
      resolvePort(address.port);
    });
  });
}

function closeServer(server: ReturnType<typeof createServer>): Promise<void> {
  return new Promise((resolveClose) => server.close(() => resolveClose()));
}

async function stopBackend(backend: ChildProcessWithoutNullStreams): Promise<void> {
  if (backend.exitCode !== null) return;

  backend.kill('SIGTERM');
  if (await waitForProcessExit(backend, 3_000)) return;

  if (process.platform === 'win32' && backend.pid) {
    try {
      execFileSync('taskkill', ['/pid', String(backend.pid), '/T', '/F'], { stdio: 'ignore' });
    } catch {
      // The process may have exited between the timeout and taskkill.
    }
  } else {
    backend.kill('SIGKILL');
  }

  await waitForProcessExit(backend, 5_000);
}

async function cleanupDirectory(directory: string): Promise<void> {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    try {
      rmSync(directory, { recursive: true, force: true });
      return;
    } catch (error) {
      await new Promise((resolveTimeout) => setTimeout(resolveTimeout, 250));
    }
  }
}

function waitForProcessExit(process: ChildProcessWithoutNullStreams, timeoutMs: number): Promise<boolean> {
  if (process.exitCode !== null) return Promise.resolve(true);

  return new Promise((resolveExit) => {
    const timeout = setTimeout(() => {
      process.off('exit', onExit);
      resolveExit(false);
    }, timeoutMs);
    const onExit = () => {
      clearTimeout(timeout);
      resolveExit(true);
    };
    process.once('exit', onExit);
  });
}
