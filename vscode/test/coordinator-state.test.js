const assert = require('node:assert/strict');
const test = require('node:test');

const { CoordinatorState } = require('../out/core/CoordinatorState');

test('new active editor invalidates previous request', () => {
  const state = new CoordinatorState();
  state.focusEditor('editor-a');
  const requestA = state.newRequest('editor-a');

  state.focusEditor('editor-b');
  const requestB = state.newRequest('editor-b');

  assert.ok(requestA);
  assert.ok(requestB);
  assert.equal(state.isCurrent(requestA), false);
  assert.equal(state.isCurrent(requestB), true);
});

test('focus loss invalidates automatic requests', () => {
  const state = new CoordinatorState();
  state.focusEditor('editor-a');
  const request = state.newRequest('editor-a');

  state.loseFocus();

  assert.equal(state.isCurrent(request), false);
  assert.equal(state.newRequest('editor-a'), null);
});

test('focusing same editor keeps current request valid', () => {
  const state = new CoordinatorState();
  state.focusEditor('editor-a');
  const request = state.newRequest('editor-a');

  state.focusEditor('editor-a');

  assert.equal(state.isCurrent(request), true);
});

test('explicit invalidation makes current request stale', () => {
  const state = new CoordinatorState();
  state.focusEditor('editor-a');
  const request = state.newRequest('editor-a');

  state.invalidateRequests();

  assert.equal(state.isCurrent(request), false);
});

test('focus loss from inactive editor does not invalidate current request', () => {
  const state = new CoordinatorState();
  state.focusEditor('editor-b');
  const request = state.newRequest('editor-b');

  state.loseFocus('editor-a');

  assert.equal(state.isCurrent(request), true);
});

test('shutdown rejects new requests', () => {
  const state = new CoordinatorState();
  state.focusEditor('editor-a');

  state.shutdown();

  assert.equal(state.isShuttingDown(), true);
  assert.equal(state.newRequest('editor-a'), null);
});
