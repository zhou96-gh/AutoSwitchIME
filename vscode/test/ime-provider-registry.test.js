const assert = require('node:assert/strict');
const test = require('node:test');

const { availableImeTypeFor, ImeType } = require('../out/core/types');
const { ImeProviderRegistry } = require('../out/ime/input/ImeProviderRegistry');

test('creates registered provider and rejects unsupported type', () => {
  const registry = new ImeProviderRegistry();
  const provider = { name: 'fake' };
  registry.register(ImeType.RIME, () => provider);

  assert.equal(registry.create({ type: ImeType.RIME }), provider);
  assert.throws(
    () => registry.create({ type: ImeType.SOGOU }),
    /IME provider not found/,
  );
});

test('unavailable or invalid config falls back to rime', () => {
  assert.equal(availableImeTypeFor(undefined), ImeType.RIME);
  assert.equal(availableImeTypeFor('invalid'), ImeType.RIME);
  assert.equal(availableImeTypeFor(ImeType.SOGOU), ImeType.RIME);
});
