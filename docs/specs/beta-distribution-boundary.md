# Beta distribution boundary — proposed, not active

Base inspected: `4979a8dbf5a6186a272b8703bb154c9a976a52db` on beta, 2026-10-11.

## Resulting behavior

The candidate Android beta workflow builds, verifies the existing signing certificate and uploads a GitHub artifact on beta push or manual dispatch. It does not check/load Firebase credentials or invoke App Distribution. Automatic merges and pushes therefore reach only the build job in this candidate workflow. No new release-dispatch input, model-controlled shell or approval boolean is introduced.

The offline regression scans all ten current Actions workflows for release credential references/publisher entrypoints, verifies build/signature/artifact preservation and readonly repository permissions. It is a source regression, not an authorization boundary against a future malicious workflow.

## Required activation transaction — owner approval first

Removing one publisher is insufficient while GitHub Actions retains a valid Firebase release credential. Before enabling autonomous merges/releases, the authorized administrator/operator must:

1. Inspect queued/running jobs; stop any run that could distribute, including manual runs from old refs. Record results without exposing secrets.
2. Revoke the legacy Firebase service-account key used by Actions at the provider, then remove its copies from repository, environment and organization secrets accessible to this repository. Merely removing YAML references or deleting one secret name does not revoke leaked/cached copies. Do not delete the account or widen IAM blindly; inventory other consumers first and preserve unrelated capabilities.
3. Audit other Firebase release authority available to Actions, including workload identity federation, environment credentials and self-hosted runner state. GitHub contents:read cannot restrict repository secret use. Environment approvals alone do not prevent a workflow from using a repository-level key.
4. Merge this exact reviewed candidate only after owner approval. In-flight/old-ref workflows must no longer possess valid publication authority. Recheck beta SHA, workflow body, active runs and credential revocation evidence.
5. Enroll a separate minimal release identity exclusively in the trusted Windows operator after its capability/security approval. Keep it outside Actions, model-readable files, Telegram, logs and Git. No credential provisioning is part of this PR.

This session has repository content access but no authorized credential-administration or Windows channel; none of the above actions occurred. The protection is **not active** until all required checks are evidenced. No push/merge to beta is allowed from this task without the owner's explicit activation approval.

## Eventual approved release

Use the immutable manifest and single-use request contract in personal-ai-stack PR #41/#42. The broker retrieves evidence by opaque request ID only and binds repository/commit, exact APK SHA-256, expected signer, package, versionCode/versionName, exact Firebase app, explicit approved testers and evidence digest. Revalidate immediately before consumption. Firebase upload and tester distribution need separate durable states and read-only crash reconciliation; never retry an ambiguous external effect blindly. First real distribution needs the owner's specific exact-release approval after simulated provider crash/duplicate tests and live Telegram acceptance.

## Executed checks / limits

- Three offline source regressions: RED on original publisher, GREEN after removal.
- All ten workflow YAMLs parsed in cloud; beta has only the build job, push/manual triggers and contents:read.
- No Android build, full Ludo regressions, Actions execution, Firebase admin check, credential revocation, upload/distribution or Windows validation was performed. No app source/dependency/version/data change.
- beta remains unchanged. This document and workflow are proposed on a separate Draft PR.
