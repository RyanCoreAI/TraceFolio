# TraceFolio

TraceFolio is a traceable technical portfolio evidence RAG service. It ingests public technical assets such as blog posts, GitHub READMEs, docs, and changelogs, then answers questions only when it can cite retrieved evidence.

## Stack

- Java 21
- Spring Boot 4.1.0
- Spring AI 2.0.0 dependencies for model ecosystem alignment
- PostgreSQL + pgvector
- PostgreSQL full text search
- DeepSeek `deepseek-v4-flash`
- Ollama `bge-m3`

## Version References

- Spring AI 2.0.0 GA and Spring Boot 4.0 / 4.1 baseline: https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/
- Spring AI PGVectorStore starter and dimension guidance: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
- DeepSeek model updates: https://api-docs.deepseek.com/updates
- DeepSeek JSON Output: https://api-docs.deepseek.com/guides/json_mode
- GitHub repository contents API: https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28

## Local Start

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:DEEPSEEK_API_KEY='your_key'
$env:GITHUB_TOKEN='optional_token'

docker compose up -d
mvn test
mvn spring-boot:run
```

TraceFolio maps PostgreSQL to host port `15432` to avoid clashing with existing local PostgreSQL services.
The HTTP API listens on `http://localhost:8081`.
The browser console is available at `http://localhost:8081`.

DeepSeek is required for normal answered QA. Without `DEEPSEEK_API_KEY`, retrieval and trace still work, but answer generation returns `ABSTAINED` with `MODEL_CALL_FAILED: DEEPSEEK_API_KEY is not configured`.

## Browser Console

Open `http://localhost:8081` after the service starts. The console includes:

- runtime status for PostgreSQL, pgvector, Ollama `bge-m3`, DeepSeek key, document counts, trace counts, and eval runs
- GitHub README/docs ingest
- blog ingest
- grounded QA
- trace replay with RRF scores and retrieved chunks
- 30-case eval runner

The UI is served by Spring Boot from `src/main/resources/static`, so there is no Node.js, npm, Vite, or frontend build step.

## APIs

- `POST /api/ingest/blog`
- `POST /api/ingest/github`
- `POST /api/qa`
- `GET /api/traces/{traceId}`
- `POST /api/evals/run`

## Smoke

```powershell
Invoke-RestMethod http://localhost:8081/api/ingest/blog `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"url":"https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/"}'

Invoke-RestMethod http://localhost:8081/api/ingest/github `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"owner":"RyanCoreAI","repo":"patchbrake","path":"README.md"}'

$qa = Invoke-RestMethod http://localhost:8081/api/qa `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"question":"PatchBrake 是什么？"}'

Invoke-RestMethod "http://localhost:8081/api/traces/$($qa.traceId)"

Invoke-RestMethod http://localhost:8081/api/evals/run -Method Post
```

## Windows Encoding

TraceFolio API responses force `application/json;charset=UTF-8` for `/api/*`, and the browser UI uses `<meta charset="UTF-8">`.

If a Windows terminal still displays Chinese incorrectly, run this once in the current PowerShell session:

```powershell
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$OutputEncoding = [System.Text.UTF8Encoding]::new()
chcp 65001
```

The browser console is the recommended smoke-test surface for users who do not want to debug shell encoding.

## Current Local Verification

- `mvn test`: 5 tests passed.
- `mvn -DskipTests package`: passed.
- `docker compose config --quiet`: passed.
- PostgreSQL `vector` extension: enabled.
- Ollama `bge-m3`: installed locally, embedding length 1024.
- Imported one GitHub README and one public blog URL.
- `POST /api/evals/run`: completed 30 cases. With no DeepSeek key configured, the current local run passed the 10 must-abstain cases and failed the 20 should-answer / must-cite cases.
