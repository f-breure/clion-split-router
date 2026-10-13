# Split Window File Router

A CLion plugin that keeps headers and implementation files side by side
without manually moving editor tabs.

With a side-by-side editor split, files with configured extensions are routed
to the right pane; all other files are routed to the left. With only one pane,
files open normally. The plugin does not create splits automatically.

## Configuration

Open **Settings > Tools > Split Window File Router** and edit the
comma-separated list of right-pane extensions.

Defaults: `h, hpp, hxx, hh, inc, inl, tpp, ipp`.

Matching is case-insensitive and extension-based. Filename glob patterns,
directory rules, and detection of files referenced by include directives are
not supported. Settings persist between IDE sessions.

The intended layout is two panes in the same editor frame. Separate IDE
windows and more complex split layouts are not verified.

## Build and install

The project targets CLion 2026.2.3 and uses a JDK 25 build toolchain, producing
Java 21-compatible bytecode. Install JDK 25 before building.

```sh
./gradlew buildPlugin
```

Install `build/distributions/clion-split-router-1.0.0.zip` using
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
