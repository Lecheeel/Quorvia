# Quorvia QRNG Proxy

Small Node.js proxy for protecting the ANU/AQN API key. It intentionally does
not provide any local random fallback. If ANU/AQN is unavailable, route
generation must fail closed.

## Local setup

```bash
npm install
cp .env.example .env
npm run dev
```

Required environment:

```bash
AQN_API_KEY=...
AQN_API_URL=https://api.quantumnumbers.anu.edu.au
AQN_TIMEOUT_MS=15000
```

Health check:

```bash
curl http://localhost:49030/health
```

QRNG request:

```bash
curl "http://localhost:49030/v1/qrng?type=uint16&length=32"
```
