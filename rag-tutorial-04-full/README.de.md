# RAG-Tutorial Teil 4 — Alles kombiniert + Deployment

> Teil 4 von 4: [Teil 1 — Grundlagen](../rag-tutorial-01-basics/) · [Teil 2 — Austauschbare LLMs](../rag-tutorial-02-llms/) · [Teil 3 — Konnektoren](../rag-tutorial-03-connectors/) · **Dieser Teil**

🇬🇧 [English version](README.md)

Dies ist das finale, vollständigste Referenzprojekt der Serie — **alles
kombiniert**: der Kern-RAG-Loop (Teil 1), austauschbare LLM-Anbieter und
Vektorspeicher per Spring-Profil (Teil 2) und die optionalen
Confluence/Teams/Slack-Konnektoren (Teil 3). Der **Anwendungscode ist unverändert
gegenüber Teil 3**; Teil 4 ergänzt Produktions-Packaging:

- ein `Dockerfile` für das Spring-Boot-Backend,
- ein `Dockerfile` + nginx-Konfiguration für das Angular-Frontend (nginx
  reverse-proxied zusätzlich API- und Webhook-Pfade zum Backend),
- ein **Helm-Chart** (`helm/rag-tutorial/`) mit optionalem In-Cluster-
  Postgres/pgvector und Ollama.

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

Dokumente (Dateien, URLs, Confluence-Seiten, Chat-Q&A-Paare) kommen über die
**Ingestion-Pipeline** hinein:

```
  Quelle ──▶ Text extrahieren ──▶ chunken ──▶ embedden ──▶ speichern
                                 (Token-     (Embedding-   (Vektor-
                                  Splitter)    Modell)      speicher)
```

Zentrale Begriffe — **Embedding** (ein Float-Vektor, der Bedeutung erfasst),
**Vektorspeicher** (Nächste-Nachbarn-Suche über diese Vektoren), **Chunking**
(Dokumente in embeddbare Stücke teilen) — erklärt
[Teil 1](../rag-tutorial-01-basics/) ausführlich. Das Profil-System (Anbieter
`azure`/`openai`/`ollama` × Speicher `simple`/`pgvector`) ist
[Teil 2](../rag-tutorial-02-llms/); die Konnektoren inkl. Setup und
Troubleshooting sind [Teil 3](../rag-tutorial-03-connectors/). Alles gilt hier
unverändert.

## Neu in Teil 4

```
rag-tutorial-04-full/
├── Dockerfile                     # NEU: Backend-Image (Multi-Stage-Maven-Build)
├── .dockerignore                  # NEU
├── rag-ui/
│   ├── Dockerfile                 # NEU: Frontend-Image (Angular-Build → nginx)
│   ├── nginx.conf.template        # NEU: SPA + Reverse-Proxy /api, /teams, /slack
│   └── .dockerignore              # NEU
└── helm/rag-tutorial/             # NEU: Helm-Chart
    ├── Chart.yaml
    ├── values.yaml
    └── templates/
        ├── _helpers.tpl
        ├── backend-deployment.yaml
        ├── backend-service.yaml
        ├── backend-configmap.yaml     # nicht-geheime Env-Vars
        ├── backend-secret.yaml        # API-Keys / Webhook-Secrets
        ├── frontend-deployment.yaml
        ├── frontend-service.yaml
        ├── ingress.yaml               # optional, standardmässig deaktiviert
        ├── postgres.yaml              # optional (postgres.enabled)
        └── ollama.yaml                # optional (ollama.enabled)
```

## Lokal starten (wie Teil 3)

Alles aus Teil 1–3 funktioniert weiterhin:

```bash
mvn spring-boot:run                                            # simple,azure
mvn spring-boot:run -Dspring-boot.run.profiles=pgvector,ollama # via docker-compose-Services
cd rag-ui && npm install && npm start                          # Dashboard auf :4200
```

Die beiden Produktions-Images laufen auch lokal:

```bash
docker build -t rag-backend .
docker build -t rag-frontend ./rag-ui
docker run -d --name rag-backend -p 8080:8080 \
  -e AZURE_OPENAI_API_KEY -e AZURE_OPENAI_ENDPOINT rag-backend
docker run -d -p 8081:80 -e BACKEND_URL=http://host.docker.internal:8080 rag-frontend
# Dashboard: http://localhost:8081
```

## Deployment nach Kubernetes mit Docker + Helm

### 1. Beide Images bauen und pushen

```bash
REGISTRY=registry.example.com/ihr-name    # eure Registry / Docker-Hub-User

docker build -t $REGISTRY/rag-backend:0.1.0 .
docker build -t $REGISTRY/rag-frontend:0.1.0 ./rag-ui
docker push $REGISTRY/rag-backend:0.1.0
docker push $REGISTRY/rag-frontend:0.1.0
```

### 2. Chart-Aufbau

| Datei | Zweck |
|---|---|
| `Chart.yaml` | Chart-Metadaten |
| `values.yaml` | Alle Stellschrauben: Images, Spring-Profile, Config-/Secret-Env-Vars, optionale Services |
| `templates/backend-*.yaml` | Backend-Deployment + -Service, ConfigMap (nicht-geheime Env), Secret (Keys/Webhook-Secrets); Pods rollen bei Config-Änderungen automatisch |
| `templates/frontend-*.yaml` | Frontend-Deployment + -Service (nginx liefert die SPA aus und proxied `/api`, `/teams`, `/slack` zum Backend-Service) |
| `templates/ingress.yaml` | Optionaler Ingress zum Frontend (`ingress.enabled`) |
| `templates/postgres.yaml` | Optionales In-Cluster-Postgres + pgvector mit PVC (`postgres.enabled`) |
| `templates/ollama.yaml` | Optionales In-Cluster-Ollama mit PVC; zieht die konfigurierten Modelle nach dem Start (`ollama.enabled`) |

### 3. Wichtige `values.yaml`-Einstellungen

| Einstellung | Bedeutung |
|---|---|
| `backend.image.*`, `frontend.image.*` | Eure gepushten Image-Repositories/Tags |
| `backend.springProfiles` | Dieselben Kombinationen wie lokal: `"simple,azure"`, `"pgvector,openai"`, `"pgvector,ollama"`, … |
| `backend.config.*` | Nicht-geheime Env-Vars → ConfigMap (Endpoints, Modellnamen, Confluence-Base-URL/E-Mail). Leere Werte werden übersprungen |
| `backend.secrets.*` | Geheime Env-Vars → Secret (API-Keys, Confluence-Token, Teams-HMAC-Secret, Slack-Signing-Secret/Bot-Token). Per `--set` setzen, nie einchecken |
| `postgres.enabled` | In-Cluster-Postgres+pgvector deployen; das Backend bekommt automatisch `POSTGRES_URL/USER/PASSWORD` darauf gesetzt |
| `ollama.enabled` | In-Cluster-Ollama deployen; das Backend bekommt automatisch `OLLAMA_BASE_URL`; `ollama.models` listet, was gezogen wird |
| `ingress.enabled`, `ingress.host` | Optionaler Ingress fürs Frontend |

### 4. Drei Beispiel-Installationen

**A — in-memory + Azure OpenAI** (kleinster Fussabdruck, keine Persistenz):

```bash
helm install rag ./helm/rag-tutorial \
  --set backend.image.repository=$REGISTRY/rag-backend \
  --set frontend.image.repository=$REGISTRY/rag-frontend \
  --set backend.image.tag=0.1.0 --set frontend.image.tag=0.1.0 \
  --set backend.springProfiles="simple\,azure" \
  --set backend.config.AZURE_OPENAI_ENDPOINT="https://<ressource>.openai.azure.com" \
  --set backend.secrets.AZURE_OPENAI_API_KEY="$AZURE_OPENAI_API_KEY"
```

**B — pgvector + OpenAI-API** (persistenter Speicher):

```bash
helm install rag ./helm/rag-tutorial \
  --set backend.image.repository=$REGISTRY/rag-backend \
  --set frontend.image.repository=$REGISTRY/rag-frontend \
  --set backend.image.tag=0.1.0 --set frontend.image.tag=0.1.0 \
  --set backend.springProfiles="pgvector\,openai" \
  --set postgres.enabled=true \
  --set postgres.password="$(openssl rand -hex 16)" \
  --set backend.secrets.OPENAI_API_KEY="$OPENAI_API_KEY"
```

**C — komplett offline: pgvector + Ollama/Mistral** (keine Cloud, keine API-Keys):

```bash
helm install rag ./helm/rag-tutorial \
  --set backend.image.repository=$REGISTRY/rag-backend \
  --set frontend.image.repository=$REGISTRY/rag-frontend \
  --set backend.image.tag=0.1.0 --set frontend.image.tag=0.1.0 \
  --set backend.springProfiles="pgvector\,ollama" \
  --set postgres.enabled=true \
  --set postgres.password="$(openssl rand -hex 16)" \
  --set ollama.enabled=true
```

(Der erste Ollama-Start lädt mehrere GB Modellgewichte in sein PVC —
`kubectl logs deploy/rag-rag-tutorial-ollama` beobachten. Dem Ollama-Pod über
`ollama.resources` grosszügig CPU/Memory geben, oder einen GPU-Node.)

Die Konnektoren aus Teil 3 aktiviert man über ihre Secrets, z. B.
`--set backend.secrets.RAG_SLACK_SIGNING_SECRET=... --set backend.secrets.RAG_SLACK_BOT_TOKEN=...`
— im Cluster gelten dieselben `@ConditionalOnProperty`-Feature-Flags.

### 5. Das Frontend erreichen

Ohne Ingress: Port-Forward:

```bash
kubectl port-forward svc/rag-rag-tutorial-frontend 8081:80
# Dashboard: http://localhost:8081  (nginx proxied API-Aufrufe zum Backend)
```

Oder den Ingress aktivieren:

```bash
helm upgrade rag ./helm/rag-tutorial --reuse-values \
  --set ingress.enabled=true --set ingress.host=rag.example.com
```

Dann liegt das Dashboard auf `https://rag.example.com`, und die Webhook-Endpunkte
für Teams (`/teams/webhook`) und Slack (`/slack/events`) sind auf demselben Host
öffentlich erreichbar — genau das, was diese Plattformen brauchen (kein ngrok mehr).

Smoke-Test über den Frontend-Proxy:

```bash
curl -X POST http://localhost:8081/api/ingest/url \
  -H "Content-Type: application/json" \
  -d '{"url": "https://de.wikipedia.org/wiki/Retrieval-Augmented_Generation"}'
curl -X POST http://localhost:8081/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "Was ist Retrieval-Augmented Generation?"}'
```

## Warum diese Technologien? (mögliche Alternativen)

| Ebene | Hier gewählt | Mögliche Alternativen |
|---|---|---|
| Packaging | Dockerfiles + Helm | Buildpacks (`spring-boot:build-image`), Jib, Kustomize, blanke Manifeste, Terraform + Managed Services |
| Runtime | Beliebiges Kubernetes | Docker Compose in Produktion (kleine Setups), Azure Container Apps, AWS ECS, Cloud Run |
| Datenbank | In-Cluster-Postgres (Chart) | Managed Postgres (RDS/Cloud SQL/Azure Flexible Server) mit pgvector-Extension — für echte Produktion vorzuziehen; `postgres.enabled=false` setzen und `POSTGRES_URL` darauf zeigen lassen |
| LLM-Serving | Ollama im Cluster | Managed APIs (wie Profile `azure`/`openai`), vLLM, TGI — Wechsel über dieselben Spring-Profile |
| Alles andere | Siehe Teil 1–3 | LLM-Anbieter, Vektor-DBs, Frameworks, Frontends, Chunking-Strategien und Retrieval-Verfeinerungen (Hybrid-Suche, Reranking, Metadaten-Filter) stehen in den früheren READMEs |

## Wie geht's weiter?

Hier endet die Serie — ihr habt jetzt ein Referenzprojekt mit dem kompletten Weg
von "Was ist ein Embedding?" bis "läuft in Kubernetes". Ideen zum Weiterbauen:
Authentifizierung vor dem Dashboard, Hybrid-Suche/Reranking, Streaming-Antworten
(SSE), Evaluation (à la RAGAS) und Observability für Token-Verbrauch und
Retrieval-Qualität.

⬅️ Zurück zu **[Teil 1](../rag-tutorial-01-basics/)** für die Grundlagen,
**[Teil 2](../rag-tutorial-02-llms/)** für die Profile oder
**[Teil 3](../rag-tutorial-03-connectors/)** für Konnektor-Setup + Troubleshooting.
