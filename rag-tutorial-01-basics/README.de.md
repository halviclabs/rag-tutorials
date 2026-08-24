# RAG-Tutorial Teil 1 — Der Kern-RAG-Loop

> Teil 1 von 4: **Dieser Teil** · [Teil 2 — Austauschbare LLMs](../rag-tutorial-02-llms/) · [Teil 3 — Konnektoren](../rag-tutorial-03-connectors/) · [Teil 4 — Alles + Deployment](../rag-tutorial-04-full/)

🇬🇧 [English version](README.md)

Dies ist die kleinstmögliche funktionierende RAG-Anwendung: ein Spring-Boot-Backend
mit In-Memory-Vektorspeicher und Azure OpenAI als einzigem Modell-Anbieter, dazu ein
Angular-Dashboard. Kein Docker, keine Datenbank, keine optionalen Konnektoren — nur
die Grundlagen.

## Was ist RAG, und warum?

Grosse Sprachmodelle (LLMs) kennen nur, was in ihren Trainingsdaten war. Sie wissen
nichts über eure internen Dokumente, euer Wiki, die Meeting-Notizen von gestern — und
wenn man trotzdem fragt, *halluzinieren* sie gern eine überzeugend klingende Antwort.

**Retrieval-Augmented Generation (RAG)** löst das, ohne das Modell neu zu trainieren.
Zum Zeitpunkt der Frage passieren drei Schritte:

```
              ┌──────────────────────────────────────────────────────┐
  Frage ────▶ │ 1. RETRIEVAL     Ähnlichkeitssuche im Vektorspeicher │
              │                  nach den relevantesten Dokument-    │
              │                  Chunks                              │
              │ 2. AUGMENTATION  diese Chunks als Kontext in den     │
              │                  Prompt einfügen                     │
              │ 3. GENERATION    das LLM antwortet *gestützt* auf    │
              │                  diesen Kontext                      │ ───▶ Antwort
              └──────────────────────────────────────────────────────┘
```

Damit Retrieval funktioniert, müssen Dokumente vorher einmal durch die
**Ingestion-Pipeline**:

```
  Datei / URL ──▶ Text extrahieren ──▶ chunken ──▶ embedden ──▶ speichern
                 (Tika / Jsoup)      (Token-      (Embedding-   (Vektor-
                                      Splitter)     Modell)      speicher)
```

### Zentrale Begriffe

| Begriff | Bedeutung |
|---|---|
| **Embedding** | Ein Vektor aus Gleitkommazahlen (hier: 1536 Dimensionen), der die *Bedeutung* eines Texts erfasst. Texte mit ähnlicher Bedeutung bekommen nahe beieinanderliegende Vektoren. |
| **Vektorspeicher** | Ein Speicher, optimiert für "gib mir die N Chunks, deren Vektoren dem Frage-Vektor am nächsten sind". Teil 1 nutzt den In-Memory-`SimpleVectorStore`. |
| **Chunking** | Lange Dokumente in Stücke teilen, die klein genug sind, um gut embedded zu werden und zu mehreren in einen Prompt zu passen. Hier erledigt durch Spring AIs `TokenTextSplitter`. |
| **Ähnlichkeitssuche** | Die gespeicherten Chunks finden, die dem Embedding der Frage am nächsten sind (Kosinus-Ähnlichkeit). |
| **Augmentierter Prompt** | Der Prompt ans LLM: System-Anweisungen + gefundene Chunks + die Frage des Users. |

## Projektstruktur

```
rag-tutorial-01-basics/
├── pom.xml
├── src/main/java/io/halvic/rag/
│   ├── RagTutorialApplication.java
│   ├── config/
│   │   ├── VectorStoreConfig.java        # In-Memory-SimpleVectorStore-Bean
│   │   └── WebConfig.java                # CORS für den Angular-Dev-Server
│   ├── ingestion/
│   │   ├── DocumentIngestionService.java # extrahieren → chunken → embedden → speichern
│   │   └── IngestedDocument.java         # Eintrag der In-Memory-Ingestion-Historie
│   ├── rag/
│   │   ├── RagService.java               # Retrieval → Augmentation → Generation
│   │   └── RagAnswer.java                # Antwort + verwendete Quell-Chunks
│   └── api/
│       ├── ChatController.java           # POST /api/chat
│       ├── IngestController.java         # POST /api/ingest/file, /api/ingest/url, GET /api/documents
│       └── ApiExceptionHandler.java
├── src/main/resources/application.yml
└── rag-ui/                               # Angular-18-Dashboard (Standalone-Komponenten)
```

## REST-Endpunkte

| Methode | Pfad | Zweck |
|---|---|---|
| `POST` | `/api/ingest/file` | Datei hochladen (Multipart, Feld `file`); Tika extrahiert den Text |
| `POST` | `/api/ingest/url` | Webseite ingestieren (`{"url": "..."}`); Jsoup extrahiert den Text |
| `POST` | `/api/chat` | Frage stellen (`{"question": "..."}`); liefert Antwort + Quell-Chunks |
| `GET` | `/api/documents` | Ingestion-Historie |

## Voraussetzungen

- Java 17+
- Maven 3.9+
- Node.js 20+ (für das Angular-Dashboard)
- Eine **Azure-OpenAI**-Ressource mit zwei Deployments: ein Chat-Modell (z. B.
  `gpt-4o`) und ein Embedding-Modell (z. B. `text-embedding-3-small`)

## Azure OpenAI konfigurieren

Alle Secrets kommen aus Umgebungsvariablen — nichts ist hartcodiert oder eingecheckt:

```bash
export AZURE_OPENAI_API_KEY="<euer Key>"
export AZURE_OPENAI_ENDPOINT="https://<eure-ressource>.openai.azure.com"
# optional, Defaults wie gezeigt:
export AZURE_OPENAI_CHAT_DEPLOYMENT="gpt-4o"
export AZURE_OPENAI_EMBEDDING_DEPLOYMENT="text-embedding-3-small"
```

## Starten

Backend (Port 8080):

```bash
mvn spring-boot:run
```

Frontend (Port 4200, proxied `/api` zum Backend):

```bash
cd rag-ui
npm install
npm start
```

<http://localhost:4200> öffnen, links ein Dokument ingestieren, rechts Fragen dazu
stellen. Die Antworten zeigen, welche Chunks und Quellen verwendet wurden.

## Mit curl ausprobieren

```bash
# Webseite ingestieren
curl -X POST http://localhost:8080/api/ingest/url \
  -H "Content-Type: application/json" \
  -d '{"url": "https://de.wikipedia.org/wiki/Retrieval-Augmented_Generation"}'

# Datei hochladen
curl -X POST http://localhost:8080/api/ingest/file \
  -F "file=@/pfad/zu/dokument.pdf"

# Frage stellen
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "Was ist Retrieval-Augmented Generation?"}'

# Ingestion-Historie abrufen
curl http://localhost:8080/api/documents
```

## Wie der Code zusammenspielt

1. `DocumentIngestionService` extrahiert Text (Tika für Dateien, Jsoup für URLs),
   teilt ihn mit dem `TokenTextSplitter` und ruft `VectorStore.add(...)` auf — das
   embeddet jeden Chunk über das Azure-OpenAI-Embedding-Modell und speichert ihn im
   `SimpleVectorStore`.
2. `RagService.ask(...)` embeddet die Frage, macht eine Ähnlichkeitssuche (`top-k`
   ist über `rag.retrieval.top-k` konfigurierbar), baut aus den Treffern einen
   augmentierten Prompt und ruft das Azure-OpenAI-Chat-Modell auf.
3. Die Controller sind dünne HTTP-Wrapper; das Angular-Dashboard ist ein dünner
   Client über denselben vier Endpunkten.

## Warum diese Technologien? (mögliche Alternativen)

| Ebene | Hier gewählt | Mögliche Alternativen |
|---|---|---|
| LLM / Embeddings | Azure OpenAI | OpenAI API, Anthropic Claude, Mistral, Google Gemini, lokale Modelle via Ollama (Teile 2+ ergänzen zwei davon) |
| Vektorspeicher | In-Memory-`SimpleVectorStore` | pgvector (Teil 2), Chroma, Qdrant, Weaviate, Milvus, Pinecone, Elasticsearch/OpenSearch |
| Backend-Framework | Spring Boot + Spring AI | LangChain4j, Quarkus + LangChain4j, Python (LangChain / LlamaIndex), Node (LangChain.js) |
| Frontend | Angular 18, pures CSS | React, Vue, Svelte, htmx, oder gar kein UI (nur API) |
| Chunking | Token-basiertes Splitting | Satz-/Absatz-Splitting, rekursives Zeichen-Splitting, semantisches Chunking, Splitting pro Überschrift |
| Retrieval | Einfaches Top-k | Hybrid-Suche (BM25 + Vektoren), Reranking (z. B. Cross-Encoder), Metadaten-Filter, Query-Rewriting — hier bewusst ausgeklammert, damit der Loop sichtbar bleibt |

Der Sinn von Teil 1 ist, jedes bewegliche Teil sichtbar zu halten: ein Anbieter, ein
In-Memory-Speicher, eine Retrieval-Strategie — damit der RAG-Loop selbst die ganze
Geschichte ist.

## Bewusste Einschränkungen von Teil 1

- Der Vektorspeicher ist **in-memory**: Backend neu starten, und alles ist weg.
- Azure OpenAI ist als einziger Anbieter **fest verdrahtet**.
- Keine Authentifizierung, keine Persistenz, keine externen Konnektoren.

## Wie geht's weiter?

➡️ **[Teil 2 — Austauschbare LLMs & persistenter Vektorspeicher](../rag-tutorial-02-llms/)**
ergänzt Spring-Profile zum Umschalten zwischen Azure OpenAI, der normalen OpenAI-API
und einem komplett offline laufenden lokalen Mistral (via Ollama), plus einen
persistenten PostgreSQL-+-pgvector-Speicher — alles per docker-compose.
