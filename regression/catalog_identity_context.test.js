const assert = require('node:assert/strict');
const {test} = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function matcher(games) {
  const context = vm.createContext({VintedLocalCatalog: {games}, VINTED_AFFARI_BGG_NAMES: {}});
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../app/src/main/assets/engine/catalog-match.js'), 'utf8'), context);
  return context.VintedCatalogMatcher;
}
function game(id, name, publisher = '') {
  return {id, bggId: String(id), name, aliases: [], editions: [{publisher}]};
}

test('a contained generic game name does not verify a longer distinct product title', () => {
  const vocabulary = Array.from({length: 20}, (_, i) => game(i + 1, `Crazy Rhythm Castle ${i + 100}`));
  const m = matcher([game(418036, 'Super'), ...vocabulary]);
  const r = m.match('Super Crazy Rhythm Castle');
  assert.notEqual(r.status, 'matched');
  assert.equal(r.candidates[0].game.bggId, '418036');
});
test('publisher context cannot authorize unexplained identity words', () => {
  const m = matcher([game(393888, 'Match!', 'Schmidt Spiele')]);
  assert.notEqual(m.match("It's a Match", {brand: 'Schmidt Spiele'}).status, 'matched');
});
test('complete names and marketplace wording still match', () => {
  const m = matcher([game(1, 'Super'), game(2, 'Azul')]);
  for (const title of ['Super', 'Gioco da tavolo Super', 'Super nuovo sigillato', 'Azul'])
    assert.equal(m.match(title).status, 'matched', title);
});
test('longer authoritative alias verifies the corresponding identity', () => {
  const g = game(424876, "It's a Match", 'Cranio Creations');
  const m = matcher([game(393888, 'Match!'), g]);
  assert.equal(m.match("It's a Match", {brand: 'Cranio Creations'}).game.bggId, '424876');
});
test('manual search continues to expose candidates with partial titles', () => {
  const m = matcher([game(418036, 'Super')]);
  assert.equal(m.search('Super Crazy Rhythm Castle')[0].bggId, '418036');
});
test('known edition modifiers remain eligible with sufficient existing ranking evidence', () => {
  const vocabulary = Array.from({length: 20}, (_, i) => game(i + 1, `Deluxe Anniversary ${i + 100}`));
  const m = matcher([game(418036, 'Super'), ...vocabulary]);
  assert.equal(m.match('Super deluxe anniversary').status, 'matched');
});
