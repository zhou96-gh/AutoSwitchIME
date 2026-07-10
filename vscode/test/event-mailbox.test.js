const assert = require('node:assert/strict');
const test = require('node:test');

const { EventMailbox } = require('../out/core/EventMailbox');

test('coalesces pending editor events while preserving the running event', async () => {
  const handled = [];
  let releaseFirst;
  const firstBlocked = new Promise((resolve) => {
    releaseFirst = resolve;
  });

  const mailbox = new EventMailbox(async (event) => {
    handled.push(event.id);
    if (event.id === 'a') await firstBlocked;
  });

  mailbox.post({ kind: 'editor', id: 'a' });
  await Promise.resolve();
  mailbox.post(
    { kind: 'editor', id: 'b' },
    (pending) => pending.kind === 'editor',
  );
  mailbox.post(
    { kind: 'editor', id: 'c' },
    (pending) => pending.kind === 'editor',
  );
  releaseFirst();

  await mailbox.waitForIdle();
  assert.deepEqual(handled, ['a', 'c']);
});

test('continues processing after an event handler failure', async () => {
  const handled = [];
  const failures = [];
  const mailbox = new EventMailbox(
    async (event) => {
      handled.push(event);
      if (event === 'bad') throw new Error('failed');
    },
    (error) => failures.push(error.message),
  );

  mailbox.post('bad');
  mailbox.post('good');

  await mailbox.waitForIdle();
  assert.deepEqual(handled, ['bad', 'good']);
  assert.deepEqual(failures, ['failed']);
});
