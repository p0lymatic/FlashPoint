# FlashPoint: Android Project Architecture & Base Implementation

## Goal
Implement the initial, robust foundation for **FlashPoint** — an open-source Android application (Kotlin + Jetpack Compose) to create bootable USB drives and install Ventoy over USB-OTG without root.

## Technical Architecture & Requirements

### 1. Build & Gradle Structure
- Standard Gradle multi-project setup with Kotlin DSL (`settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`).
- `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`.
- Jetpack Compose with Material 3 (dark theme first, dynamic color support).
- Kotlin Coroutines & Flow for reactive USB state and asynchronous byte-streaming.
- No heavy external third-party ad/tracking bloat. Only clean AndroidX, KotlinX, and native USB Host APIs.

### 2. Android Manifest & Permissions (`app/src/main/AndroidManifest.xml`)
- `<uses-feature android:name="android.hardware.usb.host" android:required="true" />`
- `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`
- `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />`
- `<uses-permission android:name="android.permission.INTERNET" />` (to download upstream Ventoy releases directly from GitHub)
- Intent filters for USB device attachments (`android.hardware.usb.action.USB_DEVICE_ATTACHED`).

### 3. USB Subsystem & Userspace SCSI Mass Storage Driver
In `com.p0lymatic.flashpoint.usb`:
- **`UsbConnectionState`**: Sealed class/interface representing USB connection states (NoDevice, PermissionRequested, Connected(device, capacityBytes, vendor, model), Error).
- **`UsbDeviceMonitor`**: Manages `UsbManager`, registers broadcast receivers for USB attachment/detachment and permission grants, and handles `requestPermission()`.
- **`UsbMassStorageStreamer`**: High-performance userspace USB Mass Storage driver:
  - Discovers Bulk IN and Bulk OUT endpoints on Interface Class 8 (Mass Storage), Subclass 6 (SCSI transparent command set), Protocol 80 (Bulk-Only Transport - BOT).
  - Implements Command Block Wrapper (CBW) and Command Status Wrapper (CSW) structures for BOT.
  - Implements SCSI CDBs:
    - `INQUIRY` (0x12) -> Device info
    - `READ CAPACITY (10)` (0x25) and `(16)` (0x9E) -> Total sectors and block size (typically 512)
    - `WRITE (10)` (0x2A) and `WRITE (16)` (0x8A) -> Streaming sector write operations
    - `TEST UNIT READY` (0x00) -> Ready state check
    - `REQUEST SENSE` (0x03) -> Error diagnostics

### 4. Image Writing Engine & Ventoy Bootstrap
In `com.p0lymatic.flashpoint.core`:
- **`RawImageBurner`**:
  - Reads from Android `Uri` (content resolver `openInputStream`) in chunks (64KB - 256KB).
  - Streams chunks to `UsbMassStorageStreamer`.
  - Calculates real-time progress: bytes written, throughput (MB/s), estimated time remaining (ETA), and SHA256 checksum on-the-fly.
  - Supports clean cancellation and resource cleanup.
- **`VentoyInstaller`**:
  - Fetches the latest official Ventoy release metadata from GitHub Releases API (`https://api.github.com/repos/ventoy/Ventoy/releases/latest`).
  - Downloads the official release package directly.
  - Handles disk partitioning layout: Partition 1 (exFAT, main ISO storage), Partition 2 (FAT16, VTOYEFI bootloader).

### 5. UI Layer (Jetpack Compose + Material 3)
In `com.p0lymatic.flashpoint.ui`:
- Dark-mode first, sleek hacker-aesthetic with cyber/engineering touches.
- **Top Bar**: "FlashPoint ⚡" with connection status indicator (dot: green = USB connected, red = no USB).
- **Tab / Navigation**:
  - `ISO Burner` (Select ISO via `OpenDocument`, pick connected USB, Verify SHA256, Start Burn).
  - `Ventoy Setup` (Show latest Ventoy version from GitHub, partition scheme MBR/GPT, Install/Update).
- **Burning Progress Dialog / Sheet**:
  - Smooth animated progress bar.
  - Live speed readout (`42.5 MB/s`), elapsed time, and ETA.
  - Cancel button with safety prompt.

## Execution Rules
- Write complete, clean, production-grade Kotlin files with full error handling and coroutines.
- Make sure all package names match `com.p0lymatic.flashpoint`.
- Create all Gradle files, directories, source files, and Compose components cleanly.
