# Privacy Policy — Throwing Bambu

**Effective date:** 18 September 2026
**Application:** Throwing Bambu (`com.vansid.panda`)

Throwing Bambu is a game. It has no accounts, no servers and no advertising, and it
is built so that there is nothing to collect in the first place: **the app does not
request the `INTERNET` permission**, so it cannot send anything anywhere, whatever a
policy might promise.

## What we collect

Nothing. We do not collect, store or transmit personal data, device identifiers,
usage statistics or crash reports. There is no analytics SDK, no advertising SDK and
no third-party tracker in the app.

## What stays on your device

Your settings — currently the interface language — are saved on the device so the
game remembers them between sessions. They never leave it, we never see them, and
uninstalling the app deletes them.

## Playing over Bluetooth

When you choose to play against another device, and only then, the game asks for the
permissions it needs to find that device and talk to it. Permissions are never
requested at startup.

- **Bluetooth** (scan, advertise, connect) and, on Android 13 and later, **nearby
  Wi-Fi devices** — to discover the other player and exchange the match.
- **Location**, on Android 11 and earlier only. Android required a location
  permission for Bluetooth Low Energy scanning on those versions. **The game never
  reads your location and never sends it.** On Android 12 and later the app does not
  request location at all.

What travels over that link is the game itself: the match seed, the angle and power
of each throw, and the score. It goes directly between the two devices. It is not
routed through any server of ours, and it is not stored after the match ends.

Bluetooth discovery uses Google Play Services' Nearby Connections API where it is
available. That part of the connection is handled by Google, under
[Google's Privacy Policy](https://policies.google.com/privacy). We receive nothing
from it.

## Children

The game collects no data from anyone, of any age. There is no chat, no user-generated
content and no way for players to exchange anything but the moves of a match.

## Your rights

Since no personal data is collected, there is nothing to access, correct, export or
delete on our side. To remove everything the app has kept on your device, uninstall it.

## Changes

If this policy changes, the new version will be published at this address and the
effective date above will be updated. Material changes will also be noted in the
app's release notes.

## Contact

Questions about this policy: **vansidgg@gmail.com**

Source code: <https://github.com/mllagostera/throwing-bambu>
