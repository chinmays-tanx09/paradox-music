// Release notes from the links-*.txt files the build jobs write ("file|size|sha256|gofile link").
// Usage: release-notes.js links        -> release notes (markdown)
//        release-notes.js links sums   -> SHA256SUMS.txt
// The GitHub release asset is the primary download; the gofile copy is the mirror.
// TAG_NAME and REPO (owner/name) come from the workflow environment.
const fs = require('fs'), path = require('path');
const dir = process.argv[2] || 'links';
const mode = process.argv[3] || 'notes';
const tag = process.env.TAG_NAME || '', repo = process.env.REPO || '';
const rows = [];
for (const f of fs.existsSync(dir) ? fs.readdirSync(dir) : []) {
  if (!/^links-.*\.txt$/.test(f)) continue;
  for (const line of fs.readFileSync(path.join(dir, f), 'utf8').split('\n')) {
    const [file, size, sha, link] = line.trim().split('|');
    if (file) rows.push({ file, size: +size, sha, link });
  }
}
const kind = f => /arm64/i.test(f) ? 'Windows on ARM' : 'Windows x64';
const type = f => f.endsWith('.msi') ? 'MSI' : 'Setup';
rows.sort((a, b) => kind(a.file).localeCompare(kind(b.file)) || type(b.file).localeCompare(type(a.file)));
const assetName = f => f.replace(/ /g, '.'); // GitHub renames spaces to dots on upload
const ghLink = f => tag && repo ? `https://github.com/${repo}/releases/download/${encodeURIComponent(tag)}/${encodeURIComponent(assetName(f))}` : '';
if (mode === 'sums') {
  process.stdout.write(rows.map(r => `${r.sha}  ${assetName(r.file)}\n`).join(''));
  process.exit(0);
}
const mb = n => (n / 1048576).toFixed(1) + ' MB';
let out = `Paradox Music for Windows. A minimal offline music player: choose your music folder once and it's all there.\n\n`;
out += `Most PCs: **Windows x64 Setup**. Snapdragon / Surface Pro X: **Windows on ARM**.\n\n`;
out += `| Download | File | Size | SHA-256 |\n|---|---|---|---|\n`;
for (const r of rows) {
  const label = `${kind(r.file)} ${type(r.file)}`;
  const gh = ghLink(r.file);
  const dl = gh ? `[${label}](${gh})${r.link ? ` · [mirror](${r.link})` : ''}` : r.link ? `[${label}](${r.link})` : `${label} (attached below)`;
  out += `| ${dl} | ${assetName(r.file)} | ${mb(r.size)} | \`${r.sha}\` |\n`;
}
out += `\nAll four are also listed in SHA256SUMS.txt (attached to this release).\n`;
out += `\nThe installers are not code-signed, so Windows SmartScreen may say "Windows protected your PC": click **More info → Run anyway**.\n`;
process.stdout.write(out);
