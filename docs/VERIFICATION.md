# Verification

Verified on 23 October 2026 for Spigot 26.3 and Java 25.

## Automated build

`mvnw.cmd -B -q clean verify` completed successfully: **68 tests, zero failures, zero errors**. The tests cover region precedence and boundaries, delay validation and evaluation, queue ownership, stale timer cancellation, loaded/unloaded chunks, retry behavior, all chain styles, stack support, snapshot companions, durable storage, malformed data, legacy imports, administration permissions and sessions, inventory transfer cancellation, provider item lookup, reflection overload resolution, and protection failure handling.

Bukkit listener tests additionally cover final mining cancellation, final drop cancellation, deferred item collection, creative furniture cancellation, paired chain ownership, and farmland events whose hand is null. Expected error logs in protection tests deliberately exercise failed providers; they do not indicate a failed test.

The packaged JAR contains no original plugin classes, test classes, JUnit, or Mockito. AntiGriefLib is bundled under a private relocated namespace with its MIT notice.

## Live Spigot checks

The final release was tested in an isolated, loopback-only server built using official BuildTools for **Spigot 26.3, build 4663**, on **Java 25**. Existing server files and worlds were left untouched. The existing accepted Minecraft EULA was reused for this temporary server.

Both initial startup and restart passed **40 actual Bukkit world probe checks**. These verify ore data, stairs, trapdoors, crop ages, duplicate timer ownership, both door halves and companion ownership, exact restoration, and direct, staged, and shared-timer chains. The probe is a temporary verification plugin rather than a simulated player.

Console checks verified global creation, rule addition/removal, boolean settings, priority, chain stages/reordering/style/final breakability, expression validation, reload, deletion, and clean shutdown. All three supported import formats succeeded. Negative delays and invalid crop ages were rejected. Re-importing a colliding region refused to overwrite it.

A migration/restart check preserved four regions and one pending timer, verified the temporary STONE block after restart, and then restored the actual world block to DIAMOND_ORE. The final shaded release was separately restarted and retained saved regions and settings. No BlocksRegenerator warnings or errors occurred on either final release run.

All temporary server/build processes were stopped and the loopback test port was closed.

## Artifact identity

Final tested artifact: `target/BlocksRegenerator-1.0.0.jar`.

SHA-256: `75185617267f366b73b7e6139c4dfe583754ee43cb157e4002708143003a85bf`.

Local-only evidence includes `.work/release-verify.log`, Surefire reports under `target/surefire-reports`, and the isolated server's `release-first.log`, `release-restart.log`, `smoke-first.log`, `smoke-restart.log`, and temporary RuntimeProbe source. These generated artifacts are intentionally excluded from Git.

## Limits

No real player connected, so visual GUI layout, player-side mining/drop interaction, and client particle rendering have not been exercised end to end. Their session, cancellation, transfer and ownership boundaries have automated tests.

ItemsAdder, Oraxen, Nexo, BlockHardness, and the actual land-claim plugins were not installed in the temporary server. Their adapters are based on original consumed APIs and official library contracts, with focused unit tests. Real furniture models, orientation, hitboxes, custom drop timing, provider reload order, and every provider version still need validation in the intended server installation.

Unloaded-chunk queue behavior is covered by engine tests. An immediate chunk-unload assertion was unreliable in the temporary live probe and was excluded from the passing live checks.

The advanced evaluator deliberately supports the documented bounded expression language, rather than the original math library's entire grammar. Source-folder deletion by legacy converters is intentionally omitted. These differences are documented in README.md and the reconstruction ledger.

Passing these checks does not establish absence of every bug or full runtime equivalence with every optional integration.
