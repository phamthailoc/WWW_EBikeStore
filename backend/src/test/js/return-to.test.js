// Run with: node --test backend/src/test/js
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/return-to.js'), 'utf8');
const sandbox = {location: {origin: 'http://localhost:8080'}, URL};
sandbox.window = sandbox;
vm.runInNewContext(source, sandbox);
const safe = value => sandbox.safeReturnTo(value);

test('keeps paths of this site', () => {
  assert.strictEqual(safe('/'), '/');
  assert.strictEqual(safe('/bike/volt?testRide=1'), '/bike/volt?testRide=1');
  assert.strictEqual(safe('/customer/test-rides#top'), '/customer/test-rides#top');
});

test('refuses addresses that leave the site', () => {
  for (const value of ['//evil.com', '/\\evil.com', '/\t/evil.com', '/\n/evil.com', '/\r/evil.com', '/ /evil.com',
    '/\u0000/evil.com', 'https://evil.com', 'javascript:alert(1)', 'evil.com', '', null, undefined, '\\\\evil.com']) {
    assert.strictEqual(safe(value), null, JSON.stringify(value));
  }
});

test('the decoded forms of %09, %0a and %0d are refused', () => {
  for (const raw of ['%2F%09%2Fevil.com', '%2F%0a%2Fevil.com', '%2F%0d%2Fevil.com']) {
    const decoded = new URLSearchParams('returnTo=' + raw).get('returnTo');
    assert.strictEqual(safe(decoded), null, raw);
  }
});
