# Grafana Monitoring Setup for MOAR

This guide connects MOAR's built-in API to Grafana via Prometheus for live dashboards of your stash scanning, organizer progress, and inventory data.

## Architecture

```
┌──────────────┐       ┌──────────────┐       ┌─────────────┐
│     MOAR     │──────▶│  Prometheus   │──────▶│   Grafana   │
│  (port 8585) │scrape │  (port 9090)  │query  │  (port 3000)│
│ /api/v1/     │ 30s   │  stores data  │       │  dashboards │
└──────────────┘       └──────────────┘       └─────────────┘
```

## Prerequisites

- Docker and Docker Compose ([get Docker](https://docs.docker.com/get-docker/))
- MOAR running in Minecraft

---

## Step 1 — Enable the API

Edit `config/moar/moar.properties` (created automatically on first launch):

```properties
api.enabled=true
api.bind=127.0.0.1
api.port=8585
api.key=pick_a_secret_key
webhook.url=
```

Restart Minecraft (or rejoin the world) to apply. Verify:

```sh
curl -H "Authorization: Bearer pick_a_secret_key" http://localhost:8585/api/v1/status
```

---

## Step 2 — Create monitoring stack

Create a folder (e.g. `moar-monitoring/`) with two files:

### docker-compose.yml

```yaml
services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    extra_hosts:
      - "host.docker.internal:host-gateway"
    restart: unless-stopped

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
    environment:
      - GFADMIN=admin
      - GFPASSWORD=admin
    restart: unless-stopped

volumes:
  prometheus_data:
  grafana_data:
```

### prometheus.yml

```yaml
global:
  scrape_interval: 30s

scrape_configs:
  - job_name: 'moar'
    metrics_path: /api/v1/metrics
    authorization:
      credentials: pick_a_secret_key
    static_configs:
      - targets: ['host.docker.internal:8585']
```

> The `credentials` value must match your `api.key` in `moar.properties`.

---

## Step 3 — Start

```sh
cd moar-monitoring
docker compose up -d
```

---

## Step 4 — Verify Prometheus

Open **http://localhost:9090/targets** — the `moar` target should show **UP**.

## Step 5 — Connect Grafana

1. Open **http://localhost:3000** (login: `admin` / `admin`)
2. **Connections** → **Data Sources** → **Add data source** → **Prometheus**
3. URL: `http://prometheus:9090`
4. Click **Save & Test**

## Step 6 — Build a dashboard

**Dashboards** → **New** → **Add visualization** → select Prometheus, then use metrics below.

---

## Available metrics

| Metric | Description | Requires DB |
|--------|-------------|:-----------:|
| `moar_containers_total` | Indexed containers (in-memory) | No |
| `moar_scanner_state` | Scanner state as ordinal (0–6) | No |
| `moar_scan_containers_found` | Containers found | No |
| `moar_scan_containers_indexed` | Containers successfully read | No |
| `moar_scan_containers_failed` | Containers that failed | No |
| `moar_scan_containers_pending` | Containers remaining | No |
| `moar_database_connected` | 1 if SQLite is connected | No |
| `moar_items_total` | Total item count (all containers) | Yes |
| `moar_unique_item_types` | Distinct item types | Yes |
| `moar_db_containers` | Containers in database | Yes |
| `moar_organizer_active` | 1 if organizer is running | No |
| `moar_organizer_tasks_completed` | Organizer tasks done | No |
| `moar_organizer_tasks_total` | Organizer tasks planned | No |
| `moar_supply_chests` | Registered supply chests | No |

### Suggested panels

| Panel | Query | Type |
|-------|-------|------|
| Total Containers | `moar_containers_total` | Stat |
| Total Items | `moar_items_total` | Stat |
| Scanner State | `moar_scanner_state` | Stat (with value mappings) |
| Scan Progress | `moar_scan_containers_indexed` | Time series |
| Organizer Progress | `moar_organizer_tasks_completed / moar_organizer_tasks_total` | Gauge |
| Failed Reads | `moar_scan_containers_failed` | Time series |

> **Tip:** Use Grafana value mappings for scanner state: 0=Idle, 1=Zone Scanning, 2=Walking, 3=Opening, 4=Reading, 5=Walking to Zone, 6=Done.

---

## PromQL examples

Scan progress percentage:
```promql
moar_scan_containers_indexed / moar_scan_containers_found
```

Alert if scan is stalled 10 minutes:
```promql
moar_scanner_state > 0 and changes(moar_scan_containers_indexed[10m]) == 0
```

Organizer completion percentage:
```promql
moar_organizer_tasks_completed / moar_organizer_tasks_total
```

---

## n8n webhook integration

Set the webhook URL in `moar.properties`:

```properties
webhook.url=https://n8n.example.com/webhook/moar-scan
```

MOAR will POST JSON on scan completion:

```json
{
  "event": "scan_complete",
  "containers_found": 120,
  "containers_indexed": 118,
  "containers_failed": 2,
  "timestamp": 1712345678000
}
```

---

## Stopping / restarting

```sh
docker compose down       # Stop (data preserved)
docker compose up -d      # Restart
docker compose down -v    # Remove everything including data
```

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Prometheus target DOWN | Check `api.enabled=true` in moar.properties and rejoin world. Verify API key matches. |
| Grafana shows "No data" | Wait 30–60s for first scrape. Confirm data source test passes. |
| Metrics missing item totals | DB metrics require the SQLite database to be connected (join a server). |
| Changed port | Update `targets` in prometheus.yml, then `docker compose restart prometheus`. |
