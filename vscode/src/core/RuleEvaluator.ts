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
const trailingPunctuationOrSymbols = /[\p{P}\p{S}]+$/u;
const leadingPunctuationOrSymbols = /^[\p{P}\p{S}]+/u;

/**
 * 评估 Insert 模式下的输入法动作
 */
export function evaluateRules(
  before: string,
  after: string,
  rules: RuleSet,
): ImeAction {
  const normalizedBefore = before.replace(trailingPunctuationOrSymbols, '');
  const normalizedAfter = after.replace(leadingPunctuationOrSymbols, '');

  // 1. 检查中文规则：前后任一匹配
  if (
    matchesRegex(rules.chineseBeforeRegex, normalizedBefore) ||
    matchesRegex(rules.chineseAfterRegex, normalizedAfter)
  ) {
    return ImeAction.CHINESE;
  }

  // 2. 检查大写规则：前后任一匹配
  if (
    matchesRegex(rules.capsBeforeRegex, normalizedBefore) ||
    matchesRegex(rules.capsAfterRegex, normalizedAfter)
  ) {
    return ImeAction.CAPS;
  }

  // 3. 默认英文（英文不需要正则匹配）
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
