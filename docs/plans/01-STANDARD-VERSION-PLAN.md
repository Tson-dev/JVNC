# DHOPM – Kế hoạch Phiên bản 1: Standard (Oracle – bám sát paper)

> Phiên bản "làm đúng trước, làm nhanh sau": code **rõ ràng, dễ đọc, dễ bảo trì**, tuân 100% chuẩn hóa thuật toán ở plan tổng thể (mục 2), dùng **Thread + worker mức Level 1**, NFR tối thiểu.
>
> ⚠️ **V1 là ORACLE — luôn ở trạng thái `ε = 0` (không cửa sổ).** V1 *không* triển khai cửa sổ ε; nó chính là **mốc đối chiếu** mà V2/V3/V4 phải khớp tuyệt đối khi `ε = 0` (bất biến **INV-I**). Xem `00-OVERALL-PLAN.md` §2.3, §2.9.

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-PLAN-001 |
| **Version** | 1.1 (Draft) |
| **Phụ thuộc** | `00-OVERALL-PLAN.md` (chuẩn hóa thuật toán **C1–C12**, khung threading) |
| **Source** | `docs/root/1-s2_0-S095219762600792X-main.md`, `docs/root/Nhom01_VDChayTay.md` |
| **Trạng thái** | ✅ **Đã hiện thực (G1)** — đóng băng làm oracle |

### Revision History

| Phiên bản | Mô tả |
|---|---|
| 1.0 | Lập plan V1 (C1–C6) |
| **1.1** | Đồng bộ với plan tổng thể **v2.0**: tham chiếu C1–**C12**, khai báo rõ **V1 = oracle ở `ε = 0`** (INV-I), đổi tên `epsilon` → `ε_cmp` (`epsilonCmp`), thêm nghĩa tròn & trần DO, thêm `TC9–TC18` làm bộ hồi quy của các phiên bản sau |

---

## 1. Mục tiêu

1. Cài đặt đúng DHOPM theo canonical spec → **qua TC1–TC8** (với `ε = 0`).
2. Dùng **Thread + worker** (Level 1): Reconstruction song song theo node; Mining song song theo cây con gốc.
3. Đạt **tập DOP trùng golden** và làm **nền golden double đầy đủ** cho V2/V3/V4 so sánh sau này.
4. NFR tối thiểu: đúng, ổn định, tái lập (deterministic), thread-safe.
5. **Cung cấp "chân lý" cho hệ cửa sổ ε**: mọi tính toán của V2+ khi `ε = 0` phải khớp V1 **bit-for-bit** (INV-I).

## 2. Phạm vi & Ngoài phạm vi

**Trong phạm vi:**
- Global DHO-List + Reconstruction + DUBO + Conditional list + Mine (DFS).
- Input: text (TID tường minh) cho test; FIMI reader cho benchmark sơ bộ (tái dùng `dhopm-common`).
- Level 1 threading.
- TC1–TC8 + benchmark cơ bản (runtime/memory sơ bộ).

**Ghi chú về ε:** V1 **luôn dùng `ε = 0`** ⇒ `W = ∞` ⇒ không có GĐ0 (cửa sổ & evict), không handle 2 tầng, `minSup = ∂ × TL` (một giai đoạn). V1 vẫn dùng chung `dhopm-common.window.WindowMath` để **in cảnh báo miền `∂` khả thi** mà **không đổi hành vi** — cảnh báo không được làm thay đổi tập DOP.

**Ngoài phạm vi (để dành V2/V3):**
- Tối ưu cấu trúc dữ liệu (List vs Set, ID+name, primitive array).
- Construction song song, work-stealing, chia cây sâu.
- Bất kỳ mẹo tối ưu nào làm giảm độ rõ ràng.
- **Cửa sổ ε, handle 2 tầng, evict O(1), minSup 2 pha, bound `min(DUBO, Z(X))`** → thuộc V2 (xem `02-EPSILON-WINDOW-VERSION-PLAN.md`).

## 3. NFR (tối thiểu)

| NFR | Yêu cầu |
|---|---|
| N1 Correctness | TC1–TC8 pass; đối chiếu với golden |
| N2 Determinism | Cùng input/tham số → cùng output (INV-E) |
| N3 Thread-safety | Không race condition; kết quả đúng khi song song |
| N4 Conf predict | Mỗi thành phần có test đơn vị cơ bản |
| N5 Runtime sơ bộ | Ghi được wall-clock 3 giai đoạn (không tối ưu) |

## 4. Định hướng thiết kế (V1 — "đúng & dễ hiểu")

> Thiết kế chi tiết class được quyết định trong giai đoạn triển khai; dưới đây là hướng dẫn (guideline). V1 dùng OOP hợp lý, trực tiếp khớp cấu trúc canonical.

- **Item**: `String` name (giữ đơn giản; V2 mới mã hoá số nguyên).
- **DHONode**: `item`, `ArrayList<Entry>` (Entry = `record(tid, len)`), `support()` = số entry, `doValue` (double).
- **DHOList**: `LinkedHashMap<Item, Node>` (thứ tự tạo + tra cứu), `ArrayList<Node>` để sort/duyệt. `sortBySupportAscending()` = stable sort theo `support()`.
- **Metrics**: các hàm thuần `decayFactor(f, tl, tid)`, `occupancy`, `dampedOccupancy`.
- **DUBO**: nhóm entry theo length dùng `TreeMap<Integer, int[2]>` (count, lastTid) hoặc nhóm tương đương; theo C1.
- **ConditionalListBuilder**: two-pointer trên 2 danh sách entry đã sắp TID.
- **Miner**: DFS đệ quy theo canonical; so sánh dùng `ε_cmp` (`epsilonCmp`, C4).
- **Engine** (`MiningEngine`): `loadBatch`, `mineNow` theo pipeline.

### Quy ước dùng chung bắt buộc từ canonical
- Entry append theo TID tăng dần (INV-B).
- DO/DUBO một node luôn tính tuần tự (C5).
- Conditional list không sort (INV-D).
- **`ε = 0` ⇒ `W = ∞` ⇒ không evict, ghi mọi entry** ⇒ V1 là trường hợp riêng của chính thuật toán cửa sổ (không phải thuật toán khác).

### 4.1 Áp dụng Design Pattern GoF (bắt buộc cho V1)

V1 **áp dụng design pattern GoF** ở những chỗ hợp lý (không ép từng pattern vào từng class). Định hướng ban đầu:

| Pattern (GoF) | Đặt vào | Mục đích |
|---|---|---|
| **Strategy** | Metrics/DUBO (cách tính DO, decay) | Cô lập công thức để test từng biến thể, giao sau cho V2 đổi chiến lược |
| **Factory Method / Builder** | Dựng `DHONode`, `DHOList`, `MiningConfig` | Kiểm soát tạo dựng, dễ thêm lựa chọn cấu trúc sau |
| **Template Method** | Pipeline giai đoạn (load → reconstruct → mine) | Khung cố định, bước con cho subclass V-sau |
| **Facade** | `MiningEngine` | API gọn cho CLI/benchmark/app (loadBatch, mineNow) |
| **Decorator/Proxy** | Instrumentation/log/benchmark ở tầng contract (`dhopm-common`) | **Logging & đo thời gian tách khỏi thuật toán** → không làm hot path chậm; tắt log = zero-overhead |
| **Observer** | (tuỳ chọn) tiến trình mining → progress callback | App UI cập nhật trạng thái/mining screen mà không trói engine vào UI |

> **Quan trọng:** dùng GoF để đạt "đúng & dễ hiểu", đồng thời tạo ranh giới module hoá cho V2 (điểm sửa đổi) và cho debug app G4 (wrapper đo tiến trình). Không tối ưu sớm lấy những pattern rườm rà.

### 4.2 CLI & kênh giao tiếp với công cụ (V1 — quyết định D6, xem 00 mục 4.2)

V1 là version đầu tiên làm đủ bộ lệnh chuẩn — vừa phục vụ G1 benchmark sơ bộ, vừa là
**khuôn mẫu** cho V2/V3 (đảm bảo so sánh chéo công bằng). Nguyên tắc: **CLI chỉ dùng API
mở trong `dhopm-common`**, không chạm nội bộ thuật toán.

| Lệnh | Ý nghĩa V1 |
|---|---|
| `mine` | Xuất tóm tắt ngắn (mặc định khi gọi `Main --dataset …` như cũ) |
| `detail` | Chi tiết từng pha: nodes/entries/roots + ms + top-N mẫu theo DO (double đầy đủ) |
| `stream` | Log thời gian thực: sự kiện load + tick mining (qua `ProgressAwareEngine`) |
| `golden` | Chạy TestKit TC1–TC8 báo PASS/FAIL |
| `inspect` | Thống kê dataset/config không mining |

> Các lệnh `window`, `validate`, `sweep` (mục 4.2 của plan tổng thể) là **đặc thù cửa sổ ε** ⇒ V1 chỉ chạy được `validate`/`window` ở chế độ thông báo (`ε = 0 ⇒ W = ∞`). Phần đầy đủ triển khai ở V2.

API mở cho tool: `Engine`, `PhaseAwareEngine`+`PhaseListener` (3 pha), `ProgressAwareEngine`+
`MiningProgressListener` (tiến trình thời gian thực), `TimedEngine`, getter chỉ đọc của
`MiningEngine` (node/entry/root count, total, lastTid). Khi tắt listener → zero-overhead (P3).

Option `--limit <n>` trên mọi lệnh đọc dataset: đọc **tối đa n giao dịch đầu** rồi dừng/mine
(đọc lười, không nạp file 1M+ vào RAM) — cần cho kosarak ~1M tx; dataset nhỏ hơn n → đọc hết
và in marker "whole dataset read (limit ignored)". Mặc định 0 = đọc hết như cũ.

## 5. Khung Threading Level 1 (bắt buộc)

```
Pool: ExecutorService, fixed, nThreads = Runtime.getRuntime().availableProcessors()

Reconstruction:
  - submit 1 Callable per node: recalcula DO(node) (reset DO về 0, cộng dồn tuần tự entries)
  - invokeAll, chờ hết → sort support (đơn luồng, stable) → xong

Mining:
  - Reconstruct xong, list được chia theo các prefix gốc (mỗi node đủ điều kiện mở rộng)
    → submit 1 task per cây con gốc (Mine(root_i, {i}))
  - Mỗi task chạy DFS nội bộ tuần tự (đệ quy trên chính task đó)
  - Kết quả các task gộp về tập kết quả chung (đồng bộ hóa tập trung sau khi join,
    HOẶC dùng ConcurrentLinkedHashSet tin cậy) — ưu tiên gộp sau join để giữ đơn giản

Construction: ĐƠN LUỒNG (không chia batch) — tránh race, giữ INV-B.
```

**Yêu cầu tối thiểu:**
- Pool đóng/giải phóng đúng (shutdown/await).
- Lỗi trong task → giữ/trả rõ ràng (Future.get ném ngoại lệ, không nuốt).
- Số task Reconstruction = số node; số task Mining = số cây con gốc khả thi.

## 6. Công việc & Milestone (V1)

**M1 – Setup hạ tầng & khởi tạo tài liệu design** (dựa trên G0 tổng thể)
- [ ] Tạo `dhopm-v1-standard` module; import `dhopm-common` (io, config, TestKit, util chung thread/log).
- [ ] CLI chạy thử đơn giản (nạp text/FIMI, in DOP).
- [ ] Bắt đầu **tài liệu design cấu trúc & từng thành phần/hàm** (đặt trong module) — viết song song khi code, **nội dung nghĩ ra trong lúc làm giai đoạn G1**; áp dụng GoF (mục 4.1).

**M2 – Mô hình dữ liệu & Construction** (GĐ1)
- [ ] `Item`, `Entry`, `Transaction` (chuẩn hoá item phân biệt, loại giao dịch rỗng).
- [ ] `DHONode`, `DHOList` (createOrGetNode, append entry, stable sort).
- [ ] `DHOListBuilder`: scan batch (đơn luồng), theo thứ tự TID.
- [ ] `StreamStatus`: tổng transaction, TL; `minSup = ∂ × tổng`.

**M3 – Reconstruction & Metrics** (GĐ2)
- [ ] `MetricCalculator` (F1–F6), `Reconstructor` (reset DO, cộng dồn tuần tự).
- [ ] Threading Level 1 cho Reconstruction (mỗi node 1 task).
- [ ] Test đơn vị: DO(A)=0.8551, DO(F)=1.2553 (TL=8, f=0.9).

**M4 – Mining** (GĐ3)
- [ ] `DUBOCalculator` (C1) — test đơn vị: E=4.5, B=1.8, CD=1.6402, AF=0.93, CDE=1.181, F=3.0375.
- [ ] `ConditionalListBuilder` (two-pointer; C2, INV-D).
- [ ] `Miner` DFS (C3,C4); gộp kết quả deterministic.
- [ ] Threading Level 1 cho Mining (mỗi cây con 1 task).

**M5 – Nghiệm thu đúng đắn**
- [ ] GoldenRunner chạy TC1–TC8 → pass (so tolerance 1e-6 với bảng; ghi lại **actual double** làm golden cho V2).
- [ ] **TC1–TC8 chạy với `ε = 0`** — đây là hình thức xác nhận rằng bộ test không bao giờ vô tình kích hoạt cửa sổ.
- [ ] Determinism: chạy lại nhiều lần (đổi pool size 1/2/4/cpu) → cùng kết quả.
- [ ] Xác nhận **log/benchmark độc lập**: bật/tắt logging không làm đổi kết quả; khi tắt log không ghi nhận chi phí đáng kể (Decorator ở contract, không ở hot path).
- [ ] CLI đa lệnh (4.2) chạy được trên cùng dataset: `mine/detail/stream/golden/inspect`; progress listener không đổi kết quả (INV-E).

**M6 – Benchmark sơ bộ & bộ tài liệu project V1**
- [ ] Chạy 2 dataset đại diện từ `dataset/` – `mushroom.dat` (dense) và `retail.dat` (sparse), chia 5 phần incremental; ghi runtime 3 giai đoạn + peak memory.
- [ ] Ghi lại **số liệu "oracle"**: runtime, peak memory, số DOP tại `mushroom.dat (∂=6%)` và `retail.dat (∂=0.1%)` với `f=0.9` — đây là **denominator** cho bảng ablation V1→V4 (plan tổng thể §7).
- [ ] **Bộ tài liệu riêng của project `dhopm-v1-standard`** (đặt trong module): README (chạy/tham số/CLI đa lệnh), design tóm tắt (kèm API mở 4.2), test plan & kết quả TC1–TC8, benchmark report sơ bộ.
- [ ] Đóng dấu: V1 là **golden reference / oracle** cho V2/V3/V4 (INV-I).

## 7. Nghiệm thu & Tiêu chí "Done" (V1)

- [ ] TC1–TC8 pass.
- [ ] **V1 được đóng băng** — mọi thay đổi sau này đều phải giữ TC1–TC8 xanh; không sửa golden để "cho xanh".
- [ ] Determinism qua nhiều pool size.
- [ ] Threading Level 1 thực sự được dùng (không phải đơn luồng ẩn) — xác nhận qua log/số task.
- [ ] Không có race (chạy `-ea`, nhiều lần; hoặc sanity check đơn giản).
- [ ] Chạy (hoặc test) **bộ lệnh CLI chuẩn** `mine/detail/stream/golden/inspect` trên `dhopm-v1-standard`; cùng kết quả khi bật/tắt progress listener (INV-E).
- [ ] Benchmark sơ bộ ghi được số liệu (mục 7 của plan tổng thể).
- [ ] Bộ tài liệu project V1 đầy đủ (M6).

## 8. Rủi ro (V1)

| Rủi ro | Xử lý |
|---|---|
| Kết quả lệch moốt chữ số thập phân so với bảng 4 chữ số | So golden với tolerance 1e-6; kiểm tra lại bằng công thức canonical (C1–C4) |
| Race khi gộp kết quả Mining | Gộp sau khi join (đơn giản nhất); sau đó mới cân nhắc concurrent set |
| Hiểu sai C2 (skip node khi sup<minSup) | Đối chiếu pseudocode Mine + hành vi G trong chạy tay |
| Recursion sâu (DFS) gây stack overflow ở dataset lớn | V1 ghi nhận; V2/V3 xử lý (đổi ranh giới/stack task). Dataset benchmark nhỏ trước |
| **V1 bị "vô tình" sửa để phục vụ V2 (mất vai trò oracle)** | INV-I + CI bắt TC1–TC8 phải xanh ở **mọi** phiên bản; không sửa golden của V1 |
| **`ε` bị hiểu nhầm là `ε_cmp`** | Tách tên ngay từ V1: `epsilon` = cửa sổ (luôn `0` ở V1), `epsilonCmp` = sai số so sánh (1e-9) |

---

*Chi tiết thuật toán: `00-OVERALL-PLAN.md` §2 (C1–C12, INV-A…INV-J). Đi tiếp `02-EPSILON-WINDOW-VERSION-PLAN.md`.*