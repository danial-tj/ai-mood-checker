import assert from 'node:assert/strict';
import { Readable, Writable } from 'node:stream';
import { pathToFileURL } from 'node:url';
import { configuration, createForwarder, createSession, runStdio, MAX_MESSAGE_BYTES } from '../agent-bridge.mjs';

export async function runChecks() {
  let checks = 0;
  const check = condition => { assert.ok(condition); checks++; };
  const reject = callback => { assert.throws(callback); checks++; };
  const token = 'synthetic-token-for-local-tests-1234567890';
  const config = configuration({ MOOD_AGENT_TOKEN: token });
  check(config.url === 'http://127.0.0.1:8471/api/agent');
  for (const base of ['https://example.com', 'http://localhost:8471', 'http://127.0.0.1:8471@evil.test', 'http://2130706433:8471', 'http://127.0.0.1:8471/private', 'http://127.0.0.1:8471?token=x', 'http://127.0.0.1:80']) {
    reject(() => configuration({ MOOD_AGENT_TOKEN: token, MOOD_PLANNER_URL: base }));
  }
  reject(() => configuration({}));
  reject(() => configuration({ MOOD_AGENT_TOKEN: token + '\nsecret' }));
  let calls = 0;
  const forward = createForwarder(config, async (url, options) => {
    calls++;
    check(url === config.url && options.redirect === 'error');
    check(options.headers.Authorization === 'Bearer ' + token);
    const request = JSON.parse(options.body);
    return new Response(JSON.stringify({ jsonrpc: '2.0', id: request.id, result: request.method === 'initialize' ? { protocolVersion: '2025-06-18' } : { tools: [] } }));
  });
  const session = createSession(forward);
  check((await session({ jsonrpc: '2.0', id: 1, method: 'tools/list' })).error.code === -32002 && calls === 0);
  check((await session({ jsonrpc: '2.0', id: 2, method: 'initialize', params: {} })).result.protocolVersion === '2025-06-18');
  check(await session({ jsonrpc: '2.0', method: 'notifications/initialized' }) === null);
  check((await session({ jsonrpc: '2.0', id: 3, method: 'tools/list' })).result.tools.length === 0);
  const beforeNotification = calls;
  check(await session({ jsonrpc: '2.0', method: 'tools/call', params: { name: 'record_checkin' } }) === null && calls === beforeNotification);
  check((await session({ jsonrpc: '2.0', id: 4, method: 'initialize' })).error.code === -32600);
  check((await session({ jsonrpc: '2.0', id: null, method: 'ping' })).error.code === -32600);
  check((await session([])).error.code === -32600);
  const unavailable = createForwarder(config, async () => { throw new Error('secret ' + token); });
  const failure = await unavailable({ jsonrpc: '2.0', id: 5, method: 'ping' });
  check(failure.error.code === -32000 && !JSON.stringify(failure).includes(token));
  const wrongId = createForwarder(config, async () => new Response(JSON.stringify({ jsonrpc: '2.0', id: 999, result: {} })));
  check((await wrongId({ jsonrpc: '2.0', id: 6, method: 'ping' })).error.code === -32000);
  const tooLarge = createForwarder(config, async () => new Response('x'.repeat(262145)));
  check((await tooLarge({ jsonrpc: '2.0', id: 7, method: 'ping' })).error.code === -32000);
  let output = '';
  const sink = new Writable({ write(chunk, encoding, done) { output += chunk.toString(); done(); } });
  const unicode = Buffer.from(JSON.stringify({ jsonrpc: '2.0', id: 'café', method: 'ping' }) + '\n');
  const split = unicode.indexOf(Buffer.from(String.fromCharCode(233))) + 1;
  const messages = [Buffer.from('{broken}\n'), Buffer.from('x'.repeat(MAX_MESSAGE_BYTES + 1)), Buffer.from('\n'), unicode.subarray(0, split), unicode.subarray(split)];
  await runStdio(Readable.from(messages), sink, async request => ({ jsonrpc: '2.0', id: request.id, result: {} }));
  const parsed = output.trim().split('\n').map(text => JSON.parse(text));
  check(parsed.length === 3);
  check(parsed[0].error.code === -32700 && parsed[1].error.code === -32600);
  check(parsed[2].id === 'café');
  return checks;
}

if (typeof process !== 'undefined' && process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  console.log('PASS: ' + await runChecks() + ' synthetic agent bridge checks; no accounts or remote requests.');
}
