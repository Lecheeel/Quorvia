# Debian 13 Deployment

Target: Alibaba Cloud lightweight server, Debian 13, 1C/1G.

## Install Node.js 24 LTS

Use NodeSource or your preferred package source, then verify:

```bash
node -v
npm -v
```

## Deploy files

```bash
sudo useradd --system --home /opt/quorvia --shell /usr/sbin/nologin quorvia
sudo mkdir -p /opt/quorvia/server /etc/quorvia
sudo chown -R quorvia:quorvia /opt/quorvia
```

Copy the `server` directory to `/opt/quorvia/server`, then install/build:

```bash
cd /opt/quorvia/server
npm ci
npm run build
```

Create `/etc/quorvia/qrng-proxy.env`:

```bash
NODE_ENV=production
HOST=127.0.0.1
PORT=8080
AQN_API_KEY=replace-with-real-key
AQN_API_URL=https://api.quantumnumbers.anu.edu.au
AQN_TIMEOUT_MS=15000
CORS_ORIGINS=*
```

Lock it down:

```bash
sudo chown root:root /etc/quorvia/qrng-proxy.env
sudo chmod 600 /etc/quorvia/qrng-proxy.env
```

Install systemd unit:

```bash
sudo cp deploy/quorvia-qrng.service /etc/systemd/system/quorvia-qrng.service
sudo systemctl daemon-reload
sudo systemctl enable --now quorvia-qrng
sudo systemctl status quorvia-qrng
```

Health check:

```bash
curl http://127.0.0.1:8080/health
curl "http://127.0.0.1:8080/v1/qrng?type=uint16&length=4"
```

For public Android access, put Nginx/Caddy in front with HTTPS and proxy to
`127.0.0.1:8080`.
