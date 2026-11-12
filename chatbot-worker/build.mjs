import { readFile, mkdir, writeFile } from 'node:fs/promises';
const conocimiento = JSON.parse(await readFile(new URL('../src/main/resources/chatbot-conocimiento.json', import.meta.url), 'utf8'));
const handler = await readFile(new URL('./src/handler.mjs', import.meta.url), 'utf8');
await mkdir(new URL('./dist/', import.meta.url), { recursive: true });
await writeFile(new URL('./dist/worker.mjs', import.meta.url), `${handler}\nconst conocimiento = ${JSON.stringify(conocimiento)};\nexport default createWorker(conocimiento);\n`);
console.log('Worker generado desde el JSON del backend, sin copiar claves.');
