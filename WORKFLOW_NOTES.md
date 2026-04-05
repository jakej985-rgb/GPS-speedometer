# Workflow Configuration Notes

These GitHub Actions workflows are configured for a standard Android project structure where:
- The project root is at the repository root.
- The main module is named `app`.

If your project structure differs, you will need to adjust the workflow files (`.github/workflows/build-debug-apk.yml` and `.github/workflows/build-release-apk.yml`).

## 1. Changing the Module Name

If your application module is named something other than `app` (e.g., `mobile`):

1.  **Update Gradle Task:**
    Change `./gradlew :app:assembleDebug` (or `assembleRelease`) to `./gradlew :mobile:assembleDebug`.

2.  **Update Artifact Path:**
    Change `app/build/outputs/apk/debug/*.apk` to `mobile/build/outputs/apk/debug/*.apk`.

## 2. Changing the Project Root

If your Android project is located in a subdirectory (e.g., `android-project/`):

1.  **Set Working Directory:**
    Add `defaults.run.working-directory` to the job configuration.

    Example:
    ```yaml
    jobs:
      build:
        runs-on: ubuntu-latest
        defaults:
          run:
            working-directory: ./android-project
    ```

2.  **Update Artifact Path:**
    Update the path to include the subdirectory, as `upload-artifact` looks for paths relative to the repository root.

    Example:
    `android-project/app/build/outputs/apk/debug/*.apk`

## 3. Artifact Paths

Ensure the `path` under `actions/upload-artifact` matches exactly where your build outputs the APKs. You can verify this by running `./gradlew assembleDebug` locally and checking the output directory.
