# GeoReport Android (MVP)

Base inicial de um aplicativo Android para **relatório georreferenciado** com:
- perguntas com respostas selecionáveis,
- captura de coordenadas GPS,
- captura de **fotos georreferenciadas**,
- persistência offline com Room.

## O que foi implementado

- Projeto Android em Kotlin + Jetpack Compose.
- Tela de relatório com perguntas e botão para salvar.
- Captura de localização atual (lat/lon).
- Captura de foto e salvamento local.
- Vinculação da foto ao relatório com latitude/longitude no banco local.

## Estrutura principal

- `app/src/main/java/com/example/georeport/MainActivity.kt`: tela principal com formulário, localização e foto.
- `app/src/main/java/com/example/georeport/data/*`: entidades Room, DAO e banco local.
- `app/src/main/java/com/example/georeport/domain/GeoReportRepository.kt`: regras de persistência.

## Sem Android Studio: gerar APK online (GitHub Actions)

Se você não tem Android Studio, você consegue gerar o APK **100% online** pelo GitHub:

1. Suba este projeto para um repositório no GitHub.
2. Abra a aba **Actions**.
3. Execute o workflow **Build Android Debug APK** (botão *Run workflow*).
4. Ao finalizar, baixe o artefato **app-debug-apk**.
5. Extraia o ZIP e use o arquivo `app-debug.apk` no celular.

Arquivo do workflow já incluído no projeto:
- `.github/workflows/android-debug-apk.yml`

## Instalar no Moto G56

### Opção A: instalação manual
1. Envie `app-debug.apk` para o celular (Drive, WhatsApp, cabo USB).
2. No Android, habilite instalação de apps desconhecidos para o app usado para abrir o arquivo.
3. Toque no APK e confirme instalação.

### Opção B: via ADB

```bash
adb install -r app-debug.apk
```

## Limite importante

Eu não consigo instalar diretamente no seu aparelho daqui porque não tenho acesso físico ao seu Moto G56/USB. O que eu consigo fazer é deixar o processo pronto para você gerar online e baixar o APK.

## Próximos ajustes sugeridos

1. Trocar os campos de texto das respostas por dropdown real (`ExposedDropdownMenuBox`).
2. Adicionar listagem histórica de relatórios.
3. Implementar sincronização com API via WorkManager.
4. Salvar miniaturas e visualização da imagem dentro do app.
