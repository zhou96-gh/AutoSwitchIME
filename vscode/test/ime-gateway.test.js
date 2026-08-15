const assert = require('node:assert/strict');
const test = require('node:test');

const { ImeGateway } = require('../out/ime/ImeGateway');
const {
  currentSystemType,
  SystemImeProviderRegistry,
  SystemType,
} = require('../out/ime/system/SystemImeProvider');
const { ImeType } = require('../out/core/types');

const logger = {
  info() {},
  warn() {},
  debug() {},
  error() {},
};

function provider(overrides = {}) {
  return {
    type: ImeType.RIME,
    name: 'fake',
    start() {},
    dispose() {},
    ...overrides,
  };
}

function system(initial = {}) {
  const state = {
    ascii: initial.ascii ?? true,
    caps: initial.caps ?? false,
    composing: initial.composing ?? false,
  };
  return {
    type: SystemType.WINDOWS,
    asciiSwitchCount: 0,
    readAsciiMode: () => state.ascii,
    readCapsLock: () => state.caps,
    readComposing: () => state.composing,
    setAsciiState: (ascii) => { state.ascii = ascii; },
    async switchAsciiMode(ascii, shouldContinue) {
      this.asciiSwitchCount++;
      if (!shouldContinue()) return false;
      state.ascii = ascii;
      return true;
    },
    async switchCapsLock(enabled, shouldContinue) {
      if (!shouldContinue()) return false;
      state.caps = enabled;
      return true;
    },
  };
}

test('system state supplies capabilities missing from input method provider', () => {
  const ime = new ImeGateway(provider(), system({ ascii: true, caps: true }), logger);

  assert.deepEqual(ime.getCurrentState(), {
    isAsciiMode: true,
    isCapsLock: true,
    isComposing: false,
  });
});

test('input method state overrides only fields it supplies', () => {
  const imeProvider = provider({
    stateSource: { readAsciiMode: () => false },
  });
  const ime = new ImeGateway(
    imeProvider,
    system({ ascii: true, caps: true, composing: true }),
    logger,
  );

  assert.deepEqual(ime.getCurrentState(), {
    isAsciiMode: false,
    isCapsLock: true,
    isComposing: true,
  });
});

test('missing input method switcher falls back to system switcher', async () => {
  const systemProvider = system({ ascii: false });
  const ime = new ImeGateway(provider(), systemProvider, logger);
  ime.refreshState();

  await ime.setAsciiMode(true);

  assert.equal(systemProvider.asciiSwitchCount, 1);
  assert.equal(ime.getTrackedState().isAsciiMode, true);
});

test('input method switcher takes precedence and failure does not fall back', async () => {
  let providerSwitchCount = 0;
  const systemProvider = system({ ascii: false });
  const imeProvider = provider({
    asciiModeSwitcher: {
      async switchAsciiMode() {
        providerSwitchCount++;
        return false;
      },
    },
  });
  const ime = new ImeGateway(imeProvider, systemProvider, logger);
  ime.refreshState();

  await ime.setAsciiMode(true);

  assert.equal(providerSwitchCount, 1);
  assert.equal(systemProvider.asciiSwitchCount, 0);
  assert.equal(ime.getTrackedState().isAsciiMode, false);
});

test('forced ASCII mode invokes input method switcher when system state is already ASCII', async () => {
  let providerSwitchCount = 0;
  const systemProvider = system({ ascii: true });
  const imeProvider = provider({
    asciiModeSwitcher: {
      async switchAsciiMode() {
        providerSwitchCount++;
        return true;
      },
    },
  });
  const ime = new ImeGateway(imeProvider, systemProvider, logger);
  ime.refreshState();

  await ime.setAsciiMode(true, () => true, true);

  assert.equal(providerSwitchCount, 1);
  assert.equal(systemProvider.asciiSwitchCount, 0);
});

test('first input method action runs when provider state has not been observed', async () => {
  let providerSwitchCount = 0;
  const systemProvider = system({ ascii: true });
  const imeProvider = provider({
    asciiModeSwitcher: {
      async switchAsciiMode() {
        providerSwitchCount++;
        return true;
      },
    },
  });
  const ime = new ImeGateway(imeProvider, systemProvider, logger);
  ime.refreshState();

  await ime.setAsciiMode(true);

  assert.equal(providerSwitchCount, 1);
  assert.equal(ime.getTrackedState().isAsciiMode, true);
});

test('input method state source remains authoritative when system state disagrees', async () => {
  let providerSwitchCount = 0;
  let providerAscii = true;
  const systemProvider = system({ ascii: true });
  const imeProvider = provider({
    stateSource: {
      readAsciiMode() {
        return providerAscii;
      },
    },
    asciiModeSwitcher: {
      async switchAsciiMode(ascii) {
        providerSwitchCount++;
        providerAscii = ascii;
        return true;
      },
    },
  });
  const ime = new ImeGateway(imeProvider, systemProvider, logger);
  ime.refreshState();

  await ime.setAsciiMode(false);
  ime.refreshState();

  assert.equal(ime.getTrackedState().isAsciiMode, false);
  assert.equal(providerSwitchCount, 1);
  assert.equal(systemProvider.asciiSwitchCount, 0);
});

test('successful switch command does not replace observed input method state', async () => {
  let providerSwitchCount = 0;
  const imeProvider = provider({
    stateSource: {
      readAsciiMode: () => false,
    },
    asciiModeSwitcher: {
      async switchAsciiMode() {
        providerSwitchCount++;
        return true;
      },
    },
  });
  const ime = new ImeGateway(imeProvider, system({ ascii: true }), logger);
  ime.refreshState();

  await ime.setAsciiMode(true);

  assert.equal(ime.getTrackedState().isAsciiMode, false);
  assert.equal(providerSwitchCount, 1);
});

test('system change updates tracked input method state', async () => {
  const systemProvider = system({ ascii: true });
  const imeProvider = provider({
    asciiModeSwitcher: {
      async switchAsciiMode() {
        return true;
      },
    },
  });
  const ime = new ImeGateway(imeProvider, systemProvider, logger);
  ime.refreshState();
  await ime.setAsciiMode(true);

  systemProvider.setAsciiState(false);
  ime.refreshState();

  assert.equal(ime.getTrackedState().isAsciiMode, false);
});

test('system providers are selected through registry', () => {
  const registry = new SystemImeProviderRegistry();
  const windows = system();
  registry.register(SystemType.WINDOWS, () => windows);

  assert.equal(registry.create(SystemType.WINDOWS), windows);
  assert.throws(() => registry.create(SystemType.LINUX), /not found/);
  assert.equal(currentSystemType('win32'), SystemType.WINDOWS);
  assert.equal(currentSystemType('darwin'), SystemType.MACOS);
  assert.equal(currentSystemType('linux'), SystemType.LINUX);
});

test('declared unavailable state source suspends actions until it recovers', async () => {
  let available = false;
  let providerSwitchCount = 0;
  const imeProvider = provider({
    stateSource: {
      isAvailable: () => available,
      readState: () => ({ isAsciiMode: false, isComposing: false }),
    },
    asciiModeSwitcher: {
      async switchAsciiMode() {
        providerSwitchCount++;
        return true;
      },
    },
  });
  const ime = new ImeGateway(imeProvider, system({ ascii: true }), logger);

  ime.start();
  await ime.setAsciiMode(false);

  assert.equal(ime.isStateSourceAvailable(), false);
  assert.equal(providerSwitchCount, 0);

  available = true;
  ime.refreshState();
  await ime.setAsciiMode(true);

  assert.equal(ime.isStateSourceAvailable(), true);
  assert.equal(providerSwitchCount, 1);
});
