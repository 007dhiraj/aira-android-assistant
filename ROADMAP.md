# Aira — Development Roadmap

## Active focus
Verify current Android baseline and build a reliable command flow on-device.

## Milestones
1. **Reproducible build** — verify Gradle and GitHub Actions debug APK.
2. **Voice command core** — Hindi/English recognition, command routing, speech responses, clear permission/error states.
3. **App actions** — open supported apps, prepare SMS, dialer handoff, alarms and device info; clearly explain actions requiring user confirmation.
4. **WhatsApp & accessibility** — investigate safe, user-consented hand-off and Android accessibility constraints; never silently send messages.
5. **Wake/background** — evaluate foreground-service, battery and microphone restrictions; test device-specific behavior.
6. **Voice personalization/security** — assess feasible enrollment and anti-spoofing limits; do not claim voice biometrics as security until validated.
7. **Phone QA** — test common commands, noisy environments, permissions, screen-off constraints and battery impact.

## Truthful current baseline
The README lists a basic native assistant. Screen-off wake-word, WhatsApp automation and owner voice verification are not complete. Mark features complete only after device testing.
