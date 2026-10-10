# Formato de backup Ang-Velda — versão 1

ZIP comum (UTF-8, sem criptografia):

```
Ang-Velda_Backup_AAAA-MM-DD.zip
├─ manifest.json          formato, versão, data, contagens, lista de arquivos com SHA-256
├─ data/topics.json       {"formatVersion":1,"items":[...]}
├─ data/entries.json      registros (inclui posição "pos":{x,y} na teia)
├─ data/connections.json  conexões (from, to, relation, label, color)
├─ data/attachments.json  metadados dos anexos (id, name, mime, size, sha256)
├─ data/settings.json     preferências de aparência
└─ attachments/<id>       arquivos anexados
```

Validações antes de restaurar: ZIP legível, caminhos seguros, sem entradas duplicadas, limites de tamanho,
versão suportada, hashes dos JSON e dos anexos, ids válidos e únicos, referências consistentes e hierarquia sem ciclos.
Restauração: *substituir* (cria backup de segurança interno antes) ou *mesclar* (ids conflitantes são remapeados).
Novas versões do formato devem incrementar `formatVersion` e acrescentar migração em `BackupManager.parseAndValidate`.
