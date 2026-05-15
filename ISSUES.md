# ISSUES.md

This document references known issues for agents to pick them and iterate on

## List

- App UX: Switching from screen is not ideal, it's not smooth, let's revise the plan at .opencode/plans/navigation_transitions.md

- DX: Codebase naming and consistency is bad. Iterate over plan .opencode/plans/naming.md, search for other missing renames.

- Map UX: Playing a route, or going from the current location to an other (e.g. after clicking the map and chosing "Walk"), the route should be traced in the map, in order to give better feedback to the user.

## Jitter applies while idle even when configured to 0

Current behavior
The mock GPS jitter system continues to slightly move the spoofed location even when the user is stationary (idle state), including cases where jitter is configured to 0.

This creates unintended movement while the user is not actively traveling between locations.

Expected behavior

Jitter should simulate normal GPS inaccuracy, not roaming behavior.

It should:

Only apply while the user is actively moving between point A → point B (e.g. walking simulation)
Remain completely disabled when the user is idle/stationary unless the user explicitly enables idle jitter
Respect a jitter value of 0 as fully disabled
Problem

Right now, idle jitter makes the spoofed location appear unstable and unrealistic because:

The location continues drifting while stationary
Movement can appear too frequent or erratic
It feels closer to random roaming than natural GPS variance

This is not the intended purpose of jitter.

Intended jitter behavior

Jitter should mimic real-world GPS drift:

Small positional variations only
Minimal movement radius
Natural-looking adjustments
No rapid/frenetic coordinate changes

Real GPS signals are rarely accurate to the exact meter, so slight periodic variation is expected—but it should remain subtle.

Suggested implementation

Add a configurable update interval:

Jitter interval: every N seconds
Default: 3 seconds

This ensures jitter updates happen periodically rather than continuously.

Example logic:

User moving → apply subtle jitter every N seconds
User idle → no jitter
User idle + explicit idle jitter enabled → apply configured jitter behavior
Jitter set to 0 → disable entirely
Acceptance criteria
 Jitter does not move location while idle by default
 Jitter respects 0 value as disabled
 Jitter only applies during active movement unless explicitly enabled for idle state
 Jitter updates occur at configurable intervals
 Movement remains subtle and realistic, without aggressive coordinate jumps

Plan from previous investigation: .opencode/plans/jitter_idle_bug_fix.md
