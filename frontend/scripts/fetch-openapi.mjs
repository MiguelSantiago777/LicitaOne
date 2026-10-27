// Baixa o contrato OpenAPI do backend rodando localmente e salva formatado em openapi/openapi.json.
// Uso: npm run api:fetch (com o backend no ar) e depois npm run api:gen.
import { writeFile } from 'node:fs/promises';

const url = process.env.OPENAPI_URL ?? 'http://localhost:18080/v3/api-docs';
const response = await fetch(url);
if (!response.ok) {
  console.error(`Falha ao baixar ${url}: HTTP ${response.status}`);
  process.exit(1);
}
const spec = await response.json();
// O servidor muda conforme o ambiente; o cliente usa caminhos relativos
delete spec.servers;
await writeFile(new URL('../openapi/openapi.json', import.meta.url), JSON.stringify(spec, null, 2) + '\n');
console.log(`openapi/openapi.json atualizado a partir de ${url}`);
