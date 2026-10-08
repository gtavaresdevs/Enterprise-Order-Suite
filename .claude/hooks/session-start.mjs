#!/usr/bin/env node
// SessionStart hook: check that this session can run the full verification (`./gradlew test`,
// which needs JDK 17+ and a running Docker daemon for Testcontainers) and say what is missing.
// stdout is added to the session context. Always exits 0: it reports, it never blocks.
//
// In Claude Code cloud sessions (CLAUDE_CODE_REMOTE=true) it also starts the Docker daemon,
// which a fresh Linux container has installed but not running (ADR-0015, register Q-77).
// On a developer machine it changes nothing.

import { spawn, spawnSync } from 'node:child_process';
import { openSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const remote = process.env.CLAUDE_CODE_REMOTE === 'true';
const lines = [];

function run(cmd, args) {
  const r = spawnSync(cmd, args, { encoding: 'utf8', timeout: 15000 });
  return { ok: r.status === 0, out: `${r.stdout || ''}${r.stderr || ''}` };
}

async function dockerReady() {
  return run('docker', ['info']).ok;
}

async function startDocker() {
  const log = openSync(join(tmpdir(), 'dockerd.log'), 'a');
  spawn('dockerd', [], { detached: true, stdio: ['ignore', log, log] }).unref();
  for (let i = 0; i < 30; i++) {
    if (await dockerReady()) return true;
    await new Promise((r) => setTimeout(r, 1000));
  }
  return false;
}

// 1. JDK
const java = run('java', ['-version']);
const major = Number((java.out.match(/version "(\d+)/) || [])[1]);
if (!java.ok || !major) lines.push('JDK: not found. `./gradlew` needs JDK 17 or newer.');
else if (major < 17) lines.push(`JDK: ${major} found; the build needs 17 or newer.`);
else lines.push(`JDK: ${major}.`);

// 2. Docker for Testcontainers
if (!run('docker', ['--version']).ok) {
  lines.push('Docker: not installed. Only unit tests (*Test) can run; integration tests (*IT) need Docker. CI runs the full suite.');
} else if (await dockerReady()) {
  lines.push('Docker: running. `./gradlew test` runs the full suite.');
} else if (remote && run('which', ['dockerd']).ok && process.getuid?.() === 0) {
  lines.push(
    (await startDocker())
      ? 'Docker: started the daemon. `./gradlew test` runs the full suite.'
      : 'Docker: the daemon did not start (see dockerd.log in the temp dir). Integration tests (*IT) will fail; CI runs the full suite.',
  );
} else {
  lines.push('Docker: installed but the daemon is not running. Start it before `./gradlew test`; integration tests (*IT) need it.');
}

process.stdout.write(`Backend environment check (.claude/hooks/session-start.mjs):\n- ${lines.join('\n- ')}\n`);
