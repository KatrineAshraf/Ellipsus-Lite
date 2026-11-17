# Ellipsus Lite

A tiny native Android app that opens [write.ellipsus.com](https://write.ellipsus.com)
full screen in a WebView. It was made for older tablets (Android 8+) that can't
install the official Ellipsus app, and it's built to be light: no Flutter, no
extra libraries, just one `Activity`.

## Features

- **Fullscreen**: status bar and navigation bar are hidden and re-hidden if
  they slide in after an edge swipe.
- **Desktop mode**: sends a desktop Chrome user agent and lays the page out at
  a desktop width, so desktop-only features are available.
- **Bluetooth keyboard friendly**: while a physical keyboard is connected, the
  on-screen keyboard is hidden automatically.
- **File picker support**, so import buttons on the site work.
- **Back button** navigates back inside the site, and exits at the first page.
- **Saved login**: cookies and site storage persist between launches.
- **Retry screen** when there's no connection.

## Requirements

| What | Version |
| --- | --- |
| Android on the device | 8.0 (API 26) or newer |
| JDK to build | 17 or newer (21 recommended) |
| Android Studio | Current stable release |

``Apk is found for direct download``