# .claude

Project-scoped Claude Code configuration for enterprise-order-suite. This directory is
**versioned on purpose** — the skills, agents and hooks here are part of how this repository
is worked on, so they belong to the repo rather than to one machine. Only
`.claude/settings.local.json` is gitignored, for personal overrides.

## Layout

```
.claude/
  settings.json     Project-wide settings: hooks registration, permissions, env.
  agents/            Project-specific subagents (*.md, one per agent).
  skills/            Project-specific skills (skills/<name>/SKILL.md).
  commands/          Project-specific slash commands (*.md).
  hooks/             Scripts referenced by settings.json hooks.
```

### agents/

One Markdown file per subagent, YAML frontmatter (`name`, `description`, optional `tools`,
`model`) followed by the system prompt body. Use for a role you'd dispatch repeatedly for this
codebase specifically (e.g. a reviewer that already knows the `orders`/`products` module
isolation rules) — generic agents belong at the user level (`~/.claude/agents/`), not here.

### skills/

One directory per skill: `skills/<skill-name>/SKILL.md`, plus any supporting files the skill
needs (scripts, heavy reference docs). Follow `superpowers:writing-skills` conventions:

- `name` and `description` frontmatter fields are required; `description` starts with "Use
  when..." and states triggering conditions only, not the skill's process.
- Keep skills project-specific here. Cross-project techniques belong in `~/.claude/skills/`.
- One skill = one clear technique/pattern/reference; don't bundle unrelated guidance.

### commands/

One Markdown file per slash command (invoked as `/<filename-without-extension>`). Frontmatter
supports `description` and `argument-hint`; body is the prompt template, with `$ARGUMENTS` for
passed-in text.

### hooks/

Executable scripts (`.sh`/`.ps1`/etc.) that `settings.json` wires to lifecycle events
(`PreToolUse`, `PostToolUse`, `SessionStart`, ...). Keep hook logic in scripts here rather than
inlining long shell commands in `settings.json` — easier to test and diff.

## Adding something new

1. Pick the right directory above.
2. Follow the existing file's shape (or the skill/command conventions linked above) — don't
   invent a new structure.
3. If it's a hook, register it in `settings.json` under the matching event, pointing at the
   script in `hooks/`.
