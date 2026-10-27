# 0007 — Armazenamento S3 com SeaweedFS no lugar do MinIO

**Status:** Aceito · 27/10/2026

## Contexto
A especificação pede MinIO no compose como alvo S3. A imagem `minio/minio` não está mais disponível para download no Docker Hub (`pull access denied`, verificado em 27/10/2026). O projeto MinIO deixou de publicar imagens da edição comunitária.

## Decisão
- No dev, usar o **SeaweedFS 4.51** (`chrislusf/seaweedfs`, Apache 2.0) com o gateway S3 na porta 8333. As credenciais vêm do `.env` (`S3_ACCESS_KEY`/`S3_SECRET_KEY`), aplicadas a um template de configuração na subida.
- O backend fala **só a API S3** (AWS SDK v2) por trás da interface `FileStorage` (Fase 3). Trocar de provedor é configuração (endpoint, região, credenciais): SeaweedFS, MinIO de build próprio, Garage, Cloudflare R2 ou AWS S3.

## Consequências
- Nenhum código depende do SeaweedFS.
- O provedor de produção fica para decidir na Fase 6, junto com backup e restore.
