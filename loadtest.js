// Load test for the rate-limited demo service (run with k6, see run-loadtest.cmd).
//
// It runs two scenarios against /api/hello, which is configured as a Token Bucket
// with capacity 5 and a refill of 1 token per second (see application.yml):
//
//   1. single_client (20s): ONE API key sends 50 requests/second, spread across all
//      three instances. The most it can legitimately get is the starting burst plus
//      what refills: 5 + 20 x 1 = about 25 allowed out of ~1000.
//      If each instance kept its own count, the total would be about 3x that.
//
//   2. many_clients (10s): TEN different API keys each send about 3 requests/second.
//      Each key has its own bucket, so each gets about 14 allowed (the burst of 5 plus
//      about 9 refills), roughly 140 in total. One noisy client must not use up anyone
//      else's allowance.
//
// Expected numbers assume the default limits in application.yml. If you change them,
// change the bounds in `thresholds` and `EXPECT` below.

import http from 'k6/http';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

// 200 (allowed) and 429 (rate limited) are both "expected"; anything else counts as a failure.
http.setResponseCallback(http.expectedStatuses(200, 429));

const TARGETS = (__ENV.TARGETS || 'http://app1:8080,http://app2:8080,http://app3:8080').split(',');
const KEY = __ENV.API_KEY || 'loadtest';

const allowedSingle = new Counter('allowed_single');
const rejectedSingle = new Counter('rejected_single');
const allowedMany = new Counter('allowed_many');
const rejectedMany = new Counter('rejected_many');
const unexpected = new Counter('unexpected_status');

// Allowed ranges (a little slack on both sides for timing).
const EXPECT = {
  single: { min: 20, max: 30 },    // about 5 + 20 = 25
  many: { min: 120, max: 170 },    // about 10 clients x 14 = 140
};

export const options = {
  scenarios: {
    single_client: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '20s',
      preAllocatedVUs: 20,
      maxVUs: 50,
      exec: 'singleClient',
    },
    many_clients: {
      executor: 'constant-vus',
      vus: 10,
      duration: '10s',
      startTime: '25s',            // after scenario 1 has finished
      exec: 'manyClients',
    },
  },
  thresholds: {
    allowed_single: [`count>=${EXPECT.single.min}`, `count<=${EXPECT.single.max}`],
    allowed_many: [`count>=${EXPECT.many.min}`, `count<=${EXPECT.many.max}`],
    unexpected_status: ['count==0'],
  },
};

function record(res, allowed, rejected) {
  if (res.status === 200) {
    allowed.add(1);
  } else if (res.status === 429) {
    rejected.add(1);
  } else {
    unexpected.add(1);
  }
}

// Scenario 1: one client, rotating over the instances.
export function singleClient() {
  const base = TARGETS[exec.scenario.iterationInTest % TARGETS.length];
  const res = http.get(`${base}/api/hello`, { headers: { 'X-API-Key': KEY } });
  record(res, allowedSingle, rejectedSingle);
}

// Scenario 2: each virtual user is its own client with its own API key.
export function manyClients() {
  const base = TARGETS[exec.vu.iterationInScenario % TARGETS.length];
  const res = http.get(`${base}/api/hello`, {
    headers: { 'X-API-Key': `${KEY}-client-${exec.vu.idInTest}` },
  });
  record(res, allowedMany, rejectedMany);
  sleep(0.33);   // about 3 requests per second per client
}

function count(data, name) {
  const metric = data.metrics[name];
  return metric && metric.values.count ? metric.values.count : 0;
}

function verdict(ok) {
  return ok ? 'PASS' : 'FAIL';
}

export function handleSummary(data) {
  const aS = count(data, 'allowed_single');
  const rS = count(data, 'rejected_single');
  const aM = count(data, 'allowed_many');
  const rM = count(data, 'rejected_many');
  const bad = count(data, 'unexpected_status');
  const dur = data.metrics.http_req_duration.values;
  const total = count(data, 'http_reqs');

  const okSingle = aS >= EXPECT.single.min && aS <= EXPECT.single.max;
  const okMany = aM >= EXPECT.many.min && aM <= EXPECT.many.max;

  const lines = [
    '',
    '==================== LOAD TEST RESULT ====================',
    `Total requests:  ${total}  (${data.metrics.http_reqs.values.rate.toFixed(1)} per second on average)`,
    `Latency:         avg ${dur.avg.toFixed(1)} ms   p95 ${dur['p(95)'].toFixed(1)} ms   max ${dur.max.toFixed(1)} ms`,
    `Unexpected:      ${bad} responses that were neither 200 nor 429`,
    '',
    'Scenario 1 - ONE client, 50 req/s, spread over 3 instances, 20 s',
    `  allowed  ${aS}   rejected ${rS}`,
    `  expected allowed: ${EXPECT.single.min}-${EXPECT.single.max} (burst 5 + refill 1/s x 20 s = about 25)`,
    `  -> ${verdict(okSingle)}   (per-instance counting would give about ${3 * 25}, not ${aS})`,
    '',
    'Scenario 2 - TEN clients, about 3 req/s each, 10 s',
    `  allowed  ${aM}   rejected ${rM}`,
    `  expected allowed: ${EXPECT.many.min}-${EXPECT.many.max} (10 clients x about 14 = roughly 140)`,
    `  -> ${verdict(okMany)}   (each client has its own bucket)`,
    '',
    `OVERALL: ${verdict(okSingle && okMany && bad === 0)}`,
    '==========================================================',
    '',
  ];
  return { stdout: lines.join('\n') };
}
