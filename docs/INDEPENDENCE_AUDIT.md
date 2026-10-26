# Technical reconstruction audit

Audit date: 26 October 2026. Author of BlocksRegenerator: Lino.

This report distinguishes observed artifact facts from source-level interpretation. It records the supplied legacy artifact's identity and the reconstruction process rather than claiming a clean-room development history. The legacy program was not executed for this audit, and no decompiled class was translated as part of the comparison.

## Artifacts and evidence

| Artifact | SHA-256 | Scope |
| --- | --- | --- |
| Supplied legacy JAR, version 2.6.2 | `c4590429b2a6f5a54253822d758de31e949949a3eb160504d69ea94ccccd84cf` | Read-only archive and JVM constant-pool inspection |
| BlocksRegenerator 1.0.3 baseline | `8e94137d45bc194ccfad98cef7831c14906baad4e2f3ecd13f0663236949de3d` | Frozen pre-change comparison artifact |
| BlocksRegenerator 2.0.0 final candidate | `885f6b92150c09f060fcd87ceb6c6133a61eadd5e76b3a1669710f3b3edbca6d` | Post-change comparison artifact |

REA 6.8.0 archive summaries provide these retained references:

- Legacy summary `ev_be448a3e61147c5fe13a0b00e45e2db6d480d2c486707676019d2767570ea957`; parent inspection `ev_64adb4a40691b11cddc0fb3cf72c06c58c39777de11425205e73ea0eee492b4a`.
- Baseline summary `ev_e17c3307c1ef6537f81aa06c7ed1799d4781b946d4e31c093707ed0764b5ba64`; parent inspection `ev_3d034a6143b3757b37c407388634d9d3d0fcdcf1fb039537b9f8253d0502252c`.
- Candidate summary `ev_2bdd250b90a91d25b504231641429be5d76d332f6b1928dc0048510c222ce901`; parent inspection `ev_549bbbffa46b62a74a177e110500c4af5d72fde580e6a493b31acbae54db0a0d`.

The legacy inventory has 2,254 graph nodes and 2,256 occurrences; the baseline has 116 nodes and 116 occurrences; the candidate has 130 nodes and 130 occurrences. REA's scope is complete container-entry inventory, with zero reported integrity contradictions. It does not establish JVM semantics, dynamic behavior, or the legal status of an implementation. Every opened session was closed after inspection.

A separate local script reads ZIP members and parses JVM constant-pool records without loading either plugin. Local evidence files are `.work/independence-audit.py`, `.work/independence-audit-baseline.json`, `.work/independence-audit-final.json`, `.work/independence-audit-rea-evidence.json` and `.work/independence-audit-rea-final.json`. The raw source artifact and extracted resource text remain local and are not distributed with this report.

## Baseline observations

| Measurement | Legacy artifact | BlocksRegenerator 1.0.3 |
| --- | --- | --- |
| Class entries | 1,795 | 92 |
| Plugin-owned class entries, excluding bundled libraries | 208 | 50 |
| Source-file metadata candidates | 124 Kotlin filenames | 36 Java filenames |
| Non-class member names | 171 | 7 |
| Distinct string literals in plugin-owned classes | 883 | 710 |

None of the 50 baseline plugin-owned class payloads has the same SHA-256 as any class payload in the supplied artifact. No non-class member with the same logical path has identical bytes. The shared resource paths are `plugin.yml`, `config.yml`, `messages.yml` and `META-INF/MANIFEST.MF`.

These are exact byte-identity checks, not an originality score. Recompiling, renaming or reorganizing copied source can change class hashes, so zero matching payloads does not establish independent authorship by itself.

## Architecture interpretation

The following interpretation combines the current maintained source with the earlier behavior record in ORIGINAL_CONTRACT.md, INTEGRATIONS.md and REVERSE_ENGINEERING.md. It does not derive program behavior from ZIP filenames alone.

| Area | Observed legacy organization | Maintained reconstruction organization |
| --- | --- | --- |
| Region selection | Region managers and region subclasses; map-based precedence selection | Immutable Region/Bounds values and a chunk-oriented RegionIndex with deterministic tie handling |
| Active recovery | Per-kind active-block implementations | One RegenerationEngine with an authoritative owner map, stale-entry guards, a deadline queue and parked unloaded entries |
| World access | Block-specific active implementations and listeners | WorldAccess boundary plus captured BlockSnapshot values, allowing engine verification without Bukkit |
| Persistence | Separate region and pending-state JSON files with version patches | StateCodec/SavedState plus an ordered StateStore with atomic replacement and preceding-state backup |
| Administration | Numerous menu classes and command-specific nested classes | Shared AdminMenus, owner/session state, command validation and deferred permission checks |
| Expressions | Bundled general-purpose expression dependency | A bounded DelayExpression parser maintained in the project |

This supports the conclusion that the baseline has a materially different organization and is more than a package or display-name replacement. It does not establish that every method's expression is independent of the previously consulted decompilation.

## Residual baseline similarities

The baseline still shared 154 exact JVM string literals with the legacy plugin-owned code. The exact count includes common language/API identifiers and interoperability strings. Several residual similarities deserved deliberate revision:

- Global configuration paths and nesting still mirrored the earlier layout, including automatic world coverage, claim integration, advanced delay expressions and outlines.
- Per-region setting identifiers retained legacy wording and a misspelled trampling key.
- Message keys and inline cooldown syntax followed the earlier convention.
- The default advanced delay expression and its variable spelling were identical.
- A product-specific startup conflict lookup string remained in the main class, despite generic user-facing diagnostics.

Of shared string literals with at least 20 characters, the only one containing a space was the default delay expression. No long shared English diagnostic phrase was found by that bounded exact-literal filter. This is not a semantic or near-duplicate message comparison, and resources were checked separately for byte identity.

## Post-change observations

The 2.0.0 candidate contains 104 class entries: 62 plugin-owned classes and 42 privately relocated protection-library classes. Compared against the same legacy artifact:

| Check | Candidate result |
| --- | --- |
| Original class payload found among plugin-owned classes | None |
| Original-owned class logical path reused | None |
| Byte-identical same-path resource | None |
| Candidate non-class payload matching any legacy non-class payload, regardless of path | None, across 7 candidate and 171 legacy resource entries |
| Product-specific legacy name in plugin-owned string literals | None |
| Original-owned package reference in plugin-owned constant pools | None |
| Shared global settings mapping leaf path | None |
| Shared locale mapping leaf path | None |
| Shared string literal of at least 20 characters containing a space | None |

Only `plugin.yml` and `META-INF/MANIFEST.MF` remain as shared resource paths; their bytes differ. The candidate does not package `config.yml` or `messages.yml`. Its application configuration is `settings.yml`; player notices are structured in `locale/en.yml` with separate text and cooldown values. Its formula is `uniform(lower, upper)`, and its public per-region settings use new dotted identifiers.

The resource comparison hashes decompressed member contents and compares each candidate non-class SHA-256 against every legacy non-class payload, independently of filenames. It therefore also checks for byte-identical renamed resources, rather than checking matching paths only. There are no such matches. The candidate's seven resources are classified separately:

| Category | Candidate members | Treatment |
| --- | --- | --- |
| Mandatory Bukkit descriptor | `plugin.yml` | Required by the plugin platform; content differs |
| Standard JAR metadata | `META-INF/MANIFEST.MF` | Container metadata; content differs |
| Project build metadata | Project Maven POM and properties under `META-INF/maven/` | Generated project metadata; no legacy payload match |
| Project configuration and locale | `settings.yml`, `locale/en.yml` | New application-owned layouts; no legacy payload match |
| Upstream license notice | `META-INF/LICENSE-antigrieflib.txt` | Attribution retained for the independently obtained bundled library; no exact legacy payload match |

No license or mandatory metadata was removed to obtain the resource result. This complete-payload check does not detect partial excerpts, changed encodings, paraphrasing or semantically equivalent content.

The new configuration boundary uses immutable settings records and a validated loader; notices use PlayerNotices/NoticeCatalog and typed notice identifiers instead of string message keys and inline throttling tags. This adds a separate configuration/presentation boundary to the already different engine, indexing and storage architecture.

There are still 125 exact string literals shared with the legacy plugin-owned classes, down from 154 in the baseline. Of those, 45 occur only inside the interoperability reader and identify external source fields/formats. The other 80 include ordinary words and separators, Minecraft material names, generic enum labels, provider names and reflected provider API signatures. This count is not a claim that every shared literal is copyrightable or that every shared literal is required. It demonstrates why a literal zero-overlap requirement would also reject normal platform integration and file import support.

The mapping-path comparison reads the selected simple YAML mapping layouts; it is not a general YAML alias-resolution or semantic-equivalence proof. The string comparison inspects exact CONSTANT_String values and a bounded phrase filter. It cannot detect paraphrased text, algorithmic similarity or expressive similarity that survives recompilation.

## Shared integration boundaries

Some names are required by the environment or by an explicitly supported interchange contract. Bukkit plugin metadata, JVM/JDK names, Minecraft material names and third-party provider API names are not evidence of copied plugin implementation. A legacy data reader must recognize its input schema to import existing definitions; those input identifiers describe interoperability rather than the new configuration format.

The bundled protection library is independently obtained under its upstream MIT license. Its license and attribution must remain in the distributable. Removing required notices or disguising library provenance would not improve technical independence.

## Limits and publication assessment

The comparison has no exhaustive normalized source or method-level semantic equivalence proof. It does not calculate a percentage of copyright independence. It also does not reverse the fact that the legacy implementation was consulted during the earlier reconstruction.

Different internal code, distinct configuration and clean packaging are relevant technical evidence. They do not by themselves determine license compliance, copyright infringement, contractual enforceability or acceptance by SpigotMC moderators. This report is not a legal-clearance or publication-approval certificate.

The candidate observations apply to the 2.0.0 digest recorded above. A later rebuild with different bytes must be audited or rebound to equivalent build evidence before this report is used as a release-artifact claim. Baseline findings remain historical and apply only to the frozen 1.0.3 artifact.
