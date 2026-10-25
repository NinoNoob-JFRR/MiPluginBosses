const fs = require('fs');
const path = require('path');
const y = require('js-yaml');

const R = 'src/main/resources';
const load = (f) => y.load(fs.readFileSync(path.join(R, f), 'utf8'));
const errors = [];
const warn = (m) => errors.push(m);

const files = [
  'bosses.yml', 'boss_abilities.yml', 'essence_abilities.yml', 'drops.yml',
  'recipes.yml', 'eggs.yml', 'progression.yml', 'items.yml', 'menus.yml',
  'mobs.yml', 'loot.yml', 'tiers.yml', 'messages.yml', 'config.yml'
];
const data = {};
for (const f of files) {
  try { data[f] = load(f); } catch (e) { warn(`YAML invalido ${f}: ${e.message}`); }
}

// bosses
const bosses = data['bosses.yml']?.bosses || {};
const bossAbilities = data['boss_abilities.yml']?.boss_abilities || {};
const byTier = {};
let bossCount = 0;
for (const [id, b] of Object.entries(bosses)) {
  bossCount++;
  byTier[b.tier] = (byTier[b.tier] || 0) + 1;
  if (!b.name) warn(`bosses.yml ${id}: falta name`);
  if (!b.archetype) warn(`bosses.yml ${id}: falta archetype`);
  if (!b.entity) warn(`bosses.yml ${id}: falta entity`);
  if (!(b.stats && b.stats.health > 0 && b.stats.damage > 0)) warn(`bosses.yml ${id}: stats invalidos`);
  if (!(b.xp > 0)) warn(`bosses.yml ${id}: xp invalido`);
  for (const a of b.abilities || []) {
    if (!bossAbilities[a]) warn(`bosses.yml ${id}: habilidad inexistente ${a}`);
  }
}
if (bossCount !== 20) warn(`bosses.yml: ${bossCount} jefes (esperados 20)`);
for (let t = 1; t <= 10; t++) if (byTier[t] !== 2) warn(`bosses.yml: tier ${t} tiene ${byTier[t] || 0} jefes`);

// boss abilities
const validTriggers = new Set(['TIMER','ON_SPAWN','ON_DAMAGE_TAKEN','ON_DAMAGE_DEALT','ON_HEALTH','ON_DEATH','ON_PLAYER_DISTANCE']);
const validEffects = new Set(['AOE_DAMAGE','BUFF','CLEAR_DEBUFFS','DASH','DEATH_EXPLOSION','DEBUFF','FREEZE','KNOCKBACK','LIGHTNING','METEOR','POTION','PROJECTILE','PULL','REFLECT','SELF_HEAL','SHIELD','SPLASH','SUMMON','TELEPORT']);
let baCount = 0;
for (const [id, a] of Object.entries(bossAbilities)) {
  baCount++;
  if (!validTriggers.has(a.trigger)) warn(`boss_abilities.yml ${id}: trigger invalido ${a.trigger}`);
  if (a.trigger === 'TIMER' && !(a.interval_seconds > 0)) warn(`boss_abilities.yml ${id}: falta interval_seconds`);
  if (!Array.isArray(a.effects) || a.effects.length === 0) warn(`boss_abilities.yml ${id}: sin efectos`);
  for (const e of a.effects || []) if (!validEffects.has(e.type)) warn(`boss_abilities.yml ${id}: efecto desconocido ${e.type}`);
}
if (baCount !== 28) warn(`boss_abilities.yml: ${baCount} habilidades (esperadas 28)`);

// player abilities
const abilities = data['essence_abilities.yml']?.abilities || {};
const recipes = data['recipes.yml']?.recipes || {};
let abCount = 0;
for (const [id, a] of Object.entries(abilities)) {
  abCount++;
  if (!['PASSIVE','ACTIVE'].includes(a.type)) warn(`essence_abilities.yml ${id}: type invalido`);
  if (!a.name) warn(`essence_abilities.yml ${id}: falta name`);
  if (!(a.max_level > 0)) warn(`essence_abilities.yml ${id}: max_level invalido`);
  const levels = a.levels ? Object.keys(a.levels) : [];
  if (levels.length !== a.max_level) warn(`essence_abilities.yml ${id}: ${levels.length} niveles para max_level ${a.max_level}`);
  if (!Array.isArray(a.compatible_with) || a.compatible_with.length === 0) warn(`essence_abilities.yml ${id}: compatible_with vacio`);
  if (!recipes.abilities || !recipes.abilities[id]) warn(`recipes.yml: falta receta de ${id}`);
  if (!a.applied_lore || !a.applied_lore.length) warn(`essence_abilities.yml ${id}: sin applied_lore`);
  if (!a.lore || !a.lore.length) warn(`essence_abilities.yml ${id}: sin lore`);
}
if (abCount !== 27) warn(`essence_abilities.yml: ${abCount} habilidades (esperadas 27)`);

// recipes
const pat = recipes.pattern || [];
if (pat.length !== 3 || pat.some(p => p.length !== 3)) warn('recipes.yml: pattern debe ser 3x3');
for (const [id, r] of Object.entries(recipes.abilities || {})) {
  if (!r.A || !r.B) warn(`recipes.yml ${id}: faltan A o B`);
}
const compass = recipes.tracking_compass;
if (!compass) warn('recipes.yml: falta tracking_compass');
else {
  if (!Array.isArray(compass.pattern) || compass.pattern.length !== 3) warn('recipes.yml: tracking_compass pattern invalido');
  for (const k of ['H','E','C','O','D']) if (!compass.ingredients || !compass.ingredients[k]) warn(`recipes.yml: tracking_compass falta ingrediente ${k}`);
}

// loot
const loot = data['loot.yml']?.loot || {};
const cats = loot.categories || {};
const pieces = loot.pieces || {};
const app = loot['material-applicability'] || {};
const sum = (o) => Object.values(o).reduce((a, b) => a + b, 0);
if (sum(cats) !== 100) warn(`loot.yml: categorias suman ${sum(cats)}`);
for (const [cat, list] of Object.entries(pieces)) {
  for (const piece of list) {
    if (loot['fixed-material-items'] && loot['fixed-material-items'][piece]) continue;
    let covered = false;
    for (const allowed of Object.values(app)) if (allowed.includes(piece)) covered = true;
    if (!covered) warn(`loot.yml: pieza ${piece} (${cat}) sin material aplicable`);
  }
}
for (const [tier, row] of Object.entries(loot.materials || {})) {
  const total = sum(row);
  if (total !== 100) warn(`loot.yml: materiales tier ${tier} suman ${total}`);
}
for (let t = 1; t <= 10; t++) {
  const row = loot['tier-table']?.[t];
  if (!Array.isArray(row) || row.length !== 10) warn(`loot.yml: tier-table fila ${t} invalida`);
}
for (let t = 1; t <= 10; t++) {
  const row = loot.curses?.['count-by-loot-tier']?.[t];
  if (!row) warn(`loot.yml: curses tier ${t} falta`);
  else if (Math.abs(sum(row) - 100) > 0.001) warn(`loot.yml: curses tier ${t} suman ${sum(row)}`);
}
const enchantCounts = loot.enchantments?.['count-by-loot-tier'] || {};
for (let t = 1; t <= 10; t++) {
  const v = enchantCounts[t];
  if (!Array.isArray(v) || v.length !== 2 || v[0] > v[1]) warn(`loot.yml: enchant count tier ${t} invalido`);
}

// mobs
const mobs = data['mobs.yml']?.mobs || {};
if (sum(mobs.distribution || {}) !== 100) warn(`mobs.yml: distribucion suma ${sum(mobs.distribution || {})}`);
if (!mobs.stats || Object.keys(mobs.stats).length !== 10) warn('mobs.yml: stats debe tener 10 tiers');

// eggs
const eggs = data['eggs.yml']?.eggs || {};
if (!(eggs['max-active'] > 0)) warn('eggs.yml: max-active invalido');
if (!eggs.worlds || eggs.worlds.length === 0) warn('eggs.yml: sin mundos');

// menus
const menus = data['menus.yml']?.menus || {};
for (const kind of ['main','admin','recipes','abilities','bosses','progress','stats','help']) {
  if (!menus[kind]) warn(`menus.yml: falta menu ${kind}`);
}
for (const [kind, menu] of Object.entries(menus)) {
  if (!menu || typeof menu !== 'object') continue;
  if (menu.items) for (const [key, item] of Object.entries(menu.items)) {
    if (item.menu && !menus[item.menu]) warn(`menus.yml ${kind}.${key}: menu destino inexistente ${item.menu}`);
  }
}

// messages usage
const flat = (o, p = '', out = {}) => {
  for (const [k, v] of Object.entries(o)) {
    const key = p ? p + '.' + k : k;
    if (v && typeof v === 'object' && !Array.isArray(v)) flat(v, key, out);
    else out[key] = v;
  }
  return out;
};
const msgKeys = flat(data['messages.yml'] || {});
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
const re = /messages\.(?:raw|getRaw|get|send|sendMap|sendRaw)\(\s*"([a-zA-Z0-9_.-]+)"/g;
const re2 = /getMessages\(\)\.(?:raw|getRaw|get|send|sendMap|sendRaw)\(\s*"([a-zA-Z0-9_.-]+)"/g;
for (const f of walk('src/main/java')) {
  const s = fs.readFileSync(f, 'utf8');
  for (const m of s.matchAll(re)) used.add(m[1]);
  for (const m of s.matchAll(re2)) used.add(m[1]);
}
const ignored = new Set(['name','type','message','amount','world','x','y','z','yaw','pitch','spawned-at','despawn-at','forced-tier','notify','pitch']);
for (const k of used) {
  if (k.includes('.yml')) continue;
  if (!(k in msgKeys) && !ignored.has(k)) warn(`messages.yml: falta clave usada ${k}`);
}

console.log(`Jefes: ${bossCount} | Boss abilities: ${baCount} | Player abilities: ${abCount}`);
if (errors.length === 0) console.log('VALIDACION OK');
else {
  console.log(`ERRORES (${errors.length}):`);
  for (const e of errors) console.log(' - ' + e);
  process.exitCode = 1;
}
