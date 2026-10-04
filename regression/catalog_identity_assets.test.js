const assert = require('node:assert/strict');
const {test} = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('bundled production catalog and Android bridge hold partial identity collisions', () => {
  const root = path.join(__dirname, '../app/src/main/assets/engine');
  const context = vm.createContext({console, performance, setTimeout, clearTimeout});
  context.window = context;
  const html = fs.readFileSync(path.join(root, 'engine.html'), 'utf8');
  for (const [, file] of html.matchAll(/<script src="([^"]+)"/g))
    vm.runInContext(fs.readFileSync(path.join(root, file), 'utf8'), context, {filename: file});
  assert.ok(context.VintedLocalCatalog.games.length > 1000, 'actual bundled catalog loaded');
  const bridge = context.VintedAffariAndroidBridge;
  const results = bridge.analyzeBatch([
    {title: 'Super Crazy Rhythm Castle', brand: '', itemPrice: 13.99},
    {title: "It's a Match", brand: 'Cranio Creations', itemPrice: 15},
    {title: 'Azul', brand: 'Asmodee', itemPrice: 15}
  ]);
  assert.notEqual(results[0].status, 'matched');
  assert.equal(results[0].game, undefined, 'held candidate must not produce priced game');
  if (results[1].status === 'matched') assert.equal(String(results[1].game.bggId), '424876');
  assert.equal(results[2].status, 'matched');
  assert.equal(String(results[2].game.bggId), '230802');
});
