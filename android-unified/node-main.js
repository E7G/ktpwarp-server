process.chdir(__dirname);
const pkg = require("./package.json");
process.env.npm_package_version = pkg.version;
require("ts-node/register/transpile-only");
require("./index.ts");
