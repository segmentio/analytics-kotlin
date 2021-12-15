Update Version
=========
1. Update VERSION_CODE in `gradle.properties`
2. Update VERSION_NAME in `gradle.properties`
3. Update LIBRARY_VERSION in `core/Constants.kt`

Releasing
=========

1. Create a new branch called `release/X.Y.Z`
2. `git checkout -b release/X.Y.Z`
3. Change the version to your desired release version (see `Update Version`)
4. `git commit -am "Prepare release X.Y.Z."` (where X.Y.Z is the new version)
5. `git tag -a X.Y.Z -m "Version X.Y.Z"` (where X.Y.Z is the new version)
6. `git push && git push --tags`
7. The CI pipeline will recognize the tag and upload, close and promote the artifacts, and generate changelog automatically
8. Create a PR to merge the new branch into `main`
9. The CI pipeline will trigger a snapshot workflow and upload the artifact.

Example (stable release)
========
1. Current version is 1.3.0
2. `git checkout -b release/1.3.1`
3. Change version to 1.3.1 (next higher version, see `Update Version`)
4. `git commit -am "Prepare release 1.3.1"`
5. `git tag -a 1.3.1 -m "Version 1.3.1"`
6. `git push && git push --tags`. This tag push will create stable release 1.3.1 with auto-generated changelog
8. Create a PR to merge the new branch into `main`. Merging PR main will create a snapshot release 1.3.1-SNAPSHOT