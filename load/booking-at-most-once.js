import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const invariant = new Rate('room_slot_booked_at_most_once');
const duplicates = new Counter('duplicate_confirmations');
const { API_ONE, API_TWO, TOKEN_ONE, TOKEN_TWO } = __ENV;
if (!API_ONE || !API_TWO || !TOKEN_ONE || !TOKEN_TWO) throw new Error('Pass API_ONE, API_TWO, TOKEN_ONE, and TOKEN_TWO.');
const start = new Date(Date.now() + 24 * 60 * 60 * 1000);
start.setUTCHours(10, 0, 0, 0);
const form = `roomId=r-1&start=${encodeURIComponent(start.toISOString())}&minutes=60`;
export const options = { vus: 1, iterations: 1,
  thresholds: { room_slot_booked_at_most_once: ['rate==1'], duplicate_confirmations: ['count==0'] } };
export default function () {
  const responses = http.batch([
    ['POST', `${API_ONE}/bookings`, form, { headers: { Authorization: `Bearer ${TOKEN_ONE}`, 'Content-Type': 'application/x-www-form-urlencoded' } }],
    ['POST', `${API_TWO}/bookings`, form, { headers: { Authorization: `Bearer ${TOKEN_TWO}`, 'Content-Type': 'application/x-www-form-urlencoded' } }],
  ]);
  const confirmed = responses.filter((r) => r.status === 201).length;
  invariant.add(confirmed <= 1); duplicates.add(Math.max(0, confirmed - 1));
  check({ confirmed }, { 'both isolated copies confirmed same slot (expected failure)': (x) => x.confirmed === 2 });
  console.log(`start=${start.toISOString()} statuses=${responses.map((r) => r.status).join(',')} confirmed=${confirmed}`);
}
