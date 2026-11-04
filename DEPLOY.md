# Deploying to a server

This moves the app from your PC to an always-on Linux server, reachable at `https://your-domain`, with an automatic HTTPS certificate. Your drafts, news, settings and audit history come along.

**What you need**
- A VPS with **Ubuntu 24.04**, **2 vCPU / 2–4 GB RAM / 40 GB disk**. A Mumbai or Bangalore region keeps it close to you. Any provider works: DigitalOcean, Hetzner, AWS Lightsail, Linode, Hostinger or similar, usually ₹500–1,200 a month.
- A **domain or subdomain** you control, e.g. `desk.yourdomain.in`.
- SSH access to the server.

---

## Free option: Oracle Cloud Always Free (ARM)

1. **Sign up** at oracle.com/cloud/free. A card is needed for verification. Choose **India South (Hyderabad)** or **India West (Mumbai)** as the *home region*; it can't be changed later and free servers only run there.
2. **Optional but recommended:** upgrade the account to **Pay-As-You-Go**, then add a **budget alert** (Billing → Budgets) of ₹100. Always Free resources stay free, and Oracle doesn't reclaim "idle" servers on paid accounts.
3. **Create the VM:** Compute → Instances → Create.
   - **Image:** Canonical Ubuntu 24.04.
   - **Shape:** Ampere → `VM.Standard.A1.Flex`, **1 OCPU, 6 GB** (or 2 OCPU / 12 GB if you upgraded to Pay-As-You-Go).
   - **Networking:** keep the default VCN and *assign a public IPv4 address*.
   - **SSH keys:** *Generate a key pair* → **download the private key**.
   - "Out of capacity"? Try another availability domain, or again later.
4. **Open ports 80 and 443 in Oracle's network:** instance → Subnet → Security List → Add Ingress Rules: source `0.0.0.0/0`, TCP, destination port `80`; repeat for `443`.
5. **Free domain:** at duckdns.org, sign in, add a subdomain such as `vijaydesk`, and set its IP to the VM's public IP. Your domain is then `vijaydesk.duckdns.org`.
6. **SSH in from Windows (Command Prompt):**
   ```bat
   icacls C:\Users\vijay\Downloads\ssh-key.key /inheritance:r /grant:r "%USERNAME%":R
   ssh -i C:\Users\vijay\Downloads\ssh-key.key ubuntu@<public-ip>
   ```
   The user is `ubuntu`. The `icacls` line fixes Windows' "unprotected private key" error.
7. Continue with **step 2** below. `setup-ubuntu.sh` also opens 80/443 in the Oracle image's built-in firewall.

## 1. Point your domain at the server
At your domain registrar or DNS provider, create an **A record**: `desk.yourdomain.in → <server public IP>`. Wait until `ping desk.yourdomain.in` shows the server IP. Caddy needs this before it can get a certificate.

## 2. Prepare the server (once)
```bash
ssh youruser@<server-ip>
sudo mkdir -p /opt/x-content-bot && sudo chown $USER /opt/x-content-bot
git clone https://github.com/vijay951997/twitter-content-bot.git /opt/x-content-bot
# Private repo? Git asks for a username and password: use your GitHub username and a token from
# github.com/settings/personal-access-tokens (fine-grained, this repo only, Contents: Read-only).
cd /opt/x-content-bot
bash deploy/setup-ubuntu.sh      # Docker, firewall (22/80/443 only), security updates, swap
exit                             # log out and back in so the docker group applies
```

## 3. Export your data from your PC (Command Prompt, in your project folder)
```bat
docker compose exec postgres pg_dump -U xbot -d xbot -Fc -f /tmp/xbot.dump
docker compose cp postgres:/tmp/xbot.dump xbot.dump
scp xbot.dump youruser@<server-ip>:/opt/x-content-bot/
```
Use Command Prompt rather than PowerShell for these commands. `scp` is built into Windows 10/11.

## 4. Create the server's `.env`
```bash
cd /opt/x-content-bot
cp .env.example .env
chmod 600 .env
nano .env
```
Fill in:

| Setting | Value |
|---|---|
| `SPRING_DATASOURCE_PASSWORD` | a **new**, long random password (the server's own database) |
| `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` | only used if you start fresh; a restored database keeps your existing login |
| `OPENAI_API_KEY`, `OPENAI_MODEL` | same as on your PC |
| `X_CLIENT_ID`, `X_CLIENT_SECRET` | same as on your PC |
| `X_REDIRECT_URI` | `https://desk.yourdomain.in/api/x/callback` |
| `APP_TOKEN_ENCRYPTION_KEY` | **the same key as on your PC** to keep your X connection; a new one means reconnecting X once |
| `DOMAIN` | `desk.yourdomain.in` |
| `ACME_EMAIL` | your email (certificate notices) |
| `SESSION_COOKIE_SECURE` | `true` |
| `APP_CORS_ALLOWED_ORIGINS` | leave empty |

## 5. Add the new callback to your X app
console.x.com → your app → **User authentication settings** → **Callback URI**: add `https://desk.yourdomain.in/api/x/callback`. You can keep the localhost one too. Save.

## 6. Start, then restore your data
```bash
cd /opt/x-content-bot
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build   # first build takes 5–10 min
./deploy/restore.sh xbot.dump        # type YES; replaces the empty server DB with your data
rm xbot.dump
```
Open **https://desk.yourdomain.in** and log in with your usual username and password.
If you didn't reuse the encryption key, go to **Settings → Connect X account** once.

## 7. Turn off the local copy
On your PC run `docker compose down` in the project folder. If both copies run with publishing on, the same approved post could be sent from both. Run only one.

## 8. Daily backups
```bash
crontab -e
# add this line (03:15 server time):
15 3 * * * cd /opt/x-content-bot && ./deploy/backup.sh >> backups/backup.log 2>&1
```
Backups stay 14 days in `/opt/x-content-bot/backups`. Copy one off the server now and then:
`scp youruser@<server-ip>:/opt/x-content-bot/backups/<file>.dump .`
Keep a copy of `.env` (especially `APP_TOKEN_ENCRYPTION_KEY`) somewhere safe, such as a password manager.

---

## Updating to a new version
```bash
cd /opt/x-content-bot
./deploy/backup.sh
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```
Database changes apply automatically.

## Security notes
- The dashboard is on the public internet. Use a long, unique admin password. After 10 failed logins in 15 minutes, further attempts from that IP or for that username are refused.
- To allow only your own IPs, uncomment the `@outsiders` lines in `deploy/Caddyfile` and restart Caddy:
  `docker compose -f docker-compose.yml -f docker-compose.prod.yml restart caddy`.
  This doesn't affect X sign-in, because X redirects *your browser*, not X's servers.
- Only ports 22, 80 and 443 are open. The database and backend listen on 127.0.0.1 only.
- Logs: `docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend`

## Troubleshooting
| Symptom | Fix |
|---|---|
| Browser shows a certificate error | DNS doesn't point at the server yet, or ports 80/443 are blocked by the provider's firewall. Check `docker compose ... logs caddy` |
| "Login failed. Is the backend running?" | Backend still starting (~40 s) or crashed: check `logs backend` |
| X says the callback URL is invalid | The callback in the X console must match `X_REDIRECT_URI` exactly |
| "Stored token can't be decrypted" | `APP_TOKEN_ENCRYPTION_KEY` differs from the one used when X was connected; reconnect X |
| Build fails with "killed" | Not enough RAM; the setup script adds swap, or use a 4 GB server |
| Oracle: site unreachable on 80/443 | Check the subnet Security List has ingress rules for 80 and 443, then rerun `bash deploy/setup-ubuntu.sh` |
