const fs = require('fs');
const path = require('path');
const y = require('js-yaml');

const msg = y.load(fs.readFileSync('src/main/resources/messages.yml', 'utf8'));
function flat(o, p = '', out = {}) {
  for (const [k, v] of Object.entries(o)) {
    const key = p ? p + '.' + k : k;
    if (v && typeof v === 'object' && !Array.isArray(v)) flat(v, key, out);
    else out[key] = v;
  }
  return out;
}
const keys = flat(msg);

function walk(d) {
  let r = [];
  for (const f of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, f.name);
    if (f.isDirectory()) r = r.concat(walk(p));
    else if (f.name.endsWith('.java')) r.push(p);
  }
  return r;
}

const used = new Set();
const re = /\.(?:raw|getRaw|get|send|sendRaw)\(\s*"([a-zA-Z0-9_.-]+)"/g;
for (const f of walk('src/main/java')) {
  const s = fs.readFileSync(f, 'utf8');
  for (const m of s.matchAll(re)) used.add(m[1]);
}
const missing = [...used].filter(k => !(k in keys)).sort();
console.log('CLAVES USADAS: ' + used.size);
console.log('FALTANTES (' + missing.length + '):');
missing.forEach(k => console.log('  ' + k));
