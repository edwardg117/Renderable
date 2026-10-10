# Adding Renderable to Gears & Frontiers

Renderable is client-only and optional. This is the MCUpdater module entry the pack author would add. It follows
ReForgedPlay Mod in `updates/base.xml`, one of the pack's optional client-only modules. It is written down
here and not applied: phase 5 leaves the pack repository alone (spec addendum §P5.C6).

```xml
		<Module name="Renderable" id="renderable" depends="" side="CLIENT">
			<URL priority="1">https://github.com/edwardg117/Renderable/releases/download/v0.6.0/renderable-0.6.0.jar</URL>
			<Size>0</Size>
			<Required>false</Required>
			<ModType>Regular</ModType>
			<MD5>0</MD5>
			<Meta>
				<license>MIT</license>
				<description>Client render profiler: shows which block entities, entities and effects cost frame time and what caused each hitch.</description>
				<version>0.6.0</version>
				<authors>edwardg117</authors>
			</Meta>
		</Module>
```

Before adding it, fill in three things:

- **`Size` and `MD5`**, the released jar's byte count and MD5. For example, on the downloaded jar:
  `stat -c %s renderable-0.6.0.jar` and `md5sum renderable-0.6.0.jar`, or `certutil -hashfile renderable-0.6.0.jar MD5`
  on Windows.
- **The URL**, if the repository is not `github.com/edwardg117/Renderable` or the tag is not `v0.6.0`. It must be
  the release asset's download link, not the release page.

**Where it goes.** In the update file of the pack revision that ships it. At the time of writing that is
`updates/updates-2026-09-18.xml`, which `GearsAndFrontiers_Preview.xml` imports and `GearsAndFrontiers.xml` does
not yet.

**Optional, unticked.** `<Required>false</Required>` makes it a module each player ticks in MCUpdater if they
want it. The pack also uses `<Required isDefault="true">false</Required>` for optional modules that start ticked.
A profiler is for when something is slow, so it starts unticked.

**Side.** `CLIENT`: the server never needs it, and a server that has it ignores it.
