#!/usr/bin/env node
// SessionStart hook: check that this session can run the full verification (`./gradlew test`,
// which needs JDK 17+ and a running Docker daemon for Testcontainers) and say what is missing.
// stdout is added to the session context. Always exits 0: it reports, it never blocks.
//
// In Claude Code cloud sessions (CLAUDE_CODE_REMOTE=true) it also repairs the two known gaps
// of a fresh Linux container (ADR-0015, register Q-77):
//   - the committed gradle.properties pins a Windows JDK path (org.gradle.java.home), so it
//     writes the local JDK into the Gradle user-home gradle.properties, which takes precedence;
//   - the Docker daemon is installed but not running, so it starts it.
// On a developer machine it changes nothing.

import { spawn, spawnSync } from 'node:child_process';
import { appendFileSync, existsSync, mkdirSync, openSync, readFileSync, realpathSync } from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { dirname, join } from 'node:path';

const projectDir = process.env.CLAUDE_PROJECT_DIR || process.cwd();
const remote = process.env.CLAUDE_CODE_REMOTE === 'true';
const lines = [];

function run(cmd, args) {
  const r = spawnSync(cmd, args, { encoding: 'utf8', timeout: 15000 });
  return { ok: r.status === 0, out: `${r.stdout || ''}${r.stderr || ''}` };
}

function readProp(file, key) {
  if (!existsSync(file)) return null;
  const line = readFileSync(file, 'utf8')
    .split(/\r?\n/)
    .find((l) => l.trim().startsWith(`${key}=`));
  return line ? line.slice(line.indexOf('=') + 1).trim().replace(/\\\\/g, '\\') : null;
}

function localJavaHome() {
  if (process.env.JAVA_HOME && existsSync(process.env.JAVA_HOME)) return process.env.JAVA_HOME;
  const which = run('which', ['java']);
  if (!which.ok) return null;
  // <home>/bin/java -> <home>
  return dirname(dirname(realpathSync(which.out.trim())));
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

// 2. org.gradle.java.home from the committed gradle.properties
const pinned = readProp(join(projectDir, 'gradle.properties'), 'org.gradle.java.home');
if (pinned && !existsSync(pinned)) {
  const userProps = join(process.env.GRADLE_USER_HOME || join(homedir(), '.gradle'), 'gradle.properties');
  const home = localJavaHome();
  if (readProp(userProps, 'org.gradle.java.home')) {
    lines.push(`Gradle: the committed JDK path does not exist here; ${userProps} overrides it.`);
  } else if (remote && home) {
    mkdirSync(dirname(userProps), { recursive: true });
    appendFileSync(userProps, `\norg.gradle.java.home=${home}\n`);
    lines.push(`Gradle: the committed JDK path does not exist here; set org.gradle.java.home=${home} in ${userProps}.`);
  } else {
    lines.push(
      'Gradle: the committed gradle.properties pins a JDK path that does not exist here. ' +
        'Pass -Dorg.gradle.java.home=<your JDK> or set it in your Gradle user-home gradle.properties.',
    );
  }
}

// 3. Docker for Testcontainers
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
