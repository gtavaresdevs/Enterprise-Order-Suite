#Requires -Version 7
# PreToolUse hook: warn before editing a security-sensitive file.
# Reads the tool call as JSON on stdin; emits additionalContext when the path matches.
# Always exits 0 - this hook advises, it never blocks.

$ErrorActionPreference = 'Stop'

$raw = [Console]::In.ReadToEnd()
if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }

try { $payload = $raw | ConvertFrom-Json } catch { exit 0 }

$path = $payload.tool_input.file_path
if ([string]::IsNullOrWhiteSpace($path)) { exit 0 }

$normalized = ($path -replace '\\', '/')

$patterns = @(
    '/security/',
    '/auth/',
    'SecurityConfig',
    'JwtAuthenticationFilter',
    'JwtService',
    'SecurityFilterChain',
    'RateLimit',
    'RefreshToken',
    'PasswordReset'
)

$matched = $false
foreach ($p in $patterns) {
    if ($normalized -like "*$p*") { $matched = $true; break }
}

if (-not $matched) { exit 0 }

$message = @'
SECURITY-SENSITIVE FILE. Before making this edit:

1. Invoke the `spring-security-changes` skill and follow it.
2. Ask the user detailed questions before changing authorization, token handling
   or rate limiting. Ask in the user's language; implement in English.
3. Authorization belongs in @PreAuthorize, never inside a method body. A manual
   getAuthorities() check bypasses the role hierarchy - see the isAdmin() defect
   documented in the skill.
4. Ship a test for BOTH the allowed and the denied path. Include a SUPER_ADMIN
   case whenever role handling is involved.
'@

$out = [ordered]@{
    hookSpecificOutput = [ordered]@{
        hookEventName     = 'PreToolUse'
        additionalContext = $message
    }
}

$out | ConvertTo-Json -Depth 5 -Compress
exit 0
