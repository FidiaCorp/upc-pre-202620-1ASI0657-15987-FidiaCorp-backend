#!/usr/bin/env node
// Experimento QA-02 (ASR-AVAIL): Disponibilidad por Degradación Controlada (Resilience4j Circuit Breaker + Fallback)
// y Resiliencia Asíncrona (Transactional Outbox -> RabbitMQ -> Idempotent Inbox ante caída del consumidor).
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { performance } from 'node:perf_hooks';

const API = (process.env.QA_API_BASE_URL || 'http://localhost:8080/api/v1').replace(/\/+$/, '');
const PRICING = (process.env.QA_PRICING_URL || 'http://127.0.0.1:8083').replace(/\/+$/, '');
const IDENTITY = (process.env.QA_IDENTITY_URL || 'http://127.0.0.1:8081').replace(/\/+$/, '');
const FIXTURE_TOKEN = process.env.E2E_FIXTURE_TOKEN || '';
const INTERNAL_TOKEN = process.env.INTERNAL_API_TOKEN || 'local-dev-only-internal-service-token-change-me';
const OUT_FILE = process.env.QA02_OUT || 'experiments/qa02-resilience/qa02-results.json';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const percentile = (vals, p) => {
  const s = [...vals].sort((a, b) => a - b);
  return Math.round(s[Math.min(s.length - 1, Math.ceil((p / 100) * s.length) - 1)] * 10) / 10;
};
const compose = (...args) => execFileSync('docker', ['compose', ...args], { stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8' });

async function req(path, { method = 'GET', token, body, base = API, headers = {} } = {}) {
  const t0 = performance.now();
  const r = await fetch(`${base}${path}`, {
    method,
    headers: {
      Accept: 'application/json',
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
    signal: AbortSignal.timeout(20000),
  });
  const ms = Math.round((performance.now() - t0) * 10) / 10;
  const txt = await r.text();
  let json = null;
  try { json = txt ? JSON.parse(txt) : null; } catch { json = txt; }
  return { status: r.status, headers: r.headers, body: json, ms };
}

async function breaker() {
  const r = await req('/actuator/circuitbreakers', { base: PRICING });
  return r.body?.circuitBreakers?.bankingProvider || {};
}

async function createUser(label, realtor = false) {
  const email = `qa02-${label.toLowerCase()}-${randomUUID().slice(0, 8)}@example.com`;
  const password = `Qa02-${randomUUID().replaceAll('-', '')}!`;
  const reg = await req('/auth/register', { method: 'POST', body: { firstName: 'QA02', lastName: label, email, password } });
  if (reg.status !== 201) throw new Error(`register ${label} failed: ${reg.status}`);
  if (realtor) {
    const p = await req(`/api/v1/internal/e2e/accounts/${reg.body.id}/realtor`, {
      method: 'POST',
      base: IDENTITY,
      headers: { 'X-Fixture-Token': FIXTURE_TOKEN, 'X-Internal-Token': INTERNAL_TOKEN },
    });
    if (p.status !== 200) throw new Error(`promote realtor failed: ${p.status}`);
  }
  const login = await req('/auth/login', { method: 'POST', body: { email, password } });
  return { id: reg.body.id, email, token: login.body.token };
}

const report = { executedAt: new Date().toISOString(), partA_circuitBreaker: {}, partB_rabbitmqOutboxResilience: {}, checks: [] };
function check(id, name, ok, detail) {
  report.checks.push({ id, name, ok: Boolean(ok), detail });
  console.log(`${ok ? 'PASS' : 'FAIL'} [${id}] ${name} -> ${detail}`);
}

const client = await createUser('Client');
const realtor = await createUser('Realtor', true);

// Part A: Resilience4j Circuit Breaker + Fallback
for (let i = 0; i < 20 && (await breaker()).state !== 'CLOSED'; i++) {
  await req('/banking/benchmarks?currency=PEN');
  await sleep(1000);
}
const bInit = await breaker();
const liveRes = await req('/banking/benchmarks?currency=PEN');
report.partA_circuitBreaker.initial = { state: bInit.state, dataSource: liveRes.headers.get('x-data-source'), ms: liveRes.ms };
check('QA02-A1', 'Estado inicial CLOSED con datos LIVE', bInit.state === 'CLOSED' && liveRes.headers.get('x-data-source') === 'LIVE', `state=${bInit.state}, X-Data-Source=${liveRes.headers.get('x-data-source')}, ${liveRes.ms}ms`);

compose('stop', 'external-bank-mock');
try {
  const tripSources = [];
  for (let i = 0; i < 12; i++) {
    const r = await req('/banking/benchmarks?currency=PEN');
    tripSources.push(r.headers.get('x-data-source'));
  }
  const bOpen = await breaker();
  const openLatencies = [];
  let fallbackSample = null;
  for (let i = 0; i < 10; i++) {
    const r = await req('/banking/benchmarks?currency=PEN');
    openLatencies.push(r.ms);
    fallbackSample = Array.isArray(r.body) ? r.body[0] : r.body;
  }
  const simDuringOutage = await req('/simulations/calculate', {
    method: 'POST',
    token: client.token,
    body: { loanAmount: '200000.00', currency: 'PEN', termMonths: 240, annualInterestRate: '0.085', startDate: '2026-01-31' },
  });
  report.partA_circuitBreaker.outage = {
    state: bOpen.state,
    failureRate: bOpen.failureRate,
    failedCalls: bOpen.failedCalls,
    fallbackP50Ms: percentile(openLatencies, 50),
    fallbackP95Ms: percentile(openLatencies, 95),
    fallbackActive: fallbackSample?.fallbackActive,
    asOfDate: fallbackSample?.asOfDate,
    coreSimulationStatus: simDuringOutage.status,
    coreSimulationMs: simDuringOutage.ms,
  };
  check('QA02-A2', 'Transición automática CLOSED -> OPEN al caer external-bank-mock', bOpen.state === 'OPEN', `state=${bOpen.state}, failureRate=${bOpen.failureRate}, failedCalls=${bOpen.failedCalls}`);
  check('QA02-A3', 'Degradación explícita FALLBACK_CACHED + fallbackActive=true', tripSources.every((s) => s === 'FALLBACK_CACHED') && fallbackSample?.fallbackActive === true, `fallbackActive=${fallbackSample?.fallbackActive}, asOfDate=${fallbackSample?.asOfDate}`);
  check('QA02-A4', 'Latencia P95 fallback en OPEN < 100 ms', percentile(openLatencies, 95) < 100, `p50=${percentile(openLatencies, 50)}ms, p95=${percentile(openLatencies, 95)}ms (n=10)`);
  check('QA02-A5', 'Cálculo financiero 100% operativo durante caída externa', simDuringOutage.status === 201 && simDuringOutage.body.schedule?.at(-1)?.finalBalance === '0.00', `HTTP ${simDuringOutage.status}, ${simDuringOutage.ms}ms, finalBalance=0.00`);
} finally {
  compose('start', 'external-bank-mock');
}

const transitions = [];
let recovered = false;
for (let i = 0; i < 30 && !recovered; i++) {
  await sleep(2000);
  const r = await req('/banking/benchmarks?currency=PEN');
  const st = (await breaker()).state;
  if (!transitions.length || transitions.at(-1) !== st) transitions.push(st);
  recovered = st === 'CLOSED' && r.headers.get('x-data-source') === 'LIVE';
}
report.partA_circuitBreaker.recovery = { recovered, transitions };
check('QA02-A6', 'Auto-recuperación OPEN -> HALF_OPEN -> CLOSED al restaurar proveedor', recovered, `transitions=${transitions.join(' -> ')}`);

// Part B: RabbitMQ + Transactional Outbox / Idempotent Inbox Resilience
const prop = (await req('/properties', {
  method: 'POST',
  token: realtor.token,
  body: { title: `Resiliencia Outbox ${randomUUID().slice(0, 6)}`, address: 'Miraflores, Lima', description: 'QA-02 Outbox test', price: '320000.00', currency: 'PEN', tags: ['qa02'] },
})).body;

compose('stop', 'communication-notification-service');
let offer = null;
try {
  const offerRes = await req('/offers', {
    method: 'POST',
    token: realtor.token,
    body: { recipientEmail: client.email, propertyId: prop.id, bankCode: 'BCP', loanAmount: '250000.00', currency: 'PEN', annualInterestRate: '0.085', termMonths: 240, startDate: '2026-01-31' },
  });
  offer = offerRes.body;
  report.partB_rabbitmqOutboxResilience.offerDuringConsumerDown = { status: offerRes.status, offerId: offer?.id, ms: offerRes.ms };
  check('QA02-B1', 'Emisión de oferta (201 Created) con MS-5 detenido (Outbox -> RabbitMQ)', offerRes.status === 201 && Boolean(offer?.id), `HTTP ${offerRes.status}, offerId=${offer?.id}, ${offerRes.ms}ms`);
} finally {
  compose('start', 'communication-notification-service');
}

let delivered = null;
for (let i = 0; i < 45; i++) {
  await sleep(2000);
  try {
    const nRes = await req('/notifications', { token: client.token });
    if (nRes.status === 200 && nRes.body?.items?.some((n) => n.offerId === offer.id)) {
      delivered = nRes.body.items.filter((n) => n.offerId === offer.id);
      break;
    }
  } catch { /* wait for service healthy */ }
}
report.partB_rabbitmqOutboxResilience.afterRecovery = { deliveredCount: delivered?.length || 0, notificationId: delivered?.[0]?.id || null };
check('QA02-B2', 'Entrega diferida e idempotente (exactamente 1 notificación) tras reiniciar MS-5', (delivered?.length || 0) === 1, `deliveredCount=${delivered?.length || 0}, notificationId=${delivered?.[0]?.id}`);

const passed = report.checks.filter((c) => c.ok).length;
report.summary = { passed, total: report.checks.length, allPassed: passed === report.checks.length };
writeFileSync(OUT_FILE, JSON.stringify(report, null, 2));
console.log(`\nQA-02 SUMMARY: ${passed}/${report.checks.length} PASS -> saved ${OUT_FILE}`);
process.exitCode = report.summary.allPassed ? 0 : 1;
