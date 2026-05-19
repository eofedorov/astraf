# AGENTS.md

## Project Goal

This app is a personal Android cycling computer. It is built only for the owner's use and is not intended for Google Play distribution or broad public release.

## Product Priorities

- Treat battery life as a core product requirement. Prefer battery-friendly designs for GPS, sensors, background work, rendering, networking, storage, and wakeups.
- Optimize for a smooth cycling-computer experience: reliable ride tracking, readable UI, fast startup, stable recording, and low overhead during long rides.
- Keep the implementation simple and direct. Avoid product, compliance, analytics, monetization, account, or distribution complexity that only matters for public apps.

## Platform Scope

- Target only Android 16+.
- Target only Pixel 8 and newer Google phones.
- Do not spend effort on device-specific compatibility layers, old Android versions, vendor quirks, or broad fallback behavior unless it is directly useful on the target Pixel devices.

## Dependencies And APIs

- Prefer latest stable libraries, frameworks, SDKs, and tooling.
- It is acceptable to choose latest pre-stable or experimental APIs when they significantly simplify the implementation or improve UX, performance, reliability, or battery life.
- When choosing dependencies, prefer well-maintained modern options over legacy compatibility packages.

## Development Phase

- The project is in active development. Backward compatibility is not required.
- Data formats, persistence schemas, APIs, contracts, configuration, and internal architecture may be changed or broken freely when that produces a cleaner result.
- Do not add migrations, compatibility shims, or legacy fallbacks unless explicitly requested.

## Agent Guidance

- Make choices for the actual target device and use case, not for a hypothetical public Android app.
- Prefer clear, maintainable code and strong runtime behavior over preserving unfinished branch behavior.
- Before adding abstractions, fallbacks, or compatibility code, check whether they serve the personal-use Pixel 8+ Android 16+ goal.
