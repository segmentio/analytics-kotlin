Releasing
=========

1. Create a new branch called `release/X.Y.Z`
2. `git checkout -b release/X.Y.Z`
3. Change the version in `gradle.properties` and `core/Consants.kt` to your desired release version
4. `git commit -am "Create release X.Y.Z."` (where X.Y.Z is the new version)
5. `git tag -a X.Y.Z -m "Version X.Y.Z"` (where X.Y.Z is the new version)
6. Upgrade to next version by changing version in `gradle.properties`
7. `git commit -am "Prepare snapshot X.Y.Z-SNAPSHOT"`
8. `git push && git push --tags`
9. Create a PR to merge the new branch into `main`
10. The CI pipeline will recognize the tag and upload, close and promote the artifacts, and generate changelog automatically

Example (stable release)
========
1. Current VERSION_NAME in `gradle.properties` and LIBRARY_VERSION in `core/Consants.kt` is 1.3.0
2. `git checkout -b release/1.3.1`
3. Change VERSION_NAME = 1.3.1 (next higher version)
4. `git commit -am "Create release 1.3.1"`
5. `git tag -a 1.3.1 -m "Version 1.3.1"`
6. `git push && git push --tags`
7. Change VERSION_NAME = 1.3.2 (next higher version)
8. `git commit -am "Prepare snapshot 1.3.2-SNAPSHOT"`
9. `git push && git push --tags`
10. Merging PR main will create a snapshot release 1.3.2-SNAPSHOT and tag push will create stable release 1.3.1 with auto-generated changelog