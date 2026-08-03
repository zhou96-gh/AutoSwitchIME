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

test('returns chinese before caps when both rules match', () => {
  assert.equal(
    evaluateRules(
      'ABC',
      '中文',
      defaultRules,
    ),
    ImeAction.CHINESE,
  );
});

test('returns caps when caps rule matches and chinese does not', () => {
  assert.equal(evaluateRules('HTTP_', '', defaultRules), ImeAction.CAPS);
});

test('ignores punctuation next to caret when matching caps context', () => {
  assert.equal(evaluateRules('HTTP_,', '', defaultRules), ImeAction.CAPS);
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
