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
  doc, collection, setDoc, updateDoc, deleteDoc, getDoc, getDocs, serverTimestamp, writeBatch
} = require('firebase/firestore');

const ownerUid = 'FxAKNm8aK6amBPoHZcXNOtSLv3t2';
const readerUid = 'reader-identity-emulator-only';
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

let env, owner, reader, other, anonymous;

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
  const sourceRules = fs.readFileSync(path.resolve(__dirname, '..', '..', 'firestore.rules'), 'utf8');
  const readerBinding = sourceRules.match(/request\.auth\.uid == '([^']+)'/);
  if (!readerBinding || readerBinding[1] === ownerUid) throw new Error('rules must bind a separate reader identity');
  const rules = sourceRules.replace(readerBinding[1], readerUid);
  env = await initializeTestEnvironment({
    projectId: 'demo-ludo-firestore-rules',
    firestore: {...emulatorFirestoreAddress(), rules},
  });
  owner = env.authenticatedContext(ownerUid).firestore();
  reader = env.authenticatedContext(readerUid).firestore();
  other = env.authenticatedContext(otherUid).firestore();
  anonymous = env.unauthenticatedContext().firestore();
});

test.after(async () => {
  if (env) await env.cleanup();
});

test('legacy v1 paths remain denied; historical beta compatibility is not reopened', async () => {
  await env.clearFirestore();
  await assertFails(setDoc(doc(owner, v1ListingPath), legacyV1Listing()));
  await assertFails(getDoc(doc(owner, v1ListingPath)));
  await assertFails(getDocs(collection(owner, v1ListingsPath)));
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

test('legacy v2 snapshot paths remain denied; historical beta compatibility is not reopened', async () => {
  await env.clearFirestore();
  const manifestPath = v2SnapshotRoot + snapshotId;
  const listingPath = manifestPath + '/listings/42';
  await assertFails(setDoc(doc(owner, manifestPath), legacyV2Manifest()));
  await assertFails(setDoc(doc(owner, listingPath), legacyV2Listing()));
  await assertFails(getDoc(doc(owner, v2CurrentPath)));
  await assertFails(getDoc(doc(owner, manifestPath)));
  await assertFails(getDoc(doc(owner, listingPath)));
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

test('existing reader can read only complete v3 snapshots and cannot write them', async () => {
  await env.clearFirestore();
  const completeRow = snapshotRoot + snapshotId + '/listings/42';
  const stagingRow = snapshotRoot + nextSnapshotId + '/listings/43';
  await seedLegacy(currentPath, v3Current(snapshotId));
  await seedLegacy(snapshotRoot + snapshotId, v3Manifest(snapshotId, true, 1));
  await seedLegacy(completeRow, v3Listing(42, snapshotId, {
    observed_text: 'Synthetic evidence', photo_urls: ['https://images.vinted.net/test.jpg'],
  }));
  await seedLegacy(snapshotRoot + nextSnapshotId, v3Manifest(nextSnapshotId, false, 1));
  await seedLegacy(stagingRow, v3Listing(43, nextSnapshotId));
  await assertSucceeds(getDoc(doc(reader, currentPath)));
  await assertSucceeds(getDoc(doc(reader, snapshotRoot + snapshotId)));
  await assertSucceeds(getDoc(doc(reader, completeRow)));
  await assertSucceeds(getDocs(collection(reader, snapshotRoot + snapshotId + '/listings')));
  await assertFails(getDoc(doc(reader, snapshotRoot + nextSnapshotId)));
  await assertFails(getDoc(doc(reader, stagingRow)));
  await assertFails(setDoc(doc(reader, stagingRow), v3Listing(43, nextSnapshotId)));

  await assertSucceeds(updateDoc(doc(owner, snapshotRoot + nextSnapshotId), {complete: true}));
  await assertSucceeds(setDoc(doc(owner, currentPath), v3Current(nextSnapshotId)));
  await assertSucceeds(getDoc(doc(reader, snapshotRoot + nextSnapshotId)));
  await assertSucceeds(getDoc(doc(reader, stagingRow)));
  await assertFails(getDoc(doc(reader, snapshotRoot + snapshotId)));
  await assertFails(getDoc(doc(reader, completeRow)));
  await assertFails(getDocs(collection(reader, snapshotRoot + snapshotId + '/listings')));
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

test('a write already prepared before revocation is rejected after its staging manifest is sealed', async () => {
  await env.clearFirestore();
  const stagingManifest = doc(owner, snapshotRoot + nextSnapshotId);
  const delayedRow = doc(owner, snapshotRoot + nextSnapshotId + '/listings/43');
  const delayedUpload = writeBatch(owner);
  delayedUpload.set(delayedRow, v3Listing(43, nextSnapshotId));

  await assertSucceeds(setDoc(stagingManifest, v3Manifest(nextSnapshotId, false, 1)));
  // Revocation/abort deletes the incomplete manifest before listing cleanup.
  await assertSucceeds(deleteDoc(stagingManifest));
  // A request prepared before revocation cannot land after that barrier.
  await assertFails(delayedUpload.commit());
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

test('retained rollback generation is undeletable and can be repointed', async () => {
  await env.clearFirestore();
  const oldManifestPath = snapshotRoot + snapshotId;
  const oldRowPath = oldManifestPath + '/listings/42';
  const currentManifestPath = snapshotRoot + nextSnapshotId;
  const currentRowPath = currentManifestPath + '/listings/42';
  await seedLegacy(oldManifestPath, v3Manifest(snapshotId, true, 1));
  await seedLegacy(oldRowPath, v3Listing(42, snapshotId));
  await seedLegacy(currentManifestPath, v3Manifest(nextSnapshotId, true, 1));
  await seedLegacy(currentRowPath, v3Listing(42, nextSnapshotId));
  await seedLegacy(currentPath, {...v3Current(nextSnapshotId), rollback_snapshot_id: snapshotId});

  await assertFails(deleteDoc(doc(owner, oldRowPath)));
  await assertFails(deleteDoc(doc(owner, oldManifestPath)));
  await assertSucceeds(setDoc(doc(owner, currentPath), {
    schema_version: 3, snapshot_id: snapshotId, rollback_snapshot_id: nextSnapshotId,
    updated_at: serverTimestamp(),
  }));
  await assertSucceeds(getDoc(doc(owner, oldRowPath)));
});
