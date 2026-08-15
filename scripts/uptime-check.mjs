#!/usr/bin/env node
/**
 * Uptime check for the live CareQ deployment.
 *
 * Runs every 15 minutes from .github/workflows/uptime-check.yml. Pings the
 * gateway health endpoint and the Vercel frontend. On failure it opens a
 * GitHub issue titled "⚠️ CareQ uptime" (if one isn't already open); on
 * recovery it comments and closes it. That's the free-tier alerting story —
 * a failed check becomes a notification in your GitHub inbox.
 *
 * Usage:
 *   GH_TOKEN=... GH_REPO=owner/repo \
 *   GATEWAY_URL=https://<gateway-fqdn> FRONTEND_URL=https://careq-frontend-eta.vercel.app \
 *   node scripts/uptime-check.mjs
 */
const GATEWAY = process.env.GATEWAY_URL || 'https://careq-api-gateway.azurecontainerapps.io';
const FRONTEND = process.env.FRONTEND_URL || 'https://careq-frontend-eta.vercel.app';
const GH_REPO = process.env.GH_REPO;
const GH_TOKEN = process.env.GH_TOKEN;
const ISSUE_TITLE = '⚠️ CareQ uptime';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function check(name, url) {
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 30000); // cold start can be slow
    const res = await fetch(url, { signal: controller.signal });
    clearTimeout(timer);
    return { ok: res.status >= 200 && res.status < 500, status: res.status };
  } catch {
    return { ok: false, status: 'unreachable' };
  }
}

async function gh(method, path, body) {
  if (!GH_TOKEN || !GH_REPO) return null;
  const res = await fetch(`https://api.github.com/repos/${GH_REPO}${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${GH_TOKEN}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'Content-Type': 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  return res.ok ? res.json() : null;
}

async function findOpenIssue() {
  const issues = await gh('GET', `/issues?state=open&per_page=50`);
  return (issues || []).find((i) => i.title === ISSUE_TITLE) || null;
}

async function main() {
  const [gw, fe] = await Promise.all([check('gateway', GATEWAY), check('frontend', FRONTEND)]);
  const up = gw.ok && fe.ok;
  console.log(`${new Date().toISOString()} gateway=${gw.status} frontend=${fe.status} → ${up ? 'UP' : 'DOWN'}`);

  const issue = await findOpenIssue();

  if (up) {
    if (issue) {
      await gh('POST', `/issues/${issue.number}/comments`, {
        body: `✅ CareQ is back up (gateway=${gw.status}, frontend=${fe.status}) at ${new Date().toISOString()}.`,
      });
      await gh('PATCH', `/issues/${issue.number}`, { state: 'closed' });
      console.log('Recovered — closed the uptime issue.');
    }
    return;
  }

  if (issue) {
    console.log(`Already reported in issue #${issue.number} — no duplicate.`);
    return;
  }

  const created = await gh('POST', '/issues', {
    title: ISSUE_TITLE,
    body: [
      '## 🚨 CareQ appears to be DOWN',
      '',
      `- Checked at: ${new Date().toISOString()}`,
      `- Gateway (${GATEWAY}): **${gw.status}**`,
      `- Frontend (${FRONTEND}): **${fe.status}**`,
      '',
      'Next steps:',
      '1. [ ] Check the latest CD run: https://github.com/actions',
      '2. [ ] Gateway health: `curl -s ' + GATEWAY + '/actuator/health`',
      '3. [ ] Container Apps → Logs in the Azure portal, or `docs/12_MONITORING.md`',
      '4. [ ] This issue auto-closes when the next check passes.',
    ].join('\n'),
  });
  console.log(created ? `Uptime issue created: #${created.number}` : 'Could not create issue (no GH_TOKEN/GH_REPO?).');
  // Give the pipeline a moment; exit non-zero so workflow notifications fire.
  await sleep(1000);
  process.exitCode = 1;
}

main().catch((err) => {
  console.error('uptime-check crashed:', err);
  process.exit(1);
});
