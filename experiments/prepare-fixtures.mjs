#!/usr/bin/env node
// Prepara fixtures reales contra el stack Docker vivo y genera el archivo de entorno Newman para QA-03 (20 vectores) + token JWT para QA-01 (k6).
import { randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';

const API = 'http://localhost:8080/api/v1';
const IDENTITY = 'http://127.0.0.1:8081';
const FIXTURE_TOKEN = process.env.E2E_FIXTURE_TOKEN;
const INTERNAL_TOKEN = process.env.INTERNAL_API_TOKEN || 'local-dev-only-internal-service-token-change-me';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function req(path, { method = 'GET', token, body, base = API, headers = {} } = {}) {
  const r = await fetch(`${base}${path}`, {
    method,
    headers: {
      Accept: 'application/json',
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const txt = await r.text();
  let json = null;
  try { json = txt ? JSON.parse(txt) : null; } catch { json = txt; }
  return { status: r.status, body: json };
}

async function createAccount(label, realtor = false) {
  const email = `exp-${label.toLowerCase()}-${randomUUID().slice(0, 8)}@example.com`;
  const password = `Exp-${randomUUID().replaceAll('-', '')}!`;
  const reg = await req('/auth/register', { method: 'POST', body: { firstName: 'Exp', lastName: label, email, password } });
  if (reg.status !== 201) throw new Error(`register ${label}: ${reg.status}`);
  if (realtor) {
    const p = await req(`/api/v1/internal/e2e/accounts/${reg.body.id}/realtor`, {
      method: 'POST', base: IDENTITY,
      headers: { 'X-Fixture-Token': FIXTURE_TOKEN, 'X-Internal-Token': INTERNAL_TOKEN },
    });
    if (p.status !== 200) throw new Error(`promote ${label}: ${p.status}`);
  }
  const login = await req('/auth/login', { method: 'POST', body: { email, password } });
  return { id: reg.body.id, email, token: login.body.token };
}

const client = await createAccount('Client');
const other = await createAccount('Other');
const realtor = await createAccount('Realtor', true);
const revoked = await createAccount('Revoked');

const sim = (await req('/simulations/calculate', {
  method: 'POST', token: client.token,
  body: { loanAmount: '180000.00', currency: 'PEN', termMonths: 120, annualInterestRate: '0.085', startDate: '2026-01-31' },
})).body;

const prop = (await req('/properties', {
  method: 'POST', token: realtor.token,
  body: { title: `QA03 Prop ${randomUUID().slice(0, 6)}`, address: 'San Isidro, Lima', description: 'Fixture QA-03', price: '350000.00', currency: 'PEN', tags: ['seguridad'] },
})).body;

const offer = (await req('/offers', {
  method: 'POST', token: realtor.token,
  body: { recipientEmail: client.email, propertyId: prop.id, bankCode: 'BCP', loanAmount: '250000.00', currency: 'PEN', annualInterestRate: '0.085', termMonths: 240, startDate: '2026-01-31' },
})).body;

let notifId = '';
for (let i = 0; i < 30; i++) {
  const n = (await req('/notifications', { token: client.token })).body;
  const hit = n?.items?.find((x) => x.offerId === offer.id);
  if (hit) { notifId = hit.id; break; }
  await sleep(500);
}

const conv = (await req('/messages/conversations', {
  method: 'POST', token: client.token,
  body: { recipientId: realtor.id, propertyId: prop.id },
})).body;

await req('/messages', {
  method: 'POST', token: client.token,
  body: { conversationId: conv.conversationId, recipientId: realtor.id, content: 'Mensaje privado confidencial QA-03' },
});

await req('/auth/logout', { method: 'POST', token: revoked.token });
await sleep(2500);

const envJson = {
  id: randomUUID(),
  name: 'CrediCasa QA-03 Live Environment',
  values: [
    { key: 'baseUrl', value: 'http://host.docker.internal:8080/api/v1', enabled: true },
    { key: 'clientToken', value: client.token, enabled: true },
    { key: 'clientId', value: client.id, enabled: true },
    { key: 'clientEmail', value: client.email, enabled: true },
    { key: 'otherToken', value: other.token, enabled: true },
    { key: 'otherEmail', value: other.email, enabled: true },
    { key: 'realtorToken', value: realtor.token, enabled: true },
    { key: 'revokedToken', value: revoked.token, enabled: true },
    { key: 'simId', value: sim.quotationId, enabled: true },
    { key: 'propertyId', value: prop.id, enabled: true },
    { key: 'offerId', value: offer.id, enabled: true },
    { key: 'notificationId', value: notifId, enabled: true },
    { key: 'conversationId', value: conv.conversationId, enabled: true },
  ],
};

writeFileSync('experiments/qa03-security/qa03-env.json', JSON.stringify(envJson, null, 2));
writeFileSync('experiments/qa01-k6/.jwt.tmp', client.token);
console.log(JSON.stringify({ ok: true, simId: sim.quotationId, offerId: offer.id, notificationId: notifId, conversationId: conv.conversationId }, null, 2));
