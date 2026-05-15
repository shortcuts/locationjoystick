# ISSUES.md

This document references known issues for agents to pick them and iterate on

## List

- DX: Codebase naming and consistency is bad. Iterate over plan .opencode/plans/naming.md, search for other missing renames.

- DX: There is too many constant spread in different files, sometimes we redefine some, we also manually provide them in the README.md file etc. we should have a single source of truth file that is imported from everywhere which constains all of the constant values with a clear description of what it does, so the README.md and AGENTS.md can just read form it. Implement the plan as your base findings .opencode/plans/constants-consolidation.md and iterate over the codebase to make sure everything is tackled and referenced.

- Bug (ongoing in claud): when having an ongoing route, the floating widget displays the "route" icon in green, which indicates a route is ongoing, however when clicking on it again which displays the "pause" and "stop" icons, the "pause" icon takes the spot of the "route" icon, shifting the whole floating widget to the side. the two icons should just be on the right of the current route icon.

- Bug (ongoing in claud): pausing a route behaves like stopping a route from the floating widget, it should just pause it and the icon should become "resume", when clicked, it continues.

- Bug: the floating map view has icons white, it should match the other view style, icons should be LjBg black. The search results should be LjText white. We should allow start/stop mock from this view just like the regular map screen.
