# Deep audit repairs (2026-11-04)

- Evidence Pack HTML escapes source HTML, sanitizes Markdown destinations and serves inline exports with CSP sandbox/nosniff. Existing saved HTML exports also receive the response policy; re-export them to obtain escaped standalone content.
- Model status must be ANSWERED or ABSTAINED. ANSWERED requires nonblank text and citations. Citation URI/type/title/heading must match the retrieved chunk as well as its ID and quote. This validates provenance, not semantic entailment of every model sentence.
- Privacy scanning handles quoted JSON keys and quoted values with spaces, including common prefixed environment keys, and both Windows path separators. This remains a heuristic scan, not a complete data-loss-prevention system. Review exports before sharing.
- Workflow writes check the configured tenant before mutation and hold the workflow row lock until commit, including review/retry append-index allocation. Application tenant configuration is not user authentication. Real PostgreSQL concurrent scheduling remains unverified locally.
- Blog ingest defaults to disabled; exact trusted host allowlist, HTTPS/443, public resolved IP checks, validated bounded redirects and 2 MB body cap are required. These checks do not pin DNS to the connected socket. Egress restrictions are still required for shared deployment.
- Local repository scanning defaults to disabled; real root allowlist and containment, link rejection, directory pruning, 15,000-entry traversal cap and bounded character reads apply. File hashes use the same successfully analyzed content, avoiding a second unbounded read. Concurrent malicious OS-level path replacement still requires process isolation.

No production DB migration, deployment or credential change is part of these repairs. Existing evidence and exports are not deleted.

Upstream references: [CommonMark renderer options](https://github.com/commonmark/commonmark-java#usage), [jsoup connection options](https://jsoup.org/apidocs/org/jsoup/Connection), [OWASP SSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html).
