# SSH Remote Exec

[English](README.md) | [简体中文](README_zh.md) | [Developer Docs (doc/)](doc/README.md)

A lightweight, secure, and modern Android native app and 1x1 Home Screen Widget tool for triggering remote server scripts via SSH.

Trigger Wake-on-LAN (WoL), restart Docker containers, run deployment pipelines, or execute any automation command on your home lab or cloud servers with a single tap on your Android home screen.

---

## 🌟 Key Features

* **Three-Tier Decoupled Architecture**:
  * **Credential Store**: Supports plaintext passwords and OpenSSH private keys (`Ed25519`, `RSA`, `ECDSA`, etc.) with optional passphrases.
  * **Server Management**: Configure host IP/domain, SSH port, username, and bound credentials. Includes distinct non-intrusive authentication badges and in-app connection testing with real-time handshake diagnostics.
  * **Command Management**: Define scripts, custom timeouts, and one-tap test execution with console feedback and clipboard copy shortcuts.
* **Independent Home Screen Widgets (AppWidget)**:
  * **Multi-Instance**: Place multiple 1x1 widgets on your launcher, each bound to distinct commands and servers.
  * **Two-Way Setup**: Pin widgets directly from within the app or add them via your launcher's widget menu.
  * **Micro-Animations & Status Feedback**: Visual running indicator (blue glow), success state (green checkmark with swift 800ms reset), and error state (red alert).
* **Full Internationalization (i18n)**:
  * Built-in English and Chinese (Simplified) support, automatically adapting to your system language.
* **Hardware-Backed Cryptographic Security**:
  * Utilizes Android Keystore with AES-256-GCM authenticated encryption for all stored credentials.
* **Zero Standby Battery Drain**:
  * Requests only `INTERNET` and `ACCESS_NETWORK_STATE` permissions. No background services or polling daemons.

---

## 🛠️ Local Linux Build Guide (Prerequisites & Commands)

If you wish to compile the project locally on a Linux distribution (Ubuntu, Debian, or WSL), follow these steps:

### 1. Install System Dependencies (JDK 17, Curl, Unzip)
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk curl unzip git
```

### 2. Download and Set Up Android SDK Command-line Tools
```bash
# Create SDK directory
mkdir -p ~/android-sdk/cmdline-tools

# Download official Google Android Command-line Tools
curl -o /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q /tmp/cmdline-tools.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
rm -f /tmp/cmdline-tools.zip

# Accept SDK licenses and install required platform & build-tools (API 34)
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
~/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

### 3. Build & Run Tests
Navigate to the project root directory:
```bash
# Set SDK location in local.properties
echo "sdk.dir=$HOME/android-sdk" > local.properties

# Run automated unit tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug
```
Output APK location: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🧹 How to Clean Up the Build Environment

If you want to completely remove the build tools and reclaim disk space on your Linux machine:

```bash
# 1. Delete the installed Android SDK directory (~1.1 GB freed)
rm -rf ~/android-sdk

# 2. Remove the local SDK pointer file in the project
rm -f local.properties

# 3. (Optional) Clear Gradle build cache and daemons (~/.gradle)
rm -rf ~/.gradle

# 4. (Optional) Remove OpenJDK 17 if no longer needed
sudo apt remove --purge -y openjdk-17-jdk
sudo apt autoremove -y
```

---

## 🚀 GitHub Actions Cloud CI/CD Guide (No Local Setup Required)

This repository includes a ready-to-use GitHub Actions workflow (`.github/workflows/build.yml`) that automatically tests and packages the app on every push.

### 1. Automated Builds
* Pushing to `main` triggers automated unit testing and builds both `app-release.apk` (optimized ProGuard build) and `app-debug.apk`.
* Download the compiled APKs directly from the **Releases** section on the repository homepage or under **Actions** -> **Artifacts**.

### 2. Automated Official Release Packaging
Configure the following 4 secrets under **Settings** -> **Secrets and variables** -> **Actions**:
* `SIGNING_KEYSTORE_BASE64`: Base64 string of your `release.jks`.
* `KEY_STORE_PASSWORD`: Keystore password.
* `ALIAS`: Key alias.
* `KEY_PASSWORD`: Key password.

Tag and push a release:
```bash
git tag v1.0.0
git push origin v1.0.0
```
GitHub Actions will automatically run tests, sign the APK, and publish an official GitHub Release.

---

## 🏛️ Tech Stack & Architecture

* **Language**: Kotlin 1.9 + Coroutines
* **UI**: Material Design 3 + ViewBinding + Single Activity Architecture
* **Persistence**: Jetpack Room 2.6 (KSP code generation)
* **Encryption**: Android Keystore + AES-256-GCM
* **SSH Engine**: `com.hierynomus:sshj:0.38.0` + BouncyCastle (`bcprov-jdk18on`)
* **Widget Engine**: `AppWidgetProvider` + `RemoteViews` + `requestPinAppWidget`
