# Especificação do aplicativo de relatório georreferenciado (Android)

## 1) Requisitos funcionais

1. O app deve permitir o preenchimento de um relatório com perguntas pré-cadastradas.
2. As respostas devem ser selecionáveis por lista (ex.: `Spinner`, dropdown ou opções fixas).
3. Ao finalizar, o relatório deve salvar:
   - data/hora,
   - coordenadas GPS (latitude/longitude),
   - respostas selecionadas.
4. O app deve permitir captura de fotos vinculadas ao relatório.
5. Cada foto deve armazenar metadados georreferenciados:
   - latitude,
   - longitude,
   - timestamp,
   - caminho do arquivo local.
6. O app deve funcionar offline para captura e consulta local dos dados.
7. Quando houver internet, deve ser possível sincronizar com API remota.

## 2) Requisitos não funcionais

- Plataforma: Android 9+
- Linguagem: Kotlin
- UI: Jetpack Compose (recomendado)
- Banco local: Room (SQLite)
- Localização: FusedLocationProviderClient
- Captura de fotos: CameraX
- Sincronização: WorkManager (tarefas em segundo plano)
- Armazenamento de fotos: pasta privada do app (`filesDir`) ou mídia app-specific

## 3) Estrutura sugerida de telas

1. **Tela Inicial / Lista de Relatórios**
   - Botão: “Novo relatório”
   - Lista dos relatórios já salvos (com status: pendente/sincronizado)

2. **Tela de Perguntas**
   - Exibir perguntas e opções de resposta
   - Botão “Tirar foto”
   - Botão “Salvar relatório”

3. **Tela de Fotos do Relatório**
   - Miniaturas
   - Exibição de coordenadas por foto

4. **Tela de Detalhes do Relatório**
   - Respostas
   - Coordenada principal
   - Fotos vinculadas

## 4) Fluxo offline

1. Usuário abre “Novo relatório”.
2. App captura posição atual (quando possível) e guarda temporariamente.
3. Usuário responde perguntas e captura fotos.
4. App salva tudo no banco local com status `PENDING_SYNC`.
5. WorkManager monitora conectividade.
6. Ao detectar rede, app envia relatórios pendentes para API.
7. Se sucesso, atualiza status para `SYNCED`.

## 5) Modelo de dados (conceitual)

- **SurveyTemplate**: estrutura do formulário (perguntas).
- **Question**: texto e tipo da pergunta.
- **Option**: opções de resposta.
- **Report**: registro preenchido pelo usuário.
- **Answer**: resposta escolhida para cada pergunta.
- **GeoPhoto**: foto associada ao relatório com coordenadas.

## 6) Validações mínimas

- Não salvar relatório sem responder perguntas obrigatórias.
- Não permitir foto sem permissão de câmera/localização (mostrar fallback com aviso).
- Se GPS indisponível, salvar com flag `locationUnavailable=true` e tentar atualizar depois.

## 7) Segurança e LGPD (mínimo)

- Avisar usuário sobre coleta de localização.
- Permitir exclusão local de relatórios/fotos.
- Se houver backend, usar HTTPS + autenticação por token.

## 8) Exemplo de payload para API (sincronização)

```json
{
  "reportId": "uuid-123",
  "createdAt": "2026-02-27T10:30:00Z",
  "latitude": -23.55052,
  "longitude": -46.633308,
  "answers": [
    { "questionId": "q1", "optionId": "o2" },
    { "questionId": "q2", "optionId": "o1" }
  ],
  "photos": [
    {
      "fileName": "IMG_20260227_103100.jpg",
      "latitude": -23.5505,
      "longitude": -46.6333,
      "timestamp": "2026-02-27T10:31:00Z"
    }
  ]
}
```

## 9) MVP sugerido (primeira versão)

- Formulário local com 5 perguntas de lista.
- Captura da coordenada do relatório.
- Captura de foto com coordenadas.
- Armazenamento local de tudo.
- Sem login e sem sincronização (apenas exportação JSON local).

Depois, em versão 2:
- Sincronização automática com backend.
- Download de novos formulários.
- Controle de usuários e trilha de auditoria.
