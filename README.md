# TraceFolio

TraceFolio is a traceable technical portfolio evidence RAG service. It ingests public technical assets such as blog posts, GitHub READMEs, docs, and changelogs, then answers questions only when it can cite retrieved evidence.

## Local Trust Boundary

This branch is a single-user local prototype. **Do not expose it to the public Internet, a shared LAN, a tunnel, or an unauthenticated reverse proxy.** The backend, Vite dev/preview servers, and Docker PostgreSQL port bind to `127.0.0.1` by default. Keep these bindings when running locally.

The API has no authentication or user authorization. Blog and local-directory ingest are disabled until the operator configures their allowlists. Set `TRACEFOLIO_BLOG_ALLOWED_HOSTS` to comma-separated exact trusted HTTPS hosts and `TRACEFOLIO_LOCAL_REPO_ALLOWED_ROOTS` to comma-separated directories intended for scanning. `confirmOwnership=true` is still required and does not authorize a directory outside those roots. Local scans skip links outside the selected real root and bound directory traversal and UTF-8 reads.

Blog requests require HTTPS on port 443 and check the configured host and resolved public IP addresses at every redirect. Jsoup resolves the host again when connecting; these application checks do not pin DNS to the actual socket. Use only domains whose DNS you trust. Public or multi-user deployment still requires authentication, request-forgery protection and network egress restrictions, plus OS isolation against concurrent filesystem changes. Keep the service local. See [deep audit fixes](docs/deep-audit-20261104.md) and [reliability notes](docs/reliability-fixes.md).

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
$env:TRACEFOLIO_BLOG_ALLOWED_HOSTS='spring.io'
$env:TRACEFOLIO_LOCAL_REPO_ALLOWED_ROOTS='D:\TraceFolio'

docker compose up -d
mvn test
mvn spring-boot:run
```

TraceFolio maps PostgreSQL to host port `15432` to avoid clashing with existing local PostgreSQL services.
The HTTP API listens on `http://localhost:8081`.
The browser console is available at `http://localhost:8081`.

If Maven fails with `class file version 65.0 ... recognizes up to 61.0`, the shell is still using Java 17. Set `JAVA_HOME` to a JDK 21 directory before running Maven.

DeepSeek is required for normal answered QA. Without `DEEPSEEK_API_KEY`, retrieval and trace still work, but answer generation returns `ABSTAINED` with `MODEL_CALL_FAILED: DEEPSEEK_API_KEY is not configured`.

## Browser Console

Open `http://localhost:8081` after the service starts. The console includes:

- runtime status for PostgreSQL, pgvector, Ollama `bge-m3`, DeepSeek key, document counts, trace counts, and eval runs
- Evidence OS audit for claim, repo scan, workflow, and portfolio invariants
- GitHub README/docs ingest
- blog ingest
- EvidenceClaim extraction from existing chunks
- read-only GitHub and confirmed local repository evidence scan
- product roadmap board for V1-V6 completion, acceptance criteria, release gate, and blockers
- JD Evidence Compiler for JD parsing, evidence matrix, resume draft, refusal list, and gap project planning
- full product-loop workflow that produces a replayable run and a reusable Evidence Pack in one call
- auditable JD evidence workflow replay
- Portfolio Evidence Pack export with capability radar, JD matrix, resume draft, interview defense, gap projects, and refusal boundaries
- grounded QA
- trace replay with RRF scores and retrieved chunks
- suite-aware eval runner with core/JD/repo/workflow/portfolio coverage

The production UI is served by Spring Boot from `src/main/resources/static`. The source UI lives in `frontend/` and uses Vue 3 + Vite. The workbench is split into page modules under `frontend/src/pages`, shared state/API wiring under `frontend/src/composables`, and reusable UI components under `frontend/src/components`.

For frontend development:

```powershell
cd frontend
npm install
npm run dev
```

Vite runs on `http://localhost:5173` by default, or the next free port if 5173 is busy, and proxies `/api` to `http://localhost:8081`.

To rebuild the Spring Boot static UI:

```powershell
cd frontend
npm run build
```

## APIs

- `POST /api/ingest/blog`
- `POST /api/ingest/github`
- `POST /api/qa`
- `GET /api/traces/{traceId}`
- `POST /api/evals/run`
- `POST /api/jd/compile`
- `POST /api/jd/match`
- `GET /api/jd/runs/{runId}`
- `POST /api/resume/compile`
- `POST /api/gaps/projects`
- `POST /api/claims/extract`
- `GET /api/claims`
- `POST /api/repos/scan`
- `POST /api/workflows/jd-evidence/run`
- `POST /api/workflows/repo-evidence/run`
- `POST /api/workflows/product-loop/run`
- `GET /api/workflows/{runId}`
- `POST /api/portfolio/export`
- `GET /api/portfolio/exports`
- `GET /api/portfolio/exports/{exportId}`
- `GET /api/portfolio/exports/{exportId}/content`
- `GET /api/audit/evidence-os`
- `GET /api/product/roadmap`
- `POST /api/workflows/{runId}/decisions`
- `POST /api/workflows/{runId}/resume`
- `POST /api/workflows/{runId}/steps/{stepIndex}/retry`

## JD Evidence Compiler

The JD Evidence Compiler is the first application layer on top of TraceFolio. It is not a generic ATS keyword optimizer.

Pipeline:

1. Parse the JD into atomic requirements.
2. Match each requirement against retrieved public evidence.
3. Label each requirement as `SUPPORTED`, `WEAKLY_SUPPORTED`, `UNSUPPORTED`, or `GAP_PROJECT_REQUIRED`.
4. Compile only supported or weakly supported claims into a resume draft.
5. Refuse unsupported work experience, production-scale, big-company, revenue, or adoption claims.
6. For hard gaps, generate a 3-7 day minimum evidence project and GitHub reference search strategy.

GitHub Scout searches reference repositories only for learning and implementation planning. Reference projects are never treated as the user's own experience.

## Evidence Operating System Slice

This branch now implements the first product-loop slice of the larger TraceFolio roadmap:

1. `EvidenceClaim` layer: extracts structured capability claims from imported chunks. Every claim must link to a source chunk and keep an original quote.
2. Claim validation: claims now carry `validationStatus` and `freshness`; missing quote/source/ref claims are invalid, and overclaimed wording is marked for review.
3. Repo Evidence Extractor V2: scans selected public GitHub repositories or confirmed local repositories in read-only mode, supports `FAST` / `DEEP`, include/exclude paths, commit snapshot, file hash, symbol type, line anchors, and warnings.
4. Repo scan evidence is promoted into `EvidenceClaim` with `source_ref_type=REPO_SCAN`, file path, line anchor, quote, strength, risk level, symbol metadata, and source kind.
5. Auditable workflow: `POST /api/workflows/jd-evidence/run` runs `claim extract -> JD compile -> evidence match -> resume compile -> validate resume claims -> gap plan` and persists each step.
5. Repo evidence workflow: `POST /api/workflows/repo-evidence/run` runs `validate repo scope -> repo scan -> validate repo evidence -> Evidence OS audit`.
6. Product loop workflow: `POST /api/workflows/product-loop/run` runs `repo scan -> claim extract -> JD compile -> evidence match -> resume compile -> validate -> gap plan -> portfolio export -> audit -> readiness`, then returns a single replayable run.
7. Human review: `POST /api/workflows/{runId}/decisions` records `APPROVED`, `REJECTED`, or `NOTE` decisions for risky steps; `resume` and `retry` endpoints append auditable review/retry events without hiding history.
8. Portfolio Evidence Pack: exports Markdown or HTML from EvidenceClaim plus an optional JD run, including capability radar, project evidence pages, JD match package, resume draft, interview defense cards, gap roadmap, and usage boundaries. Export runs a privacy scan and redacts local paths or secret-like assignments.
9. Product Roadmap: `GET /api/product/roadmap` reports the V1-V6 product route, completion, acceptance criteria, differentiators, release gate, and runtime blockers.
10. Evidence OS Audit: `GET /api/audit/evidence-os` checks whether claims have quotes, sources are linked, repo scans generated claims, workflows have replay steps, and portfolio exports are non-empty.

PDF export, multi-user SaaS, automatic repository modification, browser extension, PDF OCR, and auto-apply workflows remain out of scope for this slice.

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

Invoke-RestMethod http://localhost:8081/api/evals/run `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"suite":"core","tags":[],"failFast":false}'

Invoke-RestMethod http://localhost:8081/api/claims/extract `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"clearExisting":false,"limit":500}'

Invoke-RestMethod http://localhost:8081/api/repos/scan `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"source":"GITHUB","owner":"RyanCoreAI","repo":"TraceFolio","scanMode":"FAST","maxFiles":28}'

Invoke-RestMethod http://localhost:8081/api/repos/scan `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"source":"LOCAL","owner":"RyanCoreAI","repo":"TraceFolio","localPath":"D:\\TraceFolio","confirmOwnership":true,"scanMode":"DEEP","includePaths":["src/main","src/test","frontend/src"],"excludePaths":["target","node_modules","dist"],"maxFiles":40}'

Invoke-RestMethod http://localhost:8081/api/workflows/repo-evidence/run `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"repoScan":{"source":"LOCAL","owner":"RyanCoreAI","repo":"TraceFolio","localPath":"D:\\TraceFolio","confirmOwnership":true,"maxFiles":40}}'

$productLoop = Invoke-RestMethod http://localhost:8081/api/workflows/product-loop/run `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"jdText":"Java Spring Boot RAG PGVector Docker CI/CD","repoScan":{"source":"LOCAL","owner":"RyanCoreAI","repo":"TraceFolio","localPath":"D:\\TraceFolio","confirmOwnership":true,"maxFiles":40},"runRepoScan":true,"extractClaims":true,"targetDays":5,"portfolioFormat":"MARKDOWN","maxClaims":40}'

Invoke-RestMethod "http://localhost:8081/api/portfolio/exports/$($productLoop.output.portfolioExport.exportId)/content"

$workflow = Invoke-RestMethod http://localhost:8081/api/workflows/jd-evidence/run `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"jdText":"Java Spring Boot RAG PGVector Docker CI/CD","targetDays":5,"extractClaims":true}'

Invoke-RestMethod "http://localhost:8081/api/workflows/$($workflow.runId)/decisions" `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"stepName":"validate_resume_claims","decision":"APPROVED","reason":"人工确认 quote 和拒写边界可以接受。"}'

Invoke-RestMethod "http://localhost:8081/api/workflows/$($workflow.runId)/resume" `
  -Method Post `
  -ContentType 'application/json' `
  -Body '{"reason":"人工确认后恢复","metadata":{"surface":"powershell-smoke"}}'

Invoke-RestMethod http://localhost:8081/api/portfolio/export `
  -Method Post `
  -ContentType 'application/json' `
  -Body (@{ title="TraceFolio Evidence Pack"; format="MARKDOWN"; jdRunId=$workflow.output.jdRunId; maxClaims=30 } | ConvertTo-Json)

Invoke-RestMethod http://localhost:8081/api/product/roadmap

Invoke-RestMethod http://localhost:8081/api/audit/evidence-os
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

- `npm run build`: passed.
- `mvn test`: 23 tests passed with Java 21.
- `mvn -DskipTests package`: passed.
- `docker compose config --quiet`: passed.
- `git diff --check`: passed.
- Spring Boot jar smoke on `http://localhost:8081`: `/`, `/assets/index-FgqRyK6h.js`, and `/api/runtime/status` returned 200.
- Runtime status: PostgreSQL connected, pgvector enabled, 14 `rag_*` tables, Ollama `bge-m3` reachable with 1024 dimensions.
- Evidence OS audit: `READY`, 7/7 checks passed.
- Product-loop smoke: `POST /api/workflows/product-loop/run` completed 10 replayable steps and returned `PRODUCT_BASELINE_READY`.
- DeepSeek `DEEPSEEK_API_KEY` was not configured in the local shell, so answered QA and answer-oriented eval cases still require configuring the key before final QA acceptance.
