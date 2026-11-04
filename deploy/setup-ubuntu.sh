#!/usr/bin/env bash
# One-time server preparation for Ubuntu 22.04/24.04. Run as a sudo user:
#   curl -fsSL https://raw.githubusercontent.com/<you>/<repo>/main/deploy/setup-ubuntu.sh | bash
# or copy the file over and run: bash deploy/setup-ubuntu.sh
set -euo pipefail

echo "== Updating packages"
sudo apt-get update -y && sudo apt-get upgrade -y
sudo apt-get install -y ca-certificates curl git ufw unattended-upgrades

echo "== Installing Docker (official convenience script)"
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER"
fi

echo "== Firewall: only SSH, HTTP and HTTPS"
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw --force enable

echo "== Oracle Cloud images: open 80/443 in the preinstalled iptables rules too"
if [ -f /etc/iptables/rules.v4 ] && sudo iptables -S INPUT | grep -q "icmp-host-prohibited"; then
  for port in 80 443; do
    sudo iptables -C INPUT -p tcp --dport $port -m state --state NEW -j ACCEPT 2>/dev/null \
      || sudo iptables -I INPUT 5 -p tcp --dport $port -m state --state NEW -j ACCEPT
  done
  sudo iptables -C INPUT -p udp --dport 443 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 5 -p udp --dport 443 -j ACCEPT
  sudo netfilter-persistent save
fi

echo "== Automatic security updates"
sudo dpkg-reconfigure -f noninteractive unattended-upgrades

echo "== 2 GB swap (helps small servers build the Java backend)"
if [ ! -f /swapfile ] && [ "$(free -m | awk '/Mem:/{print $2}')" -lt 3500 ]; then
  sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

echo "Done. Log out and back in (so the docker group applies), then follow DEPLOY.md step 4."
