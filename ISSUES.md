# ISSUES.md

This document references known issues for agents to pick them and iterate on

## route row

on a created route, the row displayed in the route list, the buttons overflows to the next line, we should instead make each route a two line row:
- first line: ${Route name} - ${Distance}
- second line: every buttons

## route crash

when starting a route, the app crashes

## UX consistency

when saving a route, the input popups open and ask for the name of the route
when saving a favorite from a map, the input is always displayed for the name

change the favorite from a map creation so it opens the popup on save click (like the route behavior)

## route edit

when editing a route, we can only change the name, the list of waypoint should be displayed in order, the user should be able to remove a waypoint

cancelling the edit leaves the route as its current value

## route following the roads

it follows the road assuming we are a car, so it only create roads that doesn't violate the code of conduct for the car, can it find the road without taking the road circulation order?

## settings speed input

there seems to be a state issue because when I edit the field it's a bit funky, instead of updating the value on every keystroke, let's add a "save" button at the bottom of the page, so we only create one updan the user is actually done

## onboarding screen

when every non-optional setting(s) are valid for onboarding, just go directly to the "map" view this makes the usage simpler for user that have closed the app and already have valid settings, they don't have to scroll to have the "start locationjoystick button"

## route start

in the "route" view we should add a toggle at the top of the list "walk/teleport" so we can decide to either walk to the first waypoint of the route, or gets teleported to it
