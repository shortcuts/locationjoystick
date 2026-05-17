# Known Issues & Backlog

## Backlog

### floatingwidgetservice starting position

it currently spawns on the top left side of the screen when spoofing is enabled, it should be at the center to make it less intrusive

### floatingwidgetservice route bottom drawer

when clicking "create route from map", it redirects to the idle screen. it should redirect to the actual route creator from map

### floatingwidgetservice icon order

the order should follow the one of the SettingsScreen, currently the map is placed last but it should be first

### about page information

should leverage the AppConstants.kt AppInfo URL for linking to github and reporting a bug. The AGENTS.md "Info / About Page" section properly describes it.

### create route from map

the map overlay should display the same "map screen" features such as "pick from favorite", in order to quickly jump to a favorite location to start a route, and the "center on location" icon.

### roaming settings

in SettingsScreen and RoamingSheet, the selected speed profile for roaming should be outlined, the other two shouldn't be outlined. The exact same UI component must be used as for the "km/h" "mph" selector of speed profiles in the SettingsScreen.

### transfer from QR

nothing happens when I scan the QR, also the topappbar should still visible if possible to make it easier to go back. can we draw a square on the screen to know where the place the QR code?
