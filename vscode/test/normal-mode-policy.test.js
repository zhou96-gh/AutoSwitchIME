const assert = require('node:assert/strict');
const test = require('node:test');

const {
  isStrictNormalMode,
  resolveNormalModeAction,
  shouldEnforceNormalEnglish,
} = require('../out/core/NormalModePolicy');
const { ImeAction, VimMode } = require('../out/core/types');

test('strict normal always requires english', () => {
  assert.equal(resolveNormalModeAction(VimMode.NORMAL, false, false), ImeAction.ENGLISH);
  assert.equal(resolveNormalModeAction(VimMode.NORMAL, false, true), ImeAction.ENGLISH);
});

test('selection is normal-like but not strict normal', () => {
  assert.equal(isStrictNormalMode(VimMode.NORMAL, true), false);
  assert.equal(resolveNormalModeAction(VimMode.NORMAL, true, false), ImeAction.ENGLISH);
  assert.equal(resolveNormalModeAction(VimMode.NORMAL, true, true), ImeAction.UNCHANGED);
});

test('other normal-like modes allow manual switching after default', () => {
  assert.equal(resolveNormalModeAction(VimMode.VISUAL, false, false), ImeAction.ENGLISH);
  assert.equal(resolveNormalModeAction(VimMode.VISUAL, false, true), ImeAction.UNCHANGED);
});

test('insert mode delegates to context rules', () => {
  assert.equal(resolveNormalModeAction(VimMode.INSERT, false, false), null);
});

test('manual chinese or caps switch is rejected only in strict normal', () => {
  assert.equal(shouldEnforceNormalEnglish(VimMode.NORMAL, false, false, false), true);
  assert.equal(shouldEnforceNormalEnglish(VimMode.NORMAL, false, true, true), true);
  assert.equal(shouldEnforceNormalEnglish(VimMode.NORMAL, false, true, false), false);
  assert.equal(shouldEnforceNormalEnglish(VimMode.NORMAL, true, false, true), false);
  assert.equal(shouldEnforceNormalEnglish(VimMode.VISUAL, false, false, true), false);
});
