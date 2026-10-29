import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';
import exec from 'k6/execution';

const works = new Rate('token_works_on_every_copy');
const token = __ENV.TOKEN;
if (!token) throw new Error('Pass TOKEN from one API copy.');
export const options = { vus: 1, iterations: 20, noConnectionReuse: true,
  thresholds: { token_works_on_every_copy: ['rate==1'] } };
export default function () {
  const r = http.get(`${__ENV.PROXY_URL || 'http://proxy'}/bookings`,
    { headers: { Authorization: `Bearer ${token}` } });
  const ok = check(r, { 'token works on answering copy': (x) => x.status === 200 });
  works.add(ok);
  console.log(`request=${exec.vu.iterationInInstance + 1} status=${r.status} upstream=${r.headers['X-Upstream'] || 'missing'}`);
}
