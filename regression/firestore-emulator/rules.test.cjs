'use strict';

// Cloud Firestore emulator only: demo project, local port, no real credentials.
// This tests authorization, not phone/Hermes runtime integration.
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {
  initializeTestEnvironment, assertFails, assertSucceeds
} = require('@firebase/rules-unit-testing');
const {
  doc, collection, setDoc, updateDoc, deleteDoc, getDoc, getDocs, serverTimestamp
} = require('firebase/firestore');

const ownerUid = 'FxAKNm8aK6amBPoHZcXNOtSLv3t2';
const otherUid = 'another-account-that-must-not-see-ludo';
const deviceId = '12345678-1234-1234-1234-123456789abc';
const v1ListingsPath = 'sync_v1/' + ownerUid + '/devices/' + deviceId + '/listings';
const v1ListingPath = v1ListingsPath + '/42';
const v2SnapshotRoot = 'sync_v2/' + ownerUid + '/devices/' + deviceId + '/snapshots/';
const v2CurrentPath = 'sync_v2/' + ownerUid + '/devices/' + deviceId + '/meta/current';
const snapshotId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const nextSnapshotId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const snapshotRoot = 'sync_v3/' + ownerUid + '/devices/' + deviceId + '/snapshots/';
const currentPath = 'sync_v3/' + ownerUid + '/devices/' + deviceId + '/meta/current';

function legacyV1Listing() {
  return {
    schema_version: 1, source_id: 42, item_id: 'fixture-5678', title: 'Synthetic board game',
    brand: 'Fixture only', condition: '', price_cents: 1500, protected_price_cents: null,
    item_url: 'https://example.test/item', photo_urls: ['https://example.test/photo.jpg'],
    observed_text: 'Synthetic observation only', category: 'BASE_GAME', category_source: 'LOCAL',
    lifecycle: 'ACTIVE', enrichment_state: 'PENDING', match_state: 'UNKNOWN', bgg_id: '', language: '',
    last_seen: 1, manual_review: false, source_hash: 'a'.repeat(64), synced_at: serverTimestamp(),
  };
}

function legacyV2Listing(id = 42, version = snapshotId) {
  return {...legacyV1Listing(), schema_version: 2, snapshot_id: version, source_id: id};
}

function legacyV2Manifest(id = snapshotId, complete = false, count = 1) {
  return {schema_version: 2, snapshot_id: id, document_count: count,
    content_hash: 'b'.repeat(64), complete, created_at: serverTimestamp()};
}

function legacyV2Current(id = snapshotId) {
  return {schema_version: 2, snapshot_id: id, updated_at: serverTimestamp()};
}

function v3Listing(id = 42, version = snapshotId, overrides = {}) {
  return {
    schema_version: 3,
    snapshot_id: version,
    source_id: id,
    title: 'Synthetic board game',
    title_source: 'VINTED_LISTING',
    brand: 'Fixture only',
    brand_source: 'VINTED_LISTING',
    category: 'board_game',
    category_source: 'accessibility_product_page',
    category_provenance_available: true,
    language: 'IT',
    bgg_id: '123456',
    lifecycle: 'ACTIVE',
    enrichment_state: 'ENRICHED',
    match_state: 'MATCHED',
    observed_text: '',
    photo_urls: [],
    source_hash: 'a'.repeat(64),
    ...overrides,
  };
}

function v3Manifest(id = snapshotId, complete = false, count = 1) {
  return {schema_version: 3, snapshot_id: id, document_count: count,
    content_hash: 'b'.repeat(64), complete, author_coverage_available: false,
    created_at: serverTimestamp()};
}

function v3Current(id = snapshotId) {
  return {schema_version: 3, snapshot_id: id, updated_at: serverTimestamp()};
}

let env, owner, other, anonymous;

function emulatorFirestoreAddress() {
  const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST || '127.0.0.1:8081').split(':');
  return {host, port: Number(port)};
}

async function seedLegacy(pathName, data) {
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), pathName), data);
  });
}

test.before(async () => {
  const rules = fs.readFileSync(path.resolve(__dirname, '..', '..', 'firestore.rules'), 'utf8');
  env = await initializeTestEnvironment({
    projectId: 'demo-ludo-firestore-rules',
    firestore: {...emulatorFirestoreAddress(), rules},
  });
  owner = env.authenticatedContext(ownerUid).firestore();
  other = env.authenticatedContext(otherUid).firestore();
  anonymous = env.unauthenticatedContext().firestore();
});

test.after(async () => {
  if (env) await env.cleanup();
});

test('owner retains the existing v1 sync write boundary during v3 rollout', async () => {
  await env.clearFirestore();
  await assertSucceeds(setDoc(doc(owner, v1ListingPath), legacyV1Listing()));
  await assertSucceeds(getDoc(doc(owner, v1ListingPath)));
  await assertSucceeds(getDocs(collection(owner, v1ListingsPath)));
  await assertSucceeds(updateDoc(doc(owner, v1ListingPath), {
    title: 'Synthetic updated title', synced_at: serverTimestamp(),
  }));
  await assertFails(deleteDoc(doc(owner, v1ListingPath)));
});

test('anonymous and other Firebase accounts cannot read legacy v1 data or write it', async () => {
  await env.clearFirestore();
  await seedLegacy(v1ListingPath, legacyV1Listing());
  await assertFails(getDoc(doc(anonymous, v1ListingPath)));
  await assertFails(getDoc(doc(other, v1ListingPath)));
  await assertFails(getDocs(collection(other, v1ListingsPath)));
  await assertFails(setDoc(doc(anonymous, v1ListingPath), legacyV1Listing()));
  await assertFails(setDoc(doc(other, v1ListingPath), legacyV1Listing()));
  await assertFails(setDoc(doc(other, 'sync_v1/' + otherUid + '/devices/' + deviceId + '/listings/42'), legacyV1Listing()));
});

test('other collections and malformed legacy listing paths remain denied', async () => {
  await env.clearFirestore();
  await assertFails(setDoc(doc(owner, 'users/anything'), {email: 'fixture@example.test'}));
  await assertFails(getDoc(doc(owner, 'users/anything')));
  await assertFails(setDoc(doc(owner, 'sync_v1/' + ownerUid + '/devices/not-a-device/listings/42'), legacyV1Listing()));
  await assertFails(setDoc(doc(owner, v1ListingsPath + '/untrusted'), legacyV1Listing()));
  await assertFails(setDoc(doc(owner, 'sync_v1/' + otherUid + '/devices/' + deviceId + '/listings/42'), legacyV1Listing()));
});

test('owner retains v2 snapshot writes during v3 rollout; foreign accounts remain denied', async () => {
  await env.clearFirestore();
  const manifestPath = v2SnapshotRoot + snapshotId;
  const listingPath = manifestPath + '/listings/42';
  await assertSucceeds(setDoc(doc(owner, manifestPath), legacyV2Manifest()));
  await assertSucceeds(setDoc(doc(owner, listingPath), legacyV2Listing()));
  await assertSucceeds(updateDoc(doc(owner, manifestPath), {complete: true}));
  await assertSucceeds(setDoc(doc(owner, v2CurrentPath), legacyV2Current()));
  await assertSucceeds(getDoc(doc(owner, v2CurrentPath)));
  await assertSucceeds(getDoc(doc(owner, manifestPath)));
  await assertSucceeds(getDoc(doc(owner, listingPath)));
  await assertFails(deleteDoc(doc(owner, listingPath)));
  await assertFails(setDoc(doc(other, v2SnapshotRoot + nextSnapshotId), legacyV2Manifest(nextSnapshotId)));
  await assertFails(setDoc(doc(anonymous, v2SnapshotRoot + nextSnapshotId), legacyV2Manifest(nextSnapshotId)));
});

test('v3 listing schema, provenance, language, and protected-field boundaries are enforced', async () => {
  await env.clearFirestore();
  await assertSucceeds(setDoc(doc(owner, snapshotRoot + snapshotId), v3Manifest(snapshotId, false, 1)));
  const invalid = [
    [10, {seller_name: 'not exported'}],
    [11, {price: 100}],
    [12, {item_url: 'https://example.test/item'}],
    [13, {photos: ['https://example.test/photo.jpg']}],
    [15, {title_source: 'CATALOG'}],
    [16, {brand_source: 'CATALOG'}],
    [17, {category_provenance_available: false}],
    [18, {language: 'bad'}],
    [19, {source_hash: 'invalid'}],
    [20, {schema_version: 2}],
    [22, {photo_urls: ['https://x.test/1', 'https://x.test/2', 'https://x.test/3', 'https://x.test/4', 'https://x.test/5']}],
    [23, {photo_urls: 'not-a-list'}],
    [24, {observed_text: 'x'.repeat(8001)}],
    [26, {photo_urls: ['https://evil.test/private.jpg']}],
  ];
  for (const [id, overrides] of invalid) {
    await assertFails(setDoc(doc(owner, snapshotRoot + snapshotId + '/listings/' + id), v3Listing(id, snapshotId, overrides)));
  }
  await assertSucceeds(setDoc(doc(owner, snapshotRoot + snapshotId + '/listings/21'), v3Listing(21, snapshotId, {
    category: '', category_source: '', category_provenance_available: false,
  })));
  await assertSucceeds(setDoc(doc(owner, snapshotRoot + snapshotId + '/listings/25'), v3Listing(25, snapshotId, {
    observed_text: 'Synthetic listing description',
    photo_urls: ['https://images1.vinted.net/a.jpg', 'https://images1.vinted.net/b.jpg', 'https://images1.vinted.net/c.jpg', 'https://images1.vinted.net/d.jpg'],
  })));
});

test('completed v3 snapshots from the previous schema remain owner-readable and immutable', async () => {
  await env.clearFirestore();
  const legacy = v3Listing(42, snapshotId);
  delete legacy.observed_text;
  delete legacy.photo_urls;
  await seedLegacy(snapshotRoot + snapshotId, v3Manifest(snapshotId, true, 1));
  await seedLegacy(snapshotRoot + snapshotId + '/listings/42', legacy);
  await seedLegacy(currentPath, v3Current(snapshotId));
  await assertSucceeds(getDoc(doc(owner, snapshotRoot + snapshotId + '/listings/42')));
  await assertFails(getDoc(doc(other, snapshotRoot + snapshotId + '/listings/42')));
  await assertFails(updateDoc(doc(owner, snapshotRoot + snapshotId + '/listings/42'), {title: 'write denied'}));
});

 test('v3 current pointer cannot publish a missing or incomplete manifest', async () => {
  await env.clearFirestore();
  await assertFails(setDoc(doc(owner, currentPath), v3Current()));
  const staged = doc(owner, snapshotRoot + snapshotId);
  await assertSucceeds(setDoc(staged, v3Manifest(snapshotId, false, 0)));
  await assertFails(setDoc(doc(owner, currentPath), v3Current()));
  await assertSucceeds(updateDoc(staged, {complete: true}));
  await assertSucceeds(setDoc(doc(owner, currentPath), v3Current()));
  await assertSucceeds(getDoc(doc(owner, currentPath)));
  await assertFails(getDoc(doc(other, currentPath)));
});

test('v3 owner can stage and publish immutable rows; stale snapshots alone can be deleted', async () => {
  await env.clearFirestore();
  const activeManifest = doc(owner, snapshotRoot + snapshotId);
  const active = doc(owner, snapshotRoot + snapshotId + '/listings/42');
  const staleManifest = doc(owner, snapshotRoot + nextSnapshotId);
  const stale = doc(owner, snapshotRoot + nextSnapshotId + '/listings/42');
  await assertSucceeds(setDoc(activeManifest, v3Manifest(snapshotId, false, 1)));
  await assertSucceeds(setDoc(active, v3Listing(42, snapshotId)));
  await assertFails(setDoc(doc(owner, snapshotRoot + snapshotId + '/listings/43'), v3Listing(42, snapshotId)));
  await assertFails(setDoc(doc(other, snapshotRoot + snapshotId + '/listings/42'), v3Listing(42, snapshotId)));
  await assertSucceeds(updateDoc(activeManifest, {complete: true}));
  await assertSucceeds(setDoc(doc(owner, currentPath), v3Current(snapshotId)));
  await assertFails(deleteDoc(active));
  await assertFails(updateDoc(active, {title: 'completed snapshots are immutable'}));
  await assertFails(setDoc(doc(owner, snapshotRoot + snapshotId + '/listings/43'), v3Listing(43, snapshotId)));

  await assertSucceeds(setDoc(staleManifest, v3Manifest(nextSnapshotId, false, 1)));
  await assertSucceeds(setDoc(stale, v3Listing(42, nextSnapshotId)));
  await assertSucceeds(updateDoc(staleManifest, {complete: true}));
  await assertSucceeds(setDoc(doc(owner, currentPath), v3Current(nextSnapshotId)));
  await assertSucceeds(deleteDoc(active));
  await assertSucceeds(deleteDoc(activeManifest));
  await assertFails(setDoc(doc(owner, snapshotRoot + snapshotId + '/listings/43'), v3Listing(43, snapshotId)));
  await assertFails(setDoc(doc(owner, 'sync_v3/' + otherUid + '/devices/' + deviceId + '/snapshots/' + snapshotId), v3Manifest()));
});
