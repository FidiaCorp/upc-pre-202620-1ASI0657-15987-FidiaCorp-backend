#!/usr/bin/env node
// Verificación reproducible de QA-01 (rendimiento/exactitud), QA-02 (Circuit Breaker + external-bank-mock) y QA-03
// (BOLA/BFLA/JWT) contra el stack docker compose de la Etapa 2, siempre a través del API Gateway.
//   node scripts/qa-verify.mjs                (desde la raíz de FidiaCorp-backend, con el stack "Up (healthy)")
// Variables: QA_API_BASE_URL (http://localhost:8080/api/v1), QA_PRICING_URL (http://127.0.0.1:8083),
//            QA_IDENTITY_URL (http://127.0.0.1:8081), E2E_FIXTURE_TOKEN (obligatorio: promueve el asesor desechable),
//            INTERNAL_API_TOKEN (valor de desarrollo por defecto), QA_SKIP_CHAOS=1 para omitir el docker stop/start.
// Sale con código 0 solo si TODAS las comprobaciones pasan. No usa mocks: cada petición llega a los microservicios reales.
import { execFileSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { performance } from 'node:perf_hooks'

const api = (process.env.QA_API_BASE_URL || 'http://localhost:8080/api/v1').replace(/\/+$/, '')
const gateway = new URL(api).origin
const pricing = (process.env.QA_PRICING_URL || 'http://127.0.0.1:8083').replace(/\/+$/, '')
const identity = (process.env.QA_IDENTITY_URL || 'http://127.0.0.1:8081').replace(/\/+$/, '')
const fixtureToken = process.env.E2E_FIXTURE_TOKEN || ''
const internalToken = process.env.INTERNAL_API_TOKEN || 'local-dev-only-internal-service-token-change-me'
const checks = []
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
function record(qa, name, ok, detail) {
  checks.push({ qa, name, ok: Boolean(ok), detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  [${qa}] ${name} — ${detail}`)
}
async function http(path, { method = 'GET', token, body, base = api, headers = {}, raw = false } = {}) {
  const started = performance.now()
  const response = await fetch(`${base}${path}`, { method, headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers }, ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(20000) })
  const ms = performance.now() - started
  const payload = raw ? Buffer.from(await response.arrayBuffer()) : await response.text().then(text => { try { return text ? JSON.parse(text) : null } catch { return text } })
  return { status: response.status, headers: response.headers, body: payload, ms }
}
async function account(label, { realtor = false } = {}) {
  const credentials = { firstName: 'QA', lastName: label, email: `qa-${label.toLowerCase()}-${randomUUID()}@example.com`, password: `Qa-${randomUUID().replaceAll('-', '')}!` }
  const created = await http('/auth/register', { method: 'POST', body: credentials })
  if (created.status !== 201) throw new Error(`registro ${label}: ${created.status}`)
  if (realtor) {
    const promoted = await http(`/api/v1/internal/e2e/accounts/${created.body.id}/realtor`, { method: 'POST', base: identity, headers: { 'X-Fixture-Token': fixtureToken, 'X-Internal-Token': internalToken } })
    if (promoted.status !== 200) throw new Error(`promoción del asesor: ${promoted.status} (¿stack levantado con E2E_FIXTURE_TOKEN?)`)
  }
  const login = await http('/auth/login', { method: 'POST', body: { email: credentials.email, password: credentials.password } })
  if (login.status !== 200) throw new Error(`login ${label}: ${login.status}`)
  return { id: created.body.id, email: credentials.email, token: login.body.token }
}
const percentile = (values, p) => { const sorted = [...values].sort((a, b) => a - b); return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)] }
const round = value => Math.round(value * 10) / 10
async function breaker() {
  const response = await http('/actuator/circuitbreakers', { base: pricing })
  return response.body?.circuitBreakers?.bankingProvider || {}
}
function compose(...args) { return execFileSync('docker', ['compose', ...args], { stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8' }) }

async function qa01(client) {
  const body = { loanAmount: '350000.00', propertyValue: '450000.00', currency: 'PEN', termMonths: 360, annualInterestRate: '0.0925', rateType: 'TEA', startDate: '2026-01-31', gracePeriodType: 'TOTAL', gracePeriodMonths: 6, balloonPaymentAmount: '50000.00', balloonPaymentMonth: 360, lifeInsuranceRate: '0.00028', propertyInsuranceRate: '0.00025', monthlyAdminFee: '10.00' }
  for (let i = 0; i < 5; i++) await http('/simulations/calculate', { method: 'POST', token: client.token, body })
  const latencies = []
  let exact = 0
  for (let i = 0; i < 40; i++) {
    const response = await http('/simulations/calculate', { method: 'POST', token: client.token, body })
    latencies.push(response.ms)
    if (response.status === 201 && response.body.schedule?.length === 360 && response.body.schedule.at(-1).finalBalance === '0.00') exact++
  }
  const p95 = percentile(latencies, 95)
  const p99 = percentile(latencies, 99)
  record('QA-01', '360 cuotas (gracia total + balón + seguros) con saldo final 0.00 exacto', exact === 40, `${exact}/40 respuestas 201 con 360 filas y finalBalance "0.00"`)
  record('QA-01', 'latencia secuencial vía gateway p95 < 350 ms y p99 < 800 ms', p95 < 350 && p99 < 800, `p50=${round(percentile(latencies, 50))} ms, p95=${round(p95)} ms, p99=${round(p99)} ms, max=${round(Math.max(...latencies))} ms (n=40)`)
  const cold = await http('/banking/benchmarks?currency=USD')
  const warm = []
  for (let i = 0; i < 10; i++) warm.push((await http('/banking/benchmarks?currency=USD')).ms)
  record('QA-01', 'benchmarks bancarios responden (caché frío vs caliente)', cold.status === 200, `primera=${round(cold.ms)} ms, siguientes p50=${round(percentile(warm, 50))} ms, X-Data-Source=${cold.headers.get('x-data-source')}`)
}

async function qa02(client) {
  // Espera a que el circuito esté sano antes del experimento (otro experimento previo pudo dejarlo abierto).
  for (let i = 0; i < 20 && (await breaker()).state !== 'CLOSED'; i++) { await http('/banking/benchmarks?currency=PEN'); await sleep(1000) }
  const initial = await breaker()
  const live = await http('/banking/benchmarks?currency=PEN')
  record('QA-02', 'estado inicial CLOSED y datos LIVE del external-bank-mock', initial.state === 'CLOSED' && live.headers.get('x-data-source') === 'LIVE', `state=${initial.state}, X-Data-Source=${live.headers.get('x-data-source')}`)
  if (process.env.QA_SKIP_CHAOS === '1') { record('QA-02', 'caos omitido (QA_SKIP_CHAOS=1)', true, 'sin docker stop/start'); return }
  compose('stop', 'external-bank-mock')
  try {
    const sources = []
    for (let i = 0; i < 12; i++) { const response = await http('/banking/benchmarks?currency=PEN'); sources.push(response.headers.get('x-data-source')) }
    const opened = await breaker()
    const fast = []
    let fallbackBody
    for (let i = 0; i < 6; i++) { const response = await http('/banking/benchmarks?currency=PEN'); fast.push(response.ms); fallbackBody = response.body }
    const first = Array.isArray(fallbackBody) ? fallbackBody[0] : fallbackBody
    record('QA-02', 'external-bank-mock detenido → circuito OPEN', opened.state === 'OPEN', `state=${opened.state}, failureRate=${opened.failureRate}, failedCalls=${opened.failedCalls}`)
    record('QA-02', 'todas las respuestas degradan a la caché SBS (FALLBACK_CACHED, fallbackActive=true)', sources.every(source => source === 'FALLBACK_CACHED') && first?.fallbackActive === true, `X-Data-Source=${[...new Set(sources)].join(',')}, fallbackActive=${first?.fallbackActive}, asOfDate=${first?.asOfDate}`)
    record('QA-02', 'con el circuito abierto el fallback responde < 100 ms', percentile(fast, 95) < 100, `p95=${round(percentile(fast, 95))} ms (n=6)`)
    const simulation = await http('/simulations/calculate', { method: 'POST', token: client.token, body: { loanAmount: '100000.00', currency: 'PEN', termMonths: 120, annualInterestRate: '0.09', startDate: '2026-01-31' } })
    record('QA-02', 'el núcleo financiero sigue operativo durante la caída', simulation.status === 201, `POST /simulations/calculate → ${simulation.status}`)
  } finally {
    compose('start', 'external-bank-mock')
  }
  let recovered = false
  let states = []
  for (let i = 0; i < 30 && !recovered; i++) {
    await sleep(2000)
    const response = await http('/banking/benchmarks?currency=PEN')
    const state = (await breaker()).state
    states.push(state)
    recovered = state === 'CLOSED' && response.headers.get('x-data-source') === 'LIVE'
  }
  states = states.filter((state, index) => index === 0 || states[index - 1] !== state)
  record('QA-02', 'external-bank-mock reiniciado → HALF_OPEN → CLOSED con datos LIVE', recovered, `transiciones observadas: ${states.join(' → ')}`)
}

async function qa03(client, other, realtor) {
  const expect = async (name, call, allowed) => { const response = await call(); record('QA-03', name, allowed.includes(response.status), `HTTP ${response.status} (esperado ${allowed.join('/')})`) }
  const simulation = (await http('/simulations/calculate', { method: 'POST', token: client.token, body: { loanAmount: '120000.00', currency: 'PEN', termMonths: 24, annualInterestRate: '0.09', startDate: '2026-01-31' } })).body
  const property = (await http('/properties', { method: 'POST', token: realtor.token, body: { title: `QA ${randomUUID()}`, address: 'Lima', description: 'QA-03', price: '250000.00', currency: 'PEN', tags: ['qa03'] } })).body
  const offer = (await http('/offers', { method: 'POST', token: realtor.token, body: { recipientEmail: client.email, propertyId: property.id, bankCode: 'BCP', loanAmount: '200000.00', currency: 'PEN', annualInterestRate: '0.085', termMonths: 24, startDate: '2026-01-31' } })).body
  await expect('anónimo no lista simulaciones', () => http('/simulations'), [401])
  await expect('JWT con firma inválida es rechazado', () => http('/simulations', { token: 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.firma-invalida' }), [401])
  await expect('BOLA: simulación ajena', () => http(`/simulations/${simulation.quotationId}`, { token: other.token }), [404])
  await expect('BOLA: exportación XLSX de simulación ajena', () => http(`/simulations/${simulation.quotationId}/export/xlsx`, { token: other.token, raw: true }), [404])
  await expect('BOLA: oferta ajena', () => http(`/offers/${offer.id}`, { token: other.token }), [404])
  await expect('BOLA: versiones de oferta ajena (US21)', () => http(`/offers/${offer.id}/versions`, { token: other.token }), [404])
  await expect('BOLA: PDF de oferta ajena (US22)', () => http(`/offers/${offer.id}/schedule/export/pdf`, { token: other.token, raw: true }), [404])
  await expect('BOLA: historial de exportaciones ajeno (US22)', () => http(`/offers/${offer.id}/exports`, { token: other.token }), [404])
  await expect('BFLA: un cliente no emite versiones de cotización', () => http(`/offers/${offer.id}/versions`, { method: 'POST', token: client.token, body: { termMonths: 12 } }), [403])
  await expect('BFLA: un cliente no emite ofertas', () => http('/offers', { method: 'POST', token: client.token, body: { recipientEmail: other.email, propertyId: property.id, bankCode: 'BCP', loanAmount: '1000.00', currency: 'PEN', annualInterestRate: '0.08', termMonths: 12, startDate: '2026-01-31' } }), [403])
  let notices
  for (let i = 0; i < 40; i++) { notices = (await http('/notifications', { token: client.token })).body; if (notices?.items?.length) break; await sleep(500) }
  record('QA-03', 'evento asíncrono entregado (outbox → RabbitMQ → inbox)', Boolean(notices?.items?.length), `${notices?.items?.length || 0} notificación(es) del destinatario`)
  if (notices?.items?.length) await expect('BOLA: marcar como leída la notificación ajena', () => http(`/notifications/${notices.items[0].id}/read`, { method: 'PATCH', token: other.token }), [404])
  await expect('la API interna no se expone por el gateway', () => http(`/internal/accounts/${client.id}`, { token: client.token }), [404])
  await expect('la API interna no se alcanza con path traversal', () => http(`/simulations/..%2Finternal%2Faccounts%2F${client.id}`, { token: client.token }), [400, 404])
  await expect('la API interna rechaza llamadas sin X-Internal-Token incluso directo al servicio', () => http(`/api/v1/internal/accounts/${client.id}`, { base: identity }), [401, 404])
  await expect('la API interna rechaza un X-Internal-Token incorrecto directo al servicio', () => http(`/api/v1/internal/accounts/${client.id}`, { base: identity, token: client.token, headers: { 'X-Internal-Token': 'incorrecto' } }), [404])
  const session = await http('/auth/login', { method: 'POST', body: { email: other.email, password: 'contraseña-incorrecta' } })
  record('QA-03', 'credenciales inválidas → 401 sin filtrar detalles', session.status === 401, `HTTP ${session.status}`)
  const revoked = await account('Revoked')
  await http('/auth/logout', { method: 'POST', token: revoked.token })
  await sleep(2500) // caché de revocación del gateway: 2 s
  await expect('token revocado tras logout es rechazado en el gateway', () => http('/users/me', { token: revoked.token }), [401])
}

try {
  if (!fixtureToken) throw new Error('Define E2E_FIXTURE_TOKEN (mismo valor con el que se levantó docker compose).')
  const health = await http('/actuator/health', { base: gateway })
  if (health.status !== 200) throw new Error(`API Gateway no disponible en ${gateway} (HTTP ${health.status}).`)
  const client = await account('Client')
  const other = await account('Other')
  const realtor = await account('Realtor', { realtor: true })
  console.log(`QA: API ${api} · pricing ${pricing} · identity ${identity}\n`)
  await qa01(client)
  await qa02(client)
  await qa03(client, other, realtor)
} catch (error) {
  record('QA', 'ejecución', false, error.message)
}
const failed = checks.filter(check => !check.ok)
console.log(`\nResumen QA: ${checks.length - failed.length}/${checks.length} comprobaciones PASS`)
for (const qa of ['QA-01', 'QA-02', 'QA-03']) { const own = checks.filter(check => check.qa === qa); console.log(`  ${qa}: ${own.filter(check => check.ok).length}/${own.length}`) }
process.exitCode = failed.length ? 1 : 0
