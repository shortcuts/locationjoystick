# ISSUES.md

This document references known issues for agents to pick them and iterate on

## List

### Discrepancy of constants

Settings have their constants spread everywhere in the codebase, e.g. AppPreferencesDataSource, ExportData, SettingsViewModel, MockLocationService and so on they all define jitter settings etc. We want A SINGLE SOURCE OF TRUTH FILE for all of the constants in the codebase. Then the README.md and AGENTS.md shouldn't redefine them, it should just mention in which file all of the constants are located. Execute the plan .opencode/plans/constants-consolidation.md

### Icon

We should use the map icon everywhere we link to a map screen or map view, e.g. from the widget, not a gps or location icon.
