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
