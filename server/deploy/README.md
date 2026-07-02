# Debian Deployment

Target: Debian 13 server.

## One-command install or upgrade

Install or upgrade the service with:

```bash
curl -fsSL https://raw.githubusercontent.com/Lecheeel/Quorvia/master/server/deploy/install-debian.sh | sudo bash
```

With a public domain and automatic HTTPS via Caddy:

```bash
curl -fsSL https://raw.githubusercontent.com/Lecheeel/Quorvia/master/server/deploy/install-debian.sh | sudo bash -s -- --domain example.com
```

The installer:

- installs required Debian packages;
- installs Node.js 24 LTS from official Node.js tarballs when no suitable Node.js is already present;
- deploys the server to `/opt/quorvia/server`;
- preserves `/etc/quorvia/qrng-proxy.env` on upgrade;
- builds in `/opt/quorvia/staging/server` before publishing to avoid half-built upgrades;
- installs and enables the `quorvia-qrng` systemd service;
- optionally configures Caddy when `--domain` is provided.

The first interactive install asks for `AQN_API_KEY` and saves it to
`/etc/quorvia/qrng-proxy.env` with `0600` permissions. Upgrades keep the
existing file. To set the key manually later:

```bash
sudo editor /etc/quorvia/qrng-proxy.env
sudo systemctl restart quorvia-qrng
```

For non-interactive provisioning:

```bash
curl -fsSL https://raw.githubusercontent.com/Lecheeel/Quorvia/master/server/deploy/install-debian.sh | sudo bash -s -- --aqn-api-key "replace-with-real-key"
```

Default production environment:

```bash
NODE_ENV=production
HOST=127.0.0.1
PORT=49030
AQN_API_KEY=
AQN_API_URL=https://api.quantumnumbers.anu.edu.au
AQN_TIMEOUT_MS=15000
CORS_ORIGINS=*
```

## Status

```bash
curl -fsSL https://raw.githubusercontent.com/Lecheeel/Quorvia/master/server/deploy/install-debian.sh | sudo bash -s -- --status
```

Or, on the server:

```bash
sudo systemctl status quorvia-qrng
sudo journalctl -u quorvia-qrng -f
curl http://127.0.0.1:49030/health
```

## Uninstall

Application-level uninstall keeps secrets and backups:

```bash
curl -fsSL https://raw.githubusercontent.com/Lecheeel/Quorvia/master/server/deploy/install-debian.sh | sudo bash -s -- --uninstall
```

Full purge removes `/opt/quorvia`, `/etc/quorvia`, and the system user:

```bash
curl -fsSL https://raw.githubusercontent.com/Lecheeel/Quorvia/master/server/deploy/install-debian.sh | sudo bash -s -- --uninstall --purge
```

If Caddy was configured by the installer and you want to remove its managed
Caddyfile during uninstall:

```bash
curl -fsSL https://raw.githubusercontent.com/Lecheeel/Quorvia/master/server/deploy/install-debian.sh | sudo bash -s -- --uninstall --remove-caddy
```

## Local script usage

From a cloned repository:

```bash
sudo bash server/deploy/install-debian.sh
sudo bash server/deploy/install-debian.sh --domain example.com
sudo bash server/deploy/install-debian.sh --status
sudo bash server/deploy/install-debian.sh --uninstall
```

Useful options:

```bash
--ref master
--repo-url https://github.com/Lecheeel/Quorvia.git
--aqn-api-key replace-with-real-key
--node-version 24.18.0
--force-node
--no-caddy
-y
```
