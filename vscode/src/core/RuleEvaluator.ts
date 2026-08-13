/**
 * 正则规则评估器
 * 从 Kotlin core/rules/RuleEvaluator.kt 移植
 * 评估光标前后文本，决定输入法动作
 */

import { ImeAction } from './types';

/** 规则配置 */
export interface RuleSet {
  chineseBeforeRegex: string;
  chineseAfterRegex: string;
  capsBeforeRegex: string;
  capsAfterRegex: string;
}

/** 正则 Pattern 缓存 */
const patternCache = new Map<string, RegExp>();
const trailingPunctuationSymbolsNumbersOrSpaces = /[\p{P}\p{S}\p{N}\s]+$/u;
const leadingPunctuationSymbolsNumbersOrSpaces = /^[\p{P}\p{S}\p{N}\s]+/u;
const capsSeparators = /[-_]/gu;
const trailingNeutralCharacters = /[\p{N}\s]+$/u;
const leadingNeutralCharacters = /^[\p{N}\s]+/u;
const trailingEnglishCharacter = /[\x21-\x7E]$/u;
const leadingEnglishCharacter = /^[\x21-\x7E]/u;

/**
 * 评估 Insert 模式下的输入法动作
 */
export function evaluateRules(
  before: string,
  after: string,
  rules: RuleSet,
): ImeAction {
  const chineseBefore = before.replace(trailingPunctuationSymbolsNumbersOrSpaces, '');
  const chineseAfter = after.replace(leadingPunctuationSymbolsNumbersOrSpaces, '');
  const capsBefore = before.replace(trailingNeutralCharacters, '').replace(capsSeparators, '');
  const capsAfter = after.replace(leadingNeutralCharacters, '').replace(capsSeparators, '');
  const englishBefore = before.replace(trailingNeutralCharacters, '');
  const englishAfter = after.replace(leadingNeutralCharacters, '');

  // 光标两侧同时命中时，以左侧上下文为准。
  if (matchesRegex(rules.capsBeforeRegex, capsBefore)) {
    return ImeAction.CAPS;
  }
  if (trailingEnglishCharacter.test(englishBefore)) {
    return ImeAction.ENGLISH;
  }
  if (matchesRegex(rules.chineseBeforeRegex, chineseBefore)) {
    return ImeAction.CHINESE;
  }

  if (matchesRegex(rules.capsAfterRegex, capsAfter)) {
    return ImeAction.CAPS;
  }
  if (leadingEnglishCharacter.test(englishAfter)) {
    return ImeAction.ENGLISH;
  }
  if (matchesRegex(rules.chineseAfterRegex, chineseAfter)) {
    return ImeAction.CHINESE;
  }

  return ImeAction.ENGLISH;
}

/**
 * 检查正则是否匹配（空规则视为未配置，不触发）
 */
function matchesRegex(pattern: string, text: string): boolean {
  if (!pattern || pattern.trim() === '') {
    return false;
  }
  try {
    const compiled =
      patternCache.get(pattern) ?? buildAndCache(pattern);
    return compiled.test(text);
  } catch {
    return false;
  }
}

function buildAndCache(pattern: string): RegExp {
  const compiled = new RegExp(pattern);
  patternCache.set(pattern, compiled);
  return compiled;
}
