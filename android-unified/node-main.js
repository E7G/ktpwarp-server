const fs = require("fs");
const path = require("path");
const ts = require("typescript");
const { pathToFileURL } = require("url");

process.chdir(__dirname);

process.on("uncaughtException", (error) => {
  console.error("[ktpWarp mobile] uncaught exception:", error);
  process.exit(1);
});

process.on("unhandledRejection", (error) => {
  console.error("[ktpWarp mobile] unhandled rejection:", error);
  process.exit(1);
});

const pkg = require("./package.json");
process.env.npm_package_version = pkg.version;

console.log("[ktpWarp mobile] Node.js", process.version);
console.log("[ktpWarp mobile] Preparing config.ts…");

const configSourcePath = path.join(__dirname, "config.ts");
const configOutputPath = path.join(__dirname, "dist-mobile", "config.js");
const configSource = fs.readFileSync(configSourcePath, "utf8");

const transpiled = ts.transpileModule(configSource, {
  compilerOptions: {
    target: ts.ScriptTarget.ES2020,
    module: ts.ModuleKind.ES2020,
    esModuleInterop: true,
    allowSyntheticDefaultImports: true,
  },
  fileName: "config.ts",
  reportDiagnostics: true,
});

if (transpiled.diagnostics && transpiled.diagnostics.length > 0) {
  const host = {
    getCanonicalFileName: (fileName) => fileName,
    getCurrentDirectory: () => __dirname,
    getNewLine: () => "\n",
  };
  console.error(ts.formatDiagnosticsWithColorAndContext(transpiled.diagnostics, host));
  process.exit(1);
}

fs.writeFileSync(configOutputPath, transpiled.outputText, "utf8");
console.log("[ktpWarp mobile] Starting original ktpwarp-server…");

import(pathToFileURL(path.join(__dirname, "dist-mobile", "index.js")).href)
  .catch((error) => {
    console.error("[ktpWarp mobile] Failed to load server:", error);
    process.exit(1);
  });
