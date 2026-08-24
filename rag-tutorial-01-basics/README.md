# RAG Tutorial Part 1 — The Core RAG Loop

> Part 1 of 4: **This Part** · [Part 2 — Swappable LLMs](../rag-tutorial-02-llms/) · [Part 3 — Connectors](../rag-tutorial-03-connectors/) · [Part 4 — Everything + Deployment](../rag-tutorial-04-full/)

🇩🇪 [Deutsche Version](README.de.md)

This is the smallest possible working RAG application: a Spring Boot backend with an
in-memory vector store and Azure OpenAI as the only model provider, plus an Angular
dashboard. No Docker, no database, no optional connectors — just the fundamentals.

## What is RAG, and why?

Large language models (LLMs) only know what was in their training data. They know
nothing about your internal documents, your wiki, yesterday's meeting notes — and
when asked anyway, they tend to *hallucinate* a confident-sounding answer.

**Retrieval-Augmented Generation (RAG)** fixes this without retraining the model.
At question time, three steps happen:

```
                ┌────────────────────────────────────────────────────┐
  question ───▶ │ 1. RETRIEVAL     similarity-search the vector      │
                │                  store for the most relevant       │
                │                  document chunks                   │
                │ 2. AUGMENTATION  put those chunks into the prompt  │
                │                  as context                        │
                │ 3. GENERATION    the LLM answers *grounded* in     │
                │                  that context                      │ ───▶ answer
                └────────────────────────────────────────────────────┘
```

For retrieval to work, documents must first pass through the **ingestion pipeline**:

```
  file / URL ──▶ extract text ──▶ chunk ──▶ embed ──▶ store
               (Tika / Jsoup)   (token     (embedding  (vector
                                 splitter)   model)      store)
```

### Key terms

| Term | Meaning |
|---|---|
| **Embedding** | A vector of floats (here: 1536 dimensions) that captures the *meaning* of a text. Texts with similar meaning get vectors that are close together. |
| **Vector store** | A store optimized for "give me the N chunks whose vectors are closest to this query vector". Part 1 uses the in-memory `SimpleVectorStore`. |
| **Chunking** | Splitting long documents into pieces small enough to embed well and to fit several of them into one prompt. Done here by Spring AI's `TokenTextSplitter`. |
| **Similarity search** | Finding the stored chunks nearest to the question's embedding (cosine similarity). |
| **Augmented prompt** | The prompt sent to the LLM: system instructions + retrieved chunks + the user's question. |

## Project structure

```
rag-tutorial-01-basics/
├── pom.xml
├── src/main/java/io/halvic/rag/
│   ├── RagTutorialApplication.java
│   ├── config/
│   │   ├── VectorStoreConfig.java        # in-memory SimpleVectorStore bean
│   │   └── WebConfig.java                # CORS for the Angular dev server
│   ├── ingestion/
│   │   ├── DocumentIngestionService.java # extract → chunk → embed → store
│   │   └── IngestedDocument.java         # in-memory ingestion history entry
│   ├── rag/
│   │   ├── RagService.java               # retrieval → augmentation → generation
│   │   └── RagAnswer.java                # answer + the source chunks used
│   └── api/
│       ├── ChatController.java           # POST /api/chat
│       ├── IngestController.java         # POST /api/ingest/file, /api/ingest/url, GET /api/documents
│       └── ApiExceptionHandler.java
├── src/main/resources/application.yml
└── rag-ui/                               # Angular 18 dashboard (standalone components)
```

## REST endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/ingest/file` | Upload a file (multipart, field `file`); Tika extracts the text |
| `POST` | `/api/ingest/url` | Ingest a web page (`{"url": "..."}`); Jsoup extracts the text |
| `POST` | `/api/chat` | Ask a question (`{"question": "..."}`); returns answer + source chunks |
| `GET` | `/api/documents` | Ingestion history |

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 20+ (for the Angular dashboard)
- An **Azure OpenAI** resource with two deployments: a chat model (e.g. `gpt-4o`)
  and an embedding model (e.g. `text-embedding-3-small`)

## Configure Azure OpenAI

All secrets come from environment variables — nothing is hardcoded or committed:

```bash
export AZURE_OPENAI_API_KEY="<your key>"
export AZURE_OPENAI_ENDPOINT="https://<your-resource>.openai.azure.com"
# optional, defaults shown:
export AZURE_OPENAI_CHAT_DEPLOYMENT="gpt-4o"
export AZURE_OPENAI_EMBEDDING_DEPLOYMENT="text-embedding-3-small"
```

## Run it

Backend (port 8080):

```bash
mvn spring-boot:run
```

Frontend (port 4200, proxies `/api` to the backend):

```bash
cd rag-ui
npm install
npm start
```

Open <http://localhost:4200>, ingest a document on the left, ask about it on the right.
The chat answers show which chunks and sources were used.

## Try it with curl

```bash
# ingest a web page
curl -X POST http://localhost:8080/api/ingest/url \
  -H "Content-Type: application/json" \
  -d '{"url": "https://en.wikipedia.org/wiki/Retrieval-augmented_generation"}'

# upload a file
curl -X POST http://localhost:8080/api/ingest/file \
  -F "file=@/path/to/document.pdf"

# ask a question
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "What is retrieval-augmented generation?"}'

# list what has been ingested
curl http://localhost:8080/api/documents
```

## How the code fits together

1. `DocumentIngestionService` extracts text (Tika for files, Jsoup for URLs), splits
   it with `TokenTextSplitter`, and calls `VectorStore.add(...)` — which embeds every
   chunk via the Azure OpenAI embedding model and stores it in the `SimpleVectorStore`.
2. `RagService.ask(...)` embeds the question, runs a similarity search (`top-k` is
   configurable via `rag.retrieval.top-k`), builds an augmented prompt from the hits,
   and calls the Azure OpenAI chat model.
3. The controllers are thin HTTP wrappers; the Angular dashboard is a thin client
   over the same four endpoints.

## Why these technologies? (viable alternatives)

| Layer | Chosen here | Viable alternatives |
|---|---|---|
| LLM / embeddings | Azure OpenAI | OpenAI API, Anthropic Claude, Mistral, Google Gemini, local models via Ollama (Parts 2+ add two of these) |
| Vector store | In-memory `SimpleVectorStore` | pgvector (Part 2), Chroma, Qdrant, Weaviate, Milvus, Pinecone, Elasticsearch/OpenSearch |
| Backend framework | Spring Boot + Spring AI | LangChain4j, Quarkus + LangChain4j, Python (LangChain / LlamaIndex), Node (LangChain.js) |
| Frontend | Angular 18, plain CSS | React, Vue, Svelte, htmx, or no UI at all (API only) |
| Chunking | Token-based splitting | Sentence/paragraph splitting, recursive character splitting, semantic chunking, per-heading splitting |
| Retrieval | Plain top-k similarity | Hybrid search (BM25 + vectors), reranking (e.g. cross-encoders), metadata filtering, query rewriting — deliberately out of scope here to keep the loop visible |

The point of Part 1 is to keep every moving part visible: one provider, one
in-memory store, one retrieval strategy — so the RAG loop itself is the whole story.

## Limitations of Part 1 (by design)

- The vector store is **in-memory**: restart the backend and everything is gone.
- Azure OpenAI is **hardcoded** as the only provider.
- No authentication, no persistence, no external connectors.

## What's next

➡️ **[Part 2 — Swappable LLMs & persistent vector store](../rag-tutorial-02-llms/)**
adds Spring profiles to switch between Azure OpenAI, the plain OpenAI API and a fully
offline local Mistral (via Ollama), plus a persistent PostgreSQL + pgvector store —
all via docker-compose.
