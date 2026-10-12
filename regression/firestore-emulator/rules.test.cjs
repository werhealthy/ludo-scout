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
const listingsPath = 'sync_v1/' + ownerUid + '/devices/' + deviceId + '/listings';
const listingPath = listingsPath + '/42';
const otherPath = 'sync_v1/' + otherUid + '/devices/' + deviceId + '/listings/42';
const snapshotId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const nextSnapshotId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
const snapshotRoot = 'sync_v2/' + ownerUid + '/devices/' + deviceId + '/snapshots/';
const currentPath = 'sync_v2/' + ownerUid + '/devices/' + deviceId + '/meta/current';

function listing() {
  return {
    schema_version: 1,
    source_id: 42,
    item_id: '5678',
    title: 'Synthetic board game listing',
    brand: 'Fixture only',
    condition: '',
    price_cents: 1500,
    protected_price_cents: null,
    item_url: 'https://www.vinted.it/items/5678',
    photo_urls: ['https://example.test/photo.jpg'],
    observed_text: 'Synthetic observation; no user data',
    category: 'BASE_GAME',
    category_source: 'LOCAL',
    lifecycle: 'ACTIVE',
    enrichment_state: 'PENDING',
    match_state: 'UNKNOWN',
    bgg_id: '',
    language: '',
    last_seen: 1,
    manual_review: false,
    source_hash: 'a'.repeat(64),
    synced_at: serverTimestamp(),
  };
}

function snapshotListing(id = 42, version = snapshotId) {
  return {...listing(), schema_version: 2, snapshot_id: version, source_id: id};
}

function manifest(id = snapshotId, complete = true, count = 1) {
  return {schema_version: 2, snapshot_id: id, document_count: count,
    content_hash: 'b'.repeat(64), complete, created_at: serverTimestamp()};
}

function current(id = snapshotId) {
  return {schema_version: 2, snapshot_id: id, updated_at: serverTimestamp()};
}

let env, owner, other, anonymous;

test.before(async () => {
  const rules = fs.readFileSync(path.resolve(__dirname, '..', '..', 'firestore.rules'), 'utf8');
  env = await initializeTestEnvironment({
    projectId: 'demo-ludo-firestore-rules',
    firestore: {host: '127.0.0.1', port: 8081, rules},
  });
  owner = env.authenticatedContext(ownerUid).firestore();
  other = env.authenticatedContext(otherUid).firestore();
  anonymous = env.unauthenticatedContext().firestore();
});

test.after(async () => {
  if (env) await env.cleanup();
});

test('owner can create, read and update only a valid listing', async () => {
  await env.clearFirestore();
  const document = doc(owner, listingPath);
  await assertSucceeds(setDoc(document, listing()));
  await assertSucceeds(getDoc(document));
  await assertSucceeds(updateDoc(document, {title: 'Updated synthetic game', synced_at: serverTimestamp()}));
  await assertSucceeds(getDocs(collection(owner, listingsPath)));
  await assertFails(deleteDoc(document));
});

test('anonymous and other Firebase accounts cannot read or write the owner catalog', async () => {
  await env.clearFirestore();
  await assertSucceeds(setDoc(doc(owner, listingPath), listing()));
  await assertFails(getDoc(doc(anonymous, listingPath)));
  await assertFails(getDoc(doc(other, listingPath)));
  await assertFails(getDocs(collection(other, listingsPath)));
  await assertFails(setDoc(doc(anonymous, listingPath), listing()));
  await assertFails(setDoc(doc(other, listingPath), listing()));
  await assertFails(setDoc(doc(other, otherPath), listing()));
});

test('other collections and malformed listing paths remain denied', async () => {
  await env.clearFirestore();
  await assertFails(setDoc(doc(owner, 'users/anything'), {email: 'fixture@example.test'}));
  await assertFails(getDoc(doc(owner, 'users/anything')));
  await assertFails(setDoc(doc(owner, 'sync_v1/' + ownerUid + '/devices/not-a-device/listings/42'), listing()));
  await assertFails(setDoc(doc(owner, listingsPath + '/untrusted'), listing()));
  await assertFails(setDoc(doc(owner, otherPath), listing()));
});

test('invalid schema, extra data, missing fields and spoofed timestamp are rejected', async () => {
  await env.clearFirestore();
  const document = doc(owner, listingPath);
  await assertFails(setDoc(document, {...listing(), seller_name: 'Forbidden'}));
  const missingTitle = listing();
  delete missingTitle.title;
  await assertFails(setDoc(document, missingTitle));
  await assertFails(setDoc(document, {...listing(), observed_text: 'x'.repeat(2001)}));
  await assertFails(setDoc(document, {...listing(), source_hash: 'invalid'}));
  await assertFails(setDoc(document, {...listing(), price_cents: -10}));
  await assertFails(setDoc(document, {...listing(), synced_at: new Date('2020-01-01T00:00:00Z')}));
});

test('snapshot pointer cannot publish an incomplete or missing manifest', async () => {
  await env.clearFirestore();
  await assertFails(setDoc(doc(owner, currentPath), current()));
  await assertFails(setDoc(doc(owner, snapshotRoot + snapshotId), manifest(snapshotId, false)));
  const missingSchema = manifest(snapshotId, true);
  delete missingSchema.schema_version;
  await assertFails(setDoc(doc(owner, snapshotRoot + snapshotId), missingSchema));
  await assertFails(setDoc(doc(owner, currentPath), current()));
  await assertSucceeds(setDoc(doc(owner, snapshotRoot + nextSnapshotId + '/listings/42'), snapshotListing(42, nextSnapshotId)));
  await assertSucceeds(setDoc(doc(owner, snapshotRoot + nextSnapshotId), manifest(nextSnapshotId, true, 1)));
  await assertSucceeds(setDoc(doc(owner, currentPath), current(nextSnapshotId)));
  await assertSucceeds(getDoc(doc(owner, currentPath)));
});

test('snapshot listings are schema scoped; active docs protected, stale version cleanup allowed', async () => {
  await env.clearFirestore();
  const active=doc(owner,snapshotRoot+snapshotId+'/listings/42');
  const old=doc(owner,snapshotRoot+nextSnapshotId+'/listings/42');
  await assertSucceeds(setDoc(active,snapshotListing(42,snapshotId)));
  await assertFails(setDoc(doc(owner,snapshotRoot+snapshotId+'/listings/43'),snapshotListing(42,snapshotId)));
  await assertFails(setDoc(doc(other,snapshotRoot+snapshotId+'/listings/42'),snapshotListing(42,snapshotId)));
  await assertSucceeds(setDoc(doc(owner,snapshotRoot+snapshotId),manifest(snapshotId)));
  await assertSucceeds(setDoc(doc(owner,currentPath),current(snapshotId)));
  await assertFails(deleteDoc(active));
  await assertSucceeds(setDoc(old,snapshotListing(42,nextSnapshotId)));
  await assertSucceeds(setDoc(doc(owner,snapshotRoot+nextSnapshotId),manifest(nextSnapshotId)));
  await assertSucceeds(setDoc(doc(owner,currentPath),current(nextSnapshotId)));
  await assertSucceeds(deleteDoc(active));
});

test('snapshot foreign scopes and extra listing fields are denied', async () => {
  await env.clearFirestore();
  const bad=snapshotListing();bad.seller_name='not allowed';
  await assertFails(setDoc(doc(owner,snapshotRoot+snapshotId+'/listings/42'),bad));
  await assertFails(getDoc(doc(other,currentPath)));
  await assertFails(setDoc(doc(owner,'sync_v2/'+otherUid+'/devices/'+deviceId+'/snapshots/'+snapshotId),manifest()));
});
