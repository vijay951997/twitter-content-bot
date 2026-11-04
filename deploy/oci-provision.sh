#!/usr/bin/env bash
# Creates the free Oracle Cloud server for X Content Desk. Run it in OCI Cloud Shell
# (already signed in to your account):
#   bash <(curl -fsSL https://raw.githubusercontent.com/vijay951997/twitter-content-bot/main/deploy/oci-provision.sh) you@example.com
#
# Creates (only if missing, safe to re-run): network xbot-vcn with ports 22/80/443 open, an Always Free
# ARM server "xbot" (1 OCPU / 6 GB / 50 GB, Ubuntu 24.04), and an SSH key in Cloud Shell (~/.ssh/xbot).
# The server then installs Docker, gets the code, prepares .env WITHOUT secrets, and builds the app.
set -euo pipefail

EMAIL="${1:?Usage: oci-provision.sh your-email (used for HTTPS certificate notices)}"
C="${OCI_TENANCY:?Run this in OCI Cloud Shell}"   # root compartment
OCPUS="${OCPUS:-1}"; MEM="${MEM:-6}"
say() { printf '\n\033[1;32m== %s\033[0m\n' "$*"; }
q() { "$@" 2>/dev/null || true; }

say "SSH key for Cloud Shell -> server"
mkdir -p ~/.ssh
# Cloud Shell runs in FIPS mode, which refuses ED25519, so use RSA
[ -f ~/.ssh/xbot ] || ssh-keygen -q -t rsa -b 4096 -N "" -f ~/.ssh/xbot -C xbot

say "Network"
VCN=$(q oci network vcn list -c "$C" --display-name xbot-vcn --lifecycle-state AVAILABLE --query 'data[0].id' --raw-output)
if [ -z "$VCN" ] || [ "$VCN" = "null" ]; then
  VCN=$(oci network vcn create -c "$C" --display-name xbot-vcn --cidr-blocks '["10.0.0.0/16"]' --dns-label xbotvcn \
        --wait-for-state AVAILABLE --query 'data.id' --raw-output)
fi
IGW=$(q oci network internet-gateway list -c "$C" --vcn-id "$VCN" --query 'data[0].id' --raw-output)
if [ -z "$IGW" ] || [ "$IGW" = "null" ]; then
  IGW=$(oci network internet-gateway create -c "$C" --vcn-id "$VCN" --is-enabled true --display-name xbot-igw \
        --wait-for-state AVAILABLE --query 'data.id' --raw-output)
fi
RT=$(oci network vcn get --vcn-id "$VCN" --query 'data."default-route-table-id"' --raw-output)
oci network route-table update --rt-id "$RT" --force \
  --route-rules "[{\"destination\":\"0.0.0.0/0\",\"destinationType\":\"CIDR_BLOCK\",\"networkEntityId\":\"$IGW\"}]" >/dev/null
SL=$(oci network vcn get --vcn-id "$VCN" --query 'data."default-security-list-id"' --raw-output)
oci network security-list update --security-list-id "$SL" --force \
  --egress-security-rules '[{"destination":"0.0.0.0/0","protocol":"all","isStateless":false}]' \
  --ingress-security-rules '[
    {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":22,"max":22}}},
    {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":80,"max":80}}},
    {"source":"0.0.0.0/0","protocol":"6","isStateless":false,"tcpOptions":{"destinationPortRange":{"min":443,"max":443}}},
    {"source":"0.0.0.0/0","protocol":"17","isStateless":false,"udpOptions":{"destinationPortRange":{"min":443,"max":443}}},
    {"source":"0.0.0.0/0","protocol":"1","isStateless":false,"icmpOptions":{"type":3,"code":4}}
  ]' >/dev/null
SUB=$(q oci network subnet list -c "$C" --vcn-id "$VCN" --display-name xbot-subnet --query 'data[0].id' --raw-output)
if [ -z "$SUB" ] || [ "$SUB" = "null" ]; then
  SUB=$(oci network subnet create -c "$C" --vcn-id "$VCN" --cidr-block 10.0.0.0/24 --display-name xbot-subnet \
        --dns-label xbotsub --wait-for-state AVAILABLE --query 'data.id' --raw-output)
fi
echo "VCN, gateway, routes, firewall (22/80/443) and subnet ready."

say "Server"
INST=$(q oci compute instance list -c "$C" --display-name xbot --query 'data[?"lifecycle-state"!=`TERMINATED` && "lifecycle-state"!=`TERMINATING`] | [0].id' --raw-output)
if [ -z "$INST" ] || [ "$INST" = "null" ]; then
  IMG=$(oci compute image list -c "$C" --operating-system "Canonical Ubuntu" --operating-system-version "24.04" \
        --shape VM.Standard.A1.Flex --sort-by TIMECREATED --sort-order DESC --query 'data[0].id' --raw-output)
  [ -n "$IMG" ] && [ "$IMG" != "null" ] || { echo "No Ubuntu 24.04 ARM image found"; exit 1; }

  USERDATA=$(mktemp)
  cat > "$USERDATA" <<CLOUDINIT
#!/bin/bash
exec > /var/log/xbot-setup.log 2>&1
set -x
export DEBIAN_FRONTEND=noninteractive
apt-get update && apt-get install -y ca-certificates curl git
curl -fsSL https://get.docker.com | sh
usermod -aG docker ubuntu
# Oracle's Ubuntu image ships iptables rules that reject everything except SSH
for p in 80 443; do iptables -I INPUT 5 -p tcp --dport \$p -m state --state NEW -j ACCEPT; done
iptables -I INPUT 5 -p udp --dport 443 -j ACCEPT
netfilter-persistent save || true
if [ ! -f /swapfile ]; then fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile && echo '/swapfile none swap sw 0 0' >> /etc/fstab; fi
git clone https://github.com/vijay951997/twitter-content-bot.git /opt/x-content-bot
cd /opt/x-content-bot
IP=\$(curl -s --max-time 10 https://api.ipify.org || curl -s --max-time 10 https://ifconfig.me)
DOMAIN="\$(echo \$IP | tr . -).sslip.io"
cp .env.example .env
sed -i "s|^SPRING_DATASOURCE_PASSWORD=.*|SPRING_DATASOURCE_PASSWORD=\$(openssl rand -hex 24)|" .env
sed -i "s|^DOMAIN=.*|DOMAIN=\$DOMAIN|; s|^ACME_EMAIL=.*|ACME_EMAIL=$EMAIL|" .env
sed -i "s|^X_REDIRECT_URI=.*|X_REDIRECT_URI=https://\$DOMAIN/api/x/callback|" .env
sed -i "s|^SESSION_COOKIE_SECURE=.*|SESSION_COOKIE_SECURE=true|; s|^APP_CORS_ALLOWED_ORIGINS=.*|APP_CORS_ALLOWED_ORIGINS=|" .env
chmod 600 .env
chown -R ubuntu:ubuntu /opt/x-content-bot
docker compose -f docker-compose.yml -f docker-compose.prod.yml build
echo "\$DOMAIN" > /var/log/xbot-setup.done
CLOUDINIT

  AD_LIST=$(oci iam availability-domain list -c "$C" --query 'data[].name' --raw-output | tr -d '[]",' )
  for attempt in $(seq 1 30); do
    for AD in $AD_LIST; do
      echo "Launching in $AD (attempt $attempt)..."
      if OUT=$(oci compute instance launch -c "$C" --availability-domain "$AD" --display-name xbot \
            --shape VM.Standard.A1.Flex --shape-config "{\"ocpus\":$OCPUS,\"memoryInGBs\":$MEM}" \
            --image-id "$IMG" --subnet-id "$SUB" --assign-public-ip true --boot-volume-size-in-gbs 50 \
            --ssh-authorized-keys-file ~/.ssh/xbot.pub --user-data-file "$USERDATA" \
            --wait-for-state RUNNING --query 'data.id' --raw-output 2>&1); then
        INST=$(echo "$OUT" | grep -o 'ocid1.instance[^" ]*' | head -1)
        break 2
      fi
      # A launch that timed out while waiting may still have created the server: reuse it
      INST=$(q oci compute instance list -c "$C" --display-name xbot --query 'data[?"lifecycle-state"!=`TERMINATED` && "lifecycle-state"!=`TERMINATING`] | [0].id' --raw-output)
      if [ -n "$INST" ] && [ "$INST" != "null" ]; then
        oci compute instance get --instance-id "$INST" --wait-for-state RUNNING >/dev/null; break 2
      fi
      INST=""
      if echo "$OUT" | grep -qi "capacity"; then echo "  Oracle is out of free ARM capacity there right now."
      elif echo "$OUT" | grep -qi "TooManyRequests\|\"status\": 429"; then echo "  Oracle is rate-limiting launch requests; waiting."
      else echo "$OUT"; exit 1; fi
    done
    echo "Retrying in 90 s..."; sleep 90
  done
  [ -n "${INST:-}" ] || { echo "Still no free capacity. Run this script again later; it resumes where it stopped."; exit 1; }
fi

IP=$(oci compute instance list-vnics --instance-id "$INST" --query 'data[0]."public-ip"' --raw-output)
DOMAIN="$(echo "$IP" | tr . -).sslip.io"
cat > ~/xbot-server.txt <<INFO
Server IP:   $IP
Dashboard:   https://$DOMAIN   (after you start the app)
SSH from Cloud Shell:  ssh -i ~/.ssh/xbot ubuntu@$IP
INFO

say "Done"
cat ~/xbot-server.txt
echo
echo "The server is now installing Docker and building the app (about 10-15 minutes)."
echo "Check progress:  ssh -i ~/.ssh/xbot ubuntu@$IP 'tail -3 /var/log/xbot-setup.log; cat /var/log/xbot-setup.done 2>/dev/null'"
