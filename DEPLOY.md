# DEPLOY — DevQuest on OCI Always-Free

Step-by-step runbook to stand up DevQuest on a fresh Oracle Cloud
Infrastructure Compute instance. Target shape: **VM.Standard.A1.Flex**
(Ampere ARM, 4 OCPU / 24 GB RAM, Always-Free eligible).

Estimated time from empty tenancy → live TLS site: **~60 minutes**,
half of which is DNS propagation.

---

## Table of contents

1. Provision the OCI compute instance
2. Open network security list ports 22 / 80 / 443
3. Point DNS at the instance
4. Install Docker + docker compose
5. Clone repos and set secrets
6. First boot + Let's Encrypt
7. Verify the stack
8. Ongoing operations
9. Rollback plan
10. Known limitations

---

## 1. Provision the OCI compute instance

**Console → Compute → Instances → Create instance**

| Field | Value |
|---|---|
| Image | Canonical Ubuntu 24.04 |
| Shape | `VM.Standard.A1.Flex` (ARM Ampere) |
| OCPUs | 4 |
| Memory | 24 GB |
| Boot volume | 50 GB (Always-Free ceiling is 200 GB total across all volumes) |
| VNIC subnet | public subnet, auto-assigned public IP |
| SSH key | your public key |
| Cloud-init | leave blank |

**Do not** use the ARM Ampere Flex over 4 OCPU / 24 GB — anything over
counts against paid quota.

Once running, note the **public IP** — you'll need it in step 3.

---

## 2. Network security list — open the world-facing ports

Default Always-Free VCN blocks everything except 22. Open 80 + 443.

**Networking → Virtual Cloud Networks → your VCN → Security Lists →
Default Security List → Ingress Rules → Add**

| Source CIDR | IP Protocol | Src port | Dest port |
|---|---|---|---|
| `0.0.0.0/0` | TCP | All | **80** |
| `0.0.0.0/0` | TCP | All | **443** |
| `0.0.0.0/0` | TCP | All | 22 (already present) |

**Then, on the instance itself**, Ubuntu images ship with `iptables`
rules that also block 80/443. Fix inside the box:

```bash
ssh ubuntu@<public-ip>
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80  -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

Verify from your laptop:

```bash
nc -vz <public-ip> 80
nc -vz <public-ip> 443
# Both should print "succeeded" or "open".
```

---

## 3. DNS

Point the apex and `www` to the instance IP at your registrar.

| Type | Name | Value | TTL |
|---|---|---|---|
| A | `@` (apex) | `<public-ip>` | 300 |
| A | `www`      | `<public-ip>` | 300 |

Wait for propagation:

```bash
dig +short sushrutvaidya.in       # must return <public-ip>
dig +short www.sushrutvaidya.in
```

DNS **must resolve** before Let's Encrypt will issue a cert. Give it
5–30 minutes.

---

## 4. Install Docker on the instance

```bash
ssh ubuntu@<public-ip>

sudo apt update
sudo apt install -y ca-certificates curl gnupg lsb-release
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
    sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
    https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | \
    sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

sudo usermod -aG docker ubuntu   # log out + back in to take effect
docker --version                  # verify
docker compose version            # verify
```

---

## 5. Clone the repos + set secrets

Layout (mirrors what `docker-compose.yml` expects):

```
/home/ubuntu/
├── Portfolio-Backend/          ← this repo
└── Portfolio-Frontend/         ← the frontend repo
```

```bash
cd /home/ubuntu
git clone <backend-repo-url>  Portfolio-Backend
git clone <frontend-repo-url> Portfolio-Frontend

# Optional media assets (photos, TV clips, jukebox audio) go here:
#   Portfolio-Backend/src/main/resources/static/{clips,audio,kitchen,social,gamer,otaku}
# They're in .gitignore — restore from your local tar or S3/OCI Object Storage.
```

### 5a. `.env` (production values)

```bash
cd /home/ubuntu/Portfolio-Backend
cp .env.template .env
chmod 600 .env                    # not group-readable, ever
nano .env                          # fill in each line
```

Values you MUST supply:

| Var | How to generate |
|---|---|
| `POSTGRES_PASSWORD` / `DB_PASSWORD` | `openssl rand -base64 32` — SAME value in both fields |
| `AUTH_HMAC_SECRET` | `openssl rand -base64 48` — startup fails if this is the shipped dev default |
| `STEAM_API_KEY` / `STEAM_ID` | optional — falls back to mock games if blank |
| `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`/`REDIS_SSL` | Upstash console → free tier; leave blank for in-memory fallback |
| `DOMAIN` / `CERTBOT_EMAIL` | your domain, your email — required for cert issuance |

Do NOT commit `.env`. It's already in `.gitignore`.

### 5b. Remove the local-dev override on the OCI host

```bash
rm -f /home/ubuntu/Portfolio-Backend/docker-compose.override.yml
```

The override re-exposes 5432/8081/8080 on the host — safe on your Mac,
never wanted on a public server. It's also in `.gitignore` so git
won't restore it.

### 5c. Confirm the nginx snippets are visible

```bash
ls /home/ubuntu/Portfolio-Frontend/nginx-snippets/security-headers.conf
ls /home/ubuntu/Portfolio-Frontend/nginx-reverse-proxy.conf
```

Both files must exist before compose starts nginx.

---

## 6. First boot + Let's Encrypt

### 6a. HTTP-only bootstrap

Certbot needs port 80 reachable AND nginx serving the ACME challenge
path. Bring the stack up:

```bash
cd /home/ubuntu/Portfolio-Backend
docker compose up -d --build
docker compose ps                 # all services "healthy"
docker compose logs -f portfolio-backend | head -50
```

Expect to see:

- `Flyway ... Successfully applied 2 migrations` on a fresh DB, OR
  `Successfully baselined ...` on an existing DB.
- `Started PortfolioBackendApplication in Xs`
- No `IllegalStateException` — those would mean `AUTH_HMAC_SECRET` is
  the dev default or blank. Regenerate and redeploy.

### 6b. Issue the certificate

Cerbot runs as a sidecar in the compose stack but only tries `renew` —
it won't issue a fresh cert. Do the initial issuance manually:

```bash
docker compose stop nginx-proxy    # free port 80

docker run --rm \
  -v $(pwd)/../letsencrypt:/etc/letsencrypt \
  -v $(pwd)/../certbot_www:/var/www/certbot \
  -p 80:80 \
  certbot/certbot:v2.11.0 certonly \
    --standalone \
    -d sushrutvaidya.in -d www.sushrutvaidya.in \
    --email $CERTBOT_EMAIL --agree-tos --no-eff-email \
    --non-interactive

# Fix volume ownership so nginx-proxy can read the cert
sudo chown -R root:root /var/lib/docker/volumes/portfolio-backend_letsencrypt/_data

docker compose start nginx-proxy
```

The certbot sidecar takes over renewals from here — it runs every 12h
and calls `--deploy-hook 'docker kill -s HUP portfolio-nginx'` when a
cert actually rotates, so nginx picks up the new cert without a full
restart.

---

## 7. Verify

From your laptop:

```bash
# TLS live
curl -sSI https://sushrutvaidya.in | head -5

# Health endpoint (locked-down surface: only {"status":"UP"})
curl -s https://sushrutvaidya.in/actuator/health
# expect: {"status":"UP"}

# Register a test user
curl -s -X POST https://sushrutvaidya.in/api/user \
    -H 'Content-Type: application/json' \
    -d '{"firstName":"Deploy","lastName":"Test"}' | jq

# Leaderboard bounded
curl -s https://sushrutvaidya.in/api/leaderboard | jq 'length'

# Security headers present (X-Frame-Options, HSTS, CSP)
curl -sI https://sushrutvaidya.in/api/leaderboard | grep -iE 'x-frame|strict-transport|content-security'

# Rate limit working
for i in {1..25}; do curl -so /dev/null -w '%{http_code} ' \
    https://sushrutvaidya.in/api/leaderboard; done; echo
# expect some 429s once burst is exhausted
```

From the OCI host:

```bash
docker compose logs --tail=50 portfolio-backend | grep -iE 'error|except'
docker compose logs --tail=20 portfolio-pgbackup | grep dumping
# Confirm the last nightly backup wrote to ./backups/dq-*.sql.gz
ls -lh /home/ubuntu/Portfolio-Backend/backups/
```

---

## 8. Ongoing operations

### 8a. Deploying an update

```bash
cd /home/ubuntu/Portfolio-Backend
git pull
docker compose build portfolio-backend
docker compose up -d portfolio-backend
docker compose logs --tail=50 portfolio-backend
```

Frontend-only change: the frontend Dockerfile bakes files at build
time, so a rebuild is needed:

```bash
cd /home/ubuntu/Portfolio-Frontend
git pull
cd /home/ubuntu/Portfolio-Backend
docker compose up -d --build portfolio-frontend
```

### 8b. Flyway migrations on prod

Baseline is `V1__baseline.sql` — matches the schema Hibernate had been
producing on EC2. `V2__constraints_and_indexes.sql` adds check
constraints and hot-path indexes; it's idempotent and safe to re-run.

For any new migration (adding a column, backfill, etc.):

1. Write `V{N}__descriptive-name.sql` under
   `src/main/resources/db/migration/`.
2. Test locally: `docker compose up -d postgres portfolio-backend` and
   watch the log for `Successfully applied migration V{N}__...`.
3. Deploy as above (`git pull` → rebuild backend). Flyway runs
   pending migrations before Spring starts serving traffic.

**Do NOT edit `V1__baseline.sql` after production has adopted it** —
Flyway checksums it. If you must fix it, create a `V3__fix_baseline...`
that repairs whatever went wrong.

### 8c. Log triage — <5 minute MTTR

Logs are single-line JSON with request-id correlation:

```bash
# Live tail
docker compose logs -f portfolio-backend | jq -c

# Find a specific request the user reported (they got X-Request-Id in
# response headers or saw it in DevTools)
docker compose logs portfolio-backend | grep '"requestId":"abc123"'

# Warnings + errors only
docker compose logs portfolio-backend | jq -c 'select(.level == "WARN" or .level == "ERROR")'

# Rate-limited requests (nginx-side)
docker compose logs portfolio-nginx | grep 'limiting requests'
```

### 8d. Postgres backups

Nightly `pg_dump` writes to `/home/ubuntu/Portfolio-Backend/backups/`
via a compose sidecar. 7 days retained.

Restore:

```bash
gunzip -c backups/dq-20260705-030000.sql.gz | \
    docker exec -i portfolio-postgres psql -U portfolio_app portfolio
```

**Off-box durability**: bind mount survives `docker compose down -v`
but NOT a boot volume failure. Recommended: nightly host cron to
push into OCI Object Storage (Always-Free tier includes 10 GB):

```bash
# On the host, create a bucket first via OCI CLI or console.
# Then:
0 4 * * * oci os object bulk-upload \
    --bucket-name dq-db-backups \
    --src-dir /home/ubuntu/Portfolio-Backend/backups \
    --overwrite >> /var/log/dq-backup-upload.log 2>&1
```

### 8e. Cert renewal — should never wake you up

The certbot sidecar:

- Runs `certbot renew` every 12 hours
- On successful renewal, executes
  `--deploy-hook 'docker kill -s HUP portfolio-nginx'`
- nginx receives SIGHUP → graceful reload, zero dropped connections

Confirm it's working:

```bash
docker compose logs portfolio-certbot | tail -20
docker exec portfolio-certbot certbot certificates
```

The `Not After:` date is what matters — if it doesn't roll forward
within 30 days of expiry, something is wrong.

### 8f. Ratchet Flyway safety

After the first successful boot on OCI:

```bash
# In application.properties, change ddl-auto to validate:
spring.jpa.hibernate.ddl-auto=validate
# Then rebuild + redeploy. Now Hibernate refuses to start if entities
# and schema drift — the strongest signal that a migration is missing.
```

---

## 9. Rollback plan

### 9a. Bad deploy — rollback the code

```bash
cd /home/ubuntu/Portfolio-Backend
git log --oneline -10                 # find the last known good SHA
git checkout <sha>
docker compose up -d --build portfolio-backend
```

### 9b. Bad Flyway migration

Flyway writes to `flyway_schema_history`. If a migration failed
partway:

```bash
docker exec -it portfolio-postgres psql -U portfolio_app -d portfolio \
    -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

For a failed-but-recoverable migration:

```bash
# Repair after fixing the underlying SQL:
docker exec portfolio-backend java -jar app.jar --spring.flyway.repair=true
```

For a bad but-committed migration: add a compensating `V{N}__` that
undoes the change. Never delete a row from `flyway_schema_history`.

### 9c. Total loss — restore from backup

```bash
docker compose down
docker volume rm portfolio-backend_pgdata
docker compose up -d postgres
sleep 30
gunzip -c backups/dq-YYYYMMDD-HHMMSS.sql.gz | \
    docker exec -i portfolio-postgres psql -U portfolio_app portfolio
docker compose up -d
```

---

## 10. Known limitations (intentional / accepted)

- **No horizontal scaling.** Single VM, single backend replica. If you
  outgrow, first step is OKE (managed Kubernetes) or Container
  Instances — but we're nowhere near the ceiling for portfolio traffic.
- **Off-box backup is manual to configure** (see 8d). The nightly
  in-VM dump is real; the host cron to Object Storage is not wired
  automatically.
- **Redis is optional.** RickrollService falls back to an in-memory
  counter if Upstash is unreachable — the rickroll count resets on
  process restart, which is fine for the portfolio-tier promise.
- **Coverage gate at 30%.** Deliberately modest — this is where the
  bar is set today so it doesn't gate normal work. Ratchet upward as
  real tests are added.
- **Playwright E2E suite is manual.** CI wiring is documented but
  not implemented (see `tests/README.md`).
- **Frontend script tags are inline in HTML.** No build pipeline yet.
  CSP has `'unsafe-inline'` on script-src because of this — moving to
  a build with per-tag hashes would tighten CSP but is out of scope
  for the portfolio use case.

---

If any of the commands above fail in a way this runbook doesn't
mention, the fastest triage is:

```bash
docker compose logs --tail=100 portfolio-backend | jq -c
docker compose ps
docker exec portfolio-backend wget -qO- http://localhost:8081/actuator/health
```
