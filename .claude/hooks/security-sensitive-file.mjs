#!/usr/bin/env node
// PreToolUse hook: warn before editing a security-sensitive file.
// Reads the tool call as JSON on stdin; when the path matches it asks for confirmation
// (permissionDecision 'ask'), so the warning is shown BEFORE the edit is applied.
// additionalContext carries the same checklist into the model's context once approved.
// Always exits 0 - this hook advises and asks, it never denies.
// Node port of the former security-sensitive-file.ps1 (ADR-0015, register Q-73): runs on
// Windows, macOS and Linux cloud sessions with no dependency beyond Node.

const PATTERNS = [
  '/security/',
  '/auth/',
  'SecurityConfig',
  'JwtAuthenticationFilter',
  'JwtService',
  'SecurityFilterChain',
  'RateLimit',
  'RefreshToken',
  'PasswordReset',
];

const MESSAGE = `SECURITY-SENSITIVE FILE. Before making this edit:

1. Invoke the \`spring-security-changes\` skill and follow it.
2. Ask the user detailed questions before changing authorization, token handling
   or rate limiting. Ask in the user's language; implement in English.
3. Authorization belongs in @PreAuthorize, never inside a method body. A manual
   getAuthorities() check bypasses the role hierarchy - see the isAdmin() defect
   documented in the skill.
4. Ship a test for BOTH the allowed and the denied path. Include a SUPER_ADMIN
   case whenever role handling is involved.`;

let raw = '';
process.stdin.setEncoding('utf8');
process.stdin.on('data', (chunk) => {
  raw += chunk;
});
process.stdin.on('end', () => {
  try {
    if (!raw.trim()) return;
    const payload = JSON.parse(raw);
    const path = payload?.tool_input?.file_path;
    if (typeof path !== 'string' || !path.trim()) return;

    // Case-insensitive, like the PowerShell -like match it replaces.
    const normalized = path.replace(/\\/g, '/').toLowerCase();
    if (!PATTERNS.some((p) => normalized.includes(p.toLowerCase()))) return;

    process.stdout.write(
      JSON.stringify({
        hookSpecificOutput: {
          hookEventName: 'PreToolUse',
          permissionDecision: 'ask',
          permissionDecisionReason: MESSAGE,
          additionalContext: MESSAGE,
        },
      }),
    );
  } catch {
    // Malformed input: advise nothing, block nothing.
  }
});
