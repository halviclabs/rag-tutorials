# RAG Tutorial Part 3 — External Data Sources & Chat Platforms

> Part 3 of 4: [Part 1 — Basics](../rag-tutorial-01-basics/) · [Part 2 — Swappable LLMs](../rag-tutorial-02-llms/) · **This Part** · [Part 4 — Everything + Deployment](../rag-tutorial-04-full/)

🇩🇪 [Deutsche Version](README.de.md)

Part 3 takes Part 2 (swappable providers + swappable vector store) and adds three
**optional, off-by-default connectors** that plug your RAG system into the tools a
team already uses:

1. **Confluence ingestion** — pull wiki pages (single page or whole space) into the
   vector store.
2. **Microsoft Teams** — an outgoing webhook: @-mention the bot in a channel, get a
   RAG answer back.
3. **Slack** — an Events API bot: @-mention the app, the answer arrives in a thread.

Each connector only activates once its key config property is set (feature flags via
`@ConditionalOnProperty` — not always-on beans). Without configuration, the app is
byte-for-byte Part 2 behavior: the connector beans don't even exist, and their
endpoints answer 404.

Both chat connectors **re-embed every answered Q&A pair** into the vector store —
so future questions (from chat or the dashboard) can retrieve prior answers.

## What is RAG? (recap, so this README stands alone)

LLMs only know their training data. **Retrieval-Augmented Generation** grounds their
answers in *your* documents, at question time:

```
                ┌────────────────────────────────────────────────────┐
  question ───▶ │ 1. RETRIEVAL     similarity-search the vector      │
                │                  store for the most relevant       │
                │                  document chunks                   │
                │ 2. AUGMENTATION  put those chunks into the prompt  │
                │ 3. GENERATION    the LLM answers grounded in them  │ ───▶ answer
                └────────────────────────────────────────────────────┘
```

Documents get in through the **ingestion pipeline** — and Part 3's whole point is
that *more things* now feed it:

```
  file / URL / Confluence page / chat Q&A pair
        │
        ▼
  extract text ──▶ chunk ──▶ embed ──▶ store
 (Tika/Jsoup/     (token     (embedding  (vector
  Confluence API)  splitter)   model)      store)
```

Key terms: an **embedding** is a float vector capturing a text's meaning; a
**vector store** finds the stored chunks closest to a query vector; **chunking**
splits documents into embeddable, prompt-sized pieces.
[Part 1 explains the fundamentals in depth](../rag-tutorial-01-basics/); Part 2's
profile system (providers `azure`/`openai`/`ollama` × stores `simple`/`pgvector`)
is [documented here](../rag-tutorial-02-llms/) and works unchanged in this part.

## Project structure (new in Part 3)

```
rag-tutorial-03-connectors/
└── src/main/java/io/halvic/rag/
    ├── config/AsyncConfig.java                    # NEW: @EnableAsync
    ├── ingestion/confluence/                      # NEW: Confluence connector
    │   ├── ConfluenceProperties.java
    │   ├── ConfluenceClient.java                  # REST client, Cloud + Server/DC auth
    │   ├── ConfluenceIngestionService.java
    │   └── ConfluenceIngestController.java        # POST /api/ingest/confluence/{page,space}
    ├── teams/                                     # NEW: Microsoft Teams connector
    │   ├── TeamsProperties.java
    │   ├── TeamsWebhookPayload.java
    │   ├── TeamsSignatureVerifier.java            # HMAC-SHA256 check
    │   ├── TeamsIngestionService.java             # async Q&A re-embedding
    │   └── TeamsWebhookController.java            # POST /teams/webhook
    └── slack/                                     # NEW: Slack connector
        ├── SlackProperties.java
        ├── SlackEventPayload.java
        ├── SlackSignatureVerifier.java            # signing secret + timestamp check
        ├── SlackClient.java                       # chat.postMessage wrapper
        ├── SlackIngestionService.java             # async Q&A re-embedding
        ├── SlackEventHandler.java                 # async answer flow
        └── SlackEventsController.java             # POST /slack/events
```

Everything from Parts 1–2 (core RAG loop, dashboard, profiles, docker-compose) is
included unchanged.

## Prerequisites

- Everything from Part 2 (Java 17+, Maven, Node 20+, optionally Docker, provider
  credentials for `azure`/`openai` — or none for `ollama`).
- Per connector (only if you enable it):
  - **Confluence**: a Confluence Cloud API token, or a Server/DC Personal Access Token.
  - **Teams**: permission to create an *outgoing webhook* in a team, plus a public
    HTTPS endpoint (e.g. [ngrok](https://ngrok.com)) — Teams cannot call `localhost`.
  - **Slack**: permission to create a Slack app in your workspace, plus a public
    HTTPS endpoint (ngrok again) for the Events API.

## Connector 1 — Confluence ingestion

### 1.1 Get credentials

**Confluence Cloud** (`https://<org>.atlassian.net/wiki`):
1. Go to <https://id.atlassian.com/manage-profile/security/api-tokens>.
2. *Create API token*, copy it.
3. You'll authenticate as `email + api-token`.

**Confluence Server / Data Center**:
1. Profile → *Personal Access Tokens* → *Create token*.
2. You'll authenticate with just that PAT (Bearer).

### 1.2 Configure & start

```bash
export CONFLUENCE_BASE_URL="https://your-org.atlassian.net/wiki"
# Cloud:
export CONFLUENCE_EMAIL="you@example.com"
export CONFLUENCE_API_TOKEN="..."
# — or Server/DC instead:
# export CONFLUENCE_PAT="..."

RAG_CONFLUENCE_BASE_URL="$CONFLUENCE_BASE_URL" \
RAG_CONFLUENCE_EMAIL="$CONFLUENCE_EMAIL" \
RAG_CONFLUENCE_API_TOKEN="$CONFLUENCE_API_TOKEN" \
mvn spring-boot:run
```

(Any Spring config style works: `RAG_CONFLUENCE_*` environment variables, or
uncomment the `rag.confluence` block in `application.yml` and reference your env
vars there.)

### 1.3 Use it

```bash
# ingest one page (the page ID is in the page URL: .../pages/123456789/Title)
curl -X POST http://localhost:8080/api/ingest/confluence/page \
  -H "Content-Type: application/json" \
  -d '{"pageId": "123456789"}'

# ingest up to 50 pages of a space
curl -X POST http://localhost:8080/api/ingest/confluence/space \
  -H "Content-Type: application/json" \
  -d '{"spaceKey": "DOCS", "limit": 50}'
```

Ingested pages appear in `GET /api/documents` (and the dashboard) with source type
`confluence` and their page URL as source.

## Connector 2 — Microsoft Teams

Teams *outgoing webhooks* are **channel-scoped**: you create one per team, it gets a
name (e.g. `RAGBot`), and whenever someone @-mentions that name in a channel, Teams
POSTs the message to your endpoint and shows your synchronous JSON reply.

### 2.1 Expose your backend publicly

Teams can only call public HTTPS endpoints:

```bash
ngrok http 8080
# note the https://<random>.ngrok-free.app URL
```

### 2.2 Create the outgoing webhook

1. In Teams: team name → *Manage team* → *Apps* → *Create an outgoing webhook*
   (bottom right).
2. Name: `RAGBot` (this is what users will @-mention), description, optional icon.
3. Callback URL: `https://<your-ngrok-host>/teams/webhook`.
4. On save, Teams shows a **security token (Base64) exactly once** — copy it. This
   is the HMAC secret used to sign every request to you.

### 2.3 Configure & start

```bash
export TEAMS_HMAC_SECRET="<the security token>"
RAG_TEAMS_HMAC_SECRET="$TEAMS_HMAC_SECRET" mvn spring-boot:run
```

### 2.4 Use it

In the channel: `@RAGBot What does our onboarding doc say about laptops?`
The controller verifies the `Authorization: HMAC <signature>` header against an
HMAC-SHA256 of the raw request body, runs the RAG loop, replies synchronously with
Teams-formatted JSON (`{"type":"message","text":...}`), and asynchronously re-embeds
the Q&A pair.

Simulate a signed call without Teams (bash):

```bash
BODY='{"type":"message","text":"<at>RAGBot</at> what is RAG?","from":{"id":"u1","name":"Tester"}}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -mac HMAC \
      -macopt hexkey:$(printf '%s' "$TEAMS_HMAC_SECRET" | base64 -d | xxd -p -c 256) -binary | base64)
curl -X POST http://localhost:8080/teams/webhook \
  -H "Authorization: HMAC $SIG" -H "Content-Type: application/json" -d "$BODY"
```

## Connector 3 — Slack

The Slack connector mirrors the Teams one, but on Slack's Events API: events are
acknowledged immediately (Slack enforces a **3-second timeout**) and the answer is
posted afterwards via `chat.postMessage`, threaded on the triggering message.

### 3.1 Create the Slack app

1. <https://api.slack.com/apps> → *Create New App* → *From scratch*, pick your
   workspace.
2. *OAuth & Permissions* → *Bot Token Scopes*: add `app_mentions:read` and
   `chat:write`.
3. *Install App* to the workspace → copy the **Bot User OAuth Token** (`xoxb-...`).
4. *Basic Information* → copy the **Signing Secret**.

### 3.2 Point the Events API at your backend

```bash
ngrok http 8080
```

5. *Event Subscriptions* → enable → Request URL:
   `https://<your-ngrok-host>/slack/events`. Slack immediately sends a
   `url_verification` challenge — the backend must already be running with the
   Slack profile config so it can echo the `challenge` back (the controller does
   this) and Slack marks the URL *Verified*.
6. *Subscribe to bot events*: add `app_mention`. Save.
7. Invite the bot to a channel: `/invite @YourApp`.

### 3.3 Configure & start

```bash
export SLACK_SIGNING_SECRET="..."
export SLACK_BOT_TOKEN="xoxb-..."
RAG_SLACK_SIGNING_SECRET="$SLACK_SIGNING_SECRET" \
RAG_SLACK_BOT_TOKEN="$SLACK_BOT_TOKEN" \
mvn spring-boot:run
```

### 3.4 Use it

In the channel: `@YourApp What does the architecture doc say about caching?`
The controller verifies `X-Slack-Signature` (HMAC-SHA256 over
`v0:<timestamp>:<raw body>`, with a ~5-minute timestamp tolerance against replays),
acks with 200, and the async handler runs the RAG loop, posts the answer into a
thread, and re-embeds the Q&A pair.

Simulate the URL verification handshake locally:

```bash
TS=$(date +%s); BODY='{"type":"url_verification","challenge":"test-challenge"}'
SIG="v0=$(printf 'v0:%s:%s' "$TS" "$BODY" | openssl dgst -sha256 -hmac "$SLACK_SIGNING_SECRET" -hex | awk '{print $NF}')"
curl -X POST http://localhost:8080/slack/events \
  -H "X-Slack-Request-Timestamp: $TS" -H "X-Slack-Signature: $SIG" \
  -H "Content-Type: application/json" -d "$BODY"
# → {"challenge":"test-challenge"}
```

## Start backend & frontend

Backend — any Part 2 profile combination, plus whichever connector env vars you
want active:

```bash
mvn spring-boot:run                                        # simple,azure (default)
mvn spring-boot:run -Dspring-boot.run.profiles=pgvector,ollama   # offline + persistent
```

Frontend (unchanged):

```bash
cd rag-ui && npm install && npm start    # http://localhost:4200
```

## How the code fits together

- All three connectors funnel into the **same ingestion pipeline**
  (`DocumentIngestionService.ingestText`: chunk → embed → store) and the same
  `RagService` loop — they are thin adapters, which is the architectural takeaway
  of this part.
- Feature-flagging is done at the bean level: `@ConditionalOnProperty` on every
  connector bean means an unconfigured connector contributes *nothing* to the
  application context.
- Both chat controllers read the **raw request body** as a `String` and verify the
  platform signature over exactly those bytes *before* JSON parsing — signature
  schemes break if you let the framework deserialize first.
- Teams replies synchronously (its webhook model expects it); Slack acks in <3s and
  answers asynchronously (`@EnableAsync` + `@Async` handler). Both re-embed Q&A
  pairs asynchronously.

## Outlook

You now have a RAG system fed by files, URLs, a wiki and two chat platforms. What it
lacks is a deployment story — that's Part 4.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `404` on `/api/ingest/confluence/*`, `/teams/webhook` or `/slack/events` | The connector's key property isn't set — the beans (and endpoints) don't exist | Set `rag.confluence.base-url` / `rag.teams.hmac-secret` / `rag.slack.signing-secret` (e.g. via `RAG_..._*` env vars) and restart |
| Confluence: `401` | Wrong email/API token (Cloud) or expired PAT (Server/DC) | Recreate the token; Cloud needs *email + token* as a pair, Server/DC only the PAT |
| Confluence: `403` | The account can't see the space/page | Grant the account read permission, or use another account |
| Confluence: `404` on an existing page | Wrong `base-url` (Cloud needs the `/wiki` suffix) or a page ID from another site | Use `https://<org>.atlassian.net/wiki`; take the numeric ID from the page URL |
| Teams: bot replies "Signature verification failed" / `401` | HMAC secret mismatch — webhook recreated but old token configured, or the token was not copied completely | Reconfigure `TEAMS_HMAC_SECRET` with the exact Base64 token Teams showed at webhook creation |
| Teams: webhook never fires | Outgoing webhooks are channel-scoped and only trigger on an @-mention of the webhook's *name*; localhost URLs never work | @-mention exactly the webhook name in a standard channel of the team where it was created; use ngrok/public HTTPS |
| Teams: "The service didn't respond" | Reply took too long (slow model) — Teams expects a synchronous answer within seconds | Use a faster chat model/deployment; keep `top-k` small |
| Slack: Request URL shows "Your URL didn't respond" | Backend not running/reachable when Slack sent `url_verification`, or the signing secret doesn't match (the controller then answers `401`) | Start the backend with the correct `SLACK_SIGNING_SECRET` *before* saving the Request URL; check the ngrok tunnel |
| Slack: `401` on every event | Signing secret mismatch, or the clock skew exceeds the 5-minute replay tolerance | Copy the Signing Secret from *Basic Information* (not the bot token); sync your system clock |
| Slack: bot never answers, but events arrive | Missing scopes/`chat:write`, bot not invited to the channel, or `bot-token` unset (check logs for `chat.postMessage failed: not_in_channel` / `missing_scope` / startup error) | Add scopes, reinstall the app, `/invite` the bot, set `RAG_SLACK_BOT_TOKEN` |
| Slack: every answer arrives 3–4 times | Slack retries events that aren't acked within 3s — something blocked the controller thread | Keep the controller thin (it must not call the LLM); this code acks immediately, so look for thread-pool exhaustion |
| Chat answers ignore earlier chat answers | Q&A re-embedding failed silently (see WARN logs), e.g. because the embedding call errored | Check logs of `TeamsIngestionService`/`SlackIngestionService`; verify provider credentials |

## What's next

➡️ **[Part 4 — Everything combined + deployment](../rag-tutorial-04-full/)** keeps
the application code unchanged and adds production packaging: Dockerfiles for
backend and frontend (nginx) and a Helm chart with optional Postgres/pgvector and
Ollama deployments.

⬅️ For the fundamentals, go back to **[Part 1](../rag-tutorial-01-basics/)**; for
the profile system, see **[Part 2](../rag-tutorial-02-llms/)**.
