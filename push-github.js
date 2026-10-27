const fs = require('fs');
const path = require('path');

const OWNER = 'NinoNoob-JFRR';
const REPO = 'MiPluginBosses';
const BRANCH = 'main';
const TOKEN = process.env.GITHUB_PERSONAL_ACCESS_TOKEN;
const ROOT = process.cwd();
const API = `https://api.github.com/repos/${OWNER}/${REPO}`;
const SKIP_WORKFLOW = '.github/workflows/build.yml';
const MESSAGE = 'Fix: esencias multiples, Speed activa, Hardy, stats de mobs y lore epico';

if (!TOKEN) {
  console.error('Falta GITHUB_PERSONAL_ACCESS_TOKEN');
  process.exit(1);
}

const headers = {
  Authorization: `Bearer ${TOKEN}`,
  Accept: 'application/vnd.github+json',
  'User-Agent': 'opencode',
  'X-GitHub-Api-Version': '2022-11-28'
};

async function api(url, options = {}) {
  const res = await fetch(url, { ...options, headers: { ...headers, ...(options.headers || {}) } });
  const text = await res.text();
  let data;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!res.ok) throw new Error(`${options.method || 'GET'} ${url} -> ${res.status}: ${text}`);
  return data;
}

function collect(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    const rel = path.relative(ROOT, full).split(path.sep).join('/');
    if (entry.isDirectory()) {
      if (entry.name === 'target' || entry.name === '.git') continue;
      collect(full, out);
    } else {
      if (entry.name.endsWith('.jar')) continue;
      if (rel === SKIP_WORKFLOW) continue;
      out.push(rel);
    }
  }
  return out;
}

(async () => {
  const files = collect(ROOT);
  console.log(`Archivos a subir: ${files.length}`);
  const ref = await api(`${API}/git/ref/heads/${BRANCH}`);
  const baseSha = ref.object.sha;
  const baseCommit = await api(`${API}/git/commits/${baseSha}`);
  console.log(`Base: ${baseSha.slice(0, 10)}`);

  const entries = [];
  const BATCH = 8;
  for (let i = 0; i < files.length; i += BATCH) {
    const batch = files.slice(i, i + BATCH);
    const results = await Promise.all(batch.map(async (rel) => {
      const content = fs.readFileSync(path.join(ROOT, rel));
      const blob = await api(`${API}/git/blobs`, {
        method: 'POST',
        body: JSON.stringify({ content: content.toString('base64'), encoding: 'base64' })
      });
      return { path: rel, mode: '100644', type: 'blob', sha: blob.sha };
    }));
    entries.push(...results);
    process.stdout.write(`\rBlobs: ${entries.length}/${files.length}`);
  }
  console.log('');

  const tree = await api(`${API}/git/trees`, {
    method: 'POST',
    body: JSON.stringify({ base_tree: baseCommit.tree.sha, tree: entries })
  });
  const commit = await api(`${API}/git/commits`, {
    method: 'POST',
    body: JSON.stringify({ message: MESSAGE, tree: tree.sha, parents: [baseSha] })
  });
  await api(`${API}/git/refs/heads/${BRANCH}`, {
    method: 'PATCH',
    body: JSON.stringify({ sha: commit.sha, force: false })
  });
  console.log(`COMMIT ${BRANCH}: ${commit.sha}`);
})().catch((err) => {
  console.error('ERROR: ' + err.message);
  process.exit(1);
});
