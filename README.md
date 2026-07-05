# Artifact

Artifact is an Android app for cataloging collectible retro-computing items.

The first version is local-first:

- browse the collection
- search by manufacturer, type, serial number, or location
- manage stored items
- add an item by taking one or more photos and entering manufacturer, type, serial number, and location
- store the catalog in a local Room database with photo files kept in the app's private pictures directory

Server backup is intentionally left as a later repository layer. The current schema uses stable item/photo IDs and timestamps so a future sync adapter can be added without changing the add/browse flow.

## Build

This checkout uses the Android SDK installed at `/home/nils/metric/.android-sdk` through `local.properties`.

```bash
./gradlew assembleDebug
```

On another machine, open the project in Android Studio or update `local.properties` to point at that machine's Android SDK.
