# RAG Tutorial Series — Java + Spring AI + Angular

A four-part, progressively-built tutorial series that teaches **Retrieval-Augmented
Generation (RAG)** from first principles to a production deployment. Each part is a
**separate, fully self-contained, independently runnable Maven project** — you can
clone (or copy) a single part's folder and have everything you need. Later parts are
supersets of earlier parts.

Every part ships with an English (`README.md`) and a German (`README.de.md`) walkthrough.

## What is RAG?

Large language models only know what was in their training data. RAG lets a model
answer questions about **your** documents by combining three steps at question time:

```
                 ┌─────────────────────────────────────────────────────┐
                 │                     RAG at question time            │
                 │                                                     │
  question ───▶  │  1. RETRIEVAL      similarity-search the vector     │
                 │                    store for the most relevant      │
                 │                    document chunks                  │
                 │  2. AUGMENTATION   put those chunks into the        │
                 │                    prompt as context                │
                 │  3. GENERATION     let the LLM answer *grounded*    │
                 │                    in that context                  │
                 └─────────────────────────────────────────────────────┘
```

Before any question can be answered, documents must go through the **ingestion
pipeline** once:

```
  file / URL ──▶ extract text ──▶ chunk ──▶ embed ──▶ store
               (Tika / Jsoup)   (token     (embedding  (vector
                                 splitter)   model)      store)
```

- **Embedding** — a vector of floats that captures the *meaning* of a text; texts
  with similar meaning get vectors that are close together.
- **Vector store** — a database optimized for "give me the N stored chunks whose
  vectors are closest to this query vector".
- **Chunking** — splitting long documents into pieces small enough to embed well
  and to fit several of them into one prompt.

## The four parts

| Part | Folder | What it adds |
|---|---|---|
| 1 | [`rag-tutorial-01-basics`](rag-tutorial-01-basics/) | The smallest working RAG loop: Spring Boot + in-memory `SimpleVectorStore` + Azure OpenAI, file/URL ingestion (Tika + Jsoup), Angular dashboard |
| 2 | [`rag-tutorial-02-llms`](rag-tutorial-02-llms/) | Swappable LLM providers (`azure` / `openai` / `ollama` — incl. fully offline local Mistral) and a persistent `pgvector` store, all via Spring profiles + docker-compose |
| 3 | [`rag-tutorial-03-connectors`](rag-tutorial-03-connectors/) | Optional, off-by-default connectors: Confluence ingestion, Microsoft Teams outgoing webhook, Slack Events API bot — each with signature verification and async Q&A re-embedding |
| 4 | [`rag-tutorial-04-full`](rag-tutorial-04-full/) | Everything combined + production packaging: Dockerfiles for backend and frontend (nginx), and a Helm chart with optional Postgres/pgvector and Ollama deployments |

## Tech stack (fixed across all parts)

| Building block | Choice |
|---|---|
| Backend language | Java 17 |
| Backend framework | Spring Boot 3.4 + Spring AI 1.0 |
| LLM / Embeddings | Azure OpenAI, OpenAI API, local Mistral via Ollama (Docker) — swappable via Spring profile (from Part 2) |
| Vector store | In-memory `SimpleVectorStore` and PostgreSQL + `pgvector` — swappable via Spring profile (from Part 2) |
| Document sources | Local files (PDF/DOCX/TXT via Apache Tika) and web URLs (Jsoup); Confluence from Part 3 |
| Frontend | Angular 18 standalone components, plain CSS, no UI framework |

> Note on versions: the original series plan said "Spring Boot 3.3", but Spring AI 1.0
> requires Spring Boot 3.4.x — all parts therefore use Spring Boot 3.4.

## How to use this series

Start with Part 1 and read its README top to bottom — it explains every concept.
Each later README repeats the fundamentals so it also works standalone, then focuses
on what is new. If you only want the final result, jump straight to Part 4.
