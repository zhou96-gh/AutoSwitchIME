const assert = require('node:assert/strict');
const test = require('node:test');

const { parseStateJson } = require('../out/core/StateWatcher');

test('parses complete state json', () => {
  assert.deepEqual(
    parseStateJson('{"ascii_mode":false,"caps_lock":true,"is_composing":true}'),
    { isAsciiMode: false, isCapsLock: true, isComposing: true },
  );
});

test('defaults optional caps and composing fields to false', () => {
  assert.deepEqual(
    parseStateJson('{"ascii_mode":true}'),
    { isAsciiMode: true, isCapsLock: false, isComposing: false },
  );
});

test('returns null when ascii mode is missing', () => {
  assert.equal(
    parseStateJson('{"caps_lock":true,"is_composing":false}'),
    null,
  );
});

test('returns null for incomplete json writes', () => {
  assert.equal(parseStateJson('{"ascii_mode":'), null);
});
