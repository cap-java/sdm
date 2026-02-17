# Self-Healing CI/CD Pipeline

This repository includes a self-healing CI/CD pipeline that automatically monitors, analyzes, and responds to workflow failures.

## Overview

The self-healing agent:

1. **Monitors** all workflow runs for failures
2. **Analyzes** failure logs to determine root cause
3. **Classifies** failures into categories (code, workflow, infrastructure, quality gate)
4. **Diagnoses** issues with specific error details and recommendations
5. **Takes action** based on configuration (assist mode or auto-fix mode)

## Files

```
.github/
├── self-healing-config.yml    # Configuration file
├── workflows/
│   └── self-healing.yml       # Main workflow that monitors failures
└── scripts/
    ├── analyze-failure.js     # Failure analysis script
    └── apply-fix.js           # Auto-fix script
```

## Configuration

Edit `.github/self-healing-config.yml` to customize behavior:

### Operating Modes

- **`assist`** (default): Creates issues/comments with diagnosis and proposed fixes. Does not commit changes automatically.
- **`auto-fix`**: Automatically creates PRs with fixes for certain failure types.

```yaml
mode: "assist"  # or "auto-fix"
```

### Failure Classifications

The agent classifies failures into four categories:

| Classification | Description | Auto-retry | Auto-fix |
|---------------|-------------|------------|----------|
| `code` | Test failures, compilation errors, linting issues | No | No |
| `workflow` | YAML issues, action versions, permissions | No | Yes* |
| `infrastructure` | Timeouts, rate limits, network issues | Yes | No |
| `quality_gate` | SonarQube, coverage thresholds | No | No |

\* Only in `auto-fix` mode

### Retry Configuration

```yaml
retry:
  enabled: true
  max_attempts: 2
  delay_minutes: 1
  auto_retry_types:
    - "infrastructure"
```

### Guardrails

```yaml
guardrails:
  max_prs_per_day: 5
  max_issues_per_day: 10
  require_approval: true
  protected_files:
    - ".github/self-healing-config.yml"
    - "CODEOWNERS"
  max_lines_changed: 50
```

## How It Works

### 1. Trigger

The self-healing workflow triggers on `workflow_run` events when any workflow completes with a failure:

```yaml
on:
  workflow_run:
    workflows: ["*"]
    types: [completed]
```

### 2. Analysis

When a failure is detected:

1. Downloads the failed job logs via GitHub CLI
2. Extracts error messages and failed steps
3. Matches error patterns to classify the failure
4. Generates a diagnosis with recommendations

### 3. Actions

Based on classification and configuration:

| Classification | Assist Mode | Auto-Fix Mode |
|---------------|-------------|---------------|
| Code | Create issue | Create issue |
| Workflow | Create issue | Create PR with fix |
| Infrastructure | Retry + Issue if persists | Retry + Issue |
| Quality Gate | Create issue | Create issue |

### 4. Issue Creation

Issues are created with:
- Failure classification
- Failed job/step details
- Key error lines from logs
- Specific recommendations
- Links to workflow run

### 5. PR Creation (Auto-Fix Mode)

For workflow issues, the agent can automatically:
- Update deprecated action versions
- Add missing permissions
- Remove invalid inputs

## Supported Auto-Fixes

| Issue | Fix Applied |
|-------|------------|
| Deprecated `actions/checkout@v2/v3` | Update to `v4` |
| Deprecated `actions/setup-node@v2/v3` | Update to `v4` |
| Deprecated `actions/setup-java@v2/v3` | Update to `v4` |
| Missing permissions | Add permissions block |
| Unexpected action inputs | Remove invalid inputs |

## Labels

The agent uses these labels:
- `ci-failure` - All CI failure issues
- `self-healing` - Issues created by self-healing agent
- `auto-fix` - PRs created automatically

## Customization

### Adding New Failure Patterns

Edit `self-healing-config.yml`:

```yaml
classification:
  code:
    patterns:
      - "your-custom-pattern"
      - "another-pattern"
```

### Adding New Auto-Fixes

Edit `.github/scripts/apply-fix.js` to add new fix patterns:

```javascript
const fixPatterns = {
  'my-fix': {
    patterns: [/my error pattern/i],
    apply: myFixFunction
  }
};
```

## Permissions Required

The self-healing workflow requires:

```yaml
permissions:
  contents: write      # For creating branches/commits
  pull-requests: write # For creating PRs
  issues: write        # For creating issues
  actions: read        # For reading workflow logs
```

## Troubleshooting

### Workflow not triggering

- Ensure the workflow file is in the default branch
- Check that `workflow_run` permissions are enabled
- Verify no syntax errors in workflow file

### Issues not being created

- Check `GITHUB_TOKEN` permissions
- Verify label existence or creation permissions
- Check rate limits

### Auto-fixes not applying

- Ensure mode is set to `auto-fix`
- Verify the failure type has `auto_fix_enabled: true`
- Check that files aren't in `protected_files` list

## Security Considerations

- The agent runs with repository permissions
- Auto-fixes are limited to workflow files by default
- Protected files cannot be modified
- All PRs require human approval before merge
- Secrets are never exposed in logs or issues
