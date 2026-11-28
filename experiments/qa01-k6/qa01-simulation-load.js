import http from 'k6/http';
import { check } from 'k6';

// Experimento QA-01 (ASR-PERF): Rendimiento y Exactitud Determinista del Cálculo Financiero
// Ejecución vía Docker:
//   docker run --rm --add-host=host.docker.internal:host-gateway -e BASE_URL=http://host.docker.internal:8080/api/v1 -e JWT=$JWT -v "${PWD}/experiments/qa01-k6:/scripts" grafana/k6 run --summary-export=/scripts/qa01-k6-summary.json /scripts/qa01-simulation-load.js
export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate',
      rate: 5,
      timeUnit: '1s',
      duration: '15s',
      preAllocatedVUs: 10,
      maxVUs: 20,
    },
    sustained_qa01: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      startTime: '15s',
      duration: '60s',
      preAllocatedVUs: 25,
      maxVUs: 60,
    },
  },
  thresholds: {
    'http_req_duration{scenario:sustained_qa01}': ['p(95)<350', 'p(99)<800'],
    'http_req_failed{scenario:sustained_qa01}': ['rate<0.005'],
    'checks{scenario:sustained_qa01}': ['rate==1.0'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080/api/v1';
const JWT = __ENV.JWT;

const PAYLOAD = JSON.stringify({
  loanAmount: '350000.00',
  propertyValue: '450000.00',
  currency: 'PEN',
  termMonths: 360,
  annualInterestRate: '0.0925',
  rateType: 'TEA',
  startDate: '2026-01-31',
  gracePeriodType: 'TOTAL',
  gracePeriodMonths: 6,
  balloonPaymentAmount: '50000.00',
  balloonPaymentMonth: 360,
  lifeInsuranceRate: '0.00028',
  propertyInsuranceRate: '0.00025',
  monthlyAdminFee: '10.00',
  discountRateAnnual: '0.0850',
});

export default function () {
  const res = http.post(`${BASE_URL}/simulations/calculate`, PAYLOAD, {
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      Authorization: `Bearer ${JWT}`,
    },
  });

  let body = null;
  try {
    body = res.json();
  } catch (_) {
    body = null;
  }

  check(res, {
    'HTTP 201 Created': (r) => r.status === 201,
    '360 cuotas exactas': () => Array.isArray(body?.schedule) && body.schedule.length === 360,
    'Saldo final conciliado 0.00 (AC-02)': () => body?.schedule?.[359]?.finalBalance === '0.00',
    'TCEA presente sin notación científica': () => typeof body?.metrics?.tcea === 'string' && !body.metrics.tcea.includes('E'),
  });
}
