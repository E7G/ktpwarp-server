import fs from "node:fs";
import path from "node:path";

import type { ClassType, UserType } from "./types";

type ConfigFile = {
  CONFIG_VERSION: number;

  WEBSOCKET_SERVER_PORT: number;
  WEBSOCKET_ENABLE_TLS: boolean;
  TLS_CERT_PATH?: string;
  TLS_KEY_PATH?: string;
  WEBSOCKET_SERVER_PATH: string;

  ENABLE_TELEGRAM_BOT: boolean;
  TELEGRAM_BOT_TOKEN?: string;
  TELEGRAM_CHAT_ID?: number;
  TELEGRAM_BOT_PRIVACY_MODE?: boolean;
  TELEGRAM_BOT_PRIVACY_MODE_WHITELIST?: number[];
  TELEGRAM_BOT_REPLY_INVALID_QRCODE?: boolean;
  TELEGRAM_BOT_API_URL?: string;

  USERS: UserType[];
  CLASSES: ClassType[];

  DEFAULT_LATITUDE: string;
  DEFAULT_LONGITUDE: string;

  签到_CHECK_INTERVAL_SECONDS: number;
  MIN_DELAY_SECONDS: number;
  MAX_DELAY_SECONDS: number;
  FAKE_IP_PREFIX: string[];

  ENABLE_互动答题_CHECK: boolean;
  互动答题_CHECK_INTERVAL_SECONDS: number;
  互动答题_CHECK_DELAY_SECONDS: number;
};

function resolveConfigPath(): string {
  const explicit = process.env.KTPWARP_CONFIG_PATH?.trim();
  if (explicit) return explicit;

  const trimPkgVar = process.env.TRIM_PKGVAR?.trim();
  if (trimPkgVar) return path.join(trimPkgVar, "config.json");

  const candidates = [
    "/vol1/@appdata/ktpwarp-server/config.json",
    "/var/apps/ktpwarp-server/var/config.json",
    path.join(__dirname, "..", "config.json"),
    path.join(__dirname, "config.json"),
  ];
  for (const p of candidates) {
    if (fs.existsSync(p)) return p;
  }

  return candidates[0];
}

function readConfigFile(configPath: string): ConfigFile {
  if (!fs.existsSync(configPath)) {
    throw new Error(
      `未找到配置文件: ${configPath}\n` +
        `请复制 config.example.json 为 config.json 并按需修改，或设置环境变量 KTPWARP_CONFIG_PATH 指向配置文件。`,
    );
  }

  const raw = fs.readFileSync(configPath, "utf-8");
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch (e) {
    throw new Error(`配置文件不是合法 JSON: ${configPath}\n${String(e)}`);
  }

  return parsed as ConfigFile;
}

const CONFIG_PATH = resolveConfigPath();
const CONFIG = readConfigFile(CONFIG_PATH);

// 与旧版 config.example.ts 保持一致的导出名（以便现有代码无需改动）
export const CONFIG_VERSION = CONFIG.CONFIG_VERSION;

export const WEBSOCKET_SERVER_PORT = CONFIG.WEBSOCKET_SERVER_PORT;
export const WEBSOCKET_ENABLE_TLS = CONFIG.WEBSOCKET_ENABLE_TLS;
export const TLS_CERT_PATH = CONFIG.TLS_CERT_PATH ?? "";
export const TLS_KEY_PATH = CONFIG.TLS_KEY_PATH ?? "";
export const WEBSOCKET_SERVER_PATH = CONFIG.WEBSOCKET_SERVER_PATH;

export const ENABLE_TELEGRAM_BOT = CONFIG.ENABLE_TELEGRAM_BOT;
export const TELEGRAM_BOT_TOKEN = CONFIG.TELEGRAM_BOT_TOKEN ?? "";
export const TELEGRAM_CHAT_ID = CONFIG.TELEGRAM_CHAT_ID ?? 0;
export const TELEGRAM_BOT_PRIVACY_MODE = CONFIG.TELEGRAM_BOT_PRIVACY_MODE ?? true;
export const TELEGRAM_BOT_PRIVACY_MODE_WHITELIST =
  CONFIG.TELEGRAM_BOT_PRIVACY_MODE_WHITELIST ?? [];
export const TELEGRAM_BOT_REPLY_INVALID_QRCODE =
  CONFIG.TELEGRAM_BOT_REPLY_INVALID_QRCODE ?? false;
export const TELEGRAM_BOT_API_URL = CONFIG.TELEGRAM_BOT_API_URL ?? "https://api.telegram.org";

export const USERS = CONFIG.USERS;
export const CLASSES = CONFIG.CLASSES;

export const DEFAULT_LATITUDE = CONFIG.DEFAULT_LATITUDE;
export const DEFAULT_LONGITUDE = CONFIG.DEFAULT_LONGITUDE;

export const 签到_CHECK_INTERVAL_SECONDS = CONFIG.签到_CHECK_INTERVAL_SECONDS;
export const MIN_DELAY_SECONDS = CONFIG.MIN_DELAY_SECONDS;
export const MAX_DELAY_SECONDS = CONFIG.MAX_DELAY_SECONDS;
export const FAKE_IP_PREFIX = CONFIG.FAKE_IP_PREFIX;

export const ENABLE_互动答题_CHECK = CONFIG.ENABLE_互动答题_CHECK;
export const 互动答题_CHECK_INTERVAL_SECONDS = CONFIG.互动答题_CHECK_INTERVAL_SECONDS;
export const 互动答题_CHECK_DELAY_SECONDS = CONFIG.互动答题_CHECK_DELAY_SECONDS;

