# Compass Only

A RuneLite plugin that puts an enlarged compass where the minimap was, shrinks the minimap into the compass's old spot, and can enlarge the inventory.

- The minimap and the compass swap places. The game's small compass is redrawn large, centred where the minimap was, and turns with the camera exactly like the original.
- The minimap shrinks into the small circle where the compass used to be. It is the game's real minimap, so it still shows dots, can be clicked to walk and can be scrolled to zoom. Turn off **Small minimap** to hide it completely.
- The health, prayer, run and special attack orbs stay where they are.
- Optionally enlarges the inventory, up to 300%. Items in the enlarged inventory can still be clicked, right-clicked and dragged.
- Works in fixed, resizable classic and resizable modern layouts.

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Compass size | 100% | Size of the compass, from 10% to 1000%. 100% fills the minimap's space. Above 100% the compass grows past the minimap, is drawn on top of the interface, and is kept fully inside the game window. |
| Smooth scaling | On | Smooths the enlarged compass and inventory. Turn off for hard pixel edges. |
| Click to look north | On | Left-click the compass to face north. Right-click it for south, east and west. |
| Small minimap | On | Shows the minimap, shrunk, in the small circle where the compass used to be. Off hides the minimap completely. |
| Hide minimap frame | Off | Also hides the stone frame around the minimap, leaving the orbs. Resizable layouts only. |
| Inventory size | 100% | Size of the inventory, from 100% (normal) to 300%. It grows up and to the left from its bottom-right corner, over the game view. |

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
- Markers that other plugins draw on the minimap (for example ground markers) keep their normal
  spacing, so on the small minimap they can appear outside its circle.
- With Compass size above 100% the compass is drawn on top of everything, including the small minimap.
- The enlarged inventory is a scaled-up picture of the real one, and mouse input over it is passed on to the
  matching spot of the real inventory. Right-click menus open at your cursor as usual. Item tooltips from
  other plugins appear next to the normal-sized inventory, and the enlarged inventory can cover other parts
  of the screen, such as the top row of tabs in fixed mode.
- The inventory is only enlarged while the inventory tab is open. The bank's own inventory is not enlarged.
- The compass uses the game's standard compass image. A resource pack that replaces the compass is not picked up.
- In the few places where the game itself blanks out the compass, this plugin still shows it.
