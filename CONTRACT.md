# DHOPM — Hướng dẫn FE kết nối BE (Contract Quick Start)

> **Bản chất:** đây là **điểm vào nhanh** cho lập trình viên Frontend (FE) muốn kết nối tới Backend (BE) **đã chạy sẵn**. Nội dung ở đây chỉ là **tóm tắt vận hành** (kết nối · lệnh tối thiểu · giới hạn · ràng buộc · thông tin liên quan).
>
> **Chi tiết đầy đủ (nguồn chuẩn):** [`docs/plans/08-FE-CONTRACT.md`](docs/plans/08-FE-CONTRACT.md). Khi tài liệu này khác với `08-FE-CONTRACT.md`, **`08` luôn thắng**.
>
> **Đọc kèm:** `docs/DECISIONS.md` (D45–D49, D6/D7/D28/D41/D48) · `docs/phases/P5-G5.md` · `docs/plans/05-BACKEND-CLIAPI-PLAN.md`.

| Mục | Giá trị |
|---|---|
| **Contract version** | `protocol 1` (mỗi request ghi `"v":1`) |
| **Transport chuẩn** | TCP `localhost:7079` (FE runtime) — thêm **stdio JSONL** cho test/CI |
| **Định dạng dây** | JSON Lines (1 request / 1 dòng, 1 phản hồi / 1 dòng) |
| **Điểm vào duy nhất** | Manager CLI/API (`dhopm-cli`) — FE **không** import `dhopm.*` (D7) |

---

## 1. Giả định & điều kiện tiên quyết

- **BE đã chạy**: lệnh `serve` đã mở cổng TCP **7079** (BE chủ động mở cổng, **không** chờ FE).
- FE và BE **khởi động độc lập**; FE lưu lại địa chỉ BE sau khi handshake thành công.
- Không có xác thực ở `protocol 1` (mạng LAN nội bộ — rủi ro chấp nhận).
- FE **không** phụ thuộc jar/class Java của BE.

---

## 2. Kết nối trong 3 bước

```
1) Kết nối TCP tới 127.0.0.1:7079
2) Gửi handshake:  {"id":0,"v":1,"cmd":"hello"}
3) Kiểm tra "protocol":1  → lưu địa chỉ, chính thức kết nối
```

**Dò BE (nếu chưa chắc địa chỉ):**

```
(1) thử 127.0.0.1:7079
     └─ thất bại → (2) quét LAN tìm cổng 7079
(3) gửi {"cmd":"hello","v":1} tới địa chỉ tìm được
     ├─ protocol khớp → kết nối
     └─ không phản hồi / sai contract → coi như "không tìm thấy BE"
```

- Chỉ **một kết nối = một chuỗi JSONL**: gửi request, đọc phản hồi tương ứng **theo `id`**.
- Các dòng `event` (progress/window/step/pattern) là **tùy chọn** — client có thể bỏ qua.

### Handshake `hello`

```jsonc
// FE → BE
{"id":0,"v":1,"cmd":"hello"}

// BE → FE
{"id":0,"v":1,"ok":true,"protocol":1,"manager":"dhopm-cli",
 "versions":[{"id":"v1","status":"ready"},{"id":"v2","status":"ready"},
             {"id":"v3","status":"ready"},{"id":"v4","status":"pending"}],
 "commands":["mine","detail","stream","window","validate","inspect","golden",
             "sweep","info","version","config","fetch","session.create","feed","session.mine"]}
```

- `hello.protocol ≠ 1` ⇒ FE coi là **không tương thích** (map sang `PROTOCOL_MISMATCH`/`BE_NOT_FOUND`).

---

## 3. Ví dụ request/response tối thiểu

```jsonc
// Tra cửa sổ (không cần dataset)
{"id":1,"v":1,"cmd":"window","f":0.9,"minOcc":1e-6}
// ← {"id":1,"v":1,"ok":true,"window":{"size":153},
//    "maxDOAsymptotic":10.0,"maxPartialAsymptotic":0.0654,
//    "maxDOExact":null,"maxPartialExact":null}

// Khai phá
{"id":2,"v":1,"cmd":"mine","dataset":"retail.dat","format":"fimi",
 "partial":0.06,"f":0.9,"minOcc":1e-6,"limit":0,"versions":["v3"],"export":true}
// ← {"id":2,"v":1,"ok":true,
//    "config":{"partial":0.06,"f":0.9,"minOcc":1e-6,"epsilon":1e-9,"workers":8},
//    "totalTransactions":88162,"lastTid":88162,"minSup":9.18,"maxDO":10.0,
//    "window":{"size":153,"effective":153,"minSup":9.18,"maxDO":10.0,
//              "evictions":87981,"liveEntries":12045,"deadEntries":0},
//    "issues":[],"jobs":[{"version":"v3","engine":"v3-optimized","total_ms":39,"patterns":3}],
//    "patterns":[{"items":["A","E"],"key":"A,E","do":1.2601,"support":3,"tids":[2,4,7]}],
//    "saved":{"fileID":"26-10-11_001","path":"/mine/mine_26-10-11_001.txt"}}
```

### Phong bì (envelope) phản hồi

```jsonc
{ "id":1, "v":1, "ok":true,  ... }                              // thành công
{ "id":1, "v":1, "ok":false, "code":"...", "message":"..." }    // lỗi
```

### Bộ lệnh tóm tắt

| Nhóm | Lệnh | Ghi chú |
|---|---|---|
| Khai phá | `mine` / `detail` / `stream` | **luôn** trả danh sách DOPs (D47) |
| Cửa sổ | `window` / `validate` / `sweep` | không cần dataset (trừ `sweep`) |
| Kiểm tra | `inspect` / `golden` | thống kê dataset / test-case |
| Phiên | `session.create` → `feed` → `session.mine` | xử lý tuần tự trong 1 session |
| File | `fetch <fileID>` | tải file kết quả BE đã ghi |
| Quản trị | `info` / `version` / `config` | manifest / phiên bản / cấu hình |
| Vận hành | `serve` | phía BE: mở cổng 7079 + stdio |

CLI (dạng positional) tương đương khoá JSON:

```
<cmd> <dataset> <partial> <factor> <limit> [flags]    ← lệnh khai phá
flag bắt buộc: --minOcc <x>
tùy chọn: --workers <n> --format fimi|text|zip --parts <n>
          --export [<path>] --detail 0|1|2 --step
```

---

## 4. Giới hạn & ràng buộc (bản tóm tắt)

### 4.1 Tham số khai phá (bắt buộc khai báo tường minh)

| Field | Kiểu | Miền hợp lệ | Ghi chú |
|---|---|---|---|
| `partial` (∂) | number | `[0,1]` | `0` ⇒ cảnh báo `PARTIAL_ZERO` |
| `f` | number | `(0,1]` | hệ số suy giảm |
| `minOcc` | number | `[0,1)` | **bắt buộc**; `0` ⇒ chế độ paper (W=∞) |
| `epsilon` | number | `>0` | mặc định `1e-9` |
| `workers` | int | `≥1` | mặc định = số CPU |

### 4.2 Ràng buộc then chốt

| # | Ràng buộc |
|---|---|
| 1 | Thiếu `partial`/`f`/`minOcc` ⇒ `MISSING_PARAMETER` (FE **phải** khai báo, BE không tự lấy mặc định) |
| 2 | `minSup = ∂ × N_eff`, `N_eff = min(TL, W)`; response trả `minSup` **đã dùng** |
| 3 | `window.size = null` ⇔ `W = ∞` (khi `minOcc = 0` hoặc `f = 1`) |
| 4 | `minOcc ≥ 1/(1−f)` ⇒ `W=0` ⇒ lỗi `MIN_OCC_TOO_LARGE`; `W > 10⁷` ⇒ `WINDOW_TOO_LARGE` |
| 5 | So sánh **nhiều version** bắt buộc **cùng** `(∂, f, minOcc, TL)`; khác ⇒ `MISMATCHED_WINDOW_CONFIG` (không gộp) |
| 6 | Pattern = **tập item**; đối chiếu/hiển thị theo `key` (`canonicalKey`, item sắp tự nhiên nối `,`) |
| 7 | Truyền số thực **double đầy đủ**, không làm tròn khi truyền |
| 8 | Kết quả **deterministic**: cùng request ⇒ cùng kết quả, bất kể số worker |
| 9 | `window`/`validate` **không có `TL`** ⇒ chỉ trả `…Asymptotic`; có `TL` ⇒ phải thêm `…Exact` |
| 10 | Protocol **chỉ thêm** field/cmd khi nâng version; **không bỏ** field cũ |

### 4.3 Định dạng dataset

- Hỗ trợ: **FIMI** (`.dat`), **text**, và nén **`.zip`** (giải nén 1 entry).
- **Không** hỗ trợ `.rar`, `.7z`. Format ngoài miền ⇒ `UNSUPPORTED_FORMAT`.
- File không tồn tại ⇒ `DATASET_NOT_FOUND`.

### 4.4 Giới hạn khác

- **Không auth** ở `protocol 1`.
- `fileID` **reset `NNN` mỗi ngày** (`mine_<yy-MM-dd>_<NNN>.txt`, NNN = 3 chữ số).
- Version `v4` hiện `status:"pending"` (chưa dùng được).

---

## 5. Lưu kết quả & `fetch` (D48)

- Bật ghi file khi: config `isLog = true` **hoặc** có cờ `--export [<path>]` (cờ ghi đè config).
- `--export` **không giá trị** ⇒ ghi vào `export.dir` (mặc định `/mine`, Windows: `%USERPROFILE%\mine`).
- File: `mine_<yy-MM-dd>_<NNN>.txt`; response kèm `saved.fileID` + `saved.path`.
- Tải lại nội dung: `{"cmd":"fetch","file":"26-10-11_001"}` ⇒ `{"fileID":...,"path":...,"content":"..."}`.
- **FE BẮT BUỘC lưu một bản sao cục bộ** khi `isLog`/`--export` — để không phụ thuộc BE.

---

## 6. Config (3 cấp)

```
default (built-in)  <  file JSON  <  CLI args
```

- File JSON: `%APPDATA%\dhopm\config.json` (Windows) / `~/.config/dhopm/config.json` (macOS/Linux).
- Lệnh: `config get <key>` · `config set <key> <value>` · `config list`.
- Khoá khởi điểm: `isLog` (bool, `false`), `export.dir` (string, `/mine`), `workers` (int, số CPU), `detail` (int, `1`).

---

## 7. Mã lỗi & cảnh báo

| Nhóm | Code |
|---|---|
| Request | `UNKNOWN_COMMAND`, `UNKNOWN_VERSION`, `MISSING_PARAMETER`, `INVALID_PARAMETER`, `MIN_OCC_TOO_LARGE`, `WINDOW_TOO_LARGE`, `MISMATCHED_WINDOW_CONFIG`, `DATASET_NOT_FOUND`, `UNSUPPORTED_FORMAT`, `SESSION_NOT_FOUND`, `FILE_NOT_FOUND`, `EXPORT_FAILED`, `CONFIG_KEY_UNKNOWN`, `CONFIG_INVALID_VALUE`, `NOT_IMPLEMENTED`, `INTERNAL_ERROR` |
| Phía FE (client) | `BE_NOT_FOUND`, `PROTOCOL_MISMATCH`, `CONNECT_REFUSED` |
| Cảnh báo (kèm `issues[]`, vẫn `ok:true`) | `PARTIAL_ZERO`, `INFEASIBLE_PARTIAL` |

---

## 8. Trách nhiệm phía FE

| Việc | Yêu cầu |
|---|---|
| Kết nối | Thay `TsonV1Bridge` bằng `BackendClient` (TCP 7079); giữ interface `BridgeEngine` (DIP) |
| Không phụ thuộc | **Không** import `dhopm.*`; **không** Maven-dependency vào module BE |
| Dò BE | Nút Detect: localhost → LAN → `hello` → lưu địa chỉ |
| Hiển thị | Đọc `window` (`size=null` ⇒ "W=∞"), `issues[]`, mã lỗi |
| Sao lưu | Khi `isLog`, lưu bản sao kết quả cục bộ |
| Giữ nguyên | `TamSimulationBridge` (engine demo nội bộ, không gọi BE) |

---

## 9. Thông tin liên quan

- **Nguồn chuẩn đầy đủ:** [`docs/plans/08-FE-CONTRACT.md`](docs/plans/08-FE-CONTRACT.md) (schema từng lệnh, model `Transaction`/`Config`/`Pattern`/`WindowInfo`/`Issue`/`SavedFile`, ví dụ end-to-end §12, tiêu chí chấp nhận §13).
- **Quyết định:** `docs/DECISIONS.md` (D45–D49 transport/CLI/lưu-file/config; D6/D7 Manager; D28/D41 `minOcc`; D48 file).
- **Kế hoạch/vận hành:** `docs/phases/P5-G5.md`, `docs/plans/05-BACKEND-CLIAPI-PLAN.md`.
- **Chạy BE:** `serve` (TCP 7079 + stdio) — xem module `implementation/dhopm-cli`.

---

*Đây là bản tóm tắt kết nối. Mọi chi tiết schema/hành vi tham chiếu `docs/plans/08-FE-CONTRACT.md`. Thay đổi hợp đồng phải: (1) tăng `contract version`, (2) chỉ thêm field/cmd, (3) ghi vào `docs/DECISIONS.md`.*
