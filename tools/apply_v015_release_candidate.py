from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Missing patch anchor: {label}")
    return text.replace(old, new, 1)


# Final Android identity and release version.
path = Path("app/build.gradle.kts")
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    '        applicationId = "com.yourname.pdftoolkit"',
    '        applicationId = "com.eyadpdf.android"',
    "final application id",
)
text = text.replace(
    '                println("KEY_ALIAS value: \'${if (kAlias.isNullOrEmpty()) "null/empty" else kAlias}\'") \n',
    '',
)
text = replace_once(
    text,
    '                outputImpl.outputFileName = "pdftoolkit-${flavorName}-v${variant.versionName}.apk"',
    '                outputImpl.outputFileName = "Eyad-PDF-Android-${flavorName}-v${variant.versionName}.apk"',
    "release apk filename",
)
path.write_text(text, encoding="utf-8")

path = Path("gradle.properties")
text = path.read_text(encoding="utf-8")
for old, new in (
    ("VERSION_CODE=211", "VERSION_CODE=215"),
    ("VERSION_NAME=1.3.211", "VERSION_NAME=0.15.0"),
    ("APP_VERSION_CODE=211", "APP_VERSION_CODE=215"),
    ("APP_VERSION_NAME=1.3.211", "APP_VERSION_NAME=0.15.0"),
):
    text = replace_once(text, old, new, old)
path.write_text(text, encoding="utf-8")

# Localize the modern home workspace instead of showing English inside Arabic UI.
path = Path("app/src/main/java/com/yourname/pdftoolkit/ui/screens/PremiumToolsScreen.kt")
text = path.read_text(encoding="utf-8")
text = replace_once(
    text,
    "import androidx.compose.ui.platform.LocalContext\n",
    "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.res.stringResource\n",
    "home stringResource import",
)
text = replace_once(
    text,
    "import com.yourname.pdftoolkit.data.SafUriManager\n",
    "import com.yourname.pdftoolkit.R\nimport com.yourname.pdftoolkit.data.SafUriManager\n",
    "home R import",
)
old_section = '''private fun sectionLabel(section: ToolSection?): String = when (section) {
    null -> "All tools"
    ToolSection.QUICK_ACTIONS -> "Quick actions"
    ToolSection.ORGANIZE -> "Organize PDF"
    ToolSection.CONVERT -> "Convert"
    ToolSection.SECURITY -> "Security & markup"
    ToolSection.IMAGE_TOOLS -> "Images & OCR"
    ToolSection.VIEW_EXPORT -> "View & export"
}
'''
new_section = '''private fun sectionSearchLabel(section: ToolSection?): String = when (section) {
    null -> "All tools"
    ToolSection.QUICK_ACTIONS -> "Quick actions"
    ToolSection.ORGANIZE -> "Organize PDF"
    ToolSection.CONVERT -> "Convert"
    ToolSection.SECURITY -> "Security and markup"
    ToolSection.IMAGE_TOOLS -> "Images and OCR"
    ToolSection.VIEW_EXPORT -> "View and export"
}

@Composable
private fun sectionDisplayLabel(section: ToolSection?): String = when (section) {
    null -> stringResource(R.string.premium_all_tools)
    ToolSection.QUICK_ACTIONS -> stringResource(R.string.category_quick_actions)
    ToolSection.ORGANIZE -> stringResource(R.string.category_organize)
    ToolSection.CONVERT -> stringResource(R.string.category_convert)
    ToolSection.SECURITY -> stringResource(R.string.category_security)
    ToolSection.IMAGE_TOOLS -> stringResource(R.string.category_image_tools)
    ToolSection.VIEW_EXPORT -> stringResource(R.string.category_view_export)
}
'''
text = replace_once(text, old_section, new_section, "localized section labels")
text = replace_once(
    text,
    "            sectionLabel(tool.source.section).contains(query, ignoreCase = true)",
    "            sectionSearchLabel(tool.source.section).contains(query, ignoreCase = true)",
    "section search label",
)
text = text.replace('contentDescription = "Eyad PDF"', 'contentDescription = stringResource(R.string.app_name)')
text = replace_once(text, 'label = { Text("All") },', 'label = { Text(stringResource(R.string.action_select_all)) },', "rail all label")
text = text.replace('Text(sectionLabel(section), maxLines = 1)', 'Text(sectionDisplayLabel(section), maxLines = 1)')
text = text.replace('Text(sectionLabel(section))', 'Text(sectionDisplayLabel(section))')
text = replace_once(text, 'placeholder = { Text("Search all PDF tools") },', 'placeholder = { Text(stringResource(R.string.premium_search_tools)) },', "home search placeholder")
text = replace_once(text, 'Icon(Icons.Default.Clear, contentDescription = "Clear search")', 'Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear_search))', "home clear search")
text = replace_once(text, 'label = { Text("All tools") },', 'label = { Text(stringResource(R.string.premium_all_tools)) },', "mobile all tools")
text = replace_once(text, 'text = sectionLabel(selectedSection),', 'text = sectionDisplayLabel(selectedSection),', "section heading")
text = replace_once(text, 'text = "Choose a tool and work completely offline",', 'text = stringResource(R.string.premium_choose_tool_offline),', "offline helper")
text = replace_once(text, 'text = "${visibleTools.size} tools",', 'text = stringResource(R.string.premium_tool_count, visibleTools.size),', "tool count")
text = replace_once(text, 'text = "Eyad PDF",', 'text = stringResource(R.string.app_name),', "hero app name")
text = replace_once(text, 'text = "Your private document workspace",', 'text = stringResource(R.string.premium_private_workspace),', "hero subtitle")
text = replace_once(text, 'text = "Offline • private • $toolCount tools",', 'text = stringResource(R.string.premium_offline_private_tools, toolCount),', "hero privacy line")
text = text.replace('Text("Open PDF")', 'Text(stringResource(R.string.action_open_pdf))')
path.write_text(text, encoding="utf-8")

# Localize the Files tab and keep its labels consistent with the app language.
path = Path("app/src/main/java/com/yourname/pdftoolkit/ui/screens/FilesScreen.kt")
text = path.read_text(encoding="utf-8")
old_filter = '''enum class FileFilter(val title: String, val icon: ImageVector) {
    ALL("All", Icons.Default.Folder),
    PDF("PDF", Icons.Default.PictureAsPdf)
}
'''
new_filter = '''enum class FileFilter(val titleResId: Int, val icon: ImageVector) {
    ALL(R.string.files_filter_all, Icons.Default.Folder),
    PDF(R.string.files_filter_pdf, Icons.Default.PictureAsPdf)
}
'''
text = replace_once(text, old_filter, new_filter, "localized file filters")
text = replace_once(text, 'text = "Access your recent documents",', 'text = stringResource(R.string.files_subtitle),', "files subtitle")
text = replace_once(text, 'text = "Open PDF Document",', 'text = stringResource(R.string.files_open_pdf_title),', "files open title")
text = replace_once(text, 'text = "Browse and open PDF files",', 'text = stringResource(R.string.files_open_pdf_subtitle),', "files open subtitle")
text = replace_once(text, 'label = { Text(filter.title) },', 'label = { Text(stringResource(filter.titleResId)) },', "file filter label")
text = replace_once(text, 'text = "Recent Files",', 'text = stringResource(R.string.files_recent_title),', "recent files heading")
text = replace_once(text, 'text = "No recent files",', 'text = stringResource(R.string.files_empty_title),', "files empty title")
text = replace_once(text, 'text = "Open a document to see it here",', 'text = stringResource(R.string.files_empty_subtitle),', "files empty subtitle")
path.write_text(text, encoding="utf-8")

# Add English and Arabic resources used by the modern workspace.
english_additions = '''

    <!-- Eyad PDF v0.15 adaptive workspace -->
    <string name="premium_private_workspace">Your private document workspace</string>
    <string name="premium_offline_private_tools">Offline • private • %1$d tools</string>
    <string name="premium_search_tools">Search all PDF tools</string>
    <string name="premium_all_tools">All tools</string>
    <string name="premium_choose_tool_offline">Choose a tool and work completely offline</string>
    <string name="premium_tool_count">%1$d tools</string>
    <string name="files_filter_all">All</string>
    <string name="files_filter_pdf">PDF</string>
    <string name="files_subtitle">Access your recent documents</string>
    <string name="files_open_pdf_title">Open PDF document</string>
    <string name="files_open_pdf_subtitle">Browse and open PDF files</string>
    <string name="files_recent_title">Recent files</string>
    <string name="files_empty_title">No recent files</string>
    <string name="files_empty_subtitle">Open a document to see it here</string>
'''
path = Path("app/src/main/res/values/strings.xml")
text = path.read_text(encoding="utf-8")
text = replace_once(text, "\n</resources>", english_additions + "\n</resources>", "English v0.15 resources")
path.write_text(text, encoding="utf-8")

arabic_additions = '''

    <!-- Eyad PDF v0.15 adaptive workspace -->
    <string name="premium_private_workspace">مساحة عملك الخاصة للمستندات</string>
    <string name="premium_offline_private_tools">دون اتصال • خاص • %1$d أداة</string>
    <string name="premium_search_tools">ابحث في جميع أدوات PDF</string>
    <string name="premium_all_tools">كل الأدوات</string>
    <string name="premium_choose_tool_offline">اختر أداة واعمل بالكامل دون اتصال</string>
    <string name="premium_tool_count">%1$d أداة</string>
    <string name="files_filter_all">الكل</string>
    <string name="files_filter_pdf">PDF</string>
    <string name="files_subtitle">الوصول إلى مستنداتك الأخيرة</string>
    <string name="files_open_pdf_title">فتح مستند PDF</string>
    <string name="files_open_pdf_subtitle">تصفح ملفات PDF وفتحها</string>
    <string name="files_recent_title">الملفات الأخيرة</string>
    <string name="files_empty_title">لا توجد ملفات أخيرة</string>
    <string name="files_empty_subtitle">افتح مستندًا ليظهر هنا</string>
'''
path = Path("app/src/main/res/values-ar/strings.xml")
text = path.read_text(encoding="utf-8")
text = replace_once(text, "\n</resources>", arabic_additions + "\n</resources>", "Arabic v0.15 resources")
path.write_text(text, encoding="utf-8")

# Replace stale inherited documentation with the actual Eyad PDF project state.
Path("ANDROID_BUILD_VERSION.txt").write_text(
    "Eyad PDF Android v0.15.0\n"
    "Consolidated release candidate\n"
    "Application ID: com.eyadpdf.android\n"
    "Offline open-source flavor\n"
    "Scroll-aware tablet and phone workspace\n",
    encoding="utf-8",
)

Path("EYAD_PDF_STATUS.md").write_text(
    "# Eyad PDF Android v0.15\n\n"
    "This release candidate consolidates the physical-tablet review, council audit, "
    "identity cleanup, localization cleanup and release automation into one branch.\n\n"
    "## Frozen behavior\n\n"
    "- The main app bar hides while scrolling down and returns on upward scroll.\n"
    "- The hero, search and section heading scroll with the tool catalogue.\n"
    "- Tools/Files navigation does not use a translucent cross-fade.\n"
    "- PDF processing remains local and the open-source flavor has no INTERNET permission.\n\n"
    "## Release identity\n\n"
    "- Application ID: `com.eyadpdf.android`\n"
    "- Version name: `0.15.0`\n"
    "- Version code: `215`\n"
    "- Permanent updates require the same protected release keystore for every APK.\n",
    encoding="utf-8",
)

Path("README.md").write_text(
    "# Eyad PDF\n\n"
    "Eyad PDF is an offline Android workspace for viewing, organizing, converting, "
    "compressing, securing and extracting content from PDF and image files.\n\n"
    "## Current release candidate\n\n"
    "- Branch: `android/release-candidate-v0.15`\n"
    "- Application ID: `com.eyadpdf.android`\n"
    "- Version: `0.15.0` (`versionCode 215`)\n"
    "- Minimum Android: API 26\n"
    "- Target/compile SDK: 36\n"
    "- JDK: 17\n"
    "- UI: Kotlin + Jetpack Compose\n"
    "- Offline distribution flavor: `opensource`\n\n"
    "## Privacy contract\n\n"
    "The open-source build is checked in CI to ensure that no INTERNET permission is "
    "present. PDF and image operations run locally on the device.\n\n"
    "## Build verification\n\n"
    "```bash\n"
    "./gradlew --no-daemon :app:testOpensourceDebugUnitTest :app:lintOpensourceDebug :app:assembleOpensourceDebug\n"
    "```\n\n"
    "## Permanent signed releases\n\n"
    "The manual `Eyad PDF Signed Release` workflow expects these protected GitHub "
    "Actions secrets: `ANDROID_KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, "
    "and `KEY_PASSWORD`. Never commit the keystore or its passwords to the repository.\n\n"
    "## Attribution\n\n"
    "The project began from the Apache-2.0 `Karna14314/Pdf_Tools` codebase. See "
    "`THIRD_PARTY_ATTRIBUTIONS.md` and `LICENSE`.\n",
    encoding="utf-8",
)

Path("docs/V015_COUNCIL_RELEASE_REVIEW.md").write_text(
    "# Eyad PDF v0.15 Council Release Review\n\n"
    "Review basis: the v0.14 source branch, successful Android quality-gate artifact, "
    "and the physical tablet recording supplied on 2026-08-06.\n\n"
    "## Council decision\n\n"
    "Ship one consolidated v0.15 release candidate rather than multiple visual APKs. "
    "Freeze the working PDF engines and limit changes to release blockers, consistency, "
    "localization and automated verification.\n\n"
    "## UX and accessibility\n\n"
    "Accepted the scroll-aware top bar. The large hero and search controls now leave the "
    "viewport naturally, preserving vertical space. Hard-coded English in the modern "
    "Tools and Files surfaces is replaced with English/Arabic resources.\n\n"
    "## Android architecture and performance\n\n"
    "No risky PDF-engine rewrite is included. Existing viewer, SAF and operation flows "
    "remain frozen for this candidate. Main-route cross-fades remain disabled to prevent "
    "the translucent overlap observed in the recording.\n\n"
    "## Privacy and security\n\n"
    "The open-source flavor remains offline and CI rejects any INTERNET permission. The "
    "release keystore must remain outside the public repository and be supplied only via "
    "protected Actions secrets.\n\n"
    "## Release engineering\n\n"
    "The inherited package ID and stale v0.12/v0.13 artifact names were release blockers. "
    "v0.15 uses `com.eyadpdf.android`, version `0.15.0`/`215`, dynamic artifact names, "
    "unit tests, lint, APK assembly, checksums and a separate signed-release workflow.\n\n"
    "## QA gate\n\n"
    "A candidate is releasable only when unit tests, lint, offline-manifest validation and "
    "APK assembly all pass on the final commit. A signed APK must also pass Android's "
    "`apksigner verify` before distribution.\n\n"
    "## One unavoidable installation transition\n\n"
    "Older debug builds use a different application ID/signing identity. Installing the "
    "first permanent v0.15 release may require one uninstall. Every later APK signed with "
    "the same protected key can update normally without uninstalling.\n",
    encoding="utf-8",
)

# Stronger CI: test, lint, privacy check, identity check, build, checksum and correctly named artifact.
Path(".github/workflows/android-quality-gate.yml").write_text(
    '''name: Eyad PDF Android Quality Gate

on:
  push:
    branches: [ main, 'android/**' ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:

permissions:
  contents: read

jobs:
  build-and-verify:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Verify release identity and offline policy
        shell: bash
        run: |
          set -euo pipefail
          grep -q 'applicationId = "com.eyadpdf.android"' app/build.gradle.kts
          grep -q '^APP_VERSION_NAME=0.15.0$' gradle.properties
          grep -q '^APP_VERSION_CODE=215$' gradle.properties
          if grep -R "android.permission.INTERNET" app/src/main/AndroidManifest.xml app/src/*/AndroidManifest.xml 2>/dev/null; then
            echo "INTERNET permission is forbidden in the Eyad PDF open-source build."
            exit 1
          fi

      - name: Test, lint and build open-source APK
        shell: bash
        run: |
          chmod +x gradlew
          ./gradlew --no-daemon \
            :app:testOpensourceDebugUnitTest \
            :app:lintOpensourceDebug \
            :app:assembleOpensourceDebug

      - name: Prepare named APK and checksum
        id: artifact
        shell: bash
        run: |
          set -euo pipefail
          VERSION="$(grep '^APP_VERSION_NAME=' gradle.properties | cut -d= -f2-)"
          APK="$(find app/build/outputs/apk/opensource/debug -type f -name '*.apk' | head -n 1)"
          test -n "$APK"
          mkdir -p artifacts
          NAME="Eyad-PDF-Android-v${VERSION}-opensource-debug.apk"
          cp "$APK" "artifacts/$NAME"
          sha256sum "artifacts/$NAME" > "artifacts/$NAME.sha256"
          echo "name=Eyad-PDF-Android-v${VERSION}-opensource-debug" >> "$GITHUB_OUTPUT"

      - name: Upload verified Eyad PDF APK
        uses: actions/upload-artifact@v4
        with:
          name: ${{ steps.artifact.outputs.name }}
          path: artifacts/*
          if-no-files-found: error
          retention-days: 30
''',
    encoding="utf-8",
)

# Manual permanent release workflow. It cannot run without protected signing secrets.
Path(".github/workflows/android-signed-release.yml").write_text(
    '''name: Eyad PDF Signed Release

on:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  signed-release:
    runs-on: ubuntu-latest
    timeout-minutes: 50
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle

      - name: Verify protected signing inputs
        shell: bash
        env:
          ANDROID_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          set -euo pipefail
          test -n "$ANDROID_KEYSTORE_BASE64"
          test -n "$KEYSTORE_PASSWORD"
          test -n "$KEY_ALIAS"
          test -n "$KEY_PASSWORD"
          printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/eyad-pdf-release.jks"
          test -s "$RUNNER_TEMP/eyad-pdf-release.jks"

      - name: Test, lint and build signed release
        shell: bash
        env:
          ANDROID_KEYSTORE_FILE: ${{ runner.temp }}/eyad-pdf-release.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          chmod +x gradlew
          ./gradlew --no-daemon \
            :app:testOpensourceDebugUnitTest \
            :app:lintOpensourceRelease \
            :app:assembleOpensourceRelease

      - name: Verify signature and prepare release artifact
        id: release
        shell: bash
        run: |
          set -euo pipefail
          VERSION="$(grep '^APP_VERSION_NAME=' gradle.properties | cut -d= -f2-)"
          APK="$(find app/build/outputs/apk/opensource/release -type f -name '*.apk' | head -n 1)"
          test -n "$APK"
          APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -type f -name apksigner | sort -V | tail -n 1)"
          test -x "$APKSIGNER"
          "$APKSIGNER" verify --verbose --print-certs "$APK"
          mkdir -p artifacts
          NAME="Eyad-PDF-Android-v${VERSION}-signed.apk"
          cp "$APK" "artifacts/$NAME"
          sha256sum "artifacts/$NAME" > "artifacts/$NAME.sha256"
          echo "name=Eyad-PDF-Android-v${VERSION}-signed" >> "$GITHUB_OUTPUT"

      - name: Upload permanent signed APK
        uses: actions/upload-artifact@v4
        with:
          name: ${{ steps.release.outputs.name }}
          path: artifacts/*
          if-no-files-found: error
          retention-days: 90

      - name: Remove signing material
        if: always()
        shell: bash
        run: rm -f "$RUNNER_TEMP/eyad-pdf-release.jks"
''',
    encoding="utf-8",
)
