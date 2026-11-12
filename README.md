# OpenCarta

Open-source Web Bluetooth controller and BLE protocol documentation for the **Focus V Carta Sport**.

## Features

- **Zero-install Web Bluetooth UI** ([index.html](index.html)) — runs directly in Chrome / Chromium / Edge / Brave on desktop and Android.
- **Full BLE Protocol Reference** ([PROTOCOL.md](PROTOCOL.md)) — reverse-engineered GATT services, characteristics, telemetry, presets, LEDs, settings, and offline dab history sync.
- **Temperature Curve Studio & Live Graph:**
  - Built-in scientifically grounded curves: **Quartz Banger Cooling Curve**, **Live Rosin Terpene Ladder**, **Cold-Start Smooth Ramp**, **Live Resin Flavor → Cloud**, and **Flash Melt → Mellow Finish**.
  - Interactive breakpoint editor with **Smooth Ramp** or **Stepped** interpolation, `localStorage` custom curve saving, and a live `<canvas>` overlay of target vs. atomizer temperature.
- **Preserves Official App High Scores:** Sends UTC time sync (`0xdd`) and **never** sends the `0xfd` record-clear command, so dabs remain queued in the Carta's flash for the official app to sync later.

## Quick Start

Web Bluetooth requires a secure context (`http://localhost` or `https://`):

```bash
python3 -m http.server 8000
```

Open `http://localhost:8000` in Chrome/Chromium and click **Connect Carta**.
*(On Linux Chrome, enable `chrome://flags/#enable-experimental-web-platform-features` if BLE devices do not appear.)*
