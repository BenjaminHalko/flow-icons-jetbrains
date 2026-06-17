#!/usr/bin/env node
"use strict";

/**
 * Flow Icons base-set updater for the JetBrains plugin.
 *
 * Mirrors the Zed port's `update-icons.cjs`, but tailored to this repo's layout:
 * it pulls the freely-distributable base icon set from the Open VSX VSIX and
 * refreshes the bundled resources so the plugin always ships the latest icons.
 *
 *   node tools/update-icons.cjs
 *
 * Steps:
 *   1. Resolve the latest extension version + VSIX url from Open VSX.
 *   2. If it matches `.icon-version` (and icons exist), do nothing.
 *   3. Otherwise download the VSIX (a ZIP) and repopulate:
 *        - src/main/resources/icons/<variant>/*.svg   (8 rendered variants)
 *        - src/main/resources/flow-you-templates/*.svg (+ index.txt manifest)
 *        - src/main/resources/flow/mapping.json        (from deep.json)
 *      then write `.icon-version` and set `pluginVersion` in gradle.properties.
 *
 * Outputs `version=<v>` and `changed=<bool>` to stdout and to $GITHUB_OUTPUT so
 * the CI workflow can decide whether to build + release.
 *
 * Zero dependencies (Node built-in https + zlib).
 */

const https = require("https");
const { inflateRawSync } = require("zlib");
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const RES = path.join(ROOT, "src", "main", "resources");
const ICONS_DIR = path.join(RES, "icons");
const TEMPLATES_DIR = path.join(RES, "flow-you-templates");
const MAPPING_FILE = path.join(RES, "flow", "mapping.json");
const PACKS_FILE = path.join(RES, "flow", "packs.json");
const ICONS_ASSOC_FILE = path.join(RES, "flow", "icons.json");
const VERSION_FILE = path.join(ROOT, ".icon-version");
const GRADLE_PROPS = path.join(ROOT, "gradle.properties");

const NAMESPACE = "thang-nm";
const EXTENSION = "flow-icons";
const OPENVSX_API = `https://open-vsx.org/api/${NAMESPACE}/${EXTENSION}`;
const USER_AGENT = "Flow Icons (JetBrains)";

// The eight rendered variant folders shipped in the VSIX, plus the `icons/`
// template folder which becomes our flow-you-templates.
const RENDERED_VARIANTS = [
  "deep",
  "deep-light",
  "dim",
  "dim-light",
  "dawn",
  "dawn-light",
  "you",
  "you-light",
];
const TEMPLATE_SOURCE = "icons";

// ---------------------------------------------------------------------------
// HTTP
// ---------------------------------------------------------------------------

function httpGet(url, headers) {
  return new Promise((resolve, reject) => {
    https
      .get(
        url,
        { headers: { "user-agent": USER_AGENT, ...headers } },
        (res) => {
          if (
            (res.statusCode === 301 || res.statusCode === 302) &&
            res.headers.location
          ) {
            return httpGet(res.headers.location, headers).then(resolve, reject);
          }
          const chunks = [];
          res.on("data", (c) => chunks.push(c));
          res.on("end", () =>
            resolve({ status: res.statusCode, body: Buffer.concat(chunks) }),
          );
        },
      )
      .on("error", (err) =>
        reject(new Error(`Connection error: ${err.message}`)),
      );
  });
}

async function getExtensionInfo() {
  const { status, body } = await httpGet(OPENVSX_API);
  if (status !== 200)
    throw new Error(`Open VSX error (${status}): ${body.toString()}`);
  const info = JSON.parse(body.toString());
  return { version: info.version, vsixUrl: info.files && info.files.download };
}

async function download(url) {
  const { status, body } = await httpGet(url);
  if (status !== 200) throw new Error(`Download failed (${status})`);
  return body;
}

// ---------------------------------------------------------------------------
// ZIP (VSIX) extraction — central-directory walk, no dependencies
// ---------------------------------------------------------------------------

function findEocd(buffer) {
  for (let i = buffer.length - 22; i >= 0; i--) {
    if (buffer.readUInt32LE(i) === 0x06054b50) return i;
  }
  return -1;
}

function eachZipEntry(buffer, fn) {
  const eocd = findEocd(buffer);
  if (eocd === -1) throw new Error("Invalid ZIP: EOCD not found");
  const entries = buffer.readUInt16LE(eocd + 10);
  let offset = buffer.readUInt32LE(eocd + 16);

  for (let i = 0; i < entries; i++) {
    if (buffer.readUInt32LE(offset) !== 0x02014b50) break;
    const method = buffer.readUInt16LE(offset + 10);
    const compSize = buffer.readUInt32LE(offset + 20);
    const fnLen = buffer.readUInt16LE(offset + 28);
    const extraLen = buffer.readUInt16LE(offset + 30);
    const commentLen = buffer.readUInt16LE(offset + 32);
    const localOffset = buffer.readUInt32LE(offset + 42);
    const filename = buffer.toString("utf8", offset + 46, offset + 46 + fnLen);
    offset += 46 + fnLen + extraLen + commentLen;

    if (filename.endsWith("/")) continue;
    fn(filename, () => {
      const localFnLen = buffer.readUInt16LE(localOffset + 26);
      const localExtraLen = buffer.readUInt16LE(localOffset + 28);
      const dataOffset = localOffset + 30 + localFnLen + localExtraLen;
      const raw = buffer.slice(dataOffset, dataOffset + compSize);
      if (method === 0) return raw;
      if (method === 8) return inflateRawSync(raw);
      return null;
    });
  }
}

// Map a VSIX entry name (sans "extension/") to its destination, or null to skip.
function destFor(name) {
  if (!name.endsWith(".svg")) return null;
  const slash = name.indexOf("/");
  if (slash < 0) return null;
  const top = name.slice(0, slash);
  const rest = name.slice(slash + 1);
  if (rest.includes("/")) return null; // only one level deep, skip nested
  if (RENDERED_VARIANTS.includes(top)) return path.join(ICONS_DIR, top, rest);
  if (top === TEMPLATE_SOURCE) return path.join(TEMPLATES_DIR, rest);
  return null;
}

// ---------------------------------------------------------------------------
// Mapping
// ---------------------------------------------------------------------------

function buildMapping(theme) {
  return {
    defaults: {
      file: theme.file,
      folder: theme.folder,
      folderExpanded: theme.folderExpanded,
      rootFolder: theme.rootFolder,
      rootFolderExpanded: theme.rootFolderExpanded,
    },
    languageIds: theme.languageIds || {},
    fileExtensions: theme.fileExtensions || {},
    fileNames: theme.fileNames || {},
    folderNames: theme.folderNames || {},
    folderNamesExpanded: theme.folderNamesExpanded || {},
  };
}

// ---------------------------------------------------------------------------
// Misc
// ---------------------------------------------------------------------------

function readVersion() {
  try {
    return fs.readFileSync(VERSION_FILE, "utf8").trim();
  } catch {
    return null;
  }
}

function updateGradleVersion(version) {
  let props = fs.readFileSync(GRADLE_PROPS, "utf8");
  if (/^pluginVersion=.*$/m.test(props)) {
    props = props.replace(/^pluginVersion=.*$/m, `pluginVersion=${version}`);
  } else {
    props += `\npluginVersion=${version}\n`;
  }
  fs.writeFileSync(GRADLE_PROPS, props);
}

function emitOutputs(version, changed) {
  console.log(`version=${version}`);
  console.log(`changed=${changed}`);
  if (process.env.GITHUB_OUTPUT) {
    fs.appendFileSync(
      process.env.GITHUB_OUTPUT,
      `version=${version}\nchanged=${changed}\n`,
    );
  }
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

async function main() {
  process.stdout.write("Resolving latest version from Open VSX... ");
  const { version, vsixUrl } = await getExtensionInfo();
  console.log(version);
  if (!vsixUrl) throw new Error("Open VSX did not return a VSIX download URL.");

  const current = readVersion();
  const iconsPresent = fs.existsSync(path.join(ICONS_DIR, "deep"));
  if (current === version && iconsPresent) {
    console.log("Already up to date.");
    emitOutputs(version, false);
    return;
  }

  console.log(`Downloading VSIX v${version}...`);
  const vsix = await download(vsixUrl);

  // Repopulate icon + template folders from scratch.
  fs.rmSync(ICONS_DIR, { recursive: true, force: true });
  fs.rmSync(TEMPLATES_DIR, { recursive: true, force: true });

  let svgCount = 0;
  let deepThemeJson = null;
  let packsBuf = null;
  let iconsAssocBuf = null;
  eachZipEntry(vsix, (filename, read) => {
    const name = filename.replace(/^extension\//, "");
    if (name === "deep.json") {
      const buf = read();
      if (buf) deepThemeJson = JSON.parse(buf.toString("utf8"));
      return;
    }
    // Tables backing the activeIconPack setting.
    if (name === "settings.json") {
      packsBuf = read();
      return;
    }
    if (name === "icons.json") {
      iconsAssocBuf = read();
      return;
    }
    const dest = destFor(name);
    if (!dest) return;
    const buf = read();
    if (!buf) return;
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.writeFileSync(dest, buf);
    svgCount++;
  });

  if (svgCount === 0) throw new Error("No icons were extracted from the VSIX.");
  if (!deepThemeJson) throw new Error("deep.json not found in the VSIX.");

  // Flow You template manifest (filenames the runtime generator iterates).
  const templateNames = fs
    .readdirSync(TEMPLATES_DIR)
    .filter((f) => f.endsWith(".svg"))
    .sort();
  fs.writeFileSync(
    path.join(TEMPLATES_DIR, "index.txt"),
    templateNames.join("\n"),
  );

  // Lookup table.
  fs.mkdirSync(path.dirname(MAPPING_FILE), { recursive: true });
  fs.writeFileSync(MAPPING_FILE, JSON.stringify(buildMapping(deepThemeJson)));

  // Tables backing the activeIconPack setting (pack definitions + per-icon
  // associations used to add icons back when a default-off pack is enabled).
  if (packsBuf) fs.writeFileSync(PACKS_FILE, packsBuf);
  if (iconsAssocBuf) fs.writeFileSync(ICONS_ASSOC_FILE, iconsAssocBuf);

  fs.writeFileSync(VERSION_FILE, version);
  updateGradleVersion(version);

  console.log(
    `Extracted ${svgCount} SVGs (${templateNames.length} templates).`,
  );
  console.log(`Set pluginVersion=${version}.`);
  emitOutputs(version, true);
}

main().then(
  () => process.exit(0),
  (e) => {
    console.error(`Error: ${e.message}`);
    process.exit(1);
  },
);
