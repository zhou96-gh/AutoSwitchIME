const assert = require('node:assert/strict');
const test = require('node:test');

const { InputDisplayMode, inputDisplayModeFor } = require('../out/core/types');

test('display mode depends only on input state', () => {
  assert.equal(inputDisplayModeFor({ isAsciiMode: true, isCapsLock: false }), InputDisplayMode.ENGLISH);
  assert.equal(inputDisplayModeFor({ isAsciiMode: false, isCapsLock: false }), InputDisplayMode.CHINESE);
  assert.equal(inputDisplayModeFor({ isAsciiMode: true, isCapsLock: true }), InputDisplayMode.CAPS);
  assert.equal(inputDisplayModeFor({ isAsciiMode: false, isCapsLock: true }), InputDisplayMode.CAPS);
});
