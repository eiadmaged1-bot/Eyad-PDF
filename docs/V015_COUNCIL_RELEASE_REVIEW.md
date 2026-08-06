# Eyad PDF v0.15 Council Release Review

Review basis: the v0.14 source branch, successful Android quality-gate artifact, and the physical tablet recording supplied on 2026-08-06.

## Council decision

Ship one consolidated v0.15 release candidate rather than multiple visual APKs. Freeze the working PDF engines and limit changes to release blockers, consistency, localization and automated verification.

## UX and accessibility

Accepted the scroll-aware top bar. The large hero and search controls now leave the viewport naturally, preserving vertical space. Hard-coded English in the modern Tools and Files surfaces is replaced with English/Arabic resources.

## Android architecture and performance

No risky PDF-engine rewrite is included. Existing viewer, SAF and operation flows remain frozen for this candidate. Main-route cross-fades remain disabled to prevent the translucent overlap observed in the recording.

## Privacy and security

The open-source flavor remains offline and CI rejects any INTERNET permission. The release keystore must remain outside the public repository and be supplied only via protected Actions secrets.

## Release engineering

The inherited package ID and stale v0.12/v0.13 artifact names were release blockers. v0.15 uses `com.eyadpdf.android`, version `0.15.0`/`215`, dynamic artifact names, unit tests, lint, APK assembly, checksums and a separate signed-release workflow.

## QA gate

A candidate is releasable only when unit tests, lint, offline-manifest validation and APK assembly all pass on the final commit. A signed APK must also pass Android's `apksigner verify` before distribution.

## One unavoidable installation transition

Older debug builds use a different application ID/signing identity. Installing the first permanent v0.15 release may require one uninstall. Every later APK signed with the same protected key can update normally without uninstalling.
