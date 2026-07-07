#!/bin/bash
# oci-ampere-retry.sh
# ────────────────────────────────────────────────────────────────
# Poll OCI until an Ampere A1.Flex free-tier instance can be
# provisioned. Works around the "Out of host capacity" error that
# blocks free-tier Ampere in high-demand regions (Hyderabad included).
#
# WHERE TO RUN:
#   OCI Cloud Shell (top-right ">_" icon in the console). Pre-authed,
#   no config needed. The script auto-wraps itself in tmux so a
#   dropped browser tab can't kill progress — just run:
#
#       bash oci-ampere-retry.sh
#
#   To watch: tmux attach -t ampere
#   To bail : tmux kill-session -t ampere
#
# HOW IT WORKS:
#   Loops indefinitely, rotates FAULT-DOMAIN-1/2/3 each attempt,
#   sleeps ~45s between calls to stay under OCI's rate limit
#   (~5 requests/minute per user). On "Out of capacity" it retries.
#   On "TooManyRequests" it backs off further. On success it prints
#   the instance OCID + public IP and exits.
#
# WHAT'S LOGGED:
#   Every attempt is appended to ~/oci-retry-<timestamp>.log with
#   attempt #, fault domain, error text, and duration. A dashboard
#   prints on every 5th attempt showing success/fail per FD and ETA.
# ────────────────────────────────────────────────────────────────

set -uo pipefail

# ═══ CONFIGURE — fill these in before running ══════════════════════

# Compartment where the instance will live.
#   Console → Identity & Security → Compartments → copy OCID
COMPARTMENT_ID="ocid1.compartment.oc1..CHANGE_ME"

# Availability Domain. Hyderabad has only one AD.
#   Console → any prior Create Instance attempt → AD dropdown shows it.
#   Or: oci iam availability-domain list
AD_NAME="CHANGE_ME:AP-HYDERABAD-1-AD-1"

# Public subnet in your devquest-vcn.
#   Console → Networking → Virtual Cloud Networks → devquest-vcn
#           → Subnets → public subnet-devquest-vcn → copy OCID
SUBNET_ID="ocid1.subnet.oc1.ap-hyderabad-1..CHANGE_ME"

# Boot image OCID for Ubuntu 22.04 aarch64 (Ampere is ARM).
#   Console → Compute → Instances → Create → Image → change → pick
#     "Canonical Ubuntu 22.04" and "Aarch64" → copy the image OCID
#   Or from Cloud Shell:
#     oci compute image list \
#       --compartment-id "$COMPARTMENT_ID" \
#       --operating-system "Canonical Ubuntu" \
#       --operating-system-version "22.04" \
#       --shape "VM.Standard.A1.Flex" \
#       --query 'data[0].id' --raw-output
IMAGE_ID="ocid1.image.oc1.ap-hyderabad-1..CHANGE_ME"

# Instance display name.
DISPLAY_NAME="devquest-prod"

# SSH public key. Cloud Shell already has one at ~/.ssh/id_rsa.pub
# but if you want to use the id_ed25519_oci.pub you generated locally,
# either upload it to Cloud Shell first (drag-drop into the terminal)
# or paste it inline via SSH_PUB_KEY_INLINE below.
SSH_PUB_KEY_FILE="$HOME/.ssh/id_rsa.pub"
SSH_PUB_KEY_INLINE=""   # e.g. "ssh-ed25519 AAAA..."; leave empty to use file

# Shape + resources (free-tier Ampere: up to 4 OCPU / 24GB total across all
# A1.Flex instances — 1/6 leaves room for a second instance later).
SHAPE="VM.Standard.A1.Flex"
OCPUS=1
MEMORY_GB=6
BOOT_VOL_GB=50   # 50GB is the free-tier max per instance

# Retry pacing. OCI rate-limits at ~5 req/min per user, so 45s is safe.
SLEEP_ON_CAPACITY=45
SLEEP_ON_RATE_LIMIT=180
SLEEP_ON_UNKNOWN=60

# Fault domains to rotate through (Hyderabad AD-1 has all three).
FAULT_DOMAINS=("FAULT-DOMAIN-1" "FAULT-DOMAIN-2" "FAULT-DOMAIN-3")

# Dashboard cadence — print summary every N attempts.
DASHBOARD_EVERY=5

# ═══ END CONFIG ═════════════════════════════════════════════════════

# ─── Auto-tmux wrap ────────────────────────────────────────────────
# If not already inside tmux, re-launch ourselves inside a detached
# session called "ampere". Prevents Cloud Shell tab close from killing
# a multi-hour retry loop. Skip if TMUX var is set OR if user opted out
# via NO_TMUX=1.
if [[ -z "${TMUX:-}" && "${NO_TMUX:-0}" != "1" ]]; then
  if command -v tmux >/dev/null 2>&1; then
    if tmux has-session -t ampere 2>/dev/null; then
      echo "▸ tmux session 'ampere' already exists."
      echo "  Attach: tmux attach -t ampere"
      echo "  Kill:   tmux kill-session -t ampere"
      exit 0
    fi
    echo "▸ Wrapping in tmux session 'ampere' (detached)..."
    echo "  Watch: tmux attach -t ampere"
    echo "  Stop : tmux kill-session -t ampere"
    tmux new-session -d -s ampere "bash '$0'"
    exit 0
  else
    echo "▸ tmux not found — running in foreground. Do not close this tab."
  fi
fi

# ─── Sanity checks ─────────────────────────────────────────────────
missing=0
for var in COMPARTMENT_ID AD_NAME SUBNET_ID IMAGE_ID; do
  if [[ "${!var}" == *"CHANGE_ME"* ]]; then
    echo "✗ $var is not set — edit this script and fill it in."
    missing=1
  fi
done
if [[ -z "$SSH_PUB_KEY_INLINE" && ! -f "$SSH_PUB_KEY_FILE" ]]; then
  echo "✗ SSH key: file '$SSH_PUB_KEY_FILE' missing and SSH_PUB_KEY_INLINE empty."
  missing=1
fi
if [[ $missing -eq 1 ]]; then exit 1; fi

if ! command -v oci >/dev/null 2>&1; then
  echo "✗ oci CLI not found. Run this inside OCI Cloud Shell (pre-installed)."
  exit 1
fi

# Build the SSH key argument (--ssh-authorized-keys-file OR inline via a tmpfile)
if [[ -n "$SSH_PUB_KEY_INLINE" ]]; then
  SSH_KEY_TMP=$(mktemp)
  echo "$SSH_PUB_KEY_INLINE" > "$SSH_KEY_TMP"
  trap 'rm -f "$SSH_KEY_TMP"' EXIT
  SSH_KEY_ARGS=(--ssh-authorized-keys-file "$SSH_KEY_TMP")
else
  SSH_KEY_ARGS=(--ssh-authorized-keys-file "$SSH_PUB_KEY_FILE")
fi

# ─── Logging setup ─────────────────────────────────────────────────
# Timestamped log file so multiple runs don't stomp each other. Every
# attempt row is one line: greppable for post-mortem.
LOG_FILE="$HOME/oci-retry-$(date +%Y%m%d-%H%M%S).log"
log() { printf '[%s] %s\n' "$(date +'%H:%M:%S')" "$*" >> "$LOG_FILE"; }
log "=== oci-ampere-retry.sh started (pid $$) ==="
log "shape=$SHAPE ocpus=$OCPUS mem=${MEMORY_GB}GB ad=$AD_NAME"

# Per-fault-domain success/fail counters for the dashboard.
declare -A FD_ATTEMPTS FD_CAPACITY FD_RATELIMIT FD_OTHER
for fd in "${FAULT_DOMAINS[@]}"; do
  FD_ATTEMPTS[$fd]=0; FD_CAPACITY[$fd]=0; FD_RATELIMIT[$fd]=0; FD_OTHER[$fd]=0
done

print_dashboard() {
  local elapsed_min=$(( ($(date +%s) - started_at) / 60 ))
  echo
  echo "─── dashboard @ attempt #$attempt · elapsed ${elapsed_min}m ───"
  printf "  %-16s %8s %10s %10s %8s\n" "Fault Domain" "Attempts" "NoCapacity" "RateLimit" "Other"
  for fd in "${FAULT_DOMAINS[@]}"; do
    printf "  %-16s %8d %10d %10d %8d\n" \
      "$fd" "${FD_ATTEMPTS[$fd]}" "${FD_CAPACITY[$fd]}" "${FD_RATELIMIT[$fd]}" "${FD_OTHER[$fd]}"
  done
  echo "  log: $LOG_FILE"
  echo "─────────────────────────────────────────────────────────────"
  echo
}

echo "═══════════════════════════════════════════════════════════════"
echo "  OCI Ampere A1.Flex retry loop"
echo "  Shape:      $SHAPE  ($OCPUS OCPU / ${MEMORY_GB}GB / ${BOOT_VOL_GB}GB)"
echo "  AD:         $AD_NAME"
echo "  Rotating:   ${FAULT_DOMAINS[*]}"
echo "  Sleep:      ${SLEEP_ON_CAPACITY}s (capacity) / ${SLEEP_ON_RATE_LIMIT}s (rate-limit)"
echo "  Log file:   $LOG_FILE"
echo "  Started:    $(date)"
if [[ -n "${TMUX:-}" ]]; then
  echo "  Detach:     Ctrl+B D  (you're in tmux 'ampere')"
fi
echo "═══════════════════════════════════════════════════════════════"
echo

attempt=0
fd_index=0
started_at=$(date +%s)

# Graceful shutdown — on Ctrl+C, print a final dashboard, dump log
# path, and EXIT. Without the explicit exit, bash returns control to
# the interrupted line and the loop keeps spinning — you'd have to
# kill the tmux session to actually stop it.
on_exit() {
  echo
  echo "▸ Interrupted after $attempt attempts. Log: $LOG_FILE"
  print_dashboard
  log "=== interrupted ==="
  exit 130   # 128 + SIGINT(2), the conventional Ctrl+C exit code
}
trap on_exit INT TERM

while true; do
  attempt=$((attempt + 1))
  fd="${FAULT_DOMAINS[$fd_index]}"
  fd_index=$(((fd_index + 1) % 3))
  FD_ATTEMPTS[$fd]=$((FD_ATTEMPTS[$fd] + 1))

  ts=$(date +'%H:%M:%S')
  elapsed=$(($(date +%s) - started_at))
  printf "[%s] #%03d  %s ... " "$ts" "$attempt" "$fd"

  call_start=$(date +%s)
  result=$(oci compute instance launch \
    --availability-domain "$AD_NAME" \
    --compartment-id "$COMPARTMENT_ID" \
    --shape "$SHAPE" \
    --shape-config "{\"ocpus\":$OCPUS,\"memoryInGBs\":$MEMORY_GB}" \
    --display-name "$DISPLAY_NAME" \
    --image-id "$IMAGE_ID" \
    --subnet-id "$SUBNET_ID" \
    --assign-public-ip true \
    --fault-domain "$fd" \
    --boot-volume-size-in-gbs "$BOOT_VOL_GB" \
    "${SSH_KEY_ARGS[@]}" \
    --wait-for-state RUNNING 2>&1)
  rc=$?
  call_dur=$(($(date +%s) - call_start))

  if [[ $rc -eq 0 ]]; then
    echo "SUCCESS ✓ (${call_dur}s)"
    log "attempt=$attempt fd=$fd result=SUCCESS dur=${call_dur}s"
    echo
    echo "═══════════════════════════════════════════════════════════════"
    echo "  🎉  Instance provisioned on attempt #$attempt after ${elapsed}s"
    echo "═══════════════════════════════════════════════════════════════"
    print_dashboard

    # Pull id + public IP out of the JSON response for quick SSH access.
    instance_id=$(echo "$result" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])' 2>/dev/null || echo "")
    if [[ -n "$instance_id" ]]; then
      echo "Instance OCID: $instance_id"
      log "instance_id=$instance_id"
      public_ip=$(oci compute instance list-vnics --instance-id "$instance_id" \
                    --query 'data[0]."public-ip"' --raw-output 2>/dev/null || echo "")
      if [[ -n "$public_ip" && "$public_ip" != "null" ]]; then
        echo "Public IP:     $public_ip"
        log "public_ip=$public_ip"
        echo
        echo "SSH in (once cloud-init finishes, ~60s):"
        echo "  ssh ubuntu@$public_ip"
      fi
    else
      echo "$result" | tail -30
    fi
    log "=== done ==="
    exit 0
  fi

  # Diagnose the failure — classify, log, count, sleep.
  err_line=$(echo "$result" | grep -oE '"code": "[^"]+"|"message": "[^"]+"' | head -2 | tr '\n' ' ')
  if echo "$result" | grep -qi "Out of host capacity\|Out of capacity"; then
    echo "no capacity → sleep ${SLEEP_ON_CAPACITY}s"
    FD_CAPACITY[$fd]=$((FD_CAPACITY[$fd] + 1))
    log "attempt=$attempt fd=$fd result=OUT_OF_CAPACITY dur=${call_dur}s"
    sleep_dur=$SLEEP_ON_CAPACITY
  elif echo "$result" | grep -qi "TooManyRequests\|Too many requests"; then
    echo "rate-limited → sleep ${SLEEP_ON_RATE_LIMIT}s"
    FD_RATELIMIT[$fd]=$((FD_RATELIMIT[$fd] + 1))
    log "attempt=$attempt fd=$fd result=RATE_LIMITED dur=${call_dur}s"
    sleep_dur=$SLEEP_ON_RATE_LIMIT
  elif echo "$result" | grep -qi "LimitExceeded\|has been reached\|quota"; then
    echo "TENANCY LIMIT HIT ✗"
    echo "$result" | tail -20
    log "attempt=$attempt fd=$fd result=LIMIT_EXCEEDED err='$err_line'"
    echo
    echo "Check Governance → Limits, Quotas and Usage."
    echo "Free-tier A1.Flex limit is 4 OCPU / 24GB total across ALL instances."
    log "=== exit 2 (limit) ==="
    exit 2
  elif echo "$result" | grep -qi "InvalidParameter\|NotAuthorized\|NotAuthenticated\|CompartmentNotFound"; then
    echo "CONFIG ERROR ✗"
    echo "$result" | tail -20
    log "attempt=$attempt fd=$fd result=CONFIG_ERROR err='$err_line'"
    echo
    echo "Fix the OCIDs / permissions at the top of this script, then re-run."
    log "=== exit 3 (config) ==="
    exit 3
  else
    # Uncategorized failure. Show the full response tail on stdout AND
    # log it — otherwise you'll see "unknown error" with no hint what
    # actually broke. If the response is empty (e.g. oci killed by
    # signal, network cut), we say so explicitly.
    if [[ -z "$result" ]]; then
      echo "empty response (oci killed or network issue) → sleep ${SLEEP_ON_UNKNOWN}s"
      log "attempt=$attempt fd=$fd result=EMPTY_RESPONSE dur=${call_dur}s"
    else
      echo "unknown error → sleep ${SLEEP_ON_UNKNOWN}s"
      echo "  ↳ $(echo "$result" | tail -5 | head -3 | tr '\n' ' ')"
      log "attempt=$attempt fd=$fd result=UNKNOWN err='$err_line' full='$(echo "$result" | tail -5 | tr '\n' ' ')'"
    fi
    FD_OTHER[$fd]=$((FD_OTHER[$fd] + 1))
    sleep_dur=$SLEEP_ON_UNKNOWN
  fi

  # Dashboard every N attempts — visibility without spamming stdout.
  if (( attempt % DASHBOARD_EVERY == 0 )); then
    print_dashboard
  fi

  sleep "$sleep_dur"
done
