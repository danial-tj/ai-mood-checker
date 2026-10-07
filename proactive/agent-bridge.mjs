/** Local stdio MCP bridge. No dependencies, hosted endpoint, or personal-agent memory access. */
import { pathToFileURL } from 'node:url';
import { StringDecoder } from 'node:string_decoder';

export const MAX_MESSAGE_BYTES = 65_536;
const MAX_RESPONSE_BYTES = 262_144;
const protocol = '2025-06-18';
const hasId = value => value !== null && typeof value === 'object' && Object.hasOwn(value, 'id');
const rpcError = (id, code, message) => ({ jsonrpc: '2.0', id: id ?? null, error: { code, message } });

export function configuration(env = globalThis.process?.env ?? {}) {
  const base = env.MOOD_PLANNER_URL || 'http://127.0.0.1:8471';
  // Exact numeric loopback avoids DNS lookup, confusing URL forms and credential forwarding.
  if (!/^http:\/\/127\.0\.0\.1:\d{1,5}\/?$/.test(base)) throw new Error('MOOD_PLANNER_URL must use http://127.0.0.1:<port>.');
  const url = new URL(base);
  const port = Number(url.port || 80);
  if (port < 1024 || port > 65535) throw new Error('Choose a local port between 1024 and 65535.');
  const token = env.MOOD_AGENT_TOKEN || '';
  if (!/^[A-Za-z0-9_-]{32,512}$/.test(token)) throw new Error('Set MOOD_AGENT_TOKEN to a random token of at least 32 letters, digits, underscores or hyphens.');
  return { url: new URL('/api/agent', url).href, token };
}

async function readResponse(response) {
  const reader = response.body?.getReader();
  if (!reader) throw new Error('Missing response.');
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_RESPONSE_BYTES) throw new Error('Response too large.');
      chunks.push(value);
    }
  } catch (error) {
    await reader.cancel().catch(() => {});
    throw error;
  } finally { reader.releaseLock(); }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

export function createForwarder(config, fetchImpl = globalThis.fetch) {
  return async request => {
    const id = hasId(request) ? request.id : null;
    try {
      const response = await fetchImpl(config.url, {
        method: 'POST', redirect: 'error', signal: AbortSignal.timeout(10_000),
        headers: { 'Content-Type': 'application/json', Accept: 'application/json', Authorization: 'Bearer ' + config.token },
        body: JSON.stringify(request)
      });
      if (response.status === 202 || response.status === 204) return null;
      if (!response.ok) return hasId(request) ? rpcError(id, -32000, 'The local planner rejected access. Check that the app is running with the same agent token.') : null;
      const result = await readResponse(response);
      if (result?.jsonrpc !== '2.0' || !Object.hasOwn(result, 'id') || result.id !== id || (!Object.hasOwn(result, 'result') && !Object.hasOwn(result, 'error'))) {
        throw new Error('Invalid planner response.');
      }
      return result;
    } catch {
      // Never echo credentials, URLs, response bodies, mood data or private exception text.
      return hasId(request) ? rpcError(id, -32000, 'Could not reach the local planner. Check that it is running; no automatic retry was made.') : null;
    }
  };
}

export function createSession(forward) {
  let initialized = false;
  let ready = false;
  return async request => {
    if (!request || typeof request !== 'object' || Array.isArray(request) || request.jsonrpc !== '2.0' || typeof request.method !== 'string'
      || (hasId(request) && !(typeof request.id === 'string' || Number.isSafeInteger(request.id)))) {
      return rpcError(null, -32600, 'Invalid JSON-RPC request.');
    }
    if (!hasId(request)) {
      if (request.method === 'notifications/initialized' && initialized) ready = true;
      // No notification can call a tool or write app data.
      return null;
    }
    if (request.method === 'initialize') {
      if (initialized) return rpcError(request.id, -32600, 'This connection is already initialized.');
      const response = await forward(request);
      if (response?.result?.protocolVersion === protocol) initialized = true;
      return response;
    }
    if (request.method !== 'ping' && !ready) return rpcError(request.id, -32002, 'Initialize this MCP connection before using tools.');
    return forward(request);
  };
}

export async function runStdio(input, output, handle) {
  const decoder = new StringDecoder('utf8');
  let pending = '';
  let discarding = false;
  const send = value => { if (value !== null && value !== undefined) output.write(JSON.stringify(value) + '\n'); };
  const line = async text => {
    if (!text.trim()) return;
    if (Buffer.byteLength(text, 'utf8') > MAX_MESSAGE_BYTES) { send(rpcError(null, -32600, 'Message exceeds the size limit.')); return; }
    try { send(await handle(JSON.parse(text))); }
    catch { send(rpcError(null, -32700, 'Invalid JSON.')); }
  };
  for await (const chunk of input) {
    pending += typeof chunk === 'string' ? chunk : decoder.write(chunk);
    let newline;
    while ((newline = pending.indexOf('\n')) >= 0) {
      const text = pending.slice(0, newline);
      pending = pending.slice(newline + 1);
      if (discarding) discarding = false;
      else await line(text);
    }
    if (Buffer.byteLength(pending, 'utf8') > MAX_MESSAGE_BYTES) {
      if (!discarding) send(rpcError(null, -32600, 'Message exceeds the size limit.'));
      pending = ''; discarding = true;
    }
  }
  pending += decoder.end();
  if (pending.trim() && !discarding) await line(pending);
}

if (typeof process !== 'undefined' && process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const config = configuration();
    await runStdio(process.stdin, process.stdout, createSession(createForwarder(config)));
  } catch (error) {
    process.stderr.write('AI Mood Checker bridge: ' + error.message + '\n');
    process.exitCode = 1;
  }
}
