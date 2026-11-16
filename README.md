# Compass Only

A RuneLite plugin that changes the size and layout of parts of the game's interface. Each part can be used on its own.

**Compass**
- Replaces the minimap with an enlarged compass that turns with the camera exactly like the original.
- Shrinks the real minimap into the small circle where the compass used to be. It still shows dots, can be clicked
  to walk and can be scrolled to zoom.
- Turn off **Big compass** to leave the minimap and compass as the game draws them.

**Side panel**
- Enlarges the side panel, frame and all, up to 300%, with a separate size for the inventory, prayer, combat
  options, equipment and spellbook tabs. Everything in the enlarged panel can still be clicked, right-clicked and
  dragged, and tooltips appear at your cursor.

**Bank**
- Enlarges the bank window up to 300%, and can make it wider so it shows up to 24 items per row.
- Lines the bank's tabs up on the left, with the Bank Tags plugin's tabs moved up beside them.

Works in fixed, resizable classic and resizable modern layouts.

## Settings

### Compass

| Setting | Default | What it does |
| --- | --- | --- |
| Big compass | On | Replaces the minimap with an enlarged compass. Off leaves the minimap and compass as the game draws them, and the other settings in this section do nothing. |
| Compass size | 100% | Size of the compass, from 10% to 1000%. 100% fills the minimap's space. Above 100% the compass grows past the minimap, is drawn on top of the interface, and is kept fully inside the game window. |
| Click to look north | On | Left-click the compass to face north. Right-click it for south, east and west. |
| Small minimap | On | Shows the minimap, shrunk, in the small circle where the compass used to be. Off hides the minimap completely. |
| Hide minimap frame | Off | Also hides the stone frame around the minimap, leaving the orbs. Resizable layouts only. |

### Side panel

| Setting | Default | What it does |
| --- | --- | --- |
| Inventory size | 100% | Size of the side panel while the inventory is open, from 100% (normal) to 300%. The whole panel is enlarged, frame and tab buttons included, growing up and to the left from its bottom-right corner over the game view. |
| Prayer size, Combat options size, Equipment size, Spellbook size | 100% | The same, for when the prayer, combat options, equipment or spellbook tab is open. Each tab has its own size. |

### Bank

| Setting | Default | What it does |
| --- | --- | --- |
| Bank size | 100% | Size of the bank window, from 100% (normal) to 300%. It grows outwards from its centre, by as much as fits in the game window. It shows the same items, only bigger. |
| Bank columns | 8 | How many items the bank shows per row, from 8 (normal) to 24. More columns make the bank window wider, limited to what fits beside the side panel. |

The bank's tabs are always lined up on the left. If the Bank Tags plugin is on, its tabs move up into the same row,
and any that don't fit stay in their column on the left.

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
- The enlarged side panel is a scaled-up picture of the real one, and mouse input over it is passed on to the
  matching spot of the real inventory. Right-click menus open at your cursor as usual. Tooltips from other
  plugins are drawn at your cursor. The enlarged panel can cover other parts of the screen, such as the top row
  of tabs in fixed mode.
- The side panel is only enlarged while a tab with a size above 100% is open. The bank's own inventory is not enlarged.
- In resizable classic and fixed mode the tab buttons are part of the enlarged panel and can be clicked there.
  In resizable modern the tab buttons are a separate bar and stay their normal size.
- The enlarged bank works the same way as the enlarged inventory, and scrolling over it scrolls the bank. It can
  cover the inventory next to it; whatever it covers cannot be clicked until the bank is closed or made smaller.
- Bank columns moves the bank's item slots itself after the game lays them out, so it can clash with other
  plugins that rearrange the bank, such as Bank Tags layouts. In fixed mode there is no room to widen the bank,
  so the setting has no effect there.
- The compass uses the game's standard compass image. A resource pack that replaces the compass is not picked up.
- In the few places where the game itself blanks out the compass, this plugin still shows it.
