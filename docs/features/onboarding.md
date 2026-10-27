# Onboarding

Multi-step first-run flow. Completion is one stored flag. The module is the onboarding feature.

Key files: the onboarding screen and view model in `:feature:onboarding:impl`.

## Steps

The flow is one route. A local flag inside it switches between two screens. There is no separate welcome screen: the header is the welcome. Back on the path picker returns to the permission screen.

Screen 1 asks for three things, in order:

1. Fine location permission.
2. Overlay permission.
3. Mock location, set through the system developer settings.

Before any of these, on a fresh install, the accessibility disclosure is shown on every API level. It is shown until the user answers. Accept, Decline and Skip each record an answer, so it never returns. The disclosure's full rules are in [tap-to-walk.md](tap-to-walk.md), "Prominent Disclosure". Installs that already finished onboarding never see it.

Screen 2 is the path picker. It has no skip, and finishing it is what marks onboarding complete.

- Choose a guided path. Default is preselected. The paths and what they hide are in [guided-paths.md](guided-paths.md).
- An optional Tap to Walk card is shown on paths that use Tap to Walk. It is not required to continue. Its disclosure can also appear here as a fallback, if the user did not answer on screen 1.

The progress label counts granted permissions on screen 1. The picker always shows the last step.

A language choice sits in the header of both screens, outside the step count. See [localization.md](localization.md).

## Permission checks

Each permission has its own check: the platform permission check for location, the overlay check for overlays, and the app-ops check for mock location.

There is no manual "check again" button. The screen re-runs every check when it resumes, so returning from system settings updates every card without a tap.

## Mock location skip

Some mock-location apps are not detected by the app-ops check, so the check reports "not allowed" even though spoofing works. The mock step therefore has a skip action, behind a warning that most devices need the check to pass.

The skip is an onboarding-only action. There is no setting to turn the check back on, short of a fresh install or a full reset ([export-import.md](export-import.md)).

The skip is stored and honoured in two places. The onboarding checks treat the step as done. The navigation gate also reads it, so a user who skipped is never sent back to onboarding.

## Revoking permissions after onboarding

Once onboarding is complete, revoking the overlay permission does not send the user back through onboarding. Only location and mock location still gate the app. The joystick and widget stay unavailable until the overlay permission returns, and everything else keeps working. Revoking location does send the user back, because nothing works without it.

## Navigation gate

The gate is one pure function: the app is reachable only when core permissions are granted and onboarding is complete. A first-run user cannot jump past the path picker by granting permissions, because only finishing the picker sets the flag.

A cold start with everything already granted still shows onboarding until the picker is finished. Installs that already finished it go straight to the main screen.

## Edge cases

- Only the mock location step can be skipped. Location and overlay must be granted. After a permanent location denial, Grant opens the app settings page, because Android no longer shows the dialog. A banner shows when a required permission is still missing.
- Permissions are re-checked on resume.
