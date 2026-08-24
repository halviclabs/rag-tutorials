# RAG Tutorial Part 2 — Swappable LLMs & Persistent Vector Store

> Part 2 of 4: [Part 1 — Basics](../rag-tutorial-01-basics/) · **This Part** · [Part 3 — Connectors](../rag-tutorial-03-connectors/) · [Part 4 — Everything + Deployment](../rag-tutorial-04-full/)

🇩🇪 [Deutsche Version](README.de.md)

Part 2 takes the working RAG loop from Part 1 and makes the two big infrastructure
choices **swappable via Spring profiles**:

- **LLM / embedding provider**: `azure` (Azure OpenAI), `openai` (plain OpenAI API),
  or `ollama` (local Mistral via Docker — fully offline, no API key).
- **Vector store**: `simple` (in-memory, as in Part 1) or `pgvector` (persistent
  PostgreSQL + pgvector via docker-compose).

Any store profile combines with any provider profile: `pgvector,openai`,
`simple,ollama`, etc. The application code (`RagService`,
`DocumentIngestionService`) is **unchanged** — that's the point: it only talks to
Spring AI's `ChatModel`, `EmbeddingModel` and `VectorStore` abstractions.
The Angular dashboard is also unchanged from Part 1.

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

Documents get in through the **ingestion pipeline**:

```
  file / URL ──▶ extract text ──▶ chunk ──▶ embed ──▶ store
               (Tika / Jsoup)   (token     (embedding  (vector
                                 splitter)   model)      store)
```

Key terms: an **embedding** is a float vector capturing a text's meaning; a
**vector store** finds the stored chunks closest to a query vector; **chunking**
splits documents into embeddable, prompt-sized pieces. Part 1's README explains all
of this in depth — [go there for the fundamentals](../rag-tutorial-01-basics/).

## What's new in Part 2

```
rag-tutorial-02-llms/
├── docker-compose.yml                        # NEW: postgres (pgvector) + ollama
├── src/main/java/io/halvic/rag/config/
│   └── VectorStoreConfig.java                # CHANGED: two @Profile beans
└── src/main/resources/
    ├── application.yml                       # CHANGED: model autoconfig off by default
    ├── application-azure.yml                 # NEW: provider profile
    ├── application-openai.yml                # NEW: provider profile
    ├── application-ollama.yml                # NEW: provider profile (offline)
    ├── application-simple.yml                # NEW: store profile
    └── application-pgvector.yml              # NEW: store profile
```

### How the profile switching works

- The base `application.yml` sets every `spring.ai.model.*` selector (`chat`,
  `embedding`, `image`, `moderation`, `audio.*`) to `none`, disabling **all** model
  autoconfiguration even though three provider starters are on the classpath. Each
  provider profile switches exactly one chat + one embedding model back on (e.g.
  `spring.ai.model.chat: ollama`). Setting only `chat`/`embedding` would not be
  enough — the image/audio autoconfigurations default to active and would try to
  build an Azure/OpenAI client without credentials.
- `VectorStoreConfig` declares two mutually exclusive beans: `@Profile("simple")`
  builds the in-memory `SimpleVectorStore`, `@Profile("pgvector")` builds a
  `PgVectorStore` on top of the dockerized Postgres (`initializeSchema(true)` creates
  the table on first start).
- With no profiles at all, `spring.profiles.default: simple,azure` makes the app
  behave exactly like Part 1.

### The `rag.vector-store.dimensions` knob

Embedding dimensionality differs by provider: OpenAI/Azure `text-embedding-3-small`
produces **1536**-dimensional vectors, Ollama's `nomic-embed-text` produces **768**.
pgvector needs the dimensionality up front to create its table schema, so it's a
config property (default 1536; the `ollama` profile overrides it to 768).

> ⚠️ If you switch embedding providers on an existing pgvector database, the old
> table has the wrong dimensionality (and semantically incompatible vectors anyway).
> Drop it and re-ingest: `DROP TABLE vector_store;`

## Prerequisites

- Java 17+, Maven 3.9+, Node.js 20+
- Docker + docker-compose (only for the `pgvector` and/or `ollama` profiles)
- Credentials for whichever cloud provider profile you use:
  - `azure`: `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_ENDPOINT` (+ optional
    `AZURE_OPENAI_CHAT_DEPLOYMENT`, `AZURE_OPENAI_EMBEDDING_DEPLOYMENT`)
  - `openai`: `OPENAI_API_KEY` (+ optional `OPENAI_CHAT_MODEL`, `OPENAI_EMBEDDING_MODEL`)
  - `ollama`: nothing — that's the point

## Run it

### Variant A — like Part 1 (in-memory + Azure)

```bash
export AZURE_OPENAI_API_KEY="..." AZURE_OPENAI_ENDPOINT="https://<resource>.openai.azure.com"
mvn spring-boot:run          # default profiles: simple,azure
```

### Variant B — persistent store + OpenAI API

```bash
docker compose up -d postgres
export OPENAI_API_KEY="sk-..."
mvn spring-boot:run -Dspring-boot.run.profiles=pgvector,openai
```

### Variant C — fully offline (local Mistral + in-memory store)

```bash
docker compose up -d ollama
docker compose exec ollama ollama pull mistral
docker compose exec ollama ollama pull nomic-embed-text
mvn spring-boot:run -Dspring-boot.run.profiles=simple,ollama
```

No API key, no cloud, no data leaves your machine. First answers are slower — the
model runs on your CPU/GPU. Combine with `pgvector` for offline **and** persistent:
`-Dspring-boot.run.profiles=pgvector,ollama`.

### Frontend (unchanged from Part 1)

```bash
cd rag-ui
npm install
npm start        # http://localhost:4200
```

## Try it with curl

The endpoints are identical to Part 1:

```bash
curl -X POST http://localhost:8080/api/ingest/url \
  -H "Content-Type: application/json" \
  -d '{"url": "https://en.wikipedia.org/wiki/Retrieval-augmented_generation"}'

curl -X POST http://localhost:8080/api/ingest/file -F "file=@/path/to/document.pdf"

curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "What is retrieval-augmented generation?"}'

curl http://localhost:8080/api/documents
```

To verify persistence with `pgvector`: ingest, restart the backend, ask again — the
answer still finds the chunks. (With `simple`, they're gone after a restart.)

## Profile combinations at a glance

| Profiles | Store | Provider | Needs |
|---|---|---|---|
| `simple,azure` (default) | in-memory | Azure OpenAI | Azure credentials |
| `simple,openai` | in-memory | OpenAI API | `OPENAI_API_KEY` |
| `simple,ollama` | in-memory | local Mistral | Docker (ollama) |
| `pgvector,azure` | persistent | Azure OpenAI | Docker (postgres) + Azure credentials |
| `pgvector,openai` | persistent | OpenAI API | Docker (postgres) + `OPENAI_API_KEY` |
| `pgvector,ollama` | persistent | local Mistral | Docker (postgres + ollama), fully offline |

## Why these technologies? (viable alternatives)

| Layer | Chosen here | Viable alternatives |
|---|---|---|
| LLM / embeddings | Azure OpenAI, OpenAI, Ollama/Mistral | Anthropic Claude, Google Gemini, Mistral API, Groq, vLLM or LM Studio for local serving |
| Vector store | `SimpleVectorStore`, pgvector | Chroma, Qdrant, Weaviate, Milvus, Pinecone, Redis, Elasticsearch/OpenSearch — Spring AI has adapters for most, so the swap looks just like this part's |
| Backend framework | Spring Boot + Spring AI | LangChain4j, Quarkus, Python (LangChain / LlamaIndex), Node (LangChain.js) |
| Frontend | Angular 18, plain CSS | React, Vue, Svelte, htmx |
| Chunking | Token-based splitting | Sentence/paragraph, recursive character, semantic chunking |
| Retrieval | Plain top-k similarity | Hybrid search (BM25 + vectors), reranking, metadata filtering, query rewriting — still deliberately out of scope |

pgvector was chosen over dedicated vector databases because "it's just Postgres":
familiar operations, backups, SQL access to your chunks — a pragmatic default until
scale demands more.

## What's next

➡️ **[Part 3 — External data sources & chat platforms](../rag-tutorial-03-connectors/)**
adds three optional, off-by-default connectors: Confluence ingestion, a Microsoft
Teams outgoing webhook and a Slack Events API bot — each with request signature
verification and async re-embedding of Q&A pairs.

⬅️ For the fundamentals (what embeddings, chunking and similarity search actually
are), go back to **[Part 1](../rag-tutorial-01-basics/)**.
