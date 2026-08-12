const assert = require('node:assert/strict');
const test = require('node:test');

const { evaluateRules } = require('../out/core/RuleEvaluator');
const { ImeAction } = require('../out/core/types');

const defaultRules = {
  chineseBeforeRegex: '.*[\\u4e00-\\u9fa5]$',
  chineseAfterRegex: '^[\\u4e00-\\u9fa5].*',
  capsBeforeRegex: '.*[A-Z]{2,}[0-9_]?$',
  capsAfterRegex: '',
};

test('returns chinese when before text matches chinese rule', () => {
  assert.equal(evaluateRules('输入', '', defaultRules), ImeAction.CHINESE);
});

test('ignores punctuation next to caret when matching chinese context', () => {
  assert.equal(evaluateRules('输入，。！？', '', defaultRules), ImeAction.CHINESE);
});

test('ignores punctuation next to caret when matching text after caret', () => {
  assert.equal(evaluateRules('', '（《中文', defaultRules), ImeAction.CHINESE);
});

test('prefers matching context before caret when both sides match', () => {
  assert.equal(
    evaluateRules(
      'ABC',
      '中文',
      defaultRules,
    ),
    ImeAction.CAPS,
  );
});

test('returns english for letters and english punctuation before caret', () => {
  for (const before of ['a', ',']) {
    assert.equal(evaluateRules(before, '中文', defaultRules), ImeAction.ENGLISH);
  }
});

test('ignores numbers for chinese caps and english matching', () => {
  for (const [before, expected] of [
    ['中文1', ImeAction.CHINESE],
    ['HTTP1', ImeAction.CAPS],
    ['a1', ImeAction.ENGLISH],
  ]) {
    assert.equal(evaluateRules(before, '', defaultRules), expected);
  }
});

test('number-only left context does not override right match', () => {
  assert.equal(evaluateRules('1', '中文', defaultRules), ImeAction.CHINESE);
});

test('space-only left context does not override right match', () => {
  assert.equal(evaluateRules(' ', '中文', defaultRules), ImeAction.CHINESE);
});

test('ignores spaces and numbers for all matching modes', () => {
  for (const [before, expected] of [
    ['中文 1', ImeAction.CHINESE],
    ['HTTP 1', ImeAction.CAPS],
    ['a 1', ImeAction.ENGLISH],
  ]) {
    assert.equal(evaluateRules(before, '', defaultRules), expected);
  }
});

test('does not treat full-width punctuation as english', () => {
  assert.equal(evaluateRules('，', '中文', defaultRules), ImeAction.CHINESE);
});

test('treats english punctuation as english instead of chinese context', () => {
  assert.equal(evaluateRules('中文,', '', defaultRules), ImeAction.ENGLISH);
});

test('treats english punctuation after caret as english', () => {
  assert.equal(evaluateRules('', ',中文', defaultRules), ImeAction.ENGLISH);
});

test('returns caps when caps rule matches and chinese does not', () => {
  assert.equal(evaluateRules('HTTP_', '', defaultRules), ImeAction.CAPS);
});

test('does not ignore general punctuation when matching caps context', () => {
  assert.equal(evaluateRules('HTTP_,', '', defaultRules), ImeAction.ENGLISH);
});

test('ignores only hyphens and underscores when matching caps context', () => {
  assert.equal(evaluateRules('HTTP_-', '', defaultRules), ImeAction.CAPS);
});

test('returns english when rules are blank or invalid', () => {
  assert.equal(
    evaluateRules(
      'plain',
      'text',
      {
        chineseBeforeRegex: '',
        chineseAfterRegex: '[',
        capsBeforeRegex: '',
        capsAfterRegex: '[',
      },
    ),
    ImeAction.ENGLISH,
  );
});
