# Ang-Velda

Diário digital, organizador de conhecimento, teia interativa e backup portátil em ZIP (Android, Kotlin + Jetpack Compose + Room).

## Como gerar o APK no GitHub
1. Crie um repositório novo no GitHub.
2. Envie **todo o conteúdo desta pasta** (inclusive a pasta oculta `.github`) para a branch `main`.
3. Abra a aba **Actions** > "Gerar APK Ang-Velda". Ele roda sozinho a cada envio (ou clique em *Run workflow*).
4. Ao terminar (uns 5-10 min), abra a execução e baixe em **Artifacts**: `Ang-Velda-debug-apk` (ou `release-apk`).
5. Descompacte o artefato e instale o `.apk` no celular (permita "instalar apps desconhecidos").

## Estrutura
- `app/src/main/java/com/angvelda/app/data` — Room (registros, tópicos, conexões, anexos)
- `.../backup` — exportação/restauração ZIP versionada e validada
- `.../ui` — Teia (Canvas), Tópicos, Biblioteca, Cofre, Editor, Aparência
- `docs/FORMATO_BACKUP.md` — formato do backup (v1)
