# DevQuest — Post-Deploy Smoke Checklist

**Time:** ~15 minutes
**When to run:** Immediately after `docker compose up -d` on OCI, before announcing the site is live.
**If anything fails:** Stop, fix it, redeploy, restart the checklist from the top.

Replace `https://sushrutvaidya.in` with your actual domain in every URL below.

---

## 0. Infrastructure Pre-Check (60s)

Open a terminal on the OCI VM and run:

```bash
docker compose ps                              # all services Up
docker compose logs --tail=30 backend          # no stack traces
docker compose logs --tail=10 nginx-proxy      # 'configuration complete'
ls -lh uploads/                                # volume mounted (may be empty)
curl -sI https://sushrutvaidya.in              # HTTP/2 200, valid TLS
curl -s https://sushrutvaidya.in/api/stats     # JSON, not HTML error
```

If any of these fail → fix infra, don't proceed.

---

## 1. Landing Page (1 min)

Open in browser: **https://sushrutvaidya.in/**

- [ ] Page loads, no 404s in network tab
- [ ] Hero text + CTA visible
- [ ] "Play DevQuest" button visible
- [ ] "Who am I?" button visible
- [ ] Footer renders (jukebox icon present)
- [ ] No console errors (open DevTools → Console)

---

## 2. DevQuest Game Flow (3 min)

Navigate to: **https://sushrutvaidya.in/devquest/landing.html**

- [ ] Landing modal/page loads
- [ ] Click **Enter DevQuest** → modal appears, enter "Smoke" / "Test"
- [ ] Click **Start** → captcha page loads (`captcha.html`)
- [ ] Captcha game playable — drag a shape → drops correctly with sound
- [ ] Complete captcha (or use cheatcode if you set one) → moves to `devtype.html`
- [ ] DevType game loads, words appear, typing works
- [ ] Leaderboard renders on devtype page (your name visible after game)
- [ ] Click **Next Challenge** → incident page loads
- [ ] Bug-hunt game playable (click a buggy line)
- [ ] Complete or skip → bulb-loading.html
- [ ] Tap/scroll on bulb → screen lights up → redirects to aboutme

---

## 3. About Me / Portfolio Page (3 min)

URL: **https://sushrutvaidya.in/devquest/test/aboutme.html**

- [ ] Hero name, photo, tagline all visible (no broken `📷` placeholder)
- [ ] Character wheel (top-right fixed button) opens on click — 5 nav options
- [ ] Stats pentagon renders on scroll (canvas drawn, not blank)
- [ ] **Personas section:** Click a wanted poster → flips to reveal story + photos
- [ ] **Kitchen section:** Photos visible (NOT 404 broken-image icons)
- [ ] **Gaming carousel:** Cover-flow rotates, dots clickable
- [ ] **Lore phone thread:** Messages drop in over ~10s with typing indicators
- [ ] **TV section:** scroll to it. **Auto-plays muted in 1–2s** (Family Guy clip moves frame to frame, NOT frozen)
- [ ] Click TV channel knob → switches to next channel, video changes
- [ ] Click TV volume knob → 4 levels cycle, audio comes on
- [ ] **Jukebox pill:** visible bottom-right. Click → expands to full boombox
- [ ] Inside boombox: click **Play** → music starts. Click **Next Track** → switches track
- [ ] Volume slider works
- [ ] **Currently Into:** WATCHING / PLAYING / LEARNING / COOKING all populated
- [ ] **Player Card preview:** visible, hover tilt works
- [ ] **CTA at bottom:** "CREATE YOUR CARD" → links to card-experiments

---

## 4. Card Editor (3 min)

URL: **https://sushrutvaidya.in/devquest/test/card-experiments.html**

- [ ] All 9 card styles render (count them: Holographic, Carbon, Glass, Metal, Neon, Minimal, VHS, GTA, Royal)
- [ ] Each card shows an **OVR + tier badge** (not "XP" or "LVL")
- [ ] Click a card → zooms with bio + motto revealed
- [ ] Close zoom (X or Escape) → returns to grid
- [ ] **Editor:** click "Card Editor" → panel opens
- [ ] Type in **Bio** → cards in grid update in real-time
- [ ] Move a stat slider → OVR number updates live
- [ ] Pick a different style → that card highlights
- [ ] **Photo upload:** click "Upload Photo" → pick a JPG/PNG <10MB → photo shows in cards
- [ ] Open browser DevTools → Network tab → confirm `POST /api/user/{uuid}/photo` returns 200, response shows `/uploads/...jpg?v=...`
- [ ] Click **Save Card** → "Saved" toast
- [ ] **Sticky CTA at bottom:** shows "Want this printed on real PVC? · 25 left →"
- [ ] Click sticky CTA → smooth scrolls to **Print Section**
- [ ] **Print Section:** 3D floating PVC mockup visible, animates
- [ ] Fill print form (Name, Address, City: HYD, Pincode: 500001, Phone)
- [ ] Click **Print My Card** → success state shows
- [ ] Refresh page → card data persists (saved to backend)
- [ ] Click **Share My Card** → link copied to clipboard, button text changes

---

## 5. Leaderboard (1 min)

URL: **https://sushrutvaidya.in/devquest/test/leaderboard.html**

- [ ] Page loads
- [ ] Your "Smoke Test" name appears in podium or list
- [ ] Click "← Back to Portfolio" → returns to aboutme
- [ ] No 404 / broken styling

---

## 6. XSS / Security Spot-Check (1 min)

Open browser console on `https://sushrutvaidya.in/devquest/test/card-experiments.html` and run:

```js
// Try to inject HTML through the bio
fetch('/api/user/' + localStorage.getItem('dq-user-id') + '/card', {
  method: 'PUT',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({ bio: '<img src=x onerror="alert(1)">' })
});
```

Then refresh the page.

- [ ] **No alert appears** — bio renders as literal text in cards (HTML escaped)
- [ ] If an alert pops → XSS is broken, STOP and fix esc() helper

---

## 7. Mobile Sanity (2 min)

Open **https://sushrutvaidya.in** on your phone (or DevTools mobile view at 375px width):

- [ ] Landing fits, no horizontal scroll
- [ ] Modal padding ok (not crushed)
- [ ] DevQuest flow playable on mobile
- [ ] aboutme TV scales down, jukebox is compact
- [ ] Card editor: cards stack single-column, sticky CTA visible
- [ ] Leaderboard: podium stacks vertically (3 cards in a row, not crushed)
- [ ] Print form: inputs are tap-friendly (44px+ targets)

---

## 8. Error Response Check (30s)

Confirm error responses are clean JSON (not Spring stack traces):

```bash
# Should return 400 with clean message
curl -i -X POST https://sushrutvaidya.in/api/user/00000000-0000-0000-0000-000000000000/photo

# Should return 404 with clean message
curl -i https://sushrutvaidya.in/api/user/00000000-0000-0000-0000-000000000000/card

# Should return 400 with field validation message
curl -i -X PUT https://sushrutvaidya.in/api/user/{any-real-id}/card \
  -H "Content-Type: application/json" -d '{"wantedLevel":99}'
```

- [ ] All three return small clean JSON like `{"status":404,"error":"Not Found","message":"User not found"}`
- [ ] **None** return a 4KB Java stack trace

---

## 9. Performance Quick-Check (1 min)

On `aboutme.html`, open DevTools → Lighthouse → Run audit (Mobile, Performance only):

- [ ] LCP < 4s (good for a media-heavy page)
- [ ] No render-blocking resources flagged red
- [ ] No layout shift > 0.25

If LCP is bad → the 6.7MB hero JPG is the likely culprit (known tech debt).

---

## 10. Final Checks

- [ ] **DNS:** `dig sushrutvaidya.in +short` returns the OCI IP
- [ ] **HTTPS valid:** browser shows lock icon, not "Not Secure"
- [ ] **Both www + apex work:** `https://www.sushrutvaidya.in` and `https://sushrutvaidya.in` both load (redirect or duplicate, either is fine)
- [ ] **Nightly cron:** `sudo crontab -l` shows the cert-renewal entry (single line)
- [ ] **Uploads persist:** stop & restart `docker compose restart backend` → existing photos still load
- [ ] **DB persists:** stop & restart Postgres → leaderboard still has names

---

## If ALL 10 sections pass → DEPLOY APPROVED ✅

Tweet it, share the link, message your friends. You're live.

## If something fails

| Symptom | First thing to check |
|---|---|
| 502/504 from API | `docker compose logs backend` — Spring boot failed to start (likely DB connection) |
| Images / clips 404 | Tar wasn't extracted into `src/main/resources/static/` before build |
| Photo upload 415 | Frontend sending wrong Content-Type — verify FormData not JSON |
| TV frozen on one frame | `display()` race re-introduced (the bug we just fixed) |
| Save card 400 | Field too long (bio>280, wantedLevel>5, etc.) — check browser console for field name |
| XSS alert fires | `esc()` helper missing or stripped — check leaderboard.html + card-experiments.html |
| CORS error in console | nginx-proxy duplicate `Access-Control-Allow-Origin: *` header — remove from proxy conf |
| Cert error in browser | certbot didn't run / cron path wrong / Let's Encrypt rate limit hit |
| Domain doesn't resolve | DNS propagation (wait 5min) or A record points to wrong IP |
