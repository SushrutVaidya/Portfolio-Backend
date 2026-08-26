# Fresh GCP deploy — portfolio + DevQuest + loglens (tar-based)

Goal: stand up a **new** GCP VM serving the clean redesign, DevQuest (decoupled, at `/devquest/`),
and `loglens.sushrutvaidya.in`, from the deploy tar. The **old box stays running untouched as
backup/rollback** — DNS is the only cutover, so you can point back instantly if anything's wrong.

Artifact: `~/portfolio-deploy.tar.gz` (built locally; excludes `.env`, `node_modules`, `.git`, `target`).
No DB data migrates — DevQuest starts fresh (there was no backup anyway; Flyway builds the schema on boot).

---

## Phase 1 — Provision the GCP VM (~5 min)
- Compute Engine → Create instance. **e2-small** (2 GB) minimum; **e2-medium** (4 GB) comfortable for the Java build + Postgres.
- Boot disk: Ubuntu 24.04 LTS, ≥20 GB.
- Networking: check **Allow HTTP** and **Allow HTTPS** (adds the `http-server`/`https-server` firewall tags).
- Create, then **reserve the external IP as static** (VPC network → IP addresses → promote the ephemeral IP), so DNS never breaks on reboot. Copy the IP → call it `<GCP-IP>`.
- (Unlike OCI, GCP has no OS-side iptables gotcha — the firewall tags are enough.)

## Phase 2 — Install Docker (~3 min)
```
gcloud compute ssh <vm-name>        # or: ssh <you>@<GCP-IP>
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && exit      # re-login so docker works without sudo
gcloud compute ssh <vm-name>
docker --version && docker compose version
```

## Phase 3 — Ship the code via tar (~5 min)
From your Mac:
```
scp ~/portfolio-deploy.tar.gz <you>@<GCP-IP>:~/
```
On the VM (extract to home so the two repos sit as siblings, as compose expects):
```
cd ~ && tar -xzf portfolio-deploy.tar.gz     # → ~/Portfolio-Frontend + ~/Portfolio-Backend
ls ~/Portfolio-Frontend ~/Portfolio-Backend
```

## Phase 4 — Fresh .env with REAL secrets (~2 min)
The tar deliberately omits `.env`. Create a production one (new box = new DB, so pick fresh values):
```
cd ~/Portfolio-Backend && cp .env.template .env && nano .env
```
Set: `POSTGRES_PASSWORD` / `DB_PASSWORD` (same strong value), `AUTH_HMAC_SECRET` (`openssl rand -base64 48`),
`DOMAIN=sushrutvaidya.in`, `CERTBOT_EMAIL=<you>`. Steam/Redis can stay blank (mock/in-memory fallback).

## Phase 5 — DNS (do NOT cut apex over yet)
At your registrar, add **only** the loglens record now, and pre-stage the apex on a low TTL:
| Type | Host | Value |
|---|---|---|
| A | `loglens` | `<GCP-IP>` |

Leave `@` and `www` pointing at the **old box** for the moment — we'll flip them after the new box is verified.
Confirm: `dig +short loglens.sushrutvaidya.in` → `<GCP-IP>`.

> Cert note: certbot validates over HTTP, which needs the name to resolve to this box. So we can get the
> loglens cert now, but the apex cert needs the apex pointing here. Two clean options — pick one in Phase 6.

## Phase 6 — First certificates (the chicken-and-egg) (~5 min)
nginx won't start without a cert; certbot needs port 80 free. So start everything *except* nginx, mint the
certs standalone, then start nginx.
```
cd ~/Portfolio-Backend
docker compose up -d --build postgres portfolio-backend portfolio-frontend
docker compose logs -f portfolio-backend    # wait for "Started ... on port 8081", Ctrl-C

# Cut the apex+www DNS over to <GCP-IP> now (registrar), wait until BOTH resolve here:
dig +short sushrutvaidya.in ; dig +short www.sushrutvaidya.in    # must show <GCP-IP>

# Mint one cert covering all three names (standalone binds :80 briefly):
docker compose run --rm -p 80:80 \
  -v letsencrypt:/etc/letsencrypt -v certbot_www:/var/www/certbot \
  --entrypoint certbot certbot certonly --standalone \
  -d sushrutvaidya.in -d www.sushrutvaidya.in -d loglens.sushrutvaidya.in \
  --email <you> --agree-tos --no-eff-email
# "Successfully received certificate."  →  cert now at live/sushrutvaidya.in/ (covers the loglens SAN too)

docker compose up -d nginx-proxy certbot
docker compose ps            # all services Up
```
(Volume names: check `docker compose config --volumes` if `letsencrypt`/`certbot_www` are prefixed with the project name.)

## Phase 7 — Smoke test (before trusting it)
- `https://sushrutvaidya.in` → the redesign, valid cert, preloader "SV".
- `https://sushrutvaidya.in/about` → TV plays clips, **jukebox live (no "To Be Continued")**, résumé downloads.
- `https://sushrutvaidya.in/devquest/` → the game loads standalone (decoupled).
- `https://sushrutvaidya.in/work/devquest` → case study, **"Open it" launches the game** (not a 404).
- `https://sushrutvaidya.in/loglens` → product page with nav.
- `https://loglens.sushrutvaidya.in` → standalone loglens, valid TLS, back-links go to apex.
- Deep-link any SPA route + refresh → no 404 (SPA fallback works).

## Phase 8 — Keep the old box as backup
Do **not** decommission the old VM. Leave it running (or stopped-but-not-deleted) so a DNS flip back is instant
if the new box misbehaves. Your `~/portfolio-deploy.tar.gz` and the GCP `portfolio-full.tar` remain your code/data backups.

## Daily ops (new box)
Redeploy after a new tar: `scp` it up → `tar -xzf` → `docker compose up -d --build portfolio-frontend portfolio-backend` → `docker compose exec nginx-proxy nginx -s reload`.
DB backup: `docker compose exec postgres pg_dump -U portfolio_app portfolio | gzip > backup-$(date +%F).sql.gz` (set a cron — the thing the old box never had).
