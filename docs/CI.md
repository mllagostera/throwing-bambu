# Continuous integration and releases

Two workflows that trigger, and they do not overlap. `ci.yml` answers "is this change
sound?" and runs on pull requests and on `main`. `release.yml` answers "is this artefact
publishable?" and runs on a release tag and nothing else. A third, `determinism.yml`, never
triggers on its own: both call it, so the check exists once rather than twice.

Both take the app's version from the same place: the git tag. Nothing in the repository
names a release, so this document and `app/build.gradle.kts` are the whole story.

---

## 1. What runs when

| What is pushed | `ci.yml` | `release.yml` |
|---|---|---|
| A branch with no pull request | no — `workflow_dispatch` runs it by hand | no |
| A branch with a pull request open | yes, on the branch **already merged into its base** | no |
| `main` | yes | no |
| Tag `v0.1.0` | **no** | yes |
| Tag `v2.0.0-rc1`, `v1.2`, `release-1` | no | **no** |

Two things worth stating outright, because neither is visible from the file:

- **A tag does not run `ci.yml`.** Its trigger is `push: branches: [main]`, and naming
  `branches` excludes tags. `release.yml` therefore calls the determinism check itself
  rather than relying on CI having run it.
- **A tag that does not match the filter starts nothing, and says nothing.** The filter
  `v[0-9]+.[0-9]+.[0-9]+` looks like a regular expression but is a GitHub filter pattern,
  where `+` means "one or more of the preceding character". `v2.0.0-rc1` does not match, no
  workflow starts, and no run appears to fail. The build-side guard in §2 only fires once
  something builds, which in that case never happens. This one is still open — see §8.

---

## 2. Where the version comes from

`app/build.gradle.kts` derives it at configuration time from two git commands:

```
git describe --tags --match 'v[0-9]*' --always   → the nearest release tag
git status --porcelain                           → whether the tree is clean
```

| State of the checkout | `versionName` | `versionCode` |
|---|---|---|
| On tag `v0.1.0` | `0.1.0` | `100` |
| On tag `v1.4.3` | `1.4.3` | `10403` |
| Five commits past `v0.1.0` | `0.1.0-5-gabc1234` | `100` |
| The same, with uncommitted changes | `0.1.0-5-gabc1234-dirty` | `100` |
| No release tag behind this commit | `0.0.0-dev+abc1234` | `1` |
| `git` missing entirely | `0.0.0-dev` | `1` |

`versionCode` is `major * 10000 + minor * 100 + patch`, which reads back as the version in
Play Console — `10403` is plainly 1.4.3 — and caps minor and patch at 99. Raising the
multipliers later is safe, because a larger code is still a larger code.

Three deliberate choices here:

- **A tag that begins with `v` and does not parse aborts the build.** `v2.0.0-rc1` or `v1.2`
  fail loudly rather than becoming `0.0.0-dev` with code 1. Play accepts a `versionCode`
  once and refuses it forever after, so the quiet version of that mistake could pin the app
  to code 1 permanently.
- **Off a tag, the code stays at the tag's.** Such a build is for a phone on a desk, never
  an upload, and `release.yml` will not publish one.
- **The dirty mark comes from `git status`, not `git describe --dirty`.** The latter reads
  the index's cached file stats, which go stale and report a clean tree as dirty. Since
  `release.yml` refuses to publish when the tag and the built version disagree, a false mark
  would be a red release on a good commit.

The two jobs that produce an artefact — `build` in `ci.yml` and `release` — check out with
`fetch-depth: 0`. The default shallow clone carries no tags, and every build would call
itself `0.0.0-dev`. The macOS `determinism` job keeps the shallow default: it publishes
nothing, and `--always` means a tagless checkout reports its commit instead of failing.

CI reads the two numbers with `./gradlew -q :app:printVersion`, which prints them as
`versionName=…` / `versionCode=…` — one `key=value` per line, so a workflow can append it
straight to `$GITHUB_OUTPUT`. Nothing greps the build file for a literal.

---

## 3. `ci.yml` — pull requests and `main`

A pull request is tested on the branch **already merged into its base**, which catches a
change that is green alone and red once it lands. That is why `push` is narrowed to `main`:
a branch with an open pull request would otherwise run the whole thing twice, and the
concurrency group cannot collapse the pair — a push is `refs/heads/<branch>` and a pull
request is `refs/pull/<n>/merge`, different groups, so neither cancels the other.

### Job `build` (ubuntu-latest)

1. Checkout with `fetch-depth: 0`.
2. JDK 17 (Temurin), then `gradle/actions/setup-gradle` for the Gradle cache.
3. `./gradlew ktlintCheck detekt` — style across all three modules.
4. `./gradlew build test`. `build` assembles **every** variant, release included, so **R8 and
   resource shrinking already run on every pull request**, unsigned. It is slow, and it means
   an R8 failure surfaces in the pull request rather than at the tag.
5. Test reports uploaded with `if: always()`, so a red run still leaves something to read.
6. `./gradlew :app:assembleDebug`, renamed to
   `throwing-bambu-<version>-debug-<sha7>.apk`, with a one-row table in the job summary.
7. The APK is uploaded with `if-no-files-found: error`: an empty artefact that goes green is
   worse than a broken build.

The APK is built after the tests on purpose — if anything is red there is no APK to
download. It is the debug one, signed with the debug key, so it installs as it is
(`adb install`, or by opening it on the phone).

### Job `determinism`

One line: `uses: ./.github/workflows/determinism.yml`. That file is a `workflow_call`
workflow — checkout (shallow — see §2), JDK 17, `./gradlew :core:test` on **macos-latest** —
and `release.yml` calls the same one. §17.1 of the specification warns about floating-point
divergence between JVM implementations: the same fingerprint tests, run on a second
operating system, catch it in the commit that introduces it rather than as a networking bug
in M6.

It lives in its own file rather than in both workflows because a check copied twice is a
check that will differ twice. It appears in the Actions tab with no runs of its own, which
is what `workflow_call` means.

`concurrency` with `cancel-in-progress: true` cancels superseded runs of the same ref.

---

## 4. `release.yml` — a release tag

Three jobs in a chain. Each stage only starts if the one before it was green.

```
┌─ preflight ─────────────────────────────── ubuntu, seconds ─┐
│  are the four secrets set?  ── no ──→ ✗ naming which        │
│  is the tag on main?        ── no ──→ ✗                     │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─ determinism ──────────────────────── macos, calls the file ┐
│  :core:test on a second JVM  ── red ──→ ✗                   │
└─────────────────────────────────────────────────────────────┘
    ↓
┌─ release ───────────────────────────────── ubuntu, minutes ─┐
│  checkout (fetch-depth: 0)                                  │
│  ktlint + detekt + build (tests included)                   │
│  read the version  →  versionName / versionCode             │
│  tag == built version?  ── no ──→ ✗                         │
│  decode the keystore → $RUNNER_TEMP  (outside the checkout)  │
│  :app:bundleRelease (signed) + :app:assembleDebug           │
│  jarsigner -verify  ── "jar is unsigned" ──→ ✗              │
│  delete the keystore   (if: always)                         │
│  collect → throwing-bambu-<version>.aab + -debug.apk        │
│  gh release create --generate-notes --verify-tag            │
└─────────────────────────────────────────────────────────────┘
```

`preflight` exists so that everything able to say no in seconds does so before a runner
spends ten minutes on a build that was never going to be published.

`determinism` gates the release instead of running beside it. Running them in parallel would
save a few minutes and would allow publishing while the check is still red, which is the
outcome those minutes are worth avoiding. A tag does not run `ci.yml`, so this is the only
place the second JVM sees a release.

The tag usually lands on a commit CI has already seen green. "Usually" is not a guarantee,
and a release is the wrong place to find out, so style and tests run again in `release`.

### The gates, and what each is for

| Gate | What it catches |
|---|---|
| The four secrets, first of all | spending the whole build to discover there is no key — and, worse, publishing an unsigned bundle without noticing |
| The tag is on `main` | a tag pushed to a branch that never went through a pull request, publishing code nobody reviewed |
| `:core:test` on macOS | a floating-point divergence between JVMs, in the engine whose whole contract is reproducibility |
| Tag equals built version | a tree that was not clean, a checkout that lost its tags, a `describe` that landed on a different tag |
| `jarsigner -verify` | publishing an unsigned bundle, which Play rejects hours later and by hand |

An unsigned release build is legitimate — it is what lets anyone exercise R8 without holding
the key — so the workflow cannot assume it got a signed one. It checks.

"On `main`" means reachable from `main`, which `git merge-base --is-ancestor` decides: the
tip of `main`, any commit in its history, and any branch commit already merged into it all
pass. An unmerged branch does not. **A tag on a branch is rejected until that branch is
merged**, which is the point, and is worth knowing before tagging.

### Why the keystore lives outside the checkout

`$RUNNER_TEMP`, not the workspace. Inside it, the keystore would be an untracked file,
`git status --porcelain` would call the tree dirty, the version would become `X.Y.Z-dirty`,
and the tag gate above would fail on a perfectly good commit. The `Remove the upload key`
step carries `if: always()`, so it runs whether the build succeeded or not.

---

## 5. The upload key

Play App Signing holds the key that signs what people install, and every app created since
2021 must use it. What this repository handles is the **upload key**: it only proves to Play
that an upload is ours, and Google can reset it if it is lost. That asymmetry is the point —
losing an upload key is a support ticket, losing an app signing key would be the end of the
app.

It is generated once, on a machine that is not CI, and never enters the repository:

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

`-validity 10000` puts the expiry around 2053. Play refuses a key that expires before 22
October 2033, so a default validity would be rejected. **Back the file and its passwords up
somewhere that is not that machine** before going any further.

CI reads the key from four repository secrets, under Settings → Secrets and variables →
Actions:

| Secret | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | output of `base64 -w0 upload.jks` |
| `UPLOAD_KEYSTORE_PASSWORD` | the store password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | the key password |

To build a signed release on a developer machine, the same four values go in
`keystore.properties` at the repository root — `storeFile`, `storePassword`, `keyAlias`,
`keyPassword`. `.gitignore` keeps that file, and any `*.jks` or `*.keystore`, out of the
repository. Environment variables win over the file, which is how CI passes them.

With neither configured, a release build still runs and comes out unsigned. A
**half**-configured key is not that case: it fails, and names which of the four values is
missing.

---

## 6. Cutting a release

```bash
git switch main && git pull   # the tag has to be on main; preflight checks it
git tag v0.2.0                # annotated is fine too; the workflow does not care
git push origin v0.2.0        # tags are not pushed by `git push` alone
```

That is the whole procedure. Everything else is derived. When the run goes green, the
GitHub Release carries:

- `throwing-bambu-0.2.0.aab` — the signed bundle, the file Play wants.
- `throwing-bambu-0.2.0-debug.apk` — signed with the debug key, installs on a phone as it
  is, which is what testers actually want.

**Uploading to Play is still manual.** Nothing here talks to the Play Developer API: the
bundle has to be downloaded from the release and uploaded in Play Console by hand.

Because a `versionCode` is accepted once and never again, each upload to Play needs its own
tag. During internal testing that means `v0.2.1`, `v0.2.2`, and so on.

---

## 7. When something goes red

| Message | What happened |
|---|---|
| No run appears at all | the tag does not match `v[0-9]+.[0-9]+.[0-9]+` — see §1 |
| `Cannot version 'v…': a release tag reads vMAJOR.MINOR.PATCH` | the tag has a suffix or a missing component. Delete it, tag again |
| `Repository secrets not set: …` | the named secrets are missing — see §5 |
| `Tag vX.Y.Z points at …, which is not on main` | the tagged commit is not reachable from `main`. Merge the work first, then tag the merged commit |
| `Tag vX.Y.Z, but the build derived …` | the built version is not the tag. Usually `-dirty` (something wrote into the checkout) or a checkout without tags |
| `Upload keystore configured but missing: no file at …` | `storeFile` points nowhere. In CI that means the decode step did not produce the file |
| `Upload keystore configured but UPLOAD_… is not set` | three of the four values are present |
| `Not signed: …` | the build ran without the signing config. The secrets exist but did not reach Gradle |

A release that fails after the tag was pushed is fixed by deleting the tag, correcting the
commit, and tagging again — the version is the tag, so there is nothing else to roll back.

---

## 8. Known gaps

Listed rather than hidden. None of them blocks a release today.

1. **A malformed tag starts nothing, silently.** §1. The build-side guard cannot help,
   because no build starts.
2. **The release job builds the release variant twice** — unsigned inside `build`, then
   signed in `bundleRelease`. Correct, but R8 runs twice.
3. **`versionCode` is read and discarded.** It reaches `$GITHUB_OUTPUT` and nothing consumes
   it, though it is the number needed to reconcile a build with Play Console.
4. **R8 has never been verified on a device.** The release build is produced and shrunk, but
   nobody has installed one and confirmed the engine still behaves. That is the remaining
   half of T-52 in the development plan, and it needs a phone.
