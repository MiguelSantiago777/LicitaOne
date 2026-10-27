import { defineConfig } from 'orval';

// Cliente da API gerado do contrato OpenAPI do backend. Nunca edite src/app/api/generated à mão.
export default defineConfig({
  shemacrm: {
    input: { target: './openapi/openapi.json' },
    output: {
      target: './src/app/api/generated/endpoints.ts',
      schemas: './src/app/api/generated/model',
      client: 'angular',
      mode: 'tags-split',
      clean: true,
      prettier: true,
    },
  },
});
