# Renderable

Client-only NeoForge 1.21.1 mod: a render-thread profiler that does for frame time what
Observable does for server tick time. Built for the Gears & Frontiers modpack
(`H:\repos\Gears-Frontiers`, pack revision 4.4) but has no dependency on it.

## Where things are

- Design spec: `docs/superpowers/specs/2026-09-19-renderable-fps-profiler-design.md`.
  Read it before changing behaviour; it records verified hook targets and the reasons behind
  every scope decision.
- Implementation plans: `docs/superpowers/plans/`. One plan per phase; phase 1 first.

## Toolchain

- Java 21, ModDevGradle 2, NeoForge `21.1.220`. Use the Gradle wrapper only; there is no
  global Gradle on this machine.
- Optional integrations are compiled and run against the versions the pack pins, from the
  Modrinth Maven (`https://api.modrinth.com/maven`): Sodium `0.8.13+mc1.21.1`, Iris
  `1.8.14-beta.1+mc1.21.1`, Create `1.21.1-6.0.10` (bundles Flywheel 1.0.6), Create:
  Enchantable Machinery `3.6.0+mc1.21.1-neoforge` with Kotlin for Forge.

## Rules that keep the design intact

- `core` and the report writers have no Minecraft imports and are covered by JUnit 5.
- Every mixin calls the static `Probe` and checks its `ACTIVE` flag first. No allocation on
  hot paths while sampling; nothing at all when idle.
- Sodium, Iris and Flywheel mixins are optional (`require = 0`) and gated by the mixin plugin.
  Vanilla mixins are required.
- Mod event handlers are timed without a mixin: `hooks.events` swaps the game bus's listener arrays
  for proxies only while a session runs (spec addendum §P5.A3). The bus loads in the boot module
  layer, where no mixin reaches, so do not try to hook it.
- New hook targets must be verified against the pinned jars (javap) before they go in a plan.

## Git

- Commits are signed with a YubiKey. Say so before running `git commit` so the key gets
  touched; an unattended commit times out.
- Conventional prefixes: `feat:`, `fix:`, `docs:`, `test:`, `build:`, `ci:`.
