#!/bin/bash

# fnOS 安装后应用文件位于 target/ 下，TRIM_APPDEST 有时指向应用根目录而非 target

init_trim_env() {
  local script_dir="$1"
  local app_root
  app_root="$(dirname "${script_dir}")"

  if [ -z "${TRIM_APPNAME}" ]; then
    TRIM_APPNAME="ktpwarp-server"
  fi

  if [ -z "${TRIM_PKGVAR}" ]; then
    for d in \
      "/vol1/@appdata/${TRIM_APPNAME}" \
      "/vol1/@appdata/ktpwarp-server" \
      "/var/apps/${TRIM_APPNAME}/var" \
      "/var/apps/ktpwarp-server/var" \
      "${app_root}/var"
    do
      if [ -d "$d" ] || [ "$d" = "/vol1/@appdata/${TRIM_APPNAME}" ]; then
        TRIM_PKGVAR="$d"
        break
      fi
    done
    TRIM_PKGVAR="${TRIM_PKGVAR:-/vol1/@appdata/ktpwarp-server}"
  fi

  if [ -z "${TRIM_APPDEST}" ]; then
    for d in \
      "/vol1/@appcenter/${TRIM_APPNAME}" \
      "/vol1/@appcenter/ktpwarp-server" \
      "/var/apps/${TRIM_APPNAME}/target" \
      "/var/apps/ktpwarp-server/target" \
      "${app_root}/target" \
      "${app_root}"
    do
      if [ -d "$d" ]; then
        TRIM_APPDEST="$d"
        break
      fi
    done
    TRIM_APPDEST="${TRIM_APPDEST:-/vol1/@appcenter/ktpwarp-server}"
  fi

  export TRIM_APPNAME TRIM_PKGVAR TRIM_APPDEST
}

resolve_server_dir() {
  local d
  for d in \
    "${TRIM_APPDEST}/target/server" \
    "${TRIM_APPDEST}/server" \
    "/vol1/@appcenter/${TRIM_APPNAME}/server" \
    "/vol1/@appcenter/ktpwarp-server/server" \
    "/var/apps/${TRIM_APPNAME}/target/server" \
    "/var/apps/ktpwarp-server/target/server" \
    "/var/apps/${TRIM_APPNAME}/server" \
    "/var/apps/ktpwarp-server/server"
  do
    if [ -n "$d" ] && [ -d "$d" ]; then
      if [ -f "$d/dist/index.js" ] || [ -f "$d/package.json" ]; then
        echo "$d"
        return 0
      fi
    fi
  done
  return 1
}

resolve_ui_dir() {
  local d
  for d in \
    "${TRIM_APPDEST}/target/ui" \
    "${TRIM_APPDEST}/ui" \
    "/vol1/@appcenter/${TRIM_APPNAME}/ui" \
    "/vol1/@appcenter/ktpwarp-server/ui" \
    "/var/apps/${TRIM_APPNAME}/target/ui" \
    "/var/apps/ktpwarp-server/target/ui" \
    "/var/apps/${TRIM_APPNAME}/ui" \
    "/var/apps/ktpwarp-server/ui"
  do
    if [ -n "$d" ] && [ -d "$d" ]; then
      echo "$d"
      return 0
    fi
  done
  return 1
}

resolve_web_dir() {
  local d
  for d in \
    "${TRIM_APPDEST}/target/web" \
    "${TRIM_APPDEST}/web" \
    "/vol1/@appcenter/${TRIM_APPNAME}/web" \
    "/vol1/@appcenter/ktpwarp-server/web" \
    "/var/apps/${TRIM_APPNAME}/target/web" \
    "/var/apps/ktpwarp-server/target/web" \
    "/var/apps/${TRIM_APPNAME}/web" \
    "/var/apps/ktpwarp-server/web"
  do
    if [ -n "$d" ] && [ -f "$d/index.html" ]; then
      echo "$d"
      return 0
    fi
  done
  return 1
}

# 从 config.json 读取端口（失败则用默认值）
read_config_port() {
  local key="$1"
  local default="$2"
  local config_file="${3:-${TRIM_PKGVAR}/config.json}"
  local node_bin="/var/apps/nodejs_v22/target/bin/node"

  if [ -f "${config_file}" ] && [ -x "${node_bin}" ]; then
    local val
    val="$("${node_bin}" -e "const c=require(process.argv[1]);const v=c[process.argv[2]];if(v!=null&&!Number.isNaN(Number(v)))process.stdout.write(String(Number(v)))" "${config_file}" "${key}" 2>/dev/null || true)"
    if [ -n "${val}" ]; then
      echo "${val}"
      return 0
    fi
  fi
  echo "${default}"
}

# 释放占用端口的残留进程（重启/异常退出后 PID 文件可能已失效）
free_tcp_port() {
  local port="$1"
  [ -n "${port}" ] || return 0

  if command -v fuser >/dev/null 2>&1; then
    fuser -k "${port}/tcp" >/dev/null 2>&1 || true
  fi

  if command -v lsof >/dev/null 2>&1; then
    local pids
    pids="$(lsof -ti:"${port}" 2>/dev/null || true)"
    if [ -n "${pids}" ]; then
      echo "${pids}" | xargs kill -9 2>/dev/null || true
    fi
  fi

  if command -v ss >/dev/null 2>&1; then
    ss -ltnp 2>/dev/null | grep -E ":${port}([^0-9]|$)" | sed -n 's/.*pid=\([0-9]*\).*/\1/p' | sort -u | while read -r pid; do
      [ -n "${pid}" ] && kill -9 "${pid}" 2>/dev/null || true
    done
  fi
}

kill_ktpwarp_processes() {
  pkill -f "config-api.js" 2>/dev/null || true
  pkill -f "ktpwarp-server.*/dist/index.js" 2>/dev/null || true
  if [ -n "${TRIM_PKGVAR}" ]; then
    pkill -f "${TRIM_PKGVAR}.*dist/index.js" 2>/dev/null || true
  fi
}

# 安装/启动前确保生产依赖存在（fpk 内已捆绑 node_modules 时跳过）
ensure_node_modules() {
  local server_dir="$1"
  local npm_bin="${2:-/var/apps/nodejs_v22/target/bin/npm}"

  if [ -f "${server_dir}/node_modules/winston/package.json" ]; then
    return 0
  fi

  if [ ! -x "${npm_bin}" ] || [ ! -f "${server_dir}/package.json" ]; then
    return 1
  fi

  export PATH="/var/apps/nodejs_v22/target/bin:$PATH"
  ${npm_bin} config set registry https://registry.npmmirror.com >/dev/null 2>&1 || true
  cd "${server_dir}" || return 1
  ${npm_bin} install --omit=dev
}
