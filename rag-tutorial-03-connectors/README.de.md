# RAG-Tutorial Teil 3 — Externe Datenquellen & Chat-Plattformen

> Teil 3 von 4: [Teil 1 — Grundlagen](../rag-tutorial-01-basics/) · [Teil 2 — Austauschbare LLMs](../rag-tutorial-02-llms/) · **Dieser Teil** · [Teil 4 — Alles + Deployment](../rag-tutorial-04-full/)

🇬🇧 [English version](README.md)

Teil 3 nimmt Teil 2 (austauschbare Anbieter + austauschbarer Vektorspeicher) und
ergänzt drei **optionale, standardmässig deaktivierte Konnektoren**, die euer
RAG-System mit den Tools verbinden, die ein Team ohnehin nutzt:

1. **Confluence-Ingestion** — Wiki-Seiten (einzeln oder ganzer Space) in den
   Vektorspeicher ziehen.
2. **Microsoft Teams** — ein Outgoing Webhook: Bot im Kanal @-erwähnen, RAG-Antwort
   zurückbekommen.
3. **Slack** — ein Events-API-Bot: App @-erwähnen, die Antwort kommt im Thread.

Jeder Konnektor aktiviert sich erst, wenn seine Schlüssel-Property gesetzt ist
(Feature-Flags via `@ConditionalOnProperty` — keine always-on Beans). Ohne
Konfiguration verhält sich die App exakt wie Teil 2: Die Konnektor-Beans existieren
gar nicht, ihre Endpunkte antworten mit 404.

Beide Chat-Konnektoren **re-embedden jedes beantwortete Q&A-Paar** in den
Vektorspeicher — künftige Fragen (aus dem Chat oder dem Dashboard) können frühere
Antworten wiederfinden.

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

Dokumente kommen über die **Ingestion-Pipeline** hinein — und der Kern von Teil 3
ist, dass jetzt *mehr Quellen* sie füttern:

```
  Datei / URL / Confluence-Seite / Chat-Q&A-Paar
        │
        ▼
  Text extrahieren ──▶ chunken ──▶ embedden ──▶ speichern
 (Tika/Jsoup/         (Token-     (Embedding-   (Vektor-
  Confluence-API)      Splitter)    Modell)      speicher)
```

Zentrale Begriffe: ein **Embedding** ist ein Float-Vektor, der die Bedeutung eines
Texts erfasst; ein **Vektorspeicher** findet die gespeicherten Chunks, die einem
Frage-Vektor am nächsten sind; **Chunking** teilt Dokumente in embeddbare Stücke.
[Teil 1 erklärt die Grundlagen ausführlich](../rag-tutorial-01-basics/); das
Profil-System aus Teil 2 (Anbieter `azure`/`openai`/`ollama` × Speicher
`simple`/`pgvector`) ist [hier dokumentiert](../rag-tutorial-02-llms/) und
funktioniert in diesem Teil unverändert.

## Projektstruktur (neu in Teil 3)

```
rag-tutorial-03-connectors/
└── src/main/java/io/halvic/rag/
    ├── config/AsyncConfig.java                    # NEU: @EnableAsync
    ├── ingestion/confluence/                      # NEU: Confluence-Konnektor
    │   ├── ConfluenceProperties.java
    │   ├── ConfluenceClient.java                  # REST-Client, Cloud- + Server/DC-Auth
    │   ├── ConfluenceIngestionService.java
    │   └── ConfluenceIngestController.java        # POST /api/ingest/confluence/{page,space}
    ├── teams/                                     # NEU: Microsoft-Teams-Konnektor
    │   ├── TeamsProperties.java
    │   ├── TeamsWebhookPayload.java
    │   ├── TeamsSignatureVerifier.java            # HMAC-SHA256-Prüfung
    │   ├── TeamsIngestionService.java             # asynchrones Q&A-Re-Embedding
    │   └── TeamsWebhookController.java            # POST /teams/webhook
    └── slack/                                     # NEU: Slack-Konnektor
        ├── SlackProperties.java
        ├── SlackEventPayload.java
        ├── SlackSignatureVerifier.java            # Signing-Secret + Timestamp-Prüfung
        ├── SlackClient.java                       # chat.postMessage-Wrapper
        ├── SlackIngestionService.java             # asynchrones Q&A-Re-Embedding
        ├── SlackEventHandler.java                 # asynchroner Antwort-Flow
        └── SlackEventsController.java             # POST /slack/events
```

Alles aus Teil 1–2 (Kern-RAG-Loop, Dashboard, Profile, docker-compose) ist
unverändert enthalten.

## Voraussetzungen

- Alles aus Teil 2 (Java 17+, Maven, Node 20+, optional Docker,
  Anbieter-Zugangsdaten für `azure`/`openai` — oder keine für `ollama`).
- Pro Konnektor (nur wenn ihr ihn aktiviert):
  - **Confluence**: ein Confluence-Cloud-API-Token oder ein Server/DC Personal
    Access Token.
  - **Teams**: Berechtigung, in einem Team einen *Outgoing Webhook* anzulegen, plus
    ein öffentlicher HTTPS-Endpunkt (z. B. [ngrok](https://ngrok.com)) — Teams kann
    kein `localhost` aufrufen.
  - **Slack**: Berechtigung, im Workspace eine Slack-App anzulegen, plus ein
    öffentlicher HTTPS-Endpunkt (wieder ngrok) für die Events API.

## Konnektor 1 — Confluence-Ingestion

### 1.1 Zugangsdaten besorgen

**Confluence Cloud** (`https://<org>.atlassian.net/wiki`):
1. <https://id.atlassian.com/manage-profile/security/api-tokens> öffnen.
2. *Create API token*, Token kopieren.
3. Authentifiziert wird mit `E-Mail + API-Token`.

**Confluence Server / Data Center**:
1. Profil → *Persönliche Zugriffstoken* → Token erstellen.
2. Authentifiziert wird nur mit diesem PAT (Bearer).

### 1.2 Konfigurieren & starten

```bash
export CONFLUENCE_BASE_URL="https://eure-org.atlassian.net/wiki"
# Cloud:
export CONFLUENCE_EMAIL="ihr@example.com"
export CONFLUENCE_API_TOKEN="..."
# — oder stattdessen Server/DC:
# export CONFLUENCE_PAT="..."

RAG_CONFLUENCE_BASE_URL="$CONFLUENCE_BASE_URL" \
RAG_CONFLUENCE_EMAIL="$CONFLUENCE_EMAIL" \
RAG_CONFLUENCE_API_TOKEN="$CONFLUENCE_API_TOKEN" \
mvn spring-boot:run
```

(Jeder Spring-Konfigurationsstil funktioniert: `RAG_CONFLUENCE_*`-Umgebungsvariablen,
oder den `rag.confluence`-Block im `application.yml` einkommentieren und dort auf
eure Env-Vars verweisen.)

### 1.3 Benutzen

```bash
# eine Seite ingestieren (die Seiten-ID steht in der URL: .../pages/123456789/Titel)
curl -X POST http://localhost:8080/api/ingest/confluence/page \
  -H "Content-Type: application/json" \
  -d '{"pageId": "123456789"}'

# bis zu 50 Seiten eines Space ingestieren
curl -X POST http://localhost:8080/api/ingest/confluence/space \
  -H "Content-Type: application/json" \
  -d '{"spaceKey": "DOCS", "limit": 50}'
```

Ingestierte Seiten erscheinen in `GET /api/documents` (und im Dashboard) mit
Quelltyp `confluence` und ihrer Seiten-URL als Quelle.

## Konnektor 2 — Microsoft Teams

Teams-*Outgoing-Webhooks* sind **kanal-/team-gebunden**: Man legt pro Team einen an,
er bekommt einen Namen (z. B. `RAGBot`), und sobald jemand diesen Namen in einem
Kanal @-erwähnt, POSTet Teams die Nachricht an euren Endpunkt und zeigt eure
synchrone JSON-Antwort an.

### 2.1 Backend öffentlich erreichbar machen

Teams ruft nur öffentliche HTTPS-Endpunkte auf:

```bash
ngrok http 8080
# die https://<zufall>.ngrok-free.app-URL notieren
```

### 2.2 Outgoing Webhook anlegen

1. In Teams: Teamname → *Team verwalten* → *Apps* → *Ausgehenden Webhook erstellen*
   (unten rechts).
2. Name: `RAGBot` (das @-erwähnen die User später), Beschreibung, optional Icon.
3. Callback-URL: `https://<euer-ngrok-host>/teams/webhook`.
4. Beim Speichern zeigt Teams **genau einmal ein Security-Token (Base64)** —
   kopieren! Damit signiert Teams per HMAC jede Anfrage an euch.

### 2.3 Konfigurieren & starten

```bash
export TEAMS_HMAC_SECRET="<das Security-Token>"
RAG_TEAMS_HMAC_SECRET="$TEAMS_HMAC_SECRET" mvn spring-boot:run
```

### 2.4 Benutzen

Im Kanal: `@RAGBot Was sagt unser Onboarding-Dokument zu Laptops?`
Der Controller prüft den `Authorization: HMAC <Signatur>`-Header gegen ein
HMAC-SHA256 über den rohen Request-Body, führt den RAG-Loop aus, antwortet synchron
mit Teams-formatiertem JSON (`{"type":"message","text":...}`) und re-embeddet das
Q&A-Paar asynchron.

Signierten Aufruf ohne Teams simulieren (bash):

```bash
BODY='{"type":"message","text":"<at>RAGBot</at> was ist RAG?","from":{"id":"u1","name":"Tester"}}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -mac HMAC \
      -macopt hexkey:$(printf '%s' "$TEAMS_HMAC_SECRET" | base64 -d | xxd -p -c 256) -binary | base64)
curl -X POST http://localhost:8080/teams/webhook \
  -H "Authorization: HMAC $SIG" -H "Content-Type: application/json" -d "$BODY"
```

## Konnektor 3 — Slack

Der Slack-Konnektor spiegelt den Teams-Konnektor, aber auf Slacks Events API:
Events werden sofort bestätigt (Slack erzwingt ein **3-Sekunden-Timeout**), die
Antwort wird danach per `chat.postMessage` gepostet — als Thread an der auslösenden
Nachricht.

### 3.1 Slack-App anlegen

1. <https://api.slack.com/apps> → *Create New App* → *From scratch*, Workspace
   wählen.
2. *OAuth & Permissions* → *Bot Token Scopes*: `app_mentions:read` und `chat:write`
   hinzufügen.
3. *Install App* in den Workspace → **Bot User OAuth Token** (`xoxb-...`) kopieren.
4. *Basic Information* → **Signing Secret** kopieren.

### 3.2 Events API auf euer Backend zeigen lassen

```bash
ngrok http 8080
```

5. *Event Subscriptions* → aktivieren → Request URL:
   `https://<euer-ngrok-host>/slack/events`. Slack schickt sofort eine
   `url_verification`-Challenge — das Backend muss mit der Slack-Konfiguration
   bereits laufen, damit es die `challenge` zurückgeben kann (macht der Controller)
   und Slack die URL als *Verified* markiert.
6. *Subscribe to bot events*: `app_mention` hinzufügen. Speichern.
7. Den Bot in einen Kanal einladen: `/invite @EureApp`.

### 3.3 Konfigurieren & starten

```bash
export SLACK_SIGNING_SECRET="..."
export SLACK_BOT_TOKEN="xoxb-..."
RAG_SLACK_SIGNING_SECRET="$SLACK_SIGNING_SECRET" \
RAG_SLACK_BOT_TOKEN="$SLACK_BOT_TOKEN" \
mvn spring-boot:run
```

### 3.4 Benutzen

Im Kanal: `@EureApp Was sagt das Architektur-Dokument zu Caching?`
Der Controller prüft `X-Slack-Signature` (HMAC-SHA256 über
`v0:<timestamp>:<roher Body>`, mit ~5 Minuten Timestamp-Toleranz gegen Replays),
bestätigt mit 200, und der asynchrone Handler führt den RAG-Loop aus, postet die
Antwort in einen Thread und re-embeddet das Q&A-Paar.

Die URL-Verification lokal simulieren:

```bash
TS=$(date +%s); BODY='{"type":"url_verification","challenge":"test-challenge"}'
SIG="v0=$(printf 'v0:%s:%s' "$TS" "$BODY" | openssl dgst -sha256 -hmac "$SLACK_SIGNING_SECRET" -hex | awk '{print $NF}')"
curl -X POST http://localhost:8080/slack/events \
  -H "X-Slack-Request-Timestamp: $TS" -H "X-Slack-Signature: $SIG" \
  -H "Content-Type: application/json" -d "$BODY"
# → {"challenge":"test-challenge"}
```

## Backend & Frontend starten

Backend — jede Profil-Kombination aus Teil 2, plus die gewünschten
Konnektor-Env-Vars:

```bash
mvn spring-boot:run                                        # simple,azure (Default)
mvn spring-boot:run -Dspring-boot.run.profiles=pgvector,ollama   # offline + persistent
```

Frontend (unverändert):

```bash
cd rag-ui && npm install && npm start    # http://localhost:4200
```

## Wie der Code zusammenspielt

- Alle drei Konnektoren münden in **dieselbe Ingestion-Pipeline**
  (`DocumentIngestionService.ingestText`: chunken → embedden → speichern) und
  denselben `RagService`-Loop — sie sind dünne Adapter. Das ist die
  Architektur-Lektion dieses Teils.
- Feature-Flags sitzen auf Bean-Ebene: `@ConditionalOnProperty` an jeder
  Konnektor-Bean heisst, ein unkonfigurierter Konnektor trägt *nichts* zum
  Application Context bei.
- Beide Chat-Controller lesen den **rohen Request-Body** als `String` und prüfen
  die Plattform-Signatur über genau diese Bytes, *bevor* JSON geparst wird —
  Signaturverfahren brechen, wenn das Framework zuerst deserialisiert.
- Teams antwortet synchron (so funktioniert dessen Webhook-Modell); Slack wird in
  <3s bestätigt und asynchron beantwortet (`@EnableAsync` + `@Async`-Handler).
  Beide re-embedden Q&A-Paare asynchron.

## Ausblick

Ihr habt jetzt ein RAG-System, das von Dateien, URLs, einem Wiki und zwei
Chat-Plattformen gefüttert wird. Was fehlt, ist eine Deployment-Story — die kommt
in Teil 4.

## Troubleshooting

| Symptom | Ursache | Lösung |
|---|---|---|
| `404` auf `/api/ingest/confluence/*`, `/teams/webhook` oder `/slack/events` | Die Schlüssel-Property des Konnektors ist nicht gesetzt — Beans (und Endpunkte) existieren nicht | `rag.confluence.base-url` / `rag.teams.hmac-secret` / `rag.slack.signing-secret` setzen (z. B. via `RAG_..._*`-Env-Vars) und neu starten |
| Confluence: `401` | Falsche E-Mail/API-Token (Cloud) oder abgelaufenes PAT (Server/DC) | Token neu erstellen; Cloud braucht *E-Mail + Token* als Paar, Server/DC nur das PAT |
| Confluence: `403` | Der Account sieht den Space/die Seite nicht | Leserecht vergeben oder anderen Account nutzen |
| Confluence: `404` bei existierender Seite | Falsche `base-url` (Cloud braucht das `/wiki`-Suffix) oder Seiten-ID von einer anderen Site | `https://<org>.atlassian.net/wiki` verwenden; die numerische ID aus der Seiten-URL nehmen |
| Teams: Bot antwortet "Signature verification failed" / `401` | HMAC-Secret-Mismatch — Webhook neu angelegt, aber altes Token konfiguriert, oder Token unvollständig kopiert | `TEAMS_HMAC_SECRET` exakt mit dem Base64-Token neu setzen, das Teams beim Anlegen gezeigt hat |
| Teams: Webhook feuert nie | Outgoing Webhooks sind team-gebunden und triggern nur auf @-Erwähnung des Webhook-*Namens*; localhost-URLs funktionieren nie | Exakt den Webhook-Namen in einem Standard-Kanal des Teams @-erwähnen, in dem er angelegt wurde; ngrok/öffentliches HTTPS nutzen |
| Teams: "Der Dienst hat nicht geantwortet" | Antwort dauerte zu lang (langsames Modell) — Teams erwartet die synchrone Antwort binnen Sekunden | Schnelleres Chat-Modell/Deployment nutzen; `top-k` klein halten |
| Slack: Request URL zeigt "Your URL didn't respond" | Backend lief nicht / war nicht erreichbar, als Slack `url_verification` schickte, oder das Signing Secret passt nicht (der Controller antwortet dann `401`) | Backend mit korrektem `SLACK_SIGNING_SECRET` starten, *bevor* die Request URL gespeichert wird; ngrok-Tunnel prüfen |
| Slack: `401` bei jedem Event | Signing-Secret-Mismatch oder Uhrzeit weicht mehr als die 5-Minuten-Replay-Toleranz ab | Signing Secret aus *Basic Information* kopieren (nicht den Bot-Token); Systemuhr synchronisieren |
| Slack: Bot antwortet nie, Events kommen aber an | Fehlende Scopes/`chat:write`, Bot nicht im Kanal, oder `bot-token` nicht gesetzt (Logs: `chat.postMessage failed: not_in_channel` / `missing_scope` / Startfehler) | Scopes ergänzen, App neu installieren, Bot `/invite`n, `RAG_SLACK_BOT_TOKEN` setzen |
| Slack: jede Antwort kommt 3–4× | Slack wiederholt Events, die nicht binnen 3s bestätigt wurden — etwas hat den Controller-Thread blockiert | Controller dünn halten (er darf nie das LLM aufrufen); dieser Code bestätigt sofort — nach Thread-Pool-Erschöpfung suchen |
| Chat-Antworten ignorieren frühere Chat-Antworten | Q&A-Re-Embedding schlug still fehl (WARN-Logs), z. B. weil der Embedding-Aufruf scheiterte | Logs von `TeamsIngestionService`/`SlackIngestionService` prüfen; Anbieter-Zugangsdaten verifizieren |

## Wie geht's weiter?

➡️ **[Teil 4 — Alles kombiniert + Deployment](../rag-tutorial-04-full/)** lässt den
Anwendungscode unverändert und ergänzt Produktions-Packaging: Dockerfiles für
Backend und Frontend (nginx) sowie ein Helm-Chart mit optionalen
Postgres/pgvector- und Ollama-Deployments.

⬅️ Für die Grundlagen zurück zu **[Teil 1](../rag-tutorial-01-basics/)**; für das
Profil-System siehe **[Teil 2](../rag-tutorial-02-llms/)**.
