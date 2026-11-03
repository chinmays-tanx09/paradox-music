// Release notes from the links-*.txt files the build jobs write ("file|size|sha256|gofile link").
const fs = require('fs'), path = require('path');
const dir = process.argv[2] || 'links';
const rows = [];
for (const f of fs.existsSync(dir) ? fs.readdirSync(dir) : []) {
  for (const line of fs.readFileSync(path.join(dir, f), 'utf8').split('\n')) {
    const [file, size, sha, link] = line.trim().split('|');
    if (file) rows.push({ file, size: +size, sha, link });
  }
}
const kind = f => /arm64/i.test(f) ? 'Windows on ARM' : 'Windows x64';
const type = f => f.endsWith('.msi') ? 'MSI' : 'Setup';
rows.sort((a, b) => kind(a.file).localeCompare(kind(b.file)) || type(b.file).localeCompare(type(a.file)));
const mb = n => (n / 1048576).toFixed(1) + ' MB';
let out = `Paradox Music for Windows. A minimal offline music player: choose your music folder once and it's all there.\n\n`;
out += `Most PCs: **Windows x64 Setup**. Snapdragon / Surface Pro X: **Windows on ARM**.\n\n`;
out += `| Download | File | Size | SHA-256 |\n|---|---|---|---|\n`;
for (const r of rows) out += `| ${r.link ? `[${kind(r.file)} ${type(r.file)}](${r.link})` : `${kind(r.file)} ${type(r.file)} (attached below)`} | ${r.file} | ${mb(r.size)} | \`${r.sha}\` |\n`;
out += `\nThe installers are not code-signed, so Windows SmartScreen may say "Windows protected your PC": click **More info → Run anyway**.\n`;
process.stdout.write(out);
