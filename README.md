# Artifact

Artifact is an Android app for cataloging collectible retro-computing items.

The app is local-first, with optional self-hosted sync:

- browse the collection
- search by manufacturer, type, serial number, or location
- manage stored items
- add an item by taking one or more photos and entering manufacturer, type, serial number, and location
- store the catalog in a local Room database with photo files kept in the app's private pictures directory
- export/restore the complete collection as an `.artifactbackup` archive
- push/pull the same archive format to a self-hosted Artifact server

The first server sync path is full collection push/pull. Pulling from the server
replaces the local collection, photos, collection settings, and UID history with
the server copy; it does not merge concurrent phone and server edits.

## Server Sync

The companion server lives in `~/github/artifact_server` and uses only Python's
standard library.

Run it on the machine that should host the collection:

```bash
cd ~/github/artifact_server
ARTIFACT_SYNC_TOKEN='choose-a-long-random-token' python3 artifact_server.py --host 0.0.0.0 --port 8080
```

Then, in the app:

1. Open `Collection Settings`.
2. Enter the server URL, for example `http://192.168.1.50:8080`.
3. Enter the same access token.
4. Tap `Push to Server` to upload the phone collection.
5. Edit the collection in the server web UI.
6. Tap `Pull from Server` to synchronize the phone from the server.

## Catalog Seed

Artifact can bake a manufacturer/model reference catalog into the APK for offline autocomplete.

Export these CSV files from Directus:

- `import/manufacturers.csv` with `id,name,notes`
- `import/manufacturer_models.csv` with `manufacturer,name,production_year_start,production_year_end`; `manufacturer` references `manufacturers.csv` `id`

Then regenerate the Android asset:

```bash
python3 tools/import_catalog_csv.py
```

The generated app asset is `app/src/main/assets/catalog_seed.json`.

## Build

This checkout uses the Android SDK installed at `/home/nils/metric/.android-sdk` through `local.properties`.

```bash
./gradlew assembleDebug
```

On another machine, open the project in Android Studio or update `local.properties` to point at that machine's Android SDK.
