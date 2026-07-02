#!/usr/bin/env bash
set -Eeuo pipefail

APP_NAME="quorvia"
SERVICE_NAME="quorvia-qrng"
APP_USER="quorvia"
APP_GROUP="quorvia"
APP_ROOT="/opt/quorvia"
SERVER_DIR="${APP_ROOT}/server"
BACKUP_DIR="${APP_ROOT}/backups"
RUNTIME_DIR="${APP_ROOT}/runtime"
SOURCE_CACHE_DIR="${APP_ROOT}/source"
STAGING_DIR="${APP_ROOT}/staging/server"
NPM_CACHE_DIR="${APP_ROOT}/.npm-cache"
STATE_FILE="${APP_ROOT}/install-state.env"
ENV_DIR="/etc/quorvia"
ENV_FILE="${ENV_DIR}/qrng-proxy.env"
SYSTEMD_UNIT="/etc/systemd/system/${SERVICE_NAME}.service"
CADDYFILE="/etc/caddy/Caddyfile"
CADDY_SNIPPET="/etc/caddy/conf.d/quorvia-qrng.caddy"
DEFAULT_NODE_MAJOR="24"
DEFAULT_NODE_VERSION=""
REPO_URL="https://github.com/Lecheeel/Quorvia.git"
REPO_REF="master"
DOMAIN=""
ACTION="install"
INSTALL_CADDY="auto"
FORCE_NODE="0"
PURGE="0"
REMOVE_CADDY="0"
YES="0"
LAST_BACKUP=""
AQN_API_KEY_INPUT=""

log() {
  printf '[%s] %s\n' "${APP_NAME}" "$*" >&2
}

die() {
  printf '[%s] ERROR: %s\n' "${APP_NAME}" "$*" >&2
  exit 1
}

env_value() {
  local key="$1"
  awk -F= -v key="${key}" '$1 == key { value = substr($0, length(key) + 2) } END { print value }' "${ENV_FILE}"
}

usage() {
  cat <<'EOF'
Usage:
  sudo bash install-debian.sh [--install|--upgrade]
  curl -fsSL https://raw.githubusercontent.com/Lecheeel/Quorvia/master/server/deploy/install-debian.sh | sudo bash
  sudo bash install-debian.sh --status
  sudo bash install-debian.sh --uninstall [--purge] [--remove-caddy] [-y]

Options:
  --install              Install or upgrade the service. This is the default.
  --upgrade              Same as --install.
  --status               Show service and runtime status.
  --uninstall            Stop and remove the service and deployed app files.
  --purge                With --uninstall, also remove /etc/quorvia and the app user.
  --domain DOMAIN        Install/configure Caddy reverse proxy for DOMAIN.
  --no-caddy             Do not install or configure Caddy.
  --remove-caddy         With --uninstall, remove Caddy config managed by this script.
  --node-version VERSION Install a fixed Node.js version, for example 24.18.0.
  --force-node           Install managed Node.js even if an acceptable node exists.
  --repo-url URL         Git repository to install from. Defaults to Quorvia on GitHub.
  --ref REF              Git branch or tag to install from. Defaults to master.
  --aqn-api-key KEY      Set AQN_API_KEY non-interactively on first install.
  -y, --yes              Do not prompt for confirmation.
  -h, --help             Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --install|--upgrade)
      ACTION="install"
      shift
      ;;
    --status)
      ACTION="status"
      shift
      ;;
    --uninstall)
      ACTION="uninstall"
      shift
      ;;
    --purge)
      PURGE="1"
      shift
      ;;
    --domain)
      [[ $# -ge 2 ]] || die "--domain requires a value"
      DOMAIN="$2"
      INSTALL_CADDY="1"
      shift 2
      ;;
    --no-caddy)
      INSTALL_CADDY="0"
      shift
      ;;
    --remove-caddy)
      REMOVE_CADDY="1"
      shift
      ;;
    --node-version)
      [[ $# -ge 2 ]] || die "--node-version requires a value"
      DEFAULT_NODE_VERSION="$2"
      shift 2
      ;;
    --force-node)
      FORCE_NODE="1"
      shift
      ;;
    --repo-url)
      [[ $# -ge 2 ]] || die "--repo-url requires a value"
      REPO_URL="$2"
      shift 2
      ;;
    --ref)
      [[ $# -ge 2 ]] || die "--ref requires a value"
      REPO_REF="$2"
      shift 2
      ;;
    --aqn-api-key)
      [[ $# -ge 2 ]] || die "--aqn-api-key requires a value"
      AQN_API_KEY_INPUT="$2"
      shift 2
      ;;
    -y|--yes)
      YES="1"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown option: $1"
      ;;
  esac
done

require_root() {
  [[ "$(id -u)" -eq 0 ]] || die "Run this script as root, for example: sudo bash install-debian.sh"
}

confirm() {
  local prompt="$1"
  if [[ "${YES}" == "1" ]]; then
    return 0
  fi
  if [[ -r /dev/tty ]]; then
    read -r -p "${prompt} [y/N] " answer </dev/tty
  else
    log "No interactive terminal available; rerun with -y to confirm."
    return 1
  fi
  [[ "${answer}" == "y" || "${answer}" == "Y" || "${answer}" == "yes" || "${answer}" == "YES" ]]
}

validate_domain() {
  [[ -n "${DOMAIN}" ]] || return 0
  [[ "${DOMAIN}" =~ ^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$ ]] || die "Invalid domain: ${DOMAIN}"
}

require_debian() {
  [[ -r /etc/os-release ]] || die "Cannot read /etc/os-release"
  # shellcheck disable=SC1091
  . /etc/os-release
  [[ "${ID:-}" == "debian" ]] || die "This installer only supports Debian. Detected: ${PRETTY_NAME:-unknown}"
  if [[ "${VERSION_ID:-}" != "13" ]]; then
    log "Detected ${PRETTY_NAME:-Debian}; Debian 13 is the primary target. Continuing."
  fi
}

script_dir() {
  local source="${BASH_SOURCE[0]}"
  [[ -n "${source}" && -f "${source}" ]] || return 1
  while [[ -L "${source}" ]]; do
    local dir
    dir="$(cd -P "$(dirname "${source}")" && pwd)"
    source="$(readlink "${source}")"
    [[ "${source}" != /* ]] && source="${dir}/${source}"
  done
  cd -P "$(dirname "${source}")" && pwd
}

repo_root() {
  local dir
  if dir="$(script_dir)" && [[ -f "${dir}/../../server/package.json" ]]; then
    cd "${dir}/../.." && pwd
    return 0
  fi
  if [[ -f "./server/package.json" ]]; then
    pwd
    return 0
  fi
  return 1
}

fetch_repo_source() {
  log "Fetching source from ${REPO_URL} (${REPO_REF})"
  rm -rf "${SOURCE_CACHE_DIR}"
  install -d -m 0755 "${APP_ROOT}"
  git clone --depth 1 --branch "${REPO_REF}" "${REPO_URL}" "${SOURCE_CACHE_DIR}"
  [[ -f "${SOURCE_CACHE_DIR}/server/package.json" ]] || die "Fetched repository does not contain server/package.json"
  printf '%s\n' "${SOURCE_CACHE_DIR}"
}

server_source_dir() {
  local root
  if ! root="$(repo_root)"; then
    root="$(fetch_repo_source)"
  fi
  [[ -f "${root}/server/package.json" ]] || die "Cannot find server/package.json from ${root}"
  printf '%s\n' "${root}/server"
}

install_packages() {
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y --no-install-recommends apt-transport-https ca-certificates curl debian-archive-keyring debian-keyring git gnupg rsync xz-utils
}

node_arch() {
  case "$(uname -m)" in
    x86_64|amd64) printf 'x64\n' ;;
    aarch64|arm64) printf 'arm64\n' ;;
    armv7l) printf 'armv7l\n' ;;
    *) die "Unsupported CPU architecture for official Node.js binaries: $(uname -m)" ;;
  esac
}

node_major() {
  local node_bin="$1"
  "${node_bin}" -v 2>/dev/null | sed -E 's/^v([0-9]+).*/\1/'
}

command_path() {
  command -v "$1" 2>/dev/null || true
}

install_managed_node() {
  local arch version base_url filename tmp_dir checksum_line
  arch="$(node_arch)"

  if [[ -n "${DEFAULT_NODE_VERSION}" ]]; then
    version="${DEFAULT_NODE_VERSION#v}"
    base_url="https://nodejs.org/download/release/v${version}"
  else
    base_url="https://nodejs.org/download/release/latest-v${DEFAULT_NODE_MAJOR}.x"
    version=""
  fi

  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "${tmp_dir}"' RETURN

  log "Resolving Node.js ${DEFAULT_NODE_MAJOR} LTS for linux-${arch}"
  curl -fsSL "${base_url}/SHASUMS256.txt" -o "${tmp_dir}/SHASUMS256.txt"

  if [[ -z "${version}" ]]; then
    filename="$(grep -E "node-v[0-9]+\.[0-9]+\.[0-9]+-linux-${arch}\.tar\.xz$" "${tmp_dir}/SHASUMS256.txt" | awk '{print $2}' | head -n 1)"
    [[ -n "${filename}" ]] || die "Could not find Node.js linux-${arch} tarball in ${base_url}"
    version="$(printf '%s\n' "${filename}" | sed -E 's/^node-v([0-9]+\.[0-9]+\.[0-9]+)-linux-.*/\1/')"
  else
    filename="node-v${version}-linux-${arch}.tar.xz"
    grep -q " ${filename}$" "${tmp_dir}/SHASUMS256.txt" || die "Node.js ${filename} is not listed in ${base_url}/SHASUMS256.txt"
  fi

  local target_dir="${RUNTIME_DIR}/node-v${version}-linux-${arch}"
  if [[ ! -x "${target_dir}/bin/node" ]]; then
    log "Downloading Node.js v${version}"
    curl -fsSL "${base_url}/${filename}" -o "${tmp_dir}/${filename}"
    checksum_line="$(grep " ${filename}$" "${tmp_dir}/SHASUMS256.txt")"
    (cd "${tmp_dir}" && printf '%s\n' "${checksum_line}" | sha256sum -c -)
    mkdir -p "${RUNTIME_DIR}"
    tar -xJf "${tmp_dir}/${filename}" -C "${RUNTIME_DIR}"
  fi

  ln -sfn "${target_dir}" "${RUNTIME_DIR}/node-current"
  install -d -m 0755 /usr/local/bin
  link_managed_binary node
  link_managed_binary npm
  link_managed_binary npx
  rm -rf "${tmp_dir}"
  trap - RETURN
  printf '%s\n' "${RUNTIME_DIR}/node-current/bin/node"
}

link_managed_binary() {
  local name="$1"
  local target="${RUNTIME_DIR}/node-current/bin/${name}"
  local link="/usr/local/bin/${name}"
  if [[ -e "${link}" && ! -L "${link}" ]]; then
    log "Leaving existing ${link} in place; service will use ${target}"
    return 0
  fi
  if [[ -L "${link}" ]]; then
    local current
    current="$(readlink "${link}")"
    if [[ "${current}" != "${APP_ROOT}"/* && "${current}" != "${RUNTIME_DIR}"/* ]]; then
      log "Leaving existing symlink ${link} -> ${current} in place; service will use ${target}"
      return 0
    fi
  fi
  ln -sfn "${target}" "${link}"
}

resolve_node() {
  local existing_node existing_major
  existing_node="$(command_path node)"
  if [[ "${FORCE_NODE}" != "1" && -n "${existing_node}" ]]; then
    existing_major="$(node_major "${existing_node}")"
    if [[ -n "${existing_major}" && "${existing_major}" -ge "${DEFAULT_NODE_MAJOR}" ]]; then
      if [[ "${existing_node}" == /root/* || "${existing_node}" == /home/* ]]; then
        log "Ignoring Node.js at ${existing_node}; systemd service cannot rely on user-private paths."
      else
        log "Using existing Node.js at ${existing_node} ($("${existing_node}" -v))"
        printf '%s\n' "${existing_node}"
        return 0
      fi
    fi
  fi
  install_managed_node
}

ensure_user() {
  if ! getent group "${APP_GROUP}" >/dev/null; then
    groupadd --system "${APP_GROUP}"
  fi
  if ! id "${APP_USER}" >/dev/null 2>&1; then
    useradd --system --gid "${APP_GROUP}" --home "${APP_ROOT}" --shell /usr/sbin/nologin "${APP_USER}"
  fi
  install -d -m 0755 "${APP_ROOT}"
  install -d -m 0755 -o "${APP_USER}" -g "${APP_GROUP}" "${NPM_CACHE_DIR}"
}

write_env_template() {
  install -d -m 0755 "${ENV_DIR}"
  if [[ -f "${ENV_FILE}" ]]; then
    log "Keeping existing ${ENV_FILE}"
    return 0
  fi
  cat >"${ENV_FILE}" <<'EOF'
NODE_ENV=production
HOST=127.0.0.1
PORT=49030
AQN_API_KEY=
AQN_API_URL=https://api.quantumnumbers.anu.edu.au
AQN_TIMEOUT_MS=15000
CORS_ORIGINS=*
EOF
  chown root:root "${ENV_FILE}"
  chmod 600 "${ENV_FILE}"
  log "Created ${ENV_FILE}"
}

set_env_value() {
  local key="$1"
  local value="$2"
  local escaped
  escaped="$(printf '%s' "${value}" | sed -e 's/[\/&]/\\&/g')"
  if grep -q "^${key}=" "${ENV_FILE}"; then
    sed -i "s/^${key}=.*/${key}=${escaped}/" "${ENV_FILE}"
  else
    printf '%s=%s\n' "${key}" "${value}" >>"${ENV_FILE}"
  fi
  chown root:root "${ENV_FILE}"
  chmod 600 "${ENV_FILE}"
}

prompt_for_api_key_if_needed() {
  env_has_api_key && return 0

  if [[ -n "${AQN_API_KEY_INPUT}" ]]; then
    set_env_value "AQN_API_KEY" "${AQN_API_KEY_INPUT}"
    log "Saved AQN_API_KEY to ${ENV_FILE}"
    return 0
  fi

  if [[ "${YES}" == "1" ]]; then
    log "AQN_API_KEY is not set. Skipping interactive prompt because -y/--yes was provided."
    return 1
  fi

  if [[ ! -r /dev/tty ]]; then
    log "AQN_API_KEY is not set and no interactive terminal is available."
    log "Rerun with --aqn-api-key KEY, or edit ${ENV_FILE} manually."
    return 1
  fi

  local api_key confirm_key
  while true; do
    read -r -s -p "Enter AQN_API_KEY: " api_key </dev/tty
    printf '\n' >/dev/tty
    read -r -s -p "Confirm AQN_API_KEY: " confirm_key </dev/tty
    printf '\n' >/dev/tty

    if [[ -z "${api_key}" ]]; then
      log "AQN_API_KEY cannot be empty."
      continue
    fi
    if [[ "${api_key}" != "${confirm_key}" ]]; then
      log "AQN_API_KEY values did not match."
      continue
    fi
    set_env_value "AQN_API_KEY" "${api_key}"
    log "Saved AQN_API_KEY to ${ENV_FILE}"
    return 0
  done
}

env_has_api_key() {
  [[ -f "${ENV_FILE}" ]] || return 1
  grep -Eq '^AQN_API_KEY=.+$' "${ENV_FILE}" && ! grep -Eq '^AQN_API_KEY=(replace-with-real-key)?$' "${ENV_FILE}"
}

validate_env_file() {
  [[ -f "${ENV_FILE}" ]] || die "Missing ${ENV_FILE}"

  local host port api_key api_url timeout
  host="$(env_value HOST)"
  port="$(env_value PORT)"
  api_key="$(env_value AQN_API_KEY)"
  api_url="$(env_value AQN_API_URL)"
  timeout="$(env_value AQN_TIMEOUT_MS)"

  [[ -n "${host}" ]] || die "HOST is required in ${ENV_FILE}"
  [[ "${port}" =~ ^[0-9]+$ && "${port}" -ge 1 && "${port}" -le 65535 ]] || die "PORT must be 1..65535 in ${ENV_FILE}"
  [[ -n "${api_url}" && "${api_url}" =~ ^https?:// ]] || die "AQN_API_URL must be an http(s) URL in ${ENV_FILE}"
  [[ "${timeout}" =~ ^[0-9]+$ && "${timeout}" -ge 1000 && "${timeout}" -le 60000 ]] || die "AQN_TIMEOUT_MS must be 1000..60000 in ${ENV_FILE}"

  if [[ -z "${api_key}" || "${api_key}" == "replace-with-real-key" ]]; then
    return 1
  fi
  return 0
}

backup_existing_server() {
  [[ -d "${SERVER_DIR}" ]] || return 0
  install -d -m 0755 "${BACKUP_DIR}"
  local stamp backup
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  backup="${BACKUP_DIR}/server-${stamp}"
  log "Backing up existing server to ${backup}"
  rsync -a --delete "${SERVER_DIR}/" "${backup}/"
  LAST_BACKUP="${backup}"
}

restore_previous_server() {
  [[ -n "${LAST_BACKUP}" && -d "${LAST_BACKUP}" ]] || return 1
  log "Restoring previous server from ${LAST_BACKUP}"
  rsync -a --delete "${LAST_BACKUP}/" "${SERVER_DIR}/"
  chown -R "${APP_USER}:${APP_GROUP}" "${SERVER_DIR}"
  systemctl restart "${SERVICE_NAME}" 2>/dev/null || true
}

sync_server_files() {
  local src
  src="$(server_source_dir)"
  rm -rf "${STAGING_DIR}"
  install -d -m 0755 "${STAGING_DIR}"
  rsync -a --delete \
    --exclude '.env' \
    --exclude '.env.test' \
    --exclude 'node_modules' \
    --exclude 'dist' \
    "${src}/" "${STAGING_DIR}/"
  chown -R "${APP_USER}:${APP_GROUP}" "${STAGING_DIR}"
}

run_npm_build() {
  local node_bin="$1"
  local npm_bin
  npm_bin="$(dirname "${node_bin}")/npm"
  [[ -x "${npm_bin}" ]] || npm_bin="$(command_path npm)"
  [[ -x "${npm_bin}" ]] || die "npm was not found"

  log "Installing dependencies"
  (cd "${STAGING_DIR}" && runuser -u "${APP_USER}" -- env PATH="$(dirname "${node_bin}"):${PATH}" HOME="${APP_ROOT}" NPM_CONFIG_CACHE="${NPM_CACHE_DIR}" NPM_CONFIG_PRODUCTION=false "${npm_bin}" ci)

  log "Checking and building server"
  (cd "${STAGING_DIR}" && runuser -u "${APP_USER}" -- env PATH="$(dirname "${node_bin}"):${PATH}" HOME="${APP_ROOT}" NPM_CONFIG_CACHE="${NPM_CACHE_DIR}" "${npm_bin}" run check)
  (cd "${STAGING_DIR}" && runuser -u "${APP_USER}" -- env PATH="$(dirname "${node_bin}"):${PATH}" HOME="${APP_ROOT}" NPM_CONFIG_CACHE="${NPM_CACHE_DIR}" "${npm_bin}" run build)
}

publish_staging() {
  log "Publishing built server"
  install -d -m 0755 "${SERVER_DIR}"
  systemctl stop "${SERVICE_NAME}" 2>/dev/null || true
  rsync -a --delete "${STAGING_DIR}/" "${SERVER_DIR}/"
  chown -R "${APP_USER}:${APP_GROUP}" "${SERVER_DIR}"
  rm -rf "${STAGING_DIR}"
}

write_systemd_unit() {
  local node_bin="$1"
  cat >"${SYSTEMD_UNIT}" <<EOF
[Unit]
Description=Quorvia QRNG Proxy
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=${SERVER_DIR}
EnvironmentFile=${ENV_FILE}
ExecStart=${node_bin} dist/index.js
Restart=always
RestartSec=5
User=${APP_USER}
Group=${APP_GROUP}
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
LockPersonality=true
RestrictSUIDSGID=true
SystemCallArchitectures=native
UMask=0077

[Install]
WantedBy=multi-user.target
EOF
  chmod 644 "${SYSTEMD_UNIT}"
  systemctl daemon-reload
}

install_caddy_if_requested() {
  if [[ "${INSTALL_CADDY}" == "0" || -z "${DOMAIN}" ]]; then
    return 0
  fi
  validate_domain

  log "Installing Caddy for ${DOMAIN}"
  validate_env_file || true
  rm -f /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' -o /etc/apt/sources.list.d/caddy-stable.list
  chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg /etc/apt/sources.list.d/caddy-stable.list
  apt-get update
  apt-get install -y caddy

  install -d -m 0755 /etc/caddy/conf.d
  touch "${CADDYFILE}"
  if ! grep -q '^import /etc/caddy/conf.d/\*\.caddy$' "${CADDYFILE}"; then
    printf '\nimport /etc/caddy/conf.d/*.caddy\n' >>"${CADDYFILE}"
  fi

  local proxy_port
  proxy_port="$(env_value PORT)"
  cat >"${CADDY_SNIPPET}" <<EOF
# managed-by: quorvia-install-debian
${DOMAIN} {
	reverse_proxy 127.0.0.1:${proxy_port}
}
EOF
  systemctl enable --now caddy
  systemctl reload caddy
}

write_state() {
  local node_bin="$1"
  local node_managed="0"
  [[ "${node_bin}" == "${RUNTIME_DIR}"/* ]] && node_managed="1"
  install -d -m 0755 "${APP_ROOT}"
  {
    printf 'MANAGED_BY=%q\n' "quorvia-install-debian"
    printf 'SERVICE_NAME=%q\n' "${SERVICE_NAME}"
    printf 'APP_ROOT=%q\n' "${APP_ROOT}"
    printf 'SERVER_DIR=%q\n' "${SERVER_DIR}"
    printf 'ENV_FILE=%q\n' "${ENV_FILE}"
    printf 'NODE_BIN=%q\n' "${node_bin}"
    printf 'NODE_MANAGED=%q\n' "${node_managed}"
    printf 'REPO_URL=%q\n' "${REPO_URL}"
    printf 'REPO_REF=%q\n' "${REPO_REF}"
    printf 'CADDY_MANAGED=%q\n' "$([[ "${INSTALL_CADDY}" != "0" && -n "${DOMAIN}" ]] && printf '1' || printf '0')"
    printf 'CADDY_DOMAIN=%q\n' "${DOMAIN}"
  } >"${STATE_FILE}"
  chmod 644 "${STATE_FILE}"
}

load_state_value() {
  local key="$1"
  [[ -f "${STATE_FILE}" ]] || return 0
  awk -F= -v key="${key}" '$1 == key { value = substr($0, length(key) + 2) } END { print value }' "${STATE_FILE}"
}

load_state() {
  [[ -f "${STATE_FILE}" ]] || return 0
  NODE_BIN="$(load_state_value NODE_BIN)"
  NODE_MANAGED="$(load_state_value NODE_MANAGED)"
  CADDY_MANAGED="$(load_state_value CADDY_MANAGED)"
  CADDY_DOMAIN="$(load_state_value CADDY_DOMAIN)"
}

health_check() {
  systemctl enable "${SERVICE_NAME}" >/dev/null
  validate_env_file

  local port
  port="$(env_value PORT)"
  systemctl restart "${SERVICE_NAME}"

  local attempt
  for attempt in $(seq 1 20); do
    if curl -fsS --max-time 5 "http://127.0.0.1:${port}/health" >/dev/null; then
      log "Health check passed: http://127.0.0.1:${port}/health"
      return 0
    fi
    sleep 1
  done

  systemctl --no-pager --full status "${SERVICE_NAME}" || true
  journalctl -u "${SERVICE_NAME}" -n 80 --no-pager || true
  return 1
}

install_or_upgrade() {
  require_root
  require_debian
  validate_domain
  install_packages
  ensure_user
  local node_bin can_start
  node_bin="$(resolve_node)"
  write_env_template
  prompt_for_api_key_if_needed || true
  can_start="0"
  if validate_env_file; then
    can_start="1"
  else
    log "AQN_API_KEY is not set yet; the service will be installed but not restarted."
    if systemctl is-active --quiet "${SERVICE_NAME}" 2>/dev/null; then
      die "${SERVICE_NAME} is currently running; fix ${ENV_FILE} before upgrading so the script does not stop a running service."
    fi
  fi
  backup_existing_server
  sync_server_files
  run_npm_build "${node_bin}"
  publish_staging
  write_systemd_unit "${node_bin}"
  install_caddy_if_requested
  write_state "${node_bin}"
  if [[ "${can_start}" == "1" ]]; then
    if ! health_check; then
      restore_previous_server || true
      die "Health check failed after deployment. Previous server files were restored when a backup was available."
    fi
  else
    systemctl enable "${SERVICE_NAME}" >/dev/null
    log "Set AQN_API_KEY in ${ENV_FILE}, then run: systemctl restart ${SERVICE_NAME}"
  fi
  log "Done. Logs: journalctl -u ${SERVICE_NAME} -f"
}

status() {
  load_state
  printf 'Service: %s\n' "${SERVICE_NAME}"
  if command -v systemctl >/dev/null 2>&1; then
    systemctl --no-pager --full status "${SERVICE_NAME}" || true
  fi
  printf '\nNode:\n'
  if [[ -n "${NODE_BIN:-}" && -x "${NODE_BIN:-}" ]]; then
    "${NODE_BIN}" -v || true
  elif command -v node >/dev/null 2>&1; then
    node -v || true
  else
    printf 'not found\n'
  fi
  printf '\nPaths:\n'
  printf '  app: %s\n' "${SERVER_DIR}"
  printf '  env: %s\n' "${ENV_FILE}"
  printf '  state: %s\n' "${STATE_FILE}"
}

remove_managed_node() {
  load_state
  [[ "${NODE_MANAGED:-0}" == "1" ]] || return 0
  [[ -d "${RUNTIME_DIR}" ]] || return 0
  log "Removing managed Node.js runtime"
  for name in node npm npx; do
    local link="/usr/local/bin/${name}"
    if [[ -L "${link}" ]]; then
      local target
      target="$(readlink "${link}")"
      if [[ "${target}" == "${RUNTIME_DIR}"/* || "${target}" == "${APP_ROOT}"/* ]]; then
        rm -f "${link}"
      fi
    fi
  done
  rm -rf "${RUNTIME_DIR}"
}

remove_caddy_config() {
  if [[ "${REMOVE_CADDY}" != "1" ]]; then
    return 0
  fi
  if [[ -f "${CADDYFILE}" ]] && grep -q "managed-by: quorvia-install-debian" "${CADDYFILE}"; then
    log "Removing legacy managed Caddyfile"
    rm -f "${CADDYFILE}"
  fi
  if [[ -f "${CADDY_SNIPPET}" ]]; then
    log "Removing managed Caddy snippet"
    rm -f "${CADDY_SNIPPET}"
    systemctl reload caddy 2>/dev/null || true
  fi
}

uninstall() {
  require_root
  load_state
  if ! confirm "Remove ${SERVICE_NAME} from this server?"; then
    log "Uninstall cancelled."
    exit 0
  fi

  systemctl stop "${SERVICE_NAME}" 2>/dev/null || true
  systemctl disable "${SERVICE_NAME}" 2>/dev/null || true
  rm -f "${SYSTEMD_UNIT}"
  systemctl daemon-reload 2>/dev/null || true
  systemctl reset-failed "${SERVICE_NAME}" 2>/dev/null || true

  rm -rf "${SERVER_DIR}"
  rm -rf "${SOURCE_CACHE_DIR}" "${STAGING_DIR}"
  remove_managed_node
  remove_caddy_config

  if [[ "${PURGE}" == "1" ]]; then
    log "Purging ${APP_ROOT}, ${ENV_DIR}, and system user"
    rm -rf "${APP_ROOT}" "${ENV_DIR}"
    userdel "${APP_USER}" 2>/dev/null || true
    groupdel "${APP_GROUP}" 2>/dev/null || true
  else
    log "Keeping ${ENV_FILE}, backups, and install state. Use --purge to remove them."
  fi

  log "Uninstall complete."
}

case "${ACTION}" in
  install) install_or_upgrade ;;
  status) status ;;
  uninstall) uninstall ;;
  *) die "Unknown action: ${ACTION}" ;;
esac
