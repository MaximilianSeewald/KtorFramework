import { copyFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const webappRoot = resolve(__dirname, '../..');
const repoRoot = resolve(webappRoot, '..');
const source = resolve(__dirname, 'src/ktor-lovelace-cards.js');
const targets = [
  resolve(webappRoot, '../deploy/ha-app/browser/ktor-lovelace-cards.js'),
  resolve(repoRoot, 'addons/ktor_app/app/browser/ktor-lovelace-cards.js'),
];

for (const target of targets) {
  await mkdir(dirname(target), { recursive: true });
  await copyFile(source, target);
  console.log(`Copied ${source} -> ${target}`);
}
