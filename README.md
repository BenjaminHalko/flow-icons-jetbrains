<h1 align="center">
  <img src="https://raw.githubusercontent.com/thang-nm/Flow-Icons/main/logo.png" width="160" alt="Flow Icons"/><br/>
  <a href="https://flow-icons.pages.dev">Flow Icons</a>
</h1>

<p align="center">
  🌼 Flow Icons ported to JetBrains IDEs
</p>

![Flow Icons Preview](https://raw.githubusercontent.com/thang-nm/Flow-Icons/main/preview.png)

Beautiful file & folder icons for **every JetBrains IDE** (IntelliJ IDEA, PyCharm,
WebStorm, GoLand, Rider, CLion, RubyMine, PhpStorm, DataGrip, Android Studio, …),
ported from the [Flow Icons](https://flow-icons.pages.dev) VSCode extension.

## Installation

1. Download the latest `Flow Icons-<version>.zip` from the
   [Releases](../../releases/latest) page.
2. In the IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…**,
   pick the zip, and restart.

## Usage

Configure under **Settings → Appearance & Behavior → Flow Icons**:

- **Icon theme** — pick one of the eight variants (see [Available Themes](#available-themes)).
- **Folder color** — default folder color: `gray`, `blue`, `brown`, `green`, `lime`,
  `orange`, `pink`, `purple`, `red`, `sky`, `teal`, `yellow`.
- **License key** — unlocks and auto-downloads the premium set (stored securely in PasswordSafe).
- **Hides explorer arrows**, **Hides explorer folders**, **Specific folders** — toggles.

The customization fields below take JSON, the same shape as the VSCode extension's
`settings.json`.

### Active icon pack

Toggle framework-specific icon sets. Available packs: `angular`, `bashly`, `nest`,
`next`, `roblox` (`nest` is off by default).

```jsonc
{
  "nest": true,
  "next": false
}
```

### Files / Folders associations

Map a file extension (`*.ext`), an exact filename, or a folder name to an icon id.
An empty string removes a built-in association. Folder values are the icon base name
without the `folder_` prefix (e.g. `resource` → `folder_resource`).

```jsonc
// Files associations
{
  "*.tss": "typescript",
  "tailwind.css": "tailwindcss",
  "package.json": ""
}
```

```jsonc
// Folders associations
{
  "store": "resource",
  "data": ""
}
```

### Files / Folders replacements

Swap one icon for another everywhere it is used — typically to opt into `-alt` variants.

```jsonc
// Files replacements
{
  "rust": "rust-alt",
  "kotlin": "kotlin-alt"
}
```

```jsonc
// Folders replacements
{
  "components": "react-components"
}
```

The **Flow You palette** field is documented under [Flow You](#flow-you).

## Premium Icons (Optional)

The plugin ships the free base icon set. Entering a license key in Settings
unlocks the full premium set.

You can support the original artist and get a license key here:
https://flow-icons.pages.dev

## Available Themes

| Theme | Appearance |
| --- | --- |
| Flow Deep | Dark |
| Flow Deep (Light) | Light |
| Flow Dim | Dark |
| Flow Dim (Light) | Light |
| Flow Dawn | Dark |
| Flow Dawn (Light) | Light |
| Flow You | Dark |
| Flow You (Light) | Light |

Pick one under **Settings → Appearance & Behavior → Flow Icons → Icon theme**.

## Flow You

`Flow You` is a customizable icon theme: provide your own 14-color palette and the
plugin rebuilds the `you` / `you-light` SVGs by substituting `--<colorName>`
placeholders in the template icons.

Paste a palette into the **Flow You palette** field in Settings and click
**Rebuild Flow You icons**. Top-level keys are the dark-mode palette; anything you
omit falls back to the default monochromatic slate. The light-mode palette is
auto-derived (HSL darken), but you can override individual entries via a nested
`light` object.

```jsonc
{
  "white": "#bfbdb6",
  "black": "#0d1017",
  "blue": "#59c2ff",
  "brown": "#e6c08a",
  "gray": "#667381",
  "green": "#aad94c",
  "lime": "#c0e76e",
  "orange": "#ff8f40",
  "pink": "#f6adae",
  "purple": "#d2a6ff",
  "red": "#f07178",
  "sky": "#39bae6",
  "teal": "#95e6cb",
  "yellow": "#ffcb8f",
  "borderOpacity": 0,
  "light": {
    "borderOpacity": 0.1
  }
}
```

> 💡 See the upstream [sample palettes](https://github.com/thang-nm/Flow-Icons/tree/main/you)
> (Ayu Dark, Sequoia Moonlight, …) for inspiration, drop their JSON straight in.

### Development

```bash
./gradlew buildPlugin   # builds build/distributions/Flow Icons-<version>.zip
./gradlew runIde        # launches a sandbox IDE with the plugin for development
```

## Credits

Flow Icons by [thang-nm](https://flow-icons.pages.dev)
