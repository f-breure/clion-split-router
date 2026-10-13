# Split Window File Router

A CLion plugin that keeps headers and implementation files side by side
without manually moving editor tabs.

With a side-by-side editor split, files with configured extensions are routed
to the right pane; all other files are routed to the left. With only one pane,
files open normally. The plugin does not create splits automatically.
Routing also applies when selecting an already-open file.
When a selected file is moved, keyboard focus is restored to its destination
editor after the original tab closes. Background openings do not request focus.

Dragging a tab to another editor pane creates a manual override for that file:
the plugin no longer routes it automatically. Overrides are saved per project
in the IDE workspace and survive closing files and restarting CLion. They do
not force a particular pane when reopening a file; CLion's normal opening
behavior applies. Cancelled drags and tab reordering within a pane do not
create overrides.

## Configuration

Open **Settings > Tools > Split Window File Router** and edit the
comma-separated list of right-pane extensions.

Defaults: `h, hpp, hxx, hh, inc, inl, tpp, ipp`.

Matching is case-insensitive and extension-based. Filename glob patterns,
directory rules, and detection of files referenced by include directives are
not supported. Settings persist between IDE sessions.

To restore automatic routing, click **Reset manual routing overrides for this
project** on the same settings page and then **Apply**. Routing resumes on
subsequent file openings or selections. Extension settings apply IDE-wide;
manual overrides apply only to the current project.

The intended layout is two panes in the same editor frame. Separate IDE
windows and more complex split layouts are not verified.

## Build and install

The project targets CLion 2026.2.3 and uses a JDK 25 build toolchain, producing
Java 21-compatible bytecode. Install JDK 25 before building.

```sh
./gradlew buildPlugin
```

Install `build/distributions/clion-split-router-1.0.4.zip` using
**Settings > Plugins > Install Plugin from Disk**.

The plugin uses IntelliJ Platform editor implementation APIs. Compatibility
with other IDEs or future CLion versions is not guaranteed.

## Feedback

Report problems or request features at
https://github.com/f-breure/clion-split-router/issues.

## License

Copyright (C) 2026 Frank Breure.

Licensed under the GNU General Public License version 3 only
(`GPL-3.0-only`). See [LICENSE](LICENSE) for the full terms.
