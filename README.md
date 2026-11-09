# FlashPoint ⚡

> **A fast, clean, zero-bullshit, 100% Free & Open Source bootable USB creator & Ventoy installer for Android (over USB-OTG).**
> 
> *Быстрая, чистая, открытая утилита для создания загрузочных флешек и установки Ventoy через USB-OTG на Android без рекламы, токенов и анального зондирования.*

---

## 🏴 English

### ⚡ Why FlashPoint exists (The Spite Manifesto)

Picture this: your PC failed to boot, your laptop is dead, and you desperately need to write an Ubuntu or Windows ISO to a flash drive using your Android phone and a $1 OTG adapter.

You open Google Play, download a utility with names suspiciously mimicking open-source legends (*DROFUS*, *Ventoy Unofficial*, *Ultimate USB* by **MixApplications**), plug in your USB drive, tap "Burn", and...

```
[ ! ] INSUFFICIENT TOKENS.
Watch a 60-second unskippable video ad to earn 1 TOKEN,
or buy 5 TOKENS for $0.99!
*Note: Tokens are stored locally on your device and will be lost if you clear app data.*
```

Yes. Someone actually took the GPL-licensed Ventoy binary, wrapped it in 15 different ad SDKs (AdMob, Unity, AppLovin, ByteDance), put a paywall on the `write()` syscall, and told users: *"Users forced me to do that because they reported my disruptive popup ads"*. And to top it off, added a "USB Defragmenter" to prematurely kill your NAND flash memory.

**FlashPoint was born out of pure, unadulterated engineering spite.**

### ✨ Features
- 🚀 **100% Free & Open Source (GPLv3)** — No locked features, no closed code, no compromises.
- 🚫 **Zero Ads, Zero Tokens, Zero Trackers** — No AdMob, no analytics, no in-app purchases. Never.
- 🖴 **Raw Image Writing (`dd` mode)** — Stream hybrid ISOs and raw disk images (Linux distros, Raspberry Pi OS, BSD, recovery images) directly over USB Bulk-Only Transport (BOT) with real-time throughput metrics.
- 🧭 **Pristine Ventoy Setup** — Downloads the genuine, official upstream Ventoy release on demand from GitHub, creates the partition layout (MBR/GPT), and installs the bootloader. No stale bundled binaries in APK assets.
- 🎨 **Modern Material 3 UI** — Jetpack Compose, dynamic color (Material You), dark theme by default.
- ⚡ **Real Userspace SCSI/BOT Driver** — High-performance chunked streaming via Android USB Host API (`UsbManager`). No root required (root optional for direct block device passthrough).
- 🧠 **Sanity Guaranteed** — No "USB Defragmenter", because we actually understand how NAND flash controller wear leveling works.

---

### ⚖️ FlashPoint vs The Shovelware Monopoly

| Feature | **FlashPoint** ⚡ | **DROFUS / Ultimate USB** 🤡 |
| :--- | :---: | :---: |
| **Price** | Free Forever ($0.00) | $0.99 for 5 writes or watch 60s ads |
| **License** | GPL-3.0 (Copyleft) | Proprietary (GPL Violator) |
| **Source Code** | Full & Open on GitHub | Closed ("I'll reconsider business model") |
| **Ads & Trackers** | 0% | 90% of APK payload |
| **Token Economy** | What the fuck is a token? | Stored in `SharedPreferences` (wipe = rip) |
| **Ventoy Handling** | Downloaded fresh on-demand | Stale 3-year-old binary hardcoded in assets |
| **NAND Health** | Safe streaming writes | "USB Defragmenter" destroys flash cells |

---

## 🇷🇺 Русский

### ⚡ Манифест чистоты (или почему этот проект существует)

Классическая ситуация: домашний ПК лёг, операционка окирпичилась, под рукой только Android-смартфон, флешка и копеечный OTG-переходник.

Ты заходишь в Google Play, качаешь приложение с заманчивыми названиями вроде *DROFUS*, *Ventoy Unofficial* или *Ultimate USB* (от славных ребят из **MixApplications**), выбираешь образ, нажимаешь «Записать»... и видишь модальное окно:

> **«ПОЛУЧИТЕ ЖЕТОНЫ: посмотрите 60 секунд видеорекламы казино за 1 жетон, либо купите 5 жетонов за $0.99. Жетоны хранятся локально на устройстве и сотрутся при очистке кэша»**.

То есть какой-то гений взял опенсорсный Ventoy под лицензией GPLv3, обвешал его пятнадцатью рекламными SDK, сделал платным вызов базовой функции записи на диск, а на претензии разработчиков оригинала на GitHub на голубом глазу ответил:  
*«Пользователи сами вынудили меня сделать эту всратую систему с жетонами, потому что жаловались в Google Play на мои всплывающие окна на весь экран!»*

А вишенкой на торте добавил в приложение «Дефрагментатор флешки», чтобы пользователи гарантированно и с огоньком добивали ресурс перезаписи ячеек своей NAND-памяти за просмотр 30-секундного ролика.

**FlashPoint создан из чистого инженерного возмущения и желания вернуть адекватность в мобильные утилиты.**

### ✨ Возможности:
- 🚀 **Честный FOSS (GPL-3.0):** Никаких скрытых пейволлов и закрытых исходников.
- 🚫 **0 рекламы, 0 токенов, 0 трекеров:** Никаких AdMob, баннеров и выкачивания денег.
- 🖴 **Честный Raw Writer (`dd`):** Потоковая побайтовая запись любых гибридных ISO/IMG образов (Ubuntu, Arch, Alpine, Proxmox, Raspberry Pi) через USB Bulk-Only Transport со счётчиком скорости и контролем целостности.
- 🧭 **Чистый Ventoy без мусора:** Загрузка официальных актуальных релизов с официального репозитория Ventoy на лету, нарезка разделов MBR/GPT и чистая установка.
- 🎨 **Современный UI:** Jetpack Compose, Material 3, аккуратная тёмная тема, понятная индикация подключённых накопителей.
- 🧠 **Уважение к железу:** Мы не дефрагментируем флешки.

---

## 🛠️ Architecture & Tech Stack

- **Platform:** Android 8.0+ (API 26+)
- **Language & UI:** Kotlin + Jetpack Compose + Material 3
- **USB Subsystem:** Android USB Host API (`android.hardware.usb.UsbManager`), Bulk-Only Transport (BOT) protocol implementation, SCSI Command Descriptor Blocks (`INQUIRY`, `READ CAPACITY (10/16)`, `WRITE (10/16)`).
- **Partitioning:** MBR / GPT table creation, exFAT / FAT32 volume bootstrapping.

---

## 📜 License

FlashPoint is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.  
See [LICENSE](LICENSE) for details.
