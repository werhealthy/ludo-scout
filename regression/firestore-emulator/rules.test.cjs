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
