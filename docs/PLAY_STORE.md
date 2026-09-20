# Play Store listing

Every field the Play Console asks for, in the order it asks. Copy from here rather
than retyping into the Console: a listing is edited by hand, under a deadline, and
the version that ends up live should be the one that was reviewed.

English only. Four more languages are worth adding later — the interface is already
translated into Spanish, Catalan, French and German — but a half-translated listing
reads worse than an English one.

---

## Store settings

| Field | Value |
|---|---|
| App or game | **Game** |
| Category | **Arcade** |
| Tags | artillery, turn-based, pixel art, local multiplayer, two players |
| Email address | `vansidgg@gmail.com` |
| Website | `https://github.com/mllagostera/throwing-bambu` |
| Phone | *(optional — leave empty)* |
| External marketing | Allow |

The same address must appear here and in [`PRIVACY.md`](PRIVACY.md). A contact email
on the listing that does not match the one in the policy is exactly the kind of
mismatch a review notices.

**Arcade over Strategy** is a judgement call. The mechanic is strategic, but the match
is two minutes long and the art reads as arcade; Strategy would put it beside games
that ask for half an hour.

---

## App name

Limit 30 characters. Uses 14.

```
Throwing Bambu
```

## Short description

Limit 80 characters. Uses 66. This is what appears under the icon in search results,
and it is read far more often than the full description.

```
Two pandas, a destructible skyline and wind. Turn-based artillery.
```

## Full description

Limit 4000 characters. Uses roughly 1300. Play renders this as plain text: line
breaks survive, Markdown does not.

```
Two pandas on the rooftops of a city. Between them, a skyline that crumbles
with every hit, and a wind that changes on every turn.

Pick an angle. Pick a power. Throw the bamboo cane and watch it fly. Miss,
correct, throw again. First to three rounds takes the match.

ONE PLAYER
Three difficulty levels. On Easy the computer is learning the wind with you.
On Hard it has your roof measured within a few throws.

TWO PLAYERS, ONE DEVICE
Pass the phone back and forth. Best of five, no setup, no waiting.

EVERY MATCH IS NEW
The skyline is generated for each match and destroyed as you play. The wind
turns between throws, so the shot that worked last turn will not work twice.

PIXEL ART, AT FULL SIZE
Drawn at sixteen colours and scaled by whole pixels, never blurred or
smoothed. It is meant to look like what it is.

FIVE LANGUAGES
English, Espanol, Catala, Francais and Deutsch, or whatever your phone is
already set to.

NO STRINGS
No ads. No purchases. No accounts. No internet connection required — the app
does not even ask for network permission. Nothing you do here leaves your
phone.

Original code and artwork, released under the MIT licence.
```

Two things deliberately left out:

**Bluetooth.** Built and tested on a JVM, but no two physical devices have ever run a
match (see [`ROADMAP.md`](ROADMAP.md), M6), and `RfcommTransport` is not written, so
on a device without Google Play Services the mode would not work at all. It goes in
the listing when two phones have played a match end to end, not before.

**The AI's measured hit rate.** 87 % on Hard over 200 generated scenarios is a real
number and a good one, but it is an engineering metric. On a store page it reads as
marketing noise.

The accented characters in the language list are stripped on purpose: the block above
is plain ASCII so it survives being pasted through a console, a spreadsheet or a
terminal without turning into mojibake. Restore them by hand in the Console if you
prefer — `Español`, `Català`, `Français`.

---

## What's new

Limit 500 characters. For the first release:

```
First release.

Throw a bamboo cane across a destructible skyline, against the computer or
against someone holding the same phone. Three difficulty levels, best of
five, five languages.
```

---

## Graphics

The icon and the feature graphic are generated, like the rest of the art in this
repository, by a script rather than by hand:

```bash
python3 tools/gen_store.py     # writes art/store/
```

| Asset | Requirement | File |
|---|---|---|
| App icon | 512 × 512, 32-bit PNG, no transparency, under 1 MB | `art/store/icon_512.png` ✔ |
| Feature graphic | 1024 × 500, PNG or JPEG, no transparency | `art/store/feature_1024x500.png` ✔ |
| Phone screenshots | 2 minimum, 8 maximum; each side 320–3840 px | `art/store/screenshots/phone/` ✔ |
| 7" tablet screenshots | 4 minimum; each side 1080–7680 px; 16:9 landscape | `art/store/screenshots/tablet_7/` ✔ |
| 10" tablet screenshots | 4 minimum; each side 1080–7680 px; 16:9 landscape | `art/store/screenshots/tablet_10/` ✔ |

Both are written pre-scaled, which the rest of the pipeline forbids (hard rule 3 in
`AGENTS.md`). The exemption is deliberate and narrow: neither file ever reaches the
engine, and Play dictates their exact pixel size. They are still scaled by a whole
number with nearest-neighbour, so no pixel ends up a different size from its
neighbour.

### Why those base sizes

The scale factor has to be an integer, which constrains what the source can be:

| Asset | Source | Factor | Result |
|---|---|---|---|
| Icon | `art_icon.py`, 24 × 24 | ×21 → 504 | padded to 512 with 4 px of its own blue |
| Feature | composed at 512 × 250 | ×2 | 1024 × 500 |

24 px does not divide into 512 by a whole number, so something has to give. ×21 with
4 px of padding wastes less of the canvas than ×20 with 16 px, and the icon already
carries a margin of ground around the head — a wider border would only shrink the
panda inside Play's own rounded crop. The padding is the icon's exact background
colour (EGA index 1), so there is no visible seam, and it is opaque: Play rejects an
icon with an alpha channel.

### Screenshots

Three sets of five, one per device shape, all captured from the real renderer on an
emulator: `phone/`, `tablet_7/` and `tablet_10/` under `art/store/screenshots/`. The
same five moments each time, so the three listings tell one story. Upload them in this
order — Play shows them left to right, and the first is the only one many people ever
see:

| File | What it shows |
|---|---|
| `01_flight.png` | The cane mid-flight with its trail, thrower's arm still up |
| `02_impact.png` | An explosion going off beside the right-hand panda |
| `03_damage.png` | Buildings eaten away by earlier craters, a throw in the air |
| `04_controls.png` | A fresh board: angle, power, wind, score, Throw |
| `05_menu.png` | The title and the four modes |

#### They must not be taken at the device's native resolution

This is the trap. The phone AVD is 1440 × 3200, which in landscape is 3200 × 1440 —
an aspect ratio of **2.22:1**, past Play's 2:1 limit for screenshots. Play would
reject the set, and cropping to fit would cut off the Throw button at the right edge,
since the game uses the full width for its controls.

The fix is to resize the emulator's screen before capturing, so the game lays itself
out for the shape being shipped rather than being cut down to it afterwards:

```bash
adb shell wm size 1440x2560     # 2560 x 1440 landscape, exactly 16:9
adb shell wm density 560        # unchanged: the width is the same, only height drops
adb shell wm size reset         # afterwards, to leave the AVD as it was
```

Any device-shaped phone AVD will do, but not every one boots: `Pixel_10_Pro` on the
Android 37 Play Store image hangs at `adb: device offline` and never completes, while
`Xiaomi_13_Ultra` on the same image boots in about forty seconds. If an emulator sits
offline for more than a few minutes with its RAM figure frozen, it is not slow, it is
stuck — try a different AVD before spending an hour on GPU flags.

#### Building the APK to capture

`JAVA_HOME` on a machine may well point at a JDK 8, which Gradle refuses outright.
Android Studio ships a JDK 21 that works:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Prefixing every invocation gets old. Pinning it once in the **user's own**
`~/.gradle/gradle.properties` — not the repository's, which is checked in and would break
the Linux runners — fixes it for every build on that machine:

```properties
org.gradle.java.home=/path/to/a/jdk-17
```

17 is the major the workflows pin, so it is the one worth installing locally.

Never take a store screenshot from `art/preview/scene_1x.png`. It is a test image
assembled from the sprites by a script, not the game, and shipping it would be showing
people something they cannot play.

#### Tablets

The same five moments again, at two more shapes, in `tablet_7/` and `tablet_10/`. Play
asks for **four minimum** per tablet size, a side between 1080 and 7680 px, and **16:9 in
landscape** — a stricter band than the phone slot, and the reason neither set can be a
tablet's native resolution: `Tablet_10_1` boots at 1920 × 1200, which is 16:10.

So the screen is reshaped before capturing, exactly as for the phone, on any tablet AVD:

```bash
# 7": 1067 x 600 dp
adb shell wm size 1920x1080 && adb shell wm density 288
# 10": 1422 x 800 dp
adb shell wm size 2560x1440 && adb shell wm density 288
adb shell wm size reset && adb shell wm density reset   # afterwards
```

**The density is the whole point, not the pixel count.** `ScreenScaffold` scales every
screen by its height in dp against a phone's 400, so what makes a tablet screenshot a
tablet screenshot is the dp the app is handed, not the resolution it is captured at.
Density 288 is chosen to land on 600 dp and 800 dp — the two numbers `screenScale`'s own
documentation uses for a 7" and a 10" tablet. Capture 2560 × 1440 at a phone's density
and the result is a phone screenshot with more pixels.

Take the action frames as a burst **on the device**, not one `screencap` per `adb` call:
the flight lasts under a second and the round trip is most of it.

```bash
adb shell 'input tap <throw>; for i in 01 02 03 04 05 06 07 08 09 10 11 12; do
  screencap -p /sdcard/burst/f$i.png; done'
adb pull /sdcard/burst
```

Twelve to sixteen frames covers a throw, its explosion and the computer's reply, which is
every action shot in one pass. Pick from a contact sheet rather than opening each one.

Finally, convert to RGB. `screencap` writes RGBA and Play asks for 24-bit PNG with no
alpha channel, the same rule that makes `gen_store.py` convert the icon.

---

## App content

| Declaration | Answer |
|---|---|
| Privacy policy | URL where [`PRIVACY.md`](PRIVACY.md) is published |
| App access | All functionality is available without special access |
| Ads | **No**, this app does not contain ads |
| Content rating | See questionnaire below |
| Target audience | **13+** |
| News app | No |
| COVID-19 contact tracing | No |
| Data safety | See below |
| Government app | No |
| Financial features | None |
| Health apps | No |

**13+ rather than "under 13"** is a deliberate choice. The game is harmless enough for
anyone, but declaring a child audience pulls the listing into the Families policy
programme, with its own review, its own ads rules and its own ongoing obligations. The
game gains nothing from that and inherits all of it.

### Privacy policy URL

The policy is written but not yet hosted. Enabling GitHub Pages over `/docs` serves it at:

```
https://mllagostera.github.io/throwing-bambu/PRIVACY
```

The blob URL works too if something is needed immediately, though it shows the
repository's chrome around the text:

```
https://github.com/mllagostera/throwing-bambu/blob/main/docs/PRIVACY.md
```

### Content rating questionnaire (IARC)

Category: **Game**.

| Question | Answer |
|---|---|
| Violence | Cartoon or fantasy violence, mild |
| Realistic violence toward humans | No |
| Blood or gore | No |
| Sexuality, nudity | No |
| Language | No |
| Controlled substances | No |
| Gambling, simulated gambling | No |
| User interaction | No |
| Shares user location | No |
| Digital purchases | No |

Answer violence honestly: the bamboo cane hits a panda, an explosion plays and the
panda falls. It is pixel art with no blood and no human figures, which is what "mild
cartoon violence" describes. Expect **PEGI 3–7 / ESRB E**.

A rating obtained on false answers can be revoked later, which takes the listing down
with it. There is nothing here worth shading.

### Data safety

Every answer is **no**, and the repository backs each one.

| Question | Answer | Why it is defensible |
|---|---|---|
| Does your app collect or share any of the required user data types? | **No** | No `INTERNET` permission is declared in any manifest. The app is structurally incapable of transmitting anything. |
| Is all user data encrypted in transit? | N/A | No data leaves the device. |
| Do you provide a way to request data deletion? | N/A | Nothing is collected. Uninstalling removes the stored language preference. |

The language preference held in DataStore is **not** collection: it never leaves the
device. Play's definition turns on transmission off-device, and there is none.

When the Bluetooth mode does ship, this section needs revisiting — not because
anything is collected then either, but because Nearby Connections brings Google Play
Services into the picture and the reviewer's questions change shape.

---

## Release

| Field | Value |
|---|---|
| Countries | All *(nothing in the game is region-specific)* |
| Pricing | **Free**, permanently — a paid app cannot be made free's opposite later |
| Track | Internal testing first, then production |
| Artifact | `throwing-bambu-<version>.aab`, from the GitHub release |
| Signing | Play App Signing. The upload key is CI's; see [`CI.md`](CI.md) |

### There is no version to upload yet

`app/build.gradle.kts` derives both the version name and the version code from
`git describe`, and the repository has **no tags at all**. Every build today calls
itself `0.0.0-dev+<sha>` with code `1`, and `release.yml` refuses to publish it.

A release is cut by pushing a tag and by nothing else:

| Tag | versionName | versionCode |
|---|---|---|
| `v0.1.0` | `0.1.0` | `100` |
| `v1.0.0` | `1.0.0` | `10000` |

`v0.1.0` is the better first tag. Play will never accept a version code lower than one
already uploaded, for the life of the app, so starting low leaves room and starting at
10000 spends it for nothing.

Before the tag, `release.yml` needs its four repository secrets set:
`UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`,
`UPLOAD_KEY_PASSWORD`. The workflow checks for them before it builds anything, which
is the only reason a missing one costs seconds instead of ten minutes.

### The application ID is permanent

`com.vansid.panda`. Once an upload reaches Play under that ID it can never be changed
— not the case, not the spelling, not the `panda` that does not match the project's
own name. Renaming it after publication means a new listing and no users. Worth one
last look before the first upload, and no thought at all afterwards.
