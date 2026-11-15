# Compass Only

A RuneLite plugin that removes the minimap and puts an enlarged compass in its place.

- The minimap is hidden, and it can no longer be clicked to walk.
- The game's small compass is hidden and redrawn large, centred where the minimap was. It turns with the camera exactly like the original.
- The health, prayer, run and special attack orbs stay where they are.
- Works in fixed, resizable classic and resizable modern layouts.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Compass size | 100% | How much of the minimap's space the compass fills (25–100%). |
| Smooth scaling | On | Smooths the enlarged compass. Turn off for hard pixel edges. |
| Click to look north | On | Left-click the compass to face north. Right-click it for south, east and west. |
| Hide minimap frame | Off | Also hides the stone frame around the minimap, leaving the orbs. Resizable layouts only. |

## Running it

Quickest: double-click `run-plugin.bat`. It uses the JDK in the `jdk` folder next to this one.

Or, with your own Java 11 or newer JDK:

1. Open this folder in IntelliJ IDEA and let it import the Gradle project.
2. Run `CompassOnlyPluginTest` (in `src/test/java/com/compassonly`) with the VM option `-ea`.
3. RuneLite starts with the plugin loaded. Find **Compass Only** in the plugin list.

Logging in with a Jagex account from a development client needs a one-time setup, described on the
RuneLite wiki under "Using Jagex Accounts".

## Things to know

- If RuneLite's own Minimap plugin has **Hide minimap** switched on, or the game's own hide-minimap
  setting is on, the whole minimap block is hidden, compass included. Turn those off to use this plugin.
- The compass uses the game's standard compass image. A resource pack that replaces the compass is not picked up.
- In the few places where the game itself blanks out the compass, this plugin still shows it.
