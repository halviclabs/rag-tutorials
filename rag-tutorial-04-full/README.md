# RAG Tutorial Part 4 — Everything Combined + Deployment

> Part 4 of 4: [Part 1 — Basics](../rag-tutorial-01-basics/) · [Part 2 — Swappable LLMs](../rag-tutorial-02-llms/) · [Part 3 — Connectors](../rag-tutorial-03-connectors/) · **This Part**

🇩🇪 [Deutsche Version](README.de.md)

This is the final, most complete reference project of the series — **everything
combined**: the core RAG loop (Part 1), swappable LLM providers and vector stores
via Spring profiles (Part 2), and the optional Confluence/Teams/Slack connectors
(Part 3). The **application code is unchanged from Part 3**; what Part 4 adds is
production packaging:

- a `Dockerfile` for the Spring Boot backend,
- a `Dockerfile` + nginx config for the Angular frontend (nginx also
  reverse-proxies the API and webhook paths to the backend),
- a **Helm chart** (`helm/rag-tutorial/`) with optional in-cluster
  Postgres/pgvector and Ollama.

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

Documents (files, URLs, Confluence pages, chat Q&A pairs) get in through the
**ingestion pipeline**:

```
  source ──▶ extract text ──▶ chunk ──▶ embed ──▶ store
                             (token     (embedding  (vector
                              splitter)   model)      store)
```

Key terms — **embedding** (a float vector capturing meaning), **vector store**
(nearest-neighbour search over those vectors), **chunking** (splitting documents
into embeddable pieces) — are explained in depth in
[Part 1](../rag-tutorial-01-basics/). The profile system (providers
`azure`/`openai`/`ollama` × stores `simple`/`pgvector`) is
[Part 2](../rag-tutorial-02-llms/); the connectors incl. setup and troubleshooting
are [Part 3](../rag-tutorial-03-connectors/). All of it applies here unchanged.

## What's new in Part 4

```
rag-tutorial-04-full/
├── Dockerfile                     # NEW: backend image (multi-stage Maven build)
├── .dockerignore                  # NEW
├── rag-ui/
│   ├── Dockerfile                 # NEW: frontend image (Angular build → nginx)
│   ├── nginx.conf.template        # NEW: SPA + reverse proxy /api, /teams, /slack
│   └── .dockerignore              # NEW
└── helm/rag-tutorial/             # NEW: Helm chart
    ├── Chart.yaml
    ├── values.yaml
    └── templates/
        ├── _helpers.tpl
        ├── backend-deployment.yaml
        ├── backend-service.yaml
        ├── backend-configmap.yaml     # non-secret env vars
        ├── backend-secret.yaml        # API keys / webhook secrets
        ├── frontend-deployment.yaml
        ├── frontend-service.yaml
        ├── ingress.yaml               # optional, disabled by default
        ├── postgres.yaml              # optional (postgres.enabled)
        └── ollama.yaml                # optional (ollama.enabled)
```

## Run locally (same as Part 3)

Everything from Parts 1–3 still works as before:

```bash
mvn spring-boot:run                                            # simple,azure
mvn spring-boot:run -Dspring-boot.run.profiles=pgvector,ollama # via docker-compose services
cd rag-ui && npm install && npm start                          # dashboard on :4200
```

You can also run the two production images locally:

```bash
docker build -t rag-backend .
docker build -t rag-frontend ./rag-ui
docker run -d --name rag-backend -p 8080:8080 \
  -e AZURE_OPENAI_API_KEY -e AZURE_OPENAI_ENDPOINT rag-backend
docker run -d -p 8081:80 -e BACKEND_URL=http://host.docker.internal:8080 rag-frontend
# dashboard: http://localhost:8081
```

## Deploying to Kubernetes with Docker + Helm

### 1. Build and push both images

```bash
REGISTRY=registry.example.com/you       # your registry / Docker Hub user

docker build -t $REGISTRY/rag-backend:0.1.0 .
docker build -t $REGISTRY/rag-frontend:0.1.0 ./rag-ui
docker push $REGISTRY/rag-backend:0.1.0
docker push $REGISTRY/rag-frontend:0.1.0
```

### 2. Chart layout

| File | Purpose |
|---|---|
| `Chart.yaml` | Chart metadata |
| `values.yaml` | All knobs: images, Spring profiles, config/secret env vars, optional services |
| `templates/backend-*.yaml` | Backend Deployment + Service, ConfigMap (non-secret env), Secret (keys/webhook secrets); pods roll automatically on config changes |
| `templates/frontend-*.yaml` | Frontend Deployment + Service (nginx serving the SPA and proxying `/api`, `/teams`, `/slack` to the backend service) |
| `templates/ingress.yaml` | Optional Ingress to the frontend (`ingress.enabled`) |
| `templates/postgres.yaml` | Optional in-cluster Postgres + pgvector with PVC (`postgres.enabled`) |
| `templates/ollama.yaml` | Optional in-cluster Ollama with PVC; pulls the configured models after start (`ollama.enabled`) |

### 3. Key `values.yaml` settings

| Setting | Meaning |
|---|---|
| `backend.image.*`, `frontend.image.*` | Your pushed image repositories/tags |
| `backend.springProfiles` | Same combinations as locally: `"simple,azure"`, `"pgvector,openai"`, `"pgvector,ollama"`, … |
| `backend.config.*` | Non-secret env vars → ConfigMap (endpoints, model names, Confluence base URL/email). Empty values are skipped |
| `backend.secrets.*` | Secret env vars → Secret (API keys, Confluence token, Teams HMAC secret, Slack signing secret/bot token). Set via `--set`, never committed |
| `postgres.enabled` | Deploy in-cluster Postgres+pgvector; the backend automatically gets `POSTGRES_URL/USER/PASSWORD` pointing at it |
| `ollama.enabled` | Deploy in-cluster Ollama; the backend automatically gets `OLLAMA_BASE_URL`; `ollama.models` lists what to pull |
| `ingress.enabled`, `ingress.host` | Optional Ingress for the frontend |

### 4. Three example installs

**A — in-memory + Azure OpenAI** (smallest footprint, no persistence):

```bash
helm install rag ./helm/rag-tutorial \
  --set backend.image.repository=$REGISTRY/rag-backend \
  --set frontend.image.repository=$REGISTRY/rag-frontend \
  --set backend.image.tag=0.1.0 --set frontend.image.tag=0.1.0 \
  --set backend.springProfiles="simple\,azure" \
  --set backend.config.AZURE_OPENAI_ENDPOINT="https://<resource>.openai.azure.com" \
  --set backend.secrets.AZURE_OPENAI_API_KEY="$AZURE_OPENAI_API_KEY"
```

**B — pgvector + OpenAI API** (persistent store):

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

**C — fully offline: pgvector + Ollama/Mistral** (no cloud, no API keys):

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

(The first Ollama start downloads several GB of model weights into its PVC — watch
`kubectl logs deploy/rag-rag-tutorial-ollama`. Give the Ollama pod generous
CPU/memory via `ollama.resources`, or a GPU node.)

To enable the Part 3 connectors, add their secrets, e.g.
`--set backend.secrets.RAG_SLACK_SIGNING_SECRET=... --set backend.secrets.RAG_SLACK_BOT_TOKEN=...`
— the same `@ConditionalOnProperty` feature flags apply in the cluster.

### 5. Reach the frontend

Without an Ingress, port-forward:

```bash
kubectl port-forward svc/rag-rag-tutorial-frontend 8081:80
# dashboard: http://localhost:8081  (nginx proxies API calls to the backend)
```

Or enable the Ingress:

```bash
helm upgrade rag ./helm/rag-tutorial --reuse-values \
  --set ingress.enabled=true --set ingress.host=rag.example.com
```

Then the dashboard is at `https://rag.example.com`, and the webhook endpoints for
Teams (`/teams/webhook`) and Slack (`/slack/events`) are publicly reachable on the
same host — exactly what those platforms require (no more ngrok).

Smoke test via the frontend proxy:

```bash
curl -X POST http://localhost:8081/api/ingest/url \
  -H "Content-Type: application/json" \
  -d '{"url": "https://en.wikipedia.org/wiki/Retrieval-augmented_generation"}'
curl -X POST http://localhost:8081/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "What is retrieval-augmented generation?"}'
```

## Why these technologies? (viable alternatives)

| Layer | Chosen here | Viable alternatives |
|---|---|---|
| Packaging | Dockerfiles + Helm | Buildpacks (`spring-boot:build-image`), Jib, Kustomize, plain manifests, Terraform + managed services |
| Runtime | Any Kubernetes | Docker Compose in production (small setups), Azure Container Apps, AWS ECS, Cloud Run |
| Database | In-cluster Postgres (chart) | Managed Postgres (RDS/Cloud SQL/Azure Flexible Server) with the pgvector extension — preferable for real production; set `postgres.enabled=false` and point `POSTGRES_URL` at it |
| LLM serving | Ollama in-cluster | Managed APIs (as in profiles `azure`/`openai`), vLLM, TGI — swap via the same Spring profiles |
| Everything else | See Parts 1–3 | LLM providers, vector DBs, frameworks, frontends, chunking strategies and retrieval refinements (hybrid search, reranking, metadata filtering) are listed in the earlier READMEs |

## What's next

This is the end of the series — you now have one reference project containing the
complete path from "what is an embedding?" to "running in Kubernetes". Ideas to
take it further: authentication in front of the dashboard, hybrid search/reranking,
streaming answers (SSE), evaluation (RAGAS-style), and observability for token
usage and retrieval quality.

⬅️ Back to **[Part 1](../rag-tutorial-01-basics/)** for the fundamentals,
**[Part 2](../rag-tutorial-02-llms/)** for profiles, or
**[Part 3](../rag-tutorial-03-connectors/)** for connector setup + troubleshooting.
