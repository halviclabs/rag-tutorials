# RAG-Tutorial Teil 2 — Austauschbare LLMs & persistenter Vektorspeicher

> Teil 2 von 4: [Teil 1 — Grundlagen](../rag-tutorial-01-basics/) · **Dieser Teil** · [Teil 3 — Konnektoren](../rag-tutorial-03-connectors/) · [Teil 4 — Alles + Deployment](../rag-tutorial-04-full/)

🇬🇧 [English version](README.md)

Teil 2 nimmt den funktionierenden RAG-Loop aus Teil 1 und macht die beiden grossen
Infrastruktur-Entscheidungen **per Spring-Profil austauschbar**:

- **LLM-/Embedding-Anbieter**: `azure` (Azure OpenAI), `openai` (normale
  OpenAI-API) oder `ollama` (lokales Mistral via Docker — komplett offline, ohne
  API-Key).
- **Vektorspeicher**: `simple` (in-memory, wie in Teil 1) oder `pgvector`
  (persistentes PostgreSQL + pgvector via docker-compose).

Jedes Store-Profil lässt sich mit jedem Anbieter-Profil kombinieren:
`pgvector,openai`, `simple,ollama` usw. Der Anwendungscode (`RagService`,
`DocumentIngestionService`) ist **unverändert** — genau darum geht es: Er spricht
nur mit Spring AIs Abstraktionen `ChatModel`, `EmbeddingModel` und `VectorStore`.
Auch das Angular-Dashboard ist unverändert aus Teil 1.

## Was ist RAG? (Kurzfassung, damit dieses README allein steht)

LLMs kennen nur ihre Trainingsdaten. **Retrieval-Augmented Generation** stützt ihre
Antworten auf *eure* Dokumente — zum Zeitpunkt der Frage:

```
              ┌──────────────────────────────────────────────────────┐
  Frage ────▶ │ 1. RETRIEVAL     Ähnlichkeitssuche im Vektorspeicher │
              │                  nach den relevantesten Chunks       │
              │ 2. AUGMENTATION  diese Chunks in den Prompt einfügen │
              │ 3. GENERATION    das LLM antwortet darauf gestützt   │ ───▶ Antwort
              └──────────────────────────────────────────────────────┘
```

Dokumente kommen über die **Ingestion-Pipeline** hinein:

```
  Datei / URL ──▶ Text extrahieren ──▶ chunken ──▶ embedden ──▶ speichern
                 (Tika / Jsoup)      (Token-      (Embedding-   (Vektor-
                                      Splitter)     Modell)      speicher)
```

Zentrale Begriffe: ein **Embedding** ist ein Float-Vektor, der die Bedeutung eines
Texts erfasst; ein **Vektorspeicher** findet die gespeicherten Chunks, die einem
Frage-Vektor am nächsten sind; **Chunking** teilt Dokumente in embeddbare,
prompt-taugliche Stücke. Das README von Teil 1 erklärt das alles ausführlich —
[dort geht's zu den Grundlagen](../rag-tutorial-01-basics/).

## Neu in Teil 2

```
rag-tutorial-02-llms/
├── docker-compose.yml                        # NEU: postgres (pgvector) + ollama
├── src/main/java/io/halvic/rag/config/
│   └── VectorStoreConfig.java                # GEÄNDERT: zwei @Profile-Beans
└── src/main/resources/
    ├── application.yml                       # GEÄNDERT: Modell-Autoconfig standardmässig aus
    ├── application-azure.yml                 # NEU: Anbieter-Profil
    ├── application-openai.yml                # NEU: Anbieter-Profil
    ├── application-ollama.yml                # NEU: Anbieter-Profil (offline)
    ├── application-simple.yml                # NEU: Store-Profil
    └── application-pgvector.yml              # NEU: Store-Profil
```

### Wie das Profil-Umschalten funktioniert

- Das Basis-`application.yml` setzt jeden `spring.ai.model.*`-Selektor (`chat`,
  `embedding`, `image`, `moderation`, `audio.*`) auf `none` und deaktiviert damit
  **alle** Modell-Autokonfigurationen, obwohl drei Anbieter-Starter auf dem
  Classpath liegen. Jedes Anbieter-Profil schaltet genau ein Chat- + ein
  Embedding-Modell wieder ein (z. B. `spring.ai.model.chat: ollama`). Nur
  `chat`/`embedding` zu setzen würde nicht reichen — die
  Image-/Audio-Autokonfigurationen sind standardmässig aktiv und würden versuchen,
  einen Azure-/OpenAI-Client ohne Zugangsdaten zu bauen.
- `VectorStoreConfig` deklariert zwei sich ausschliessende Beans:
  `@Profile("simple")` baut den In-Memory-`SimpleVectorStore`,
  `@Profile("pgvector")` baut einen `PgVectorStore` auf dem dockerisierten
  Postgres (`initializeSchema(true)` legt die Tabelle beim ersten Start an).
- Ganz ohne Profile sorgt `spring.profiles.default: simple,azure` dafür, dass sich
  die App exakt wie Teil 1 verhält.

### Der Konfigurationsknopf `rag.vector-store.dimensions`

Die Embedding-Dimensionalität hängt vom Anbieter ab: OpenAI/Azure
`text-embedding-3-small` erzeugt **1536**-dimensionale Vektoren, Ollamas
`nomic-embed-text` erzeugt **768**. pgvector braucht die Dimensionalität vorab, um
sein Tabellenschema anzulegen — deshalb ist sie eine Config-Property (Default 1536;
das `ollama`-Profil überschreibt sie mit 768).

> ⚠️ Wer den Embedding-Anbieter auf einer bestehenden pgvector-Datenbank wechselt,
> hat eine Tabelle mit falscher Dimensionalität (und ohnehin semantisch
> inkompatible Vektoren). Tabelle löschen und neu ingestieren:
> `DROP TABLE vector_store;`

## Voraussetzungen

- Java 17+, Maven 3.9+, Node.js 20+
- Docker + docker-compose (nur für die Profile `pgvector` und/oder `ollama`)
- Zugangsdaten für das jeweils genutzte Cloud-Anbieter-Profil:
  - `azure`: `AZURE_OPENAI_API_KEY`, `AZURE_OPENAI_ENDPOINT` (+ optional
    `AZURE_OPENAI_CHAT_DEPLOYMENT`, `AZURE_OPENAI_EMBEDDING_DEPLOYMENT`)
  - `openai`: `OPENAI_API_KEY` (+ optional `OPENAI_CHAT_MODEL`, `OPENAI_EMBEDDING_MODEL`)
  - `ollama`: nichts — das ist der Witz daran

## Starten

### Variante A — wie Teil 1 (in-memory + Azure)

```bash
export AZURE_OPENAI_API_KEY="..." AZURE_OPENAI_ENDPOINT="https://<ressource>.openai.azure.com"
mvn spring-boot:run          # Default-Profile: simple,azure
```

### Variante B — persistenter Speicher + OpenAI-API

```bash
docker compose up -d postgres
export OPENAI_API_KEY="sk-..."
mvn spring-boot:run -Dspring-boot.run.profiles=pgvector,openai
```

### Variante C — komplett offline (lokales Mistral + In-Memory-Speicher)

```bash
docker compose up -d ollama
docker compose exec ollama ollama pull mistral
docker compose exec ollama ollama pull nomic-embed-text
mvn spring-boot:run -Dspring-boot.run.profiles=simple,ollama
```

Kein API-Key, keine Cloud, keine Daten verlassen den Rechner. Die ersten Antworten
sind langsamer — das Modell läuft auf eurer CPU/GPU. Kombiniert mit `pgvector` wird
es offline **und** persistent: `-Dspring-boot.run.profiles=pgvector,ollama`.

### Frontend (unverändert aus Teil 1)

```bash
cd rag-ui
npm install
npm start        # http://localhost:4200
```

## Mit curl ausprobieren

Die Endpunkte sind identisch mit Teil 1:

```bash
curl -X POST http://localhost:8080/api/ingest/url \
  -H "Content-Type: application/json" \
  -d '{"url": "https://de.wikipedia.org/wiki/Retrieval-Augmented_Generation"}'

curl -X POST http://localhost:8080/api/ingest/file -F "file=@/pfad/zu/dokument.pdf"

curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "Was ist Retrieval-Augmented Generation?"}'

curl http://localhost:8080/api/documents
```

Persistenz mit `pgvector` prüfen: ingestieren, Backend neu starten, nochmal fragen —
die Antwort findet die Chunks weiterhin. (Mit `simple` sind sie nach dem Neustart weg.)

## Profil-Kombinationen im Überblick

| Profile | Speicher | Anbieter | Braucht |
|---|---|---|---|
| `simple,azure` (Default) | in-memory | Azure OpenAI | Azure-Zugangsdaten |
| `simple,openai` | in-memory | OpenAI-API | `OPENAI_API_KEY` |
| `simple,ollama` | in-memory | lokales Mistral | Docker (ollama) |
| `pgvector,azure` | persistent | Azure OpenAI | Docker (postgres) + Azure-Zugangsdaten |
| `pgvector,openai` | persistent | OpenAI-API | Docker (postgres) + `OPENAI_API_KEY` |
| `pgvector,ollama` | persistent | lokales Mistral | Docker (postgres + ollama), komplett offline |

## Warum diese Technologien? (mögliche Alternativen)

| Ebene | Hier gewählt | Mögliche Alternativen |
|---|---|---|
| LLM / Embeddings | Azure OpenAI, OpenAI, Ollama/Mistral | Anthropic Claude, Google Gemini, Mistral-API, Groq, vLLM oder LM Studio fürs lokale Serving |
| Vektorspeicher | `SimpleVectorStore`, pgvector | Chroma, Qdrant, Weaviate, Milvus, Pinecone, Redis, Elasticsearch/OpenSearch — Spring AI hat Adapter für die meisten, der Wechsel sieht genauso aus wie hier |
| Backend-Framework | Spring Boot + Spring AI | LangChain4j, Quarkus, Python (LangChain / LlamaIndex), Node (LangChain.js) |
| Frontend | Angular 18, pures CSS | React, Vue, Svelte, htmx |
| Chunking | Token-basiertes Splitting | Satz-/Absatz-, rekursives Zeichen-, semantisches Chunking |
| Retrieval | Einfaches Top-k | Hybrid-Suche (BM25 + Vektoren), Reranking, Metadaten-Filter, Query-Rewriting — weiterhin bewusst ausgeklammert |

pgvector wurde dedizierten Vektordatenbanken vorgezogen, weil es "einfach Postgres"
ist: vertraute Ops, Backups, SQL-Zugriff auf die Chunks — ein pragmatischer
Standard, bis die Skalierung mehr verlangt.

## Wie geht's weiter?

➡️ **[Teil 3 — Externe Datenquellen & Chat-Plattformen](../rag-tutorial-03-connectors/)**
ergänzt drei optionale, standardmässig deaktivierte Konnektoren:
Confluence-Ingestion, einen Microsoft-Teams-Outgoing-Webhook und einen
Slack-Events-API-Bot — jeweils mit Signaturprüfung und asynchronem Re-Embedding von
Q&A-Paaren.

⬅️ Für die Grundlagen (was Embeddings, Chunking und Ähnlichkeitssuche wirklich
sind) zurück zu **[Teil 1](../rag-tutorial-01-basics/)**.
