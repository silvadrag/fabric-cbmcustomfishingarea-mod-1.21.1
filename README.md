# CBM Custom Fishing Area

Mod Fabric **1.21.1** (server-oriented): thay cơ chế câu cá vanilla / Cobblemon bằng **khu vực câu + pool phần thưởng**.

- Mod id: `cbmcustomfishingarea`
- JAR: `build/libs/cbmcustomfishingarea-1.0.0.jar`
- Cobblemon: **soft dependency**
- Economy (Impactor…): **soft adapter**

---

## Cài đặt

1. Minecraft **1.21.1** + Fabric Loader + Fabric API  
2. Thả JAR vào `mods/` (server)  
3. (Tuỳ chọn) Cobblemon, LuckPerms, Impactor  

| File | Nội dung |
|---|---|
| `config/cbmcustomfishingarea/config.json` | Bật/tắt hệ thống, gate rod, message |
| `config/cbmcustomfishingarea/areas.json` | Khu vực + pool |

---

## Hai vai trò

| Vai trò | Permission | Việc làm |
|---|---|---|
| Fisher | `cbmcustomfishingarea.fish` (mọi người) | List / info / where / GUI |
| Manage | `cbmcustomfishingarea.manage` (OP 2) | Marker A→B, tạo khu, pool, config, GUI |

```
/fishingarea
/fa
```

---

## Marker A → B

1. `/fishingarea manage marker`  
2. Click góc **A** rồi góc **B** (không phá block)  
3. `/fishingarea manage create <name> coordinates|chunks`

| Shape | Ý nghĩa |
|---|---|
| `coordinates` | Hộp block A→B (có Y) |
| `chunks` | Chunk(A)→chunk(B) (bỏ Y) |

---

## Hệ thống câu

| Config | Ý nghĩa |
|---|---|
| `enable` / `disable` | Bật / tắt hệ thống khu vực |
| `replace-vanilla true\|false` | Gate cần câu thường |
| `replace-cobblemon true\|false` | Gate Poké Rod |

Quyết định theo **vị trí phao (bobber)**. Trùng khu: volume nhỏ hơn thắng.

| Status | Fisher thấy? | Câu được? |
|---|---|---|
| `open` | Có | Có |
| `maintenance` | Có | Không |
| `hidden` | Không | Có |

---

## Pool rewards

Mỗi entry có **weight** + **channel** (`both` | `vanilla` | `cobblemon`).  
Mặc định `both` (an toàn cho data cũ/test). Roll chỉ lấy entry khớp rod đang dùng.

| Type | Ý nghĩa |
|---|---|
| `item` | Item cố định |
| `item_tag` | Random 1 item trong tag (vd `minecraft:fishes`) |
| `xp` | Điểm / level XP |
| `nothing` | “Nothing bites…” (weight pad) |
| `command` | Chạy lệnh console (`{player}`, `{uuid}`) |
| `scoreboard` | Cộng/set scoreboard |
| `economy` | Tiền tệ server (adapter Impactor…, soft) |
| `cobblemon_species` | Species đơn giản + level + delivery |
| `cobblemon_pokemon` | Form / shiny / delivery — **không chỉnh IV/EV** (Cobblemon random) |

### Cobblemon delivery

| Mode | Hành vi |
|---|---|
| `party_or_pc` | Give vào party, đầy thì PC |
| `spawn_world` | Spawn entity gần bobber/player |

### Smart form (GUI)

Khi chọn species trong GUI manage:

- Species **có** Mega / Gmax / regional → slot đó **enable** để chọn  
- Species **không có** → slot **disable** (xám); click báo rõ *“X has no Mega form”*  
- Form/aspect thật của species được liệt kê từ Cobblemon data  

### Lệnh pool

```
/fishingarea manage pool-add <area> item <itemId> [count] [weight] [channel]
/fishingarea manage pool-add <area> item_tag <tagId> [count] [weight] [channel]
/fishingarea manage pool-add <area> xp <amount> [points|levels] [weight] [channel]
/fishingarea manage pool-add <area> nothing [weight] [channel]
/fishingarea manage pool-add <area> command "<cmd>" [weight] [channel]
/fishingarea manage pool-add <area> scoreboard <objective> [amount] [op] [weight] [channel]
/fishingarea manage pool-add <area> economy <currency> <amount> [weight] [channel]
/fishingarea manage pool-add <area> cobblemon_species <species> [lvMin] [lvMax] [delivery] [weight] [channel]
/fishingarea manage pool-add <area> cobblemon_pokemon <species> [form] [lvMin] [lvMax] [shiny] [delivery] [weight] [channel]
/fishingarea manage pool-remove <area> <index>
/fishingarea manage pool-clear <area>
```

GUI manage: click khu → Edit pool / Add Cobblemon… (smart options).

---

## Lệnh nhanh

### Fisher
```
/fishingarea fisher gui|list|info|where
```

### Manage
```
/fishingarea manage gui|marker|selection
/fishingarea manage create <name> coordinates|chunks
/fishingarea manage visualize <name|temp|off> [coordinates|chunks]
/fishingarea manage status <name> open|maintenance|hidden
/fishingarea manage config enable|disable|replace-vanilla|replace-cobblemon
/fishingarea manage reload
```

---

## Setup nhanh

```
/fishingarea manage config enable
/fishingarea manage marker
# click A rồi B
/fishingarea manage create lake chunks
/fishingarea manage pool-add lake item minecraft:cod 1 100 vanilla
/fishingarea manage pool-add lake cobblemon_species magikarp 5 15 party_or_pc 100 cobblemon
/fishingarea manage gui
```

---

## Build

```
./gradlew build
```

Java 21 · Minecraft 1.21.1 · Fabric API
