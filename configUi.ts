import fs from "fs";
import path from "path";
import type { IncomingMessage, ServerResponse } from "http";

import {
  WEBSOCKET_ENABLE_TLS,
  WEBSOCKET_SERVER_PATH,
  WEBSOCKET_SERVER_PORT,
} from "./config";
import { LabelledLogger } from "./logger";

const logger = new LabelledLogger("config-ui");

function resolveUiDir(): string {
  const candidates = [
    process.env.KTPWARP_UI_DIR,
    process.env.TRIM_APPDEST ? path.join(process.env.TRIM_APPDEST, "target", "ui") : "",
    process.env.TRIM_APPDEST ? path.join(process.env.TRIM_APPDEST, "ui") : "",
    path.join(__dirname, "..", "ui"),
    path.join(__dirname, "ui"),
  ].filter(Boolean) as string[];

  for (const d of candidates) {
    if (fs.existsSync(path.join(d, "index.html"))) return d;
  }
  return path.join(__dirname, "..", "ui");
}

function resolveWebDir(): string | null {
  const candidates = [
    process.env.KTPWARP_WEB_DIR,
    process.env.TRIM_APPDEST ? path.join(process.env.TRIM_APPDEST, "target", "web") : "",
    process.env.TRIM_APPDEST ? path.join(process.env.TRIM_APPDEST, "web") : "",
    path.join(__dirname, "..", "web"),
    path.join(__dirname, "web"),
  ].filter(Boolean) as string[];

  for (const d of candidates) {
    if (fs.existsSync(path.join(d, "index.html"))) return d;
  }
  return null;
}

function configPath(): string {
  return (
    process.env.KTPWARP_CONFIG_PATH ||
    (process.env.TRIM_PKGVAR
      ? path.join(process.env.TRIM_PKGVAR, "config.json")
      : path.join(__dirname, "config.json"))
  );
}

function contentType(filePath: string): string {
  const ext = path.extname(filePath).toLowerCase();
  const map: Record<string, string> = {
    ".html": "text/html; charset=utf-8",
    ".css": "text/css; charset=utf-8",
    ".js": "application/javascript; charset=utf-8",
    ".png": "image/png",
    ".ico": "image/x-icon",
    ".json": "application/json; charset=utf-8",
    ".map": "application/json; charset=utf-8",
  };
  return map[ext] || "application/octet-stream";
}

function listenPort(): number {
  const fromEnv = process.env.KTPWARP_LISTEN_PORT || process.env.WEBSOCKET_SERVER_PORT;
  if (fromEnv && !Number.isNaN(Number(fromEnv))) return Number(fromEnv);
  return WEBSOCKET_SERVER_PORT;
}

function suggestedWsUrl(req: IncomingMessage): string {
  const host = req.headers.host || `127.0.0.1:${listenPort()}`;
  const scheme = WEBSOCKET_ENABLE_TLS ? "wss" : "ws";
  const pathPart = WEBSOCKET_SERVER_PATH.startsWith("/")
    ? WEBSOCKET_SERVER_PATH
    : `/${WEBSOCKET_SERVER_PATH}`;
  return `${scheme}://${host}${pathPart}`;
}

function serveStaticFile(
  res: ServerResponse,
  rootDir: string,
  relPath: string
): boolean {
  let rel = relPath === "/" ? "/index.html" : relPath;
  const filePath = path.normalize(path.join(rootDir, rel));
  if (!filePath.startsWith(path.normalize(rootDir))) {
    res.writeHead(403);
    res.end("Forbidden");
    return true;
  }
  if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
    res.writeHead(404);
    res.end("Not Found");
    return true;
  }
  const buf = fs.readFileSync(filePath);
  res.writeHead(200, { "Content-Type": contentType(filePath) });
  res.end(buf);
  return true;
}

function sendJson(res: ServerResponse, status: number, body: unknown) {
  const data = JSON.stringify(body);
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(data),
  });
  res.end(data);
}

function readBody(req: IncomingMessage): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on("data", (c) => chunks.push(c));
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", reject);
  });
}

function ensureConfigFile(cfg: string, serverDir: string) {
  if (fs.existsSync(cfg)) return;
  const example = path.join(path.dirname(cfg), "config.example.json");
  const serverExample = path.join(serverDir, "config.example.json");
  const src = fs.existsSync(example) ? example : fs.existsSync(serverExample) ? serverExample : null;
  if (!src) return;
  fs.mkdirSync(path.dirname(cfg), { recursive: true });
  fs.copyFileSync(src, cfg);
}

export function attachConfigUi(server: import("http").Server | import("https").Server) {
  const UI_DIR = resolveUiDir();
  const WEB_DIR = resolveWebDir();
  const SERVER_DIR = path.join(__dirname);
  logger.info(`UI dir: ${UI_DIR}`);
  if (WEB_DIR) logger.info(`Web client dir: ${WEB_DIR}`);

  server.on("request", async (req, res) => {
    if (!req.url) return;

    if (req.headers.upgrade?.toLowerCase() === "websocket") {
      return;
    }

    const url = new URL(req.url, "http://127.0.0.1");

    if (req.method === "GET" && url.pathname === "/api/health") {
      sendJson(res, 200, {
        ok: true,
        port: listenPort(),
      });
      return;
    }

    if (req.method === "GET" && url.pathname === "/api/public-info") {
      sendJson(res, 200, {
        port: listenPort(),
        wsPath: WEBSOCKET_SERVER_PATH,
        enableTls: WEBSOCKET_ENABLE_TLS,
        suggestedWsUrl: suggestedWsUrl(req),
      });
      return;
    }

    if (req.method === "GET" && url.pathname === "/api/config") {
      try {
        const cfg = configPath();
        ensureConfigFile(cfg, SERVER_DIR);
        const raw = fs.existsSync(cfg) ? fs.readFileSync(cfg, "utf8") : "{}";
        sendJson(res, 200, { path: cfg, config: JSON.parse(raw) });
      } catch (e) {
        sendJson(res, 500, { error: String(e) });
      }
      return;
    }

    if (req.method === "POST" && url.pathname === "/api/config") {
      try {
        const body = await readBody(req);
        const parsed = JSON.parse(body) as { config?: unknown };
        if (typeof parsed.config !== "object" || parsed.config === null) {
          sendJson(res, 400, { error: "Missing config object" });
          return;
        }
        const cfg = configPath();
        fs.mkdirSync(path.dirname(cfg), { recursive: true });
        fs.writeFileSync(cfg, JSON.stringify(parsed.config, null, 2) + "\n", "utf8");
        sendJson(res, 200, { ok: true, path: cfg });
      } catch (e) {
        sendJson(res, 400, { error: String(e) });
      }
      return;
    }

    if (req.method !== "GET") {
      res.writeHead(405);
      res.end("Method Not Allowed");
      return;
    }

    if (WEB_DIR && (url.pathname === "/web" || url.pathname.startsWith("/web/"))) {
      let rel = url.pathname.slice("/web".length) || "/";
      serveStaticFile(res, WEB_DIR, rel);
      return;
    }

    serveStaticFile(res, UI_DIR, url.pathname === "/" ? "/index.html" : url.pathname);
  });
}
