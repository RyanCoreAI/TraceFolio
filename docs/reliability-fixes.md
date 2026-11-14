# Reliability changes and verification boundaries

## Knowledge ingest

All embeddings are obtained and checked for dimensions and finite values before database writes. `KnowledgeRepository.storeDocument` then upserts the content version, locks its document row, and changes chunks inside one database transaction. A failed model request cannot delete previously stored chunks or dependent evidence. A database insert failure propagates so the transaction rolls back.

An identical complete version keeps its existing chunk IDs and evidence claims. Concurrent imports of that same version serialize on the document row; the second writer checks committed chunks after the lock. Different content hashes remain separate document versions under the existing schema. No migration or historical evidence deletion is part of this change.

## Evidence deduplication

The original compound unique constraint contains nullable source IDs and technology, so it cannot prevent all duplicate claims. Repository insertion now locks the referenced chunk or repository-scan row in a new `READ COMMITTED` transaction, then uses `IS NOT DISTINCT FROM` for nullable comparisons before inserting. This recognizes historical duplicates and preserves them. The duplicate definition remains scoped to the existing source ID, capability, technology and quote hash; a new scan ID represents a different snapshot.

All application inserts use this repository method. The fix does not add a database uniqueness constraint covering independent SQL writers, and external direct writers must not bypass the repository protocol. Existing duplicates remain available for a separately reviewed, non-destructive reconciliation.

PostgreSQL documents [row locking](https://www.postgresql.org/docs/17/explicit-locking.html#LOCKING-ROWS), [READ COMMITTED statement snapshots](https://www.postgresql.org/docs/17/transaction-iso.html#XACT-READ-COMMITTED), and [NULL-safe comparison](https://www.postgresql.org/docs/17/functions-comparison.html).

## Immutable GitHub evidence

Each scan resolves the requested branch, abbreviated SHA, or full SHA once through GitHub's [Get a commit API](https://docs.github.com/en/rest/commits/commits#get-a-commit). Its resolved full commit SHA is used for the tree request, response, snapshot and evidence links. File contents still use GitHub blob SHAs. Links encode path segments, preserving spaces, Unicode and `#` characters. Failure to resolve a full SHA prevents saving evidence.

## Frontend maintenance

The existing Vue 3.5 and Vite 6 requirements are raised to `^3.5.44` and `^6.4.5`; no major upgrade or forced dependency override is used. The lockfile resolves compatible transitive patches, and the Spring-served static assets are rebuilt.

Sources checked on 2026-11-02:

- [Vue upstream SSR attribute advisory](https://github.com/vuejs/core/security/advisories/GHSA-g2v6-rqmx-r4w6), [patched 3.5.44 attribute validation source](https://github.com/vuejs/core/blob/v3.5.44/packages/shared/src/domAttrConfig.ts#L34), and [release](https://github.com/vuejs/core/releases/tag/v3.5.44). The advisory page still says no patched version; the current source includes the missing CR check and registry audit excludes 3.5.44. This app uses client-side rendering, so the SSR condition is not established here.
- [Vite upstream advisory](https://github.com/vitejs/vite/security/advisories/GHSA-9jrq-w75r-8gcw), [6.4.5 changelog](https://github.com/vitejs/vite/blob/v6.4.5/packages/vite/CHANGELOG.md) and [release](https://github.com/vitejs/vite/releases/tag/v6.4.5). Vite 6.4.4 includes the October development-server fixes; 6.4.5 adds another filesystem check. Not every advisory's triggering configuration is used by this app.
- Live npm registry versions and `npm audit`: six high package findings before, zero findings after the compatible update. Registry results are time-specific, not a guarantee that every runtime path is secure.

## Executed verification

The backend's complete Java 21 Maven test suite passes 34 tests (11 added regressions). Tests cover a later model-call failure before any storage, vector rejection, embedding/store ordering, identical-version preservation, transaction rollback on insert failure through Spring's transaction proxy, nullable claim comparison and parent locking, transaction scope, commit resolution, slash-containing branches, encoded evidence URLs, and unresolved-commit rejection.

HTTP calls in GitHub tests use `MockRestServiceServer`; database calls use controlled JDBC/connection mocks. PostgreSQL concurrency, cascade rollback and actual row-lock behavior were not exercised against a live database because Docker was unavailable. Run those database integration scenarios before treating this as a production release. No existing database migration was executed. No real model request, repository scan, or credential-bearing external call was made.

The production frontend build passes. The existing frontend package has no test script; no frontend test coverage is claimed. Default binding changes and this document do not fix the SSRF/local filesystem issues described in the README; public deployment remains unsupported.
