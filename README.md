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


## Aviso de “arquivos binários não compatíveis” no GitHub

Se esse aviso continuar aparecendo no PR, normalmente é por um destes motivos:

1. O PR ainda está baseado em commits antigos que tinham binários (ex.: `gradle-wrapper.jar`).
2. O GitHub está comparando histórico antigo do branch, não apenas o estado atual dos arquivos.

### Como resolver

- Recomendado: criar um **novo branch limpo** a partir da `main` e abrir PR novo só com os commits atuais.
- Alternativa: reescrever histórico e dar `push --force` (mais arriscado).

### Passo a passo para limpar o PR no GitHub

```bash
git checkout main
git pull origin main
git checkout -b fix/clean-pr
# aplique somente os commits atuais desejados
# exemplo: git cherry-pick <hash_do_commit_bom>
git push -u origin fix/clean-pr
```

Depois abra um PR novo de `fix/clean-pr` para `main`.

Nesta entrega, os arquivos binários problemáticos foram removidos do estado atual e o CI foi ajustado para não depender de wrapper binário no repositório.


### Como reconhecer que o CI está rodando um commit antigo

Se no log aparecer algo como:
- `Welcome to Gradle 9.3.1`
- erro `Smart cast to 'Uri!' is impossible ... photoUri`

então o GitHub Actions está executando uma revisão antiga do branch/PR.

No estado atual deste repositório:
- o `MainActivity` já não usa `photoUri` nullable para o `TakePicture`;
- o workflow usa `gradle :app:assembleDebug --no-daemon --stacktrace`.

Nessa situação, atualize o PR com os commits mais recentes e rode **Re-run all jobs**.


