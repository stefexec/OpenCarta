# Focus V Carta Sport — BLE Protocol Reference

Reverse-engineered from `btsnoop_hci_261111_231847.log` captured against a Focus V Carta Sport (`Pfeifi McPfeifengesicht` → renamed to `Pfeifi McDabface`, MAC `a4:c1:38:f6:6d:b7`, Telink Semiconductor BLE SoC).

---

## 1. BLE Device & GATT Profile

- **MAC Address:** `a4:c1:38:f6:6d:b7` (OUI `a4:c1:38` — Telink Semiconductor)
- **Advertised Name:** `Pfeifi McPfeifengesicht` (null-padded in advertisement data)
- **Scan Response Manufacturer Data (`0xff`, Company ID `0x0211` Telink Semiconductor):**
  20-byte payload `{<Model>|<Serial>~<Battery%>^<0x08>}`, e.g. `7b 53 33 42 4c 7c 53 32 36 43 42 31 37 32 33 7e 52 5e 08 7d` (`{S3BL|S26CB1723~` + `0x52` (82%) + `^` + `0x08` + `}`). Broadcasts live battery percentage before pairing/connecting.
- **Device Information Service (`0x180a`):**
  - **Model Number (`0x2a24`, Handle `0x0010`):** `S3BL`
  - **Serial Number (`0x2a25`, Handle `0x0012`):** `S26CB1723`
  - **Software Revision (`0x2a28`, Handle `0x0016`):** `PROD-030426`

### Primary Custom GATT Service (Carta Control & Telemetry)

- **Service UUID:** `1011123e-8535-b5a0-7140-a304d2495cb7` (Handles `0x0019`–`0x0020`)

| Role | UUID | Decl Handle | Value Handle | Properties | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **NOTIFY (Carta → App)** | `1011123e-8535-b5a0-7140-a304d2495cb8` | `0x001a` | `0x001b` | `0x12` (`Notify`, `Read`) | Enable by writing `01 00` to CCCD handle `0x001c` (`0x2902`) |
| **WRITE (App → Carta)** | `1011123e-8535-b5a0-7140-a304d2495cb9` | `0x001e` | `0x001f` | `0x06` (`Write Without Response`, `Read`) | Uses ATT Write Command (`0x52`) |

### Secondary Custom GATT Service (Telink OTA)

- **Service UUID:** `00010203-0405-0607-0809-0a0b0c0d1912` (Handles `0x0021`–`0x0024`)
- **OTA Characteristic UUID:** `00010203-0405-0607-0809-0a0b0c0d2b12` (Value Handle `0x0023`, `Write Without Response` / `Read`)

---

## 2. Packet Framing Format

All standard command and notification packets (except the `dd 01 ...` high-rate session stream) share a uniform framing structure:

```text
+---------+---------+------------------------+---------+
| Byte 0  | Byte 1  | Bytes 2 .. (Length-2)  | Last    |
| Opcode  | Length  | Payload                | Opcode  |
+---------+---------+------------------------+---------+
```

- **Byte 0 (`Opcode`):** Packet type identifier (`0x11`, `0x22`, `0x33`, `0x44`, `0x55`, `0x82`, `0x99`, `0xaa`, `0xac`, `0xaf`, `0xcc`, `0xce`, `0xd1`, `0xd2`, `0xdd`, `0xe0`–`0xea`, `0xfa`, `0xfc`, `0xfd`).
- **Byte 1 (`Length`):** Total packet length in bytes, **including** `Byte 0` and the trailing byte.
- **Last Byte (`Byte[Length - 1]`):** Repeats `Byte 0` as an end-of-frame marker.
- **Endianness:** Multi-byte numeric fields (temperatures, timers, counters, year) are **big-endian** (`uint16` or `uint32`).

---

## 3. App → Carta Commands (Write to Handle `0x001f`)

### 3.1 Handshake / Time Sync (`0xdd`, 12 bytes)

Sent by the app immediately after enabling notifications on `0x001c`. Triggers the Carta to dump its configuration and preset tables (`0xce`, `0x99`, `0xaa`, `0x55`, `0x44`, `0x33`, `0x22`, `0xea`..`0xe6`, `0x82`, `0xd2`).

**Captured Packets:**
- Frame 944 (`20.503s`): `dd 0c 07 ea 0b 0b 16 13 00 00 00 dd` (`2026-11-11 22:19`)
- Frame 9086 (`674.399s`): `dd 0c 07 ea 0b 0b 16 1e 00 00 00 dd` (`2026-11-11 22:30`)
- Frame 12983 (`1170.537s`): `dd 0c 07 ea 0b 0b 16 26 00 00 00 dd` (`2026-11-11 22:38`)

| Byte(s) | Example | Meaning |
| :--- | :--- | :--- |
| `0` | `0xdd` | Opcode (`0xdd`) |
| `1` | `0x0c` | Total length (`12` bytes) |
| `2..3` | `0x07ea` | Year (`2026`) |
| `4` | `0x0b` | Month (`11`) |
| `5` | `0x0b` | Day (`11`) |
| `6` | `0x16` | Hour (`22`) |
| `7` | `0x13`, `0x1e`, `0x26` | Minute (`19`, `30`, `38`) |
| `8..10` | `00 00 00` | Seconds / reserved (`0`) |
| `11` | `0xdd` | Trailer (`0xdd`) |

---

### 3.2 Device Settings, Units, Presets, Vibration, LED Toggle, Low Power & Power Off (`0x11`, 12 bytes)

Controls temperature unit (°C/°F), LED brightness, active heating preset (`1`–`5`), active LED preset (`1`–`5` or `0` for LEDs Off), vibration intensity (`20%`–`100%`), Low Power Mode, and Power Off.

**Captured Examples:**
- **Switch Heating Preset (`1` ↔ `2` ↔ `3`):**
  - Preset 2 (Frame 1529): `11 0c 00 22 08 00 02 05 00 08 00 11`
  - Preset 1 (Frame 1640): `11 0c 00 22 08 00 01 05 00 08 00 11`
- **Switch LED Preset (`4` ↔ `5`) & Toggle LEDs ON/OFF (`0x00` vs `0x05`):**
  - LED Preset 4 (Frame 1724): `11 0c 00 22 08 00 01 04 00 08 00 11`
  - LED Preset 5 / ON (Frame 19061): `11 0c 00 22 08 00 03 05 00 08 00 11`
  - **LEDs OFF / Stealth** (Frame 19028, 19103, 19151): `11 0c 00 22 08 00 03 00 00 08 00 11` (`Byte 7 = 0x00`)
- **Change Vibration Intensity (`Byte 9`, Frames 17141–17361):**
  - `60%` (`0x06`): `11 0c 00 22 08 00 03 00 00 06 00 11`
  - `80%` (`0x08`): `11 0c 00 22 08 00 03 00 00 08 00 11`
  - `100%` (`0x0a`): `11 0c 00 22 08 00 03 00 00 0a 00 11`
  - `40%` (`0x04`): `11 0c 00 22 08 00 03 00 00 04 00 11`
  - `20%` (`0x02`): `11 0c 00 22 08 00 03 00 00 02 00 11`
- **Switch Temperature Unit (°C ↔ °F, `Byte 3`, Frames 17401–17436):**
  - **Fahrenheit (`0x11`):** `11 0c 00 11 08 00 03 00 00 08 00 11`
  - **Celsius (`0x22`):** `11 0c 00 22 08 00 03 00 00 08 00 11`
- **Toggle Low Power Mode (`Byte 4` & `Byte 10`, Frames 17480–17508):**
  - **Low Power Mode ON:** `11 0c 00 22 02 00 03 00 00 08 01 11` (`Byte 4 = 0x02` dims LED brightness to 20%; `Byte 10 = 0x01` enables Low Power Mode)
  - **Low Power Mode OFF:** `11 0c 00 22 08 00 03 00 00 08 00 11` (`Byte 4 = 0x08` restores 80% brightness; `Byte 10 = 0x00`)
- **Power Off Device (`Byte 2 = 0xaf`, captured `btsnoop_hci_261112_223553.log`):**
  - **Turn Off:** `11 0c af 22 08 00 01 05 00 06 00 11` (`Byte 2 = 0xaf` powers off the device while preserving current settings bytes)

| Byte | Values Seen | Meaning |
| :--- | :--- | :--- |
| `0` | `0x11` | Opcode (`0x11`) |
| `1` | `0x0c` | Total length (`12` bytes) |
| `2` | `0x00`, `0xaf` | **Power / Action Flag:** `0x00` = Normal settings update, `0xaf` = **Power Off** (parallels `0xcc` Byte 13 `0xaf` = Stop Session) |
| `3` | `0x22`, `0x11` | **Temperature Unit:** `0x22` = **Celsius (°C)**, `0x11` = **Fahrenheit (°F)** |
| `4` | `0x08`, `0x02` | **LED Brightness:** `0x02` = 20% (Low Power), `0x08` = 80% (reflected in low nibble of status `Byte 13`) |
| `5` | `0x00` | Reserved (`0x00`) |
| `6` | `0x01`, `0x02`, `0x03` | **Active Heating Preset Index** (`1`–`5`) |
| `7` | `0x00`, `0x04`, `0x05` | **Active LED Preset Index:** `0x00` = **LEDs OFF**, `0x01`–`0x05` = **LED Preset 1–5** (reflected in high nibble of status `Byte 13`) |
| `8` | `0x00` | Reserved (`0x00`) |
| `9` | `0x02`, `0x04`, `0x06`, `0x08`, `0x0a` | **Vibration Intensity:** `0x02` (20%), `0x04` (40%), `0x06` (60%), `0x08` (80%), `0x0a` (100%) (reflected in high nibble of status `Byte 17`) |
| `10` | `0x00`, `0x01` | **Low Power Mode:** `0x00` = Off, `0x01` = On (reflected in low nibble of status `Byte 17`) |
| `11` | `0x11` | Trailer (`0x11`) |

---

### 3.3 Configure LED Preset Colors & Animation Mode (`0xe0`–`0xe4`, 20 bytes)

Configures one of the 5 LED preset slots (`0xe0` = Slot 1 .. `0xe4` = Slot 5), including its **animation effect** (`Byte 3`) and **5 RGB color stops** (`Bytes 4..18`).

**Captured Animation Mode Sequence on Slot 5 (`0xe4`, Frames 14672–15038):**
- **Fade (`0x33`, Frame 14672):** `e4 14 aa 33 00f028 0000f9 00f413 0800e1 0fe300 e4`
- **Sparkle (`0x43`, Frame 14808):** `e4 14 aa 43 00f028 0000f9 00f413 0800e1 0fe300 e4`
- **Static (`0x53`, Frame 14919):** `e4 14 aa 53 00f028 0000f9 00f413 0800e1 0fe300 e4`
- **Pulse (`0x23`, Frame 14967):** `e4 14 aa 23 00f028 0000f9 00f413 0800e1 0fe300 e4`
- **Rotate (`0x13`, Frame 15038):** `e4 14 aa 13 00f028 0000f9 00f413 0800e1 0fe300 e4`

| Byte(s) | Values Seen | Meaning |
| :--- | :--- | :--- |
| `0` | `0xe0`–`0xe4` | Opcode (`0xe0` = LED Slot 1, `0xe1` = Slot 2, `0xe2` = Slot 3, `0xe3` = Slot 4, `0xe4` = Slot 5) |
| `1` | `0x14` | Total length (`20` bytes) |
| `2` | `0xaa` | Constant marker (`0xaa`) |
| `3` | `0x13`, `0x23`, `0x33`, `0x43`, `0x53` | **LED Animation Mode:**<br>• `0x13` = **Rotate**<br>• `0x23` = **Pulse**<br>• `0x33` = **Fade**<br>• `0x43` = **Sparkle**<br>• `0x53` = **Static** |
| `4..6` | `R G B` | LED Color 1 (3 bytes RGB) |
| `7..9` | `R G B` | LED Color 2 (3 bytes RGB) |
| `10..12` | `R G B` | LED Color 3 (3 bytes RGB) |
| `13..15` | `R G B` | LED Color 4 (3 bytes RGB) |
| `16..18` | `R G B` | LED Color 5 (3 bytes RGB) |
| `19` | `0xe0`–`0xe4` | Trailer (matches Byte 0) |

---

### 3.4 Set Temperature / Save Preset / Start & Stop Session (`0xcc`, 16 bytes)

Used to adjust target temperatures/timers, save them to a preset slot, start a heating session (`Byte 13 = 0xa5`), and stop/cancel an active session (`Byte 13 = 0xaf`).

**Captured Start + Stop Sequence (Frames 24589 & 24636):**
- **Start Session (`2147.918s`, Frame 24589):**
  `cc 10 00 a5 01 0c 22 00 b4 00 32 00 03 a5 00 cc` (Preset 3, `268°C`, `50s`, `Byte 13 = 0xa5`)
- **Stop / Cancel Session (`2149.409s`, Frame 24636):**
  `cc 10 00 a5 01 0c 22 00 b4 00 32 00 03 af 00 cc` (Preset 3, `268°C`, `50s`, `Byte 13 = 0xaf`)

| Byte(s) | Example | Meaning |
| :--- | :--- | :--- |
| `0` | `0xcc` | Opcode (`0xcc`) |
| `1` | `0x10` | Total length (`16` bytes) |
| `2..3` | `0x00a5` | Dry Herb Target Temperature (`165`°C) |
| `4..5` | `0x00e1`..`0x010c` | **Concentrate Target Temperature** (`225`–`268`°C) |
| `6` | `0x22`, `0x11` | Mode / Temperature Unit (`0x22` = °C, `0x11` = °F) |
| `7..8` | `0x00b4` | Dry Herb Session Timer (`180` seconds) |
| `9..10` | `0x0037`, `0x0032` | **Concentrate Session Timer** (`55`s, `50`s) |
| `11` | `0x00` | Reserved (`0x00`) |
| `12` | `0x00`..`0x05` | **Preset Slot** (`0x00` while scrubbing/previewing, `0x01`–`0x05` to apply/save to preset) |
| `13` | `0x00`, `0xa5`, `0xaf` | **Session Action:**<br>• `0x00` = Configure / save preset only<br>• **`0xa5` = Start Session**<br>• **`0xaf` = Stop / Cancel Session** |
| `14` | `0x00` | Reserved (`0x00`) |
| `15` | `0xcc` | Trailer (`0xcc`) |

---

### 3.5 Set LED Timeout & Device Sleep Timeout (`0xd1`, 7 bytes)

Sets the LED timeout and device auto-sleep timeout (both encoded in **seconds** as `uint16` big-endian). The Carta immediately acknowledges with a `0xd2` notification containing the new values.

**Captured Sequence (Frames 17548–17659):**
- `d1 07 02 1c 02 1c d1` → LED = `540s` (`9 min`), Sleep = `540s` (`9 min`)
- `d1 07 01 e0 01 e0 d1` → LED = `480s` (`8 min`), Sleep = `480s` (`8 min`)
- `d1 07 01 e0 02 1c d1` → LED = `480s` (`8 min`), Sleep = `540s` (`9 min`)
- `d1 07 01 e0 02 58 d1` → LED = `480s` (`8 min`), Sleep = `600s` (`10 min`)
- `d1 07 01 a4 02 58 d1` → LED = `420s` (`7 min`), Sleep = `600s` (`10 min`)
- `d1 07 01 68 02 58 d1` → LED = `360s` (`6 min`), Sleep = `600s` (`10 min`)

| Byte(s) | Example | Meaning |
| :--- | :--- | :--- |
| `0` | `0xd1` | Opcode (`0xd1`) |
| `1` | `0x07` | Total length (`7` bytes) |
| `2..3` | `0x0168`..`0x0258` | **LED Timeout in Seconds** (`360`s = 6 min .. `600`s = 10 min) |
| `4..5` | `0x01e0`..`0x0258` | **Sleep Mode Timeout in Seconds** (`480`s = 8 min .. `600`s = 10 min) |
| `6` | `0xd1` | Trailer (`0xd1`) |

---

### 3.6 Rename Device (`0xac` + `0xaf`, 20 bytes + 15 bytes)

Renaming sends two packets back-to-back:
1. **`0xac` (20 bytes):** First 17 bytes of the UTF-8/ASCII device name (null-padded).
2. **`0xaf` (15 bytes):** Remaining 12 bytes of the device name (null-padded, for names up to 29 characters).

**Captured Packets (Frames 18761 & 18774 — `"Pfeifi McDabface"`):**
```text
ac 14 50 66 65 69 66 69 20 4d 63 44 61 62 66 61 63 65 00 ac   ("Pfeifi McDabface\0")
af 0f 00 00 00 00 00 00 00 00 00 00 00 00 af                  (12 zero bytes)
```

---

### 3.7 Offline Session History Log Sync (`0xfa` Query → `0xfc` Record → `0xfd` Ack)

When reconnecting after a session, the app fetches and clears any un-synced session records:

1. **Query Session Record (`0xfa`, 7 bytes, Frame 9214):**
   `fa 07 00 10 00 10 fa` (Request session `#16` / `0x0010`)
2. **Session Record Response (`0xfc`, 18 bytes, Frame 9226):**
   `fc 12 00 10 00 10 61 07 ea 0b 0b 16 16 37 00 e6 22 fc`
   - `Bytes 2..3` (`0x0010`): Total records (`16`)
   - `Bytes 4..5` (`0x0010`): Record index (`16`)
   - `Byte 6` (`0x61`): Status / flags
   - `Bytes 7..8` (`0x07ea`): Year (`2026`)
   - `Byte 9` (`0x0b`): Month (`11`)
   - `Byte 10` (`0x0b`): Day (`11`)
   - `Byte 11` (`0x16`): Hour (`22`)
   - `Byte 12` (`0x16`): Minute (`22`)
   - `Byte 13` (`0x37`): Session duration (`55` seconds)
   - `Bytes 14..15` (`0x00e6`): Session temperature (`230`°C)
   - `Byte 16` (`0x22`): Mode / unit (`0x22` = Concentrate °C)
3. **Acknowledge / Clear Synced Record (`0xfd`, 7 bytes, Frame 9283):**
   `fd 07 00 10 00 10 fd`

---

## 4. Carta → App Notifications (Handle `0x001b`)

### 4.1 Live Status Telemetry (`0x99`, 20 bytes, ~2 Hz / every ~530 ms)

| Byte(s) | Captured Values | Meaning |
| :--- | :--- | :--- |
| `0` | `0x99` | Opcode (`0x99`) |
| `1` | `0x14` | Total length (`20` bytes) |
| `2` | `0x40`, `0x00` | **Device Status / Atomizer Flags:** Bit 6 (`0x40`) = Atomizer attached (`0x00` = No atomizer attached) |
| `3` | `0x22`, `0x11` | **Active Temperature Unit:** `0x22` = **°C**, `0x11` = **°F** |
| `4` | `0x00`..`0x03`, `0x11` | **Active Heating Preset** (`0x01`–`0x05` = Preset 1–5, `0x00` = manual/unsaved, `0x11` = initial standby) |
| `5` | `0x00`..`0x37` | **Remaining Session Time (seconds)** (`0x00` when idle; holds `0x37` = 55s during pre-heat, then counts down `54`..`0` once target temp is reached) |
| `6..7` | `0x000e`..`0x00e7` (°C)<br>`0x005d` (°F) | **Current Atomizer Temperature** (in active unit: °C when `Byte 3 == 0x22`, °F when `Byte 3 == 0x11`) |
| `8..9` | `0x00a5` (165°C)<br>`0x0149` (329°F) | **Dry Herb Target Temperature** (in active unit) |
| `10..11` | `0x00e1`..`0x00eb` (°C)<br>`0x01be` (446°F) | **Concentrate Target Temperature** (in active unit) |
| `12` | `0x52` → `0x4e` | **Battery Level (%)** (`0x52` = 82% → `0x4e` = 78%) |
| `13` | `0x58`, `0x48`, `0x08`, `0x02` | **High nibble:** Active LED Preset (`0` = LEDs Off, `1`–`5` = Preset 1–5)<br>**Low nibble:** LED Brightness (`2` = 20%, `8` = 80%) |
| `14` | `0xb4` | **Dry Herb Target Timer** (`180` seconds) |
| `15` | `0x3c`, `0x37` | **Concentrate Target Timer** (`60`s → `55`s) |
| `16` | `0x00`, `0x01` | **Charging State:** `0x00` = Unplugged, `0x01` = Plugged in / Charging |
| `17` | `0x20`, `0x40`, `0x60`, `0x80`, `0xa0`, `0x81` | **High nibble:** Vibration Intensity (`2` = 20%, `4` = 40%, `6` = 60%, `8` = 80%, `a` = 100%)<br>**Low nibble:** Low Power Mode (`0` = Off, `1` = On) |
| `18` | `0x00`, `0xef`..`0xb4` | **Standby Auto-Off Countdown** (starts at `0xef` = 239 and ticks down by 1 every ~2.5s when idle, i.e. 240 ticks ≈ 600s / 10 min; `0x00` during active interaction/session or while >10 min remain) |
| `19` | `0x99` | Trailer (`0x99`) |

---

### 4.2 Other Notifications

- **`0xaa` (19 bytes — Dab Counters):** Four big-endian `uint32` session counters (`Bytes 2..5`, `6..9`, `10..13`, `14..17`).
- **`0x55`, `0x44`, `0x33`, `0x22` (18 bytes each — Preset Tables):** 5 presets × 3 bytes (`uint16` temp + `uint8` seconds) for Herb °F (`0x55`), Herb °C (`0x44`), Concentrate °F (`0x33`), and Concentrate °C (`0x22`).
- **`0xea`–`0xe6` (20 bytes each — LED Preset Tables):** Stored LED profiles for Slots 1–5 (`0xea` = Slot 1 .. `0xe6` = Slot 5).
- **`0xd2` (7 bytes — LED & Sleep Timeouts):** `d2 07 [LED_Timeout_Sec_u16] [Sleep_Timeout_Sec_u16] d2`.
- **`0xce` (7 bytes):** Device/hardware version or state marker (`ce 07 02 55 01 ea ce` / `ce 07 02 55 01 e9 ce`).
- **`0x82` (8 bytes):** `82 08 00 00 00 00 00 82`.
- **`0xdd 0x01` (20 bytes — High-rate Session Telemetry):** Sent at ~4 Hz during heating (`dd 01 00 ...` while heating up; `dd 01 01 ...` once target temperature is reached).
