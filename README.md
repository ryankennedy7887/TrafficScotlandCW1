# Scotland Traffic | Android CW1

Native Android Studio project for the Mobile Platform Development Traffic Scotland coursework. The app is written in Java and uses separate XML layouts for portrait and landscape. **The bundled snapshots are demonstration data, not a live traffic service.**

## Run

1. Unzip the source archive and open the `TrafficScotlandCW1` folder in Android Studio (the directory containing `settings.gradle`).
2. Use JDK 17 and install Android SDK Platform 35 and Build-Tools 34.0.0 in SDK Manager if prompted. The project includes the Gradle 8.9 wrapper. Use an emulator or device running Android 8.0 (API 26) or later.
3. Sync and run the `app` configuration. The default display uses the supplied 26 October 2026 samples unless you have a newer locally cached or live publication.
4. To test the exact submitted source, clone it into a fresh directory, open that clone in Android Studio, sync, build and run before uploading the final package.

```sh
./gradlew clean testDebugUnitTest assembleDebug
```

On Windows, run `gradlew.bat clean testDebugUnitTest assembleDebug`. The APK builds to `app/build/outputs/apk/debug/app-debug.apk`. It does not include any credentials.

## Use and verify

- **Results:** Tap a row for the original public comment, record type, validity, impact and geographic details, where available. On a fresh sample run, expect 7 incidents and 452 roadworks.
- **Road search:** Try `M8` or `Phoenix Rd`. A full road number such as `M8` does not match `M80` merely because of the prefix. Roads mentioned in a comment or location descriptor include connected roads; a match does not necessarily mean the works are physically on every road listed.
- **Distance:** The Glasgow selector uses a fixed city centre of 55.8642° N, 4.2518° W. Distance is straight-line to a point or the nearest point of a simplified link, not driving distance. Results without coordinates remain on the unfiltered list.
- **Map:** Map shows the current filter results; Map inside details shows that one situation. A point plots as a marker; a link plots as a straight line between the supplied endpoints, with a midpoint marker. Tap a marker for details, pinch or double tap to zoom, drag to pan, and tap Fit to reset. Pins can overlap at broad zooms. This is an overview map, not a road-routing map.
- **Rotation:** Portrait switches between results and the detail/map panel. Landscape displays results and details/map side by side. The same Activity-scoped ViewModel holds filters, the selected ID and the active panel; the map camera refits after rotation.

## Add live Traffic Scotland access

1. Use your GCU email for [DATEX II registration](https://developer.trafficscotland.org/applications/datex-ii/developer-guide). Request access and wait for approval, then retrieve the client ID and key in the portal.
2. In the app, tap **API access**, type the credentials, then select **Connect**. Do not paste credentials into chat, issue reports, a recording, source code or GitHub. **Remember on this device** is opt-in and stores encrypted credentials using Android Keystore. **Forget** removes the saved credentials without clearing the traffic cache.
3. Check that each feed independently shows **Live**. Capture a matching event and confirm source parity before recording. If it shows an access error, verify approval and compare the official portal/current starter with `TrafficRepository.java`. The app uses the coursework starter's `Authorization` format (Base64 of `clientID:clientKey`, without a prefix). It does not follow redirects with credentials.
4. Disconnect the device and refresh. After a successful live fetch, **Cached** should show the preceding validated feed independently for incidents and works. If you have never downloaded a valid live snapshot, expect **Sample**. A legitimate empty live publication is not replaced by sample data.

Official paths (same base host and version as the assignment):

- `https://datex2.trafficscotland.org/rest/2.3/publications/UnplannedEvents/Content.xml`
- `https://datex2.trafficscotland.org/rest/2.3/publications/CurrentRoadworks/Content.xml`

Startup, manual refresh and periodic refresh use a single background scheduled executor. Auto-refresh is once per hour while the app is in the foreground, with a refresh on return if overdue. A failed or malformed response never replaces a validated cache. For a demonstration only, shorten `REFRESH_MINUTES` in `TrafficViewModel.java`, verify a second fetch, and restore 60 before the final build.

## Code tour

| Component | Responsibility |
| --- | --- |
| `TrafficRepository` | HTTPS requests, UTF-8 response strings, per-feed atomic cache, sample fallback and optional encrypted credential storage |
| `DatexParser` | Namespace-aware `XmlPullParser` over a `StringReader`, with nested record, impact and coordinate association |
| `TrafficData` | Immutable situations, records, impact, places, route extraction, classification and distance |
| `TrafficViewModel` | Worker, refresh schedule, filters, selected ID and observable state |
| `MainActivity`, `ResultsFragment`, `DetailsFragment` | Native controls, selectable rows, detail rendering and rotation |
| `ScotlandMapView`, `SituationIcon` | Native Canvas map and original geometric category icons |

Runtime dependencies are AndroidX Fragment and Lifecycle Jetpack components, not a third-party runtime framework. The JVM-only tests use JUnit and kXML as a local `XmlPullParser` implementation; they are not packaged in the app.

## Credits and academic use

- Feed data and coursework snapshots: Traffic Scotland/Transport Scotland. Keep the snapshots within coursework use unless wider redistribution is allowed; if necessary, use a marker-accessible repository rather than a public one.
- Map: made with [Natural Earth 1:50m map units](https://www.naturalearthdata.com/downloads/50m-cultural-vectors/50m-admin-0-details/). The extracted British Isles boundaries are [public-domain data](https://www.naturalearthdata.com/about/terms-of-use/).
- Icons: eight original geometric glyphs and an original launcher icon created for this app with AI assistance. No external icon pack is included. The supplied snapshots exercise six of the eight situation glyphs; collision and other-incident variants require different XML records.
- Parser interface: [Android Developers, XmlPullParser](https://developer.android.com/reference/org/xmlpull/v1/XmlPullParser).
- The coursework allows guided AI use with full disclosure. Document the generated implementation, tests, troubleshooting and drafting in the Word summary, retain the exact prompts, and append details of your own changes and verification.

## Before submission

- Rename the project (`rootProject.name` in `settings.gradle`) and submission items to the `Surname_GivenName_StudentId` convention. You do not need to disclose your student ID in chat.
- Confirm the correct deadline in GCULearn. The PDF gives noon on 23 November 2026; the assignment Word document gives 5 pm that day. Use noon until confirmed otherwise.
- Finish the live/cached checks and record actual outcomes. Record a 5–10 minute voiceover on an emulator or phone showing both orientations, situation details, delay variants, filters and maps. Show the actual data source labels. Name the OneDrive video `Surname_GivenName_StudentId_Video.mp4` and test the “Anyone with the link” setting.
- Copy the final APK out of the build directory before using **Build > Clean Project**. Upload the clean Android Studio source to GitHub **without the APK**, credentials or `local.properties`. Clone it into a fresh folder and test the import. Include the GitHub source and OneDrive MP4 links in `Surname_GivenName_StudentId.docx` alongside the test strategy and AI disclosure.
- The backup ZIP contains the correctly named folder with the APK, the video or a file containing its link, and a **separate clean Android Studio project ZIP**. Exclude `.gradle`, build folders, credentials and local SDK configuration from the inner source ZIP. Do not password-protect the outer ZIP.
