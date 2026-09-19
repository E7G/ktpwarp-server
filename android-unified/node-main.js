const fs = require("fs");
const path = require("path");
const ts = require("typescript");
const { pathToFileURL } = require("url");

process.chdir(__dirname);

const pkg = require("./package.json");
process.env.npm_package_version = pkg.version;

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
  const message = ts.formatDiagnosticsWithColorAndContext(transpiled.diagnostics, host);
  console.error(message);
  process.exit(1);
}

fs.writeFileSync(configOutputPath, transpiled.outputText, "utf8");

import(pathToFileURL(path.join(__dirname, "dist-mobile", "index.js")).href)
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
