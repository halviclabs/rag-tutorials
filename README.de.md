# RAG-Tutorial-Serie — Java + Spring AI + Angular

🇬🇧 [English version](README.md)

Eine vierteilige, aufeinander aufbauende Tutorial-Serie, die **Retrieval-Augmented
Generation (RAG)** von den Grundlagen bis zum Produktions-Deployment lehrt. Jeder
Teil ist ein **eigenständiges, unabhängig lauffähiges Maven-Projekt** — man kann
einen einzelnen Teil klonen (oder kopieren) und hat alles, was man braucht.
Spätere Teile sind Obermengen der früheren.

Jeder Teil enthält einen englischen (`README.md`) und einen deutschen
(`README.de.md`) Walkthrough.

## Was ist RAG?

Grosse Sprachmodelle kennen nur ihre Trainingsdaten. RAG lässt ein Modell Fragen
zu **euren** Dokumenten beantworten, indem es zum Zeitpunkt der Frage drei
Schritte kombiniert:

```
              ┌─────────────────────────────────────────────────────┐
              │                 RAG zum Zeitpunkt der Frage         │
              │                                                     │
  Frage ───▶  │  1. RETRIEVAL      Ähnlichkeitssuche im Vektor-     │
              │                    speicher nach den relevantesten  │
              │                    Dokument-Chunks                  │
              │  2. AUGMENTATION   diese Chunks als Kontext in      │
              │                    den Prompt einfügen              │
              │  3. GENERATION     das LLM antwortet *gestützt*     │
              │                    auf diesen Kontext               │
              └─────────────────────────────────────────────────────┘
```

Bevor eine Frage beantwortet werden kann, durchläuft jedes Dokument einmal die
**Ingestion-Pipeline**:

```
  Datei / URL ──▶ Text extrahieren ──▶ chunken ──▶ embedden ──▶ speichern
                 (Tika / Jsoup)      (Token-      (Embedding-   (Vektor-
                                      Splitter)     Modell)      speicher)
```

- **Embedding** — ein Vektor aus Gleitkommazahlen, der die *Bedeutung* eines
  Texts erfasst; Texte mit ähnlicher Bedeutung bekommen nahe beieinanderliegende
  Vektoren.
- **Vektorspeicher** — eine Datenbank, optimiert für „gib mir die N gespeicherten
  Chunks, deren Vektoren diesem Frage-Vektor am nächsten sind".
- **Chunking** — lange Dokumente in Stücke teilen, die klein genug sind, um gut
  embedded zu werden und zu mehreren in einen Prompt zu passen.

## Die vier Teile

| Teil | Ordner | Was er ergänzt |
|---|---|---|
| 1 | [`rag-tutorial-01-basics`](rag-tutorial-01-basics/) | Der kleinste funktionierende RAG-Loop: Spring Boot + In-Memory-`SimpleVectorStore` + Azure OpenAI, Datei-/URL-Ingestion (Tika + Jsoup), Angular-Dashboard |
| 2 | [`rag-tutorial-02-llms`](rag-tutorial-02-llms/) | Austauschbare LLM-Anbieter (`azure` / `openai` / `ollama` — inkl. komplett offline mit lokalem Mistral) und ein persistenter `pgvector`-Speicher, alles via Spring-Profile + docker-compose |
| 3 | [`rag-tutorial-03-connectors`](rag-tutorial-03-connectors/) | Optionale, standardmässig deaktivierte Konnektoren: Confluence-Ingestion, Microsoft-Teams-Outgoing-Webhook, Slack-Events-API-Bot — jeweils mit Signaturprüfung und asynchronem Q&A-Re-Embedding |
| 4 | [`rag-tutorial-04-full`](rag-tutorial-04-full/) | Alles kombiniert + Produktions-Packaging: Dockerfiles für Backend und Frontend (nginx) und ein Helm-Chart mit optionalen Postgres/pgvector- und Ollama-Deployments |

## Tech-Stack (fix über alle Teile)

| Baustein | Wahl |
|---|---|
| Backend-Sprache | Java 17 |
| Backend-Framework | Spring Boot 3.4 + Spring AI 1.0 |
| LLM / Embeddings | Azure OpenAI, OpenAI-API, lokales Mistral via Ollama (Docker) — per Spring-Profil austauschbar (ab Teil 2) |
| Vektorspeicher | In-Memory-`SimpleVectorStore` und PostgreSQL + `pgvector` — per Spring-Profil austauschbar (ab Teil 2) |
| Dokumentquellen | Lokale Dateien (PDF/DOCX/TXT via Apache Tika) und Web-URLs (Jsoup); Confluence ab Teil 3 |
| Frontend | Angular 18 Standalone-Komponenten, pures CSS, kein UI-Framework |

> Hinweis zu den Versionen: Der ursprüngliche Serienplan nannte „Spring Boot 3.3",
> aber Spring AI 1.0 setzt Spring Boot 3.4.x voraus — alle Teile nutzen deshalb
> Spring Boot 3.4.

## Wie man diese Serie nutzt

Mit Teil 1 anfangen und dessen README von oben bis unten lesen — es erklärt jedes
Konzept. Jedes spätere README wiederholt die Grundlagen, damit es auch für sich
allein funktioniert, und konzentriert sich dann auf das Neue. Wer nur das
Endergebnis will, springt direkt zu Teil 4.
