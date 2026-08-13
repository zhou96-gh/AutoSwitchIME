const assert = require('node:assert/strict');
const test = require('node:test');

const {
  parseRimeSessionState,
  RimeSessionTracker,
} = require('../out/core/StateWatcher');

test('parses complete protocol v2 state json', () => {
  assert.deepEqual(
    parseRimeSessionState('{"protocol_version":2,"provider":"rime","session_token":"s1","sequence":3,"ascii_mode":false,"caps_lock":true,"is_composing":true,"timestamp":1730000000}'),
    {
      state: { isAsciiMode: false, isCapsLock: true, isComposing: true },
      sessionToken: 's1',
      sequence: 3,
    },
  );
});

test('rejects protocol v1 state', () => {
  assert.equal(
    parseRimeSessionState('{"ascii_mode":false,"caps_lock":true,"is_composing":true}'),
    null,
  );
});

test('returns null for incomplete json writes', () => {
  assert.equal(parseRimeSessionState('{"ascii_mode":'), null);
});

test('rejects incomplete session-aware state', () => {
  assert.equal(
    parseRimeSessionState('{"protocol_version":2,"provider":"rime","sequence":1,"ascii_mode":true,"caps_lock":false,"is_composing":false,"timestamp":1730000000}'),
    null,
  );
  assert.equal(
    parseRimeSessionState('{"protocol_version":2,"provider":"rime","session_token":"s1","sequence":0,"ascii_mode":true,"caps_lock":false,"is_composing":false,"timestamp":1730000000}'),
    null,
  );
});

test('tracker filters stale sequence and accepts a new session', () => {
  const tracker = new RimeSessionTracker();
  const base = {
    state: { isAsciiMode: true, isCapsLock: false, isComposing: false },
    sequence: 1,
  };

  assert.equal(tracker.accept({ ...base, sessionToken: 's1' }), true);
  assert.equal(tracker.accept({ ...base, sessionToken: 's1' }), false);
  assert.equal(tracker.accept({ ...base, sessionToken: 's2' }), true);
});
