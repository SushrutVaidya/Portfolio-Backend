# DevQuest — OCI Deploy Runbook

End-to-end deploy from a blank OCI VM to live HTTPS at sushrutvaidya.in.
**~45 min total** (~10 min waiting for cert + DNS to propagate).

---

## Phase 1 — Provision OCI VM (5 min)

In **OCI Console → Compute → Instances → Create**:

| Field | Value |
|---|---|
| Name | `devquest-prod` |
| Image | Canonical Ubuntu 22.04 (Always Free Eligible) |
| Shape | **VM.Standard.A1.Flex** — 4 OCPU, 24 GB RAM (max free tier) |
| Boot volume | 100 GB |
| VCN | Create new (auto) — public subnet, assign public IP |
| SSH key | **Generate a key pair for me** → Save private key to your Mac |

Wait until status = **RUNNING**, copy the **Public IP**.

### Open ports 80/443 (twice — yes really)

1. **Security List** (cloud-side firewall): Console → VCN → Security Lists → default → Add Ingress Rules: TCP 80 from `0.0.0.0/0`, TCP 443 from `0.0.0.0/0`.

2. **iptables** (Ubuntu OS-side firewall — OCI's gotcha). SSH in and run:
```bash
ssh -i ~/Downloads/ssh-key-*.key ubuntu@<OCI-IP>
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80  -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

---

## Phase 2 — Install Docker on the VM (3 min)

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu
exit                                    # log out + back in so the group sticks
ssh -i ~/Downloads/ssh-key-*.key ubuntu@<OCI-IP>
docker --version && docker compose version   # both should print versions
```

---

## Phase 3 — Clone repos + extract media (5 min)

```bash
cd ~
git clone -b release/v2.0 https://github.com/SushrutVaidya/Portfolio-Backend.git
git clone -b release/v2.0 https://github.com/SushrutVaidya/Portfolio-Frontend.git
```

From your **Mac**, copy the 328MB media tar:
```bash
# On your Mac:
scp -i ~/Downloads/ssh-key-*.key /tmp/portfolio-backend-assets.tar.gz ubuntu@<OCI-IP>:~/
```

Back on the **VM**, extract media into the backend's static folder:
```bash
mkdir -p ~/Portfolio-Backend/src/main/resources/static
tar -xzf ~/portfolio-backend-assets.tar.gz -C ~/Portfolio-Backend/src/main/resources/static/
ls ~/Portfolio-Backend/src/main/resources/static/   # should show: audio clips gamer kitchen otaku social
```

---

## Phase 4 — Configure .env (2 min)

```bash
cd ~/Portfolio-Backend
cp .env.template .env
nano .env
```

Fill in:
- `POSTGRES_PASSWORD` + `DB_PASSWORD` — **same strong password**
- `DOMAIN=sushrutvaidya.in`
- `CERTBOT_EMAIL=you@example.com` (Let's Encrypt expiry alerts)
- `REDIS_*` — paste from Upstash console (or leave blank for in-memory fallback)
- `STEAM_API_KEY` + `STEAM_ID` — optional, leave blank to use mock games

Save (Ctrl+O, Enter, Ctrl+X).

---

## Phase 5 — DNS cutover (2 min — propagation can take longer)

In your domain registrar (GoDaddy/Namecheap/wherever sushrutvaidya.in is):

| Record | Value |
|---|---|
| A | `@` → `<OCI-IP>` |
| A | `www` → `<OCI-IP>` |

Verify from your Mac:
```bash
dig sushrutvaidya.in +short        # should print the OCI IP
dig www.sushrutvaidya.in +short    # same
```

If it still shows the old EC2 IP, wait 5–10 min. Don't proceed until both resolve to the OCI IP, or certbot will fail.

---

## Phase 6 — Get the first SSL cert (5 min)

⚠️ The compose file expects certs at `/etc/letsencrypt/live/sushrutvaidya.in/`.
First-time bootstrap is a chicken-and-egg problem — nginx won't start without a cert, certbot needs nginx running to serve the ACME challenge.

**Solution:** start everything *except* nginx, get the cert, then start nginx.

```bash
cd ~/Portfolio-Backend

# Start postgres + backend + frontend (no nginx yet)
docker compose up -d postgres portfolio-backend portfolio-frontend
docker compose logs -f portfolio-backend     # wait for "Started PortfolioBackendApplication"
# Ctrl-C once you see it

# Bootstrap the cert (standalone mode — temporarily binds port 80 itself)
docker run --rm -it \
  -p 80:80 \
  -v portfolio-backend_letsencrypt:/etc/letsencrypt \
  -v portfolio-backend_certbot_www:/var/www/certbot \
  certbot/certbot certonly --standalone \
    -d sushrutvaidya.in -d www.sushrutvaidya.in \
    --email you@example.com --agree-tos --no-eff-email

# Should print "Successfully received certificate."
# Now start nginx (which expects the cert that now exists)
docker compose up -d nginx-proxy certbot
docker compose ps    # all 5 services Up
```

---

## Phase 7 — Smoke test (15 min)

Open `DEPLOY_SMOKE_CHECKLIST.md` and run through every section.

If anything fails, common fixes:

| Symptom | Fix |
|---|---|
| `502 Bad Gateway` | `docker compose logs portfolio-backend` — usually DB password mismatch |
| Photo upload fails 413 | nginx `client_max_body_size` — already 10M in our conf, check it's loaded |
| Images 404 | tar wasn't extracted into `src/main/resources/static/` before build |
| Cert renewal alerts in 60 days | certbot sidecar not running: `docker compose logs certbot` |
| TV clips don't play | Backend not serving `/clips/*.mp4` — confirm nginx routes them |

---

## Phase 8 — Decommission EC2 (after 24h of OCI stability)

Don't shut down AWS the same day. Give yourself a rollback window:

```bash
# On EC2 — stop containers but don't delete
ssh -i ~/Desktop/portfolio-key.pem ubuntu@35.154.211.40
docker compose down

# Wait 24h, monitor OCI
# Then on AWS Console → EC2 → Instances → Terminate
```

---

## Daily ops (post-deploy)

```bash
# Update to a new release
cd ~/Portfolio-Backend
git pull origin release/v2.0
cd ~/Portfolio-Frontend
git pull origin release/v2.0
cd ~/Portfolio-Backend
docker compose up -d --build portfolio-backend portfolio-frontend

# View logs
docker compose logs -f portfolio-backend
docker compose logs --tail=100 nginx-proxy

# Backup database
docker compose exec postgres pg_dump -U portfolio_app portfolio > backup-$(date +%F).sql

# Restore database
cat backup-2026-06-16.sql | docker compose exec -T postgres psql -U portfolio_app portfolio

# Backup user uploads
docker run --rm -v portfolio-backend_uploads_data:/data -v $(pwd):/backup alpine \
  tar czf /backup/uploads-$(date +%F).tar.gz -C /data .
```

---

## Rollback to EC2 (emergency)

If something is broken on OCI and we need to go back:

```bash
# Re-point DNS in registrar:
#   A @   → 35.154.211.40   (EC2)
#   A www → 35.154.211.40

# On EC2, restart what was already there:
ssh -i ~/Desktop/portfolio-key.pem ubuntu@35.154.211.40
docker compose up -d

# Wait 5 min for DNS to flip, verify https://sushrutvaidya.in works on EC2 again
```

---

## Cost monitor (OCI Always Free)

| Resource | Free Tier Limit | This deploy uses |
|---|---|---|
| Compute (Ampere) | 4 OCPU, 24 GB RAM total | 4 OCPU, 24 GB (max) |
| Block storage | 200 GB total | 100 GB (boot) + ~5 GB (volumes) |
| Outbound bandwidth | 10 TB / month | Tiny — well under |
| Public IP | 2 reserved free | 1 |

**Should never hit a paid tier.** If OCI tries to bill you, check the instance shape — `VM.Standard.A1.Flex` ≤ 4 OCPU & 24 GB is always free.
