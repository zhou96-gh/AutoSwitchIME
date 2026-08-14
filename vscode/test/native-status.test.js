const assert = require('node:assert/strict');
const test = require('node:test');

const { decodeSystemImeStatus } = require('../out/ime/system/native');

test('zero conversion mode is valid ascii state', () => {
  assert.deepEqual(decodeSystemImeStatus(0n), {
    isOpen: false,
    isAsciiMode: true,
    conversionMode: 0,
  });
});

test('native flag maps to chinese state', () => {
  assert.deepEqual(decodeSystemImeStatus((1n << 32n) | 0x781n), {
    isOpen: true,
    isAsciiMode: false,
    conversionMode: 0x781,
  });
});

test('closed ime maps native flags to ascii input', () => {
  assert.deepEqual(decodeSystemImeStatus(0x781n), {
    isOpen: false,
    isAsciiMode: true,
    conversionMode: 0x781,
  });
});

test('negative status is unavailable', () => {
  assert.equal(decodeSystemImeStatus(-1n), null);
});
