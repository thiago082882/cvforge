# CVForge: passo a passo para rodar no Windows

Gerador de currículos adaptados a cada vaga, com IA. Gratuito.

## Antes de tudo

1. **Crie sua chave gratuita da Groq** (é a IA que escreve o currículo): https://console.groq.com/keys
   Clique em *Create API Key* e guarde a chave. Ela fica só com você.
2. **Nunca publique** chave, senha ou arquivos `.env` no GitHub nem em prints.
3. Use sempre a **sua** chave e a **sua** senha. Não reaproveite as de ninguém.

## O que instalar

- **Android Studio** (já traz o Java): https://developer.android.com/studio
- **PostgreSQL 17** para Windows x86-64: https://www.postgresql.org/download/windows/
    - Deixe a porta padrão `5432`.
    - Escolha uma senha para o usuário `postgres` e **anote**: ela será usada nos passos 3 e 4.

## Passo 1: abrir o projeto

1. Descompacte o projeto.
2. Abra a pasta no Android Studio e espere o Gradle terminar de sincronizar (a primeira vez demora).
3. No arquivo `gradle.properties`, confira se existe esta linha (se não, adicione):

```properties
kotlin.native.ignoreDisabledTargets=true
```

## Passo 2: criar o banco de dados

1. No menu Iniciar, abra o **SQL Shell (psql)**.
2. Aperte Enter nas perguntas (servidor, banco, porta, usuário) até ele pedir a senha.
   Digite a senha que você escolheu na instalação.
3. Rode (só precisa fazer uma vez):

```sql
CREATE DATABASE cvforge;
```

## Passo 3: ligar o servidor

Abra o PowerShell na pasta do projeto e rode, **tudo na mesma janela**
(troque os trechos em maiúsculas pelos seus dados):

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/cvforge"
$env:DATABASE_USER = "postgres"
$env:DATABASE_PASSWORD = "SUA_SENHA_DO_POSTGRES"
$env:GROQ_API_KEY = "SUA_CHAVE_DA_GROQ"
./gradlew :server:run
```

Quando aparecer `Responding at http://0.0.0.0:8080`, o servidor está no ar.
**Deixe essa janela aberta.**

Opcional: defina também `$env:JWT_SECRET = "UM_TEXTO_LONGO_COM_32_OU_MAIS_CARACTERES"`.
Sem isso, você precisa fazer login de novo toda vez que reiniciar o servidor.

## Passo 4: testar o servidor

Em **outra** janela do PowerShell:

```powershell
Invoke-RestMethod http://localhost:8080/health
```

Deve responder `status: ok`.

## Passo 5: abrir o app

Nessa segunda janela, na pasta do projeto:

```powershell
./gradlew :composeApp:run
```

A primeira execução demora porque baixa muita coisa.

## Passo 6: usar

1. Clique em **Cadastrar** e crie sua conta (senha com 8 caracteres ou mais).
2. Preencha o **Perfil** com experiências, formação e habilidades **reais**.
3. Vá em **Novo**, cole o **texto** da vaga e clique em **Gerar currículo**.
4. No resultado, veja a nota de aderência e baixe o **PDF**.

Prefira colar o texto da vaga em vez do link, porque LinkedIn e Indeed bloqueiam a leitura automática.

## Outras plataformas

- **Android:** rode o módulo `androidApp` no emulador (ele acessa o servidor do seu PC sozinho).
- **Web:** `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` e abra `http://localhost:8081`.
- **iOS:** só em Mac, com Xcode.

## Problemas comuns

| Mensagem | O que fazer |
|---|---|
| `password authentication failed` | A senha em `DATABASE_PASSWORD` não é a que você definiu ao instalar o Postgres. |
| `database "cvforge" does not exist` | Faltou o `CREATE DATABASE cvforge;` do Passo 3. |
| `Connection refused` (porta 5432) | O Postgres não está rodando. Abra `services.msc` e inicie o serviço `postgresql-x64-17`. |
| `Address already in use` (porta 8080) | Outro programa usa essa porta. Feche-o ou troque a porta com `$env:PORT`. |
| "Não foi possível conectar ao servidor" | O servidor do Passo 4 não está rodando. |
| Erro 503 ao gerar currículo | A `GROQ_API_KEY` não foi definida ou está inválida. |
| Pede login toda hora | Defina o `JWT_SECRET` (Passo 4). |

