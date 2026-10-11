# Báo cáo so sánh tối ưu V1 – V2 – V3 (DHOPM)

> **Mục đích:** đối chiếu khách quan phần "tối ưu hoá" giữa ba phiên bản V1/V2/V3, **dựa trên mã nguồn thật + kết quả build/test thật**, và chỉ rõ những chỗ **tuyên bố tối ưu nhưng thực tế không**.
> **Ngày lập:** 2026-10-10 · **Cập nhật:** 2026-10-11 (sau khi viết lại V3) · **Phương pháp:** đọc code `implementation/*`, chạy `mvn` (JDK 25.0.4.1), đối chiếu `docs/plans/01–03`, `docs/reports/G1/G2`.
> **Nguồn chân lý quyết định:** `docs/DECISIONS.md`.

---

## 0. Tóm tắt điều hành (verdict)

| Phiên bản | Tuyên bố | Thực tế đã kiểm chứng | Kết luận |
|---|---|---|---|
| **V1** | Oracle, bám paper, GoF, `minOcc=0` | ✅ Build + **32 test xanh**, deterministic | **Đạt** — đóng băng làm mốc |
| **V2** | Cửa sổ minOcc, evict O(1), minSup 2 pha, `min(DUBO,Z)` | ✅ Build + **18 test xanh** (+55 common), ablation **~10–17×** nhanh hơn V1 | **Đạt** — tối ưu & ngữ nghĩa đúng, có đo |
| **V3** (trước 2026-10-11) | "Optimized": bỏ Handle (an toàn hơn), SoA, `int itemId`, decay lookup, ForkJoin Level 2 | ❌ **KHÔNG biên dịch** (~30 lỗi), **0 test**, **không CLI**, không docs; **tái lập lỗi D39**; sort sai canonical; DUBO bound sai | ⚠️ **KHÔNG đạt** — chưa có cơ sở gọi là "tối ưu"; thậm chí **kém an toàn hơn V2** |
| **V3** (sau 2026-10-11) | như trên | ✅ Build + **16 test xanh**, **minOcc=0 ≡ V1 bit-for-bit**, **minOcc>0 ≡ V2 bit-for-bit**, deterministic theo số luồng; benchmark **median ≥3** (tập thưa) + **7 dataset dày** | ✅ **Đạt** — sau khi **sửa lỗi nhân bản tác vụ mining**, V3 nhanh hơn V2 **2.3–2.9×** (thưa/∂=0.01) và **4.5–28×** (dày/∂=0.05), heap thấp hơn |

> **Kết luận thẳng (cập nhật 2026-10-11):** V3 đã được **viết lại toàn bộ**, **đạt tính đúng** (bit-for-bit với V1/V2, deterministic) với **16 test** riêng (toàn reactor **121 test xanh**), và **đạt tối ưu rõ rệt**: sau khi sửa lỗi nhân bản tác vụ trong `MinerOptimized` (mỗi task chỉ xử lý node của mình), V3 nhanh hơn V2 **2.3–2.9×** trên dataset thưa (mining thật) và **4.5–28×** trên dataset dày (mining chi phối), **heap thấp hơn**. Lợi ích đến từ **SoA + bỏ `Handle` + loại bỏ công việc mining dư**, **không** từ song song.

---

## 1. Bằng chứng build / test

### 1.1 Trạng thái mới nhất (2026-10-11, sau khi viết lại V3)

| Lệnh | Kết quả |
|---|---|
| `mvn -f implementation\pom.xml test` (toàn reactor) | ✅ **BUILD SUCCESS** — tests: common **55**, v1 **32**, v2 **18**, v3 **16** = **121** |
| `mvn -f implementation\pom.xml -pl dhopm-v3-optimized -am test-compile` | ✅ **BUILD SUCCESS** |
| `run.bat` (mvn test toàn reactor) | ✅ **thành công** |

`dhopm-v3-optimized/src/test/java/dhopm/v3/engine/` có **2 file test** (`InvarianceTest` 5 ca, `WindowBehaviorTest` 11 ca).

### 1.2 Trạng thái tại thời điểm audit (2026-10-10, trước khi viết lại)

| Lệnh | Kết quả |
|---|---|
| `mvn -pl dhopm-common,dhopm-v1-standard,dhopm-v2-minocc test` | ✅ **BUILD SUCCESS** — tests: common **55**, v1 **32**, v2 **18** = **105** |
| `mvn -pl dhopm-v3-optimized -am test-compile` | ❌ **COMPILATION FAILURE**, ~30 lỗi |
| `run.bat` (mvn test toàn reactor) | ❌ **thất bại** tại `dhopm-v3-optimized` (V3 nằm trong `<modules>`) |

Thư mục `implementation/dhopm-v3-optimized/src/test/java/dhopm/v3` **rỗng**; `.../v3/cli` **rỗng**; `docs/` **rỗng (0 file)**.

---

## 2. Ba phiên bản tối ưu cái gì (đối chiếu plan ↔ code)

### V1 — Standard (oracle)
- Item = `String`, DHO-List = `LinkedHashMap`, `ArrayList<Entry>`, threading Level 1.
- **Không có cửa sổ**, `minSup = ∂ × TL` (1 pha) ⇒ bộ nhớ/thời gian theo `N`.
- Đo thật (G1 report): `retail.dat` **87 s** và `mushroom.dat` **301 s** để ra **0 pattern** — đây là động lực cho minOcc.
- Đạt mục tiêu "đúng & dễ hiểu". Không nhắm hiệu năng.

### V2 — MinOcc / Window
Ngữ nghĩa mới + tối ưu thật, **đã đo**:
- **Cửa sổ suy giảm** `W(f,minOcc)`: bỏ qua ngoài cửa sổ, `head` offset, **evict O(1)**.
- **minSup 2 pha** `∂ × min(TL, W)` — đóng băng khi cửa sổ đầy.
- **Short-circuit** `minSup > Z(f,TL) ⇒ ∅` O(1) (biến 87–300 s thành ∅ tức thì).
- **Bound `min(DUBO, Z(X))`** prune chặt hơn.
- **D39-safe:** `Entry(Handle ref1, int len, int tid)` + `isLive()` kiểm `ref1.tid == tid` (`Entry.java:19,27-29`) — chống bẫy tái sử dụng slot.
- Kết quả ablation (1 lần, khảo sát): retail **291 ms → 17 ms (~17×)**, kosarak **1 946 ms → 193 ms (~10×)**.

### V3 — Optimized (đã viết lại 2026-10-11)
Thiết kế thực tế (sau viết lại):
- `int itemId` + `ItemDictionary`; DHO-List = `LinkedHashMap` giữ **thứ tự tạo node**.
- **SoA** thật cho node: `DHONodeOptimized` giữ `int[] tids, int[] lens` **riêng tư**, `head`, `size`, `doValue` (không còn object `Entry`).
- **Bỏ object `Handle`** nhưng **giữ `tid` gốc** trong `int[] tids` ⇒ **không tái lập D39** (liveness kiểm theo `alive[tid mod W]` + `tid` được suy ra chính xác từ vị trí; dead-prefix được cắt single-thread trong reconstruction).
- `WindowBufferOptimized`: `long windowSize` + cờ `infinite` cho `minOcc=0` (paper mode **không** cấp phát mảng) ⇒ cách ly đúng INV-I.
- `DecayLookup`: bảng `Math.pow(f,k)` cho `k=0..W-1`, fallback `Math.pow` ⇒ **bit-identical** với V1/V2.
- `ForkJoinPool` song song **theo từng node của list** (subtree tasks) ở mining + song song DoTask ở reconstruction; **gộp sau join** theo thứ tự ⇒ deterministic.
- `minSup` 2 pha + short-circuit + bound `min(DUBO,Z)` **kế thừa đúng ngữ nghĩa V2**; sort canonical **tăng dần stable**.
- Trạng thái: **đã biên dịch, 16 test xanh, 121 test toàn reactor**.

---

## 3. Bảng so sánh tối ưu theo hạng mục

| Hạng mục | V1 | V2 | V3 (sau viết lại 2026-10-11) |
|---|---|---|---|
| Biểu diễn item | `String` | `String` | `String` ở interface; `int itemId` + `ItemDictionary` nội bộ ✔ |
| Tra cứu node | `LinkedHashMap<Item,Node>` | `LinkedHashMap` | `DHOListOptimized` (LinkedHashMap giữ thứ tự tạo) ✔ |
| Xích entry | object `Entry` | `Entry(Handle,len,tid)` | SoA `int[] tids, int[] lens` (private) + `head/size` ✔ |
| Cơ chế sống/chết | không cửa sổ | `Handle.tx` + kiểm `ref1.tid==tid` ✅ | `int[] tids` giữ `tid` gốc + `alive[tid mod W]`; dead-prefix cắt single-thread ✅ |
| Bộ nhớ cửa sổ | O(N) | O(W), Handle[] | SoA mảng nguyên thuỷ; `minOcc=0` dùng cờ `infinite` (**không** cấp phát) ✔ |
| minSup 2 pha | không | ✅ | ✅ kế thừa ngữ nghĩa |
| Short-circuit | không | ✅ | ✅ kế thừa |
| Prune bound | DUBO | `min(DUBO, Z(X))` per-node | `min(DUBO, Z(X))` **per-node** ✓ |
| Sort canonical (C3) | support ↑, stable | support ↑, stable | support ↑ **stable** theo thứ tự tạo node ✓ |
| Threading | Level 1 (Executor) | Level 1 | `ForkJoinPool` theo node; **gộp sau join** (không CopyOnWriteArrayList) ✓ |
| CLI | 5 lệnh | 10 lệnh | (bench có `--engine v3`); không có CLI app riêng |
| Test | 32 | 18 | **16** (invariance + window behavior) ✅ |
| Số đo hiệu năng | có (G1) | có (G2) | **có** (xem §4) ✅ |

> Các nhãn `❌` trong phiên bản audit trước đây (mất `tid` gốc/D39, `W=∞` OOM, sort giảm dần, DUBO group toàn cục, `CopyOnWriteArrayList`, dùng API nền sai) **đều đã được khắc phục** khi viết lại.

---

## 4. Số liệu đo được

### 4.1 Ablation V1 → V2 (tham chiếu G2, `--limit`)

| So sánh | Dataset | V1 | V2 |
|---|---|---|---|
| Runtime (khảo sát `--limit`) | retail 88 162 tx | 291 ms | **17 ms** |
| Runtime (khảo sát `--limit`) | kosarak 200 000 tx | 1 946 ms | **193 ms** |
| Memory | retail | 180.6 MB | **119.8 MB** |
| Test | — | 32 ✅ | 18 ✅ |

*(Nguồn: `docs/reports/G2-V2-MINOCC-BAOCAO.md` §4 — số 1 lần, chưa median.)*

### 4.2 V3 vs V2 vs V1 — đo trực tiếp 2026-10-11 (sau sửa lỗi tác vụ; `BenchmarkMain`, JDK 25, workers=12)

`f=0.9`, `minOcc=1e-6`, `parts=5`. Tập **thưa: median 3 lần**; tập **dày: 1 lần** (timeout 90 s). Số cuối = dòng cuối cùng (tích luỹ sau khi nạp hết dữ liệu).

**Tập thưa — mining chạy thật (∂=0.01):**

| Workload | Engine | constr (ms) | reconst (ms) | mining (ms) | **total (ms)** | patterns | heap (MB) |
|---|---|---|---|---|---|---|---|
| retail 88 162 tx | v1 | 104 | 105 | 591 | **802** | 0 | 301 |
| retail 88 162 tx | v2 | 17 | 28 | 68 | **115** | 0 | 227 |
| retail 88 162 tx | **v3** | 16 | 12 | 9 | **39** | 0 | 144 |
| kosarak 200 000 tx | v1 | 134 | 149 | 1703 | **2009** | 0 | 500 |
| kosarak 200 000 tx | v2 | 17 | 25 | 50 | **95** | 3 | 157 |
| kosarak 200 000 tx | **v3** | 19 | 11 | 7 | **39** | 3 | 137 |
| chainstore 100 000 tx | v1 | 101 | 151 | 253 | **508** | 0 | 195 |
| chainstore 100 000 tx | v2 | 17 | 27 | 39 | **84** | 0 | 133 |
| chainstore 100 000 tx | **v3** | 16 | 12 | 7 | **36** | 0 | 127 |

**Tập dày — mining chi phối (∂=0.05):**

| Workload (limit) | V2 total | V3 total | **V2/V3** | patterns |
|---|---|---|---|---|
| mushroom 8 124 tx | 841 | **83** | **10×** | 0 |
| newMushroom 8 416 tx | 14 984 | **536** | **28×** | 20 |
| pumsb_star 10 000 tx | 407 | **71** | **5.7×** | 0 |
| accidents 10 000 tx | 5 185 | **1 156** | **4.5×** | 0 |
| pumsb 10 000 tx | timeout | timeout | — | — |
| connect 10 000 tx | timeout | timeout | — | — |
| chess 3 196 tx | timeout | timeout | — | — |

**Đọc số liệu:**
- **Kết quả trùng khớp tuyệt đối** (cùng số pattern ở mọi workload) — parity V3 ≡ V2.
- **V3 vs V2:** thưa **2.3–2.9×** (retail 115→39; kosarak 95→39; chainstore 84→36); dày **4.5–28×**.
- **V3 vs V1:** thưa **~13–21×**; short-circuit (∂=0.15) V1 307/455 ms còn V2/V3 ~16–18 ms.
- **Heap: V3 thấp nhất** ở mọi workload (retail 144 < 227 < 301; mushroom V3 117 < V2 327; accidents 41 < 429).
- **Nguyên nhân:** (1) basket reconstruction 1.7–2.6× nhanh hơn (SoA, bỏ `Handle`); (2) **sửa lỗi nhân bản tác vụ** trong `MinerOptimized.SubtreeTask.compute()` — mỗi task chỉ xử lý index `start` (trước đây lặp cả dải, làm mining dày chậm hơn V2 4–30×). Xem `G3-V3-OPTIMIZED-BAOCAO.md` §5.4.

### 4.3 Đo độ mở rộng theo số luồng

Tập dày (mining chi phối), so đơn luồng vs 12 luồng:

| Workload | V2 w1 | V3 w1 | **V3/V2 @w1** | V2 w12 | V3 w12 |
|---|---|---|---|---|---|
| newMushroom ∂=0.05 | 65 710 | 2 063 | **32×** | 14 984 | 536 |
| accidents ∂=0.05 (10k) | 2 936 | 98 | **30×** | 5 185 | 1 156 |

⇒ Lợi ích V3 đến **chủ yếu từ bỏ công việc dư (thuật toán)**, không phải từ ForkJoin. Ở cửa sổ nhỏ, `workers=12` có thể **chậm hơn `w1`** (overhead fork/join) — hướng tinh chỉnh còn mở.

---

## 5. Mâu thuẫn "tuyên bố tối ưu vs thực tế"

> Mỗi mục có **bằng chứng địa chỉ `file:line`** để kiểm chứng lại.

> **Trạng thái xử lý (cập nhật 2026-10-11):** toàn bộ C1–C9 dưới đây là **phát hiện tại thời điểm audit 2026-10-10** (V3 draft). Sau khi **viết lại V3**, tất cả đã được khắc phục:
>
> | Mục | Vấn đề audit | Trạng thái 2026-10-11 |
> |---|---|---|
> | C1 | V3 không biên dịch (~30 lỗi) | ✅ **BUILD SUCCESS** (toàn reactor 121 test) |
> | C2 | Dùng API nền giả định (`f()`, `epsilonCmp()`, `.Record`, `onWindowUpdate`) | ✅ khớp `MiningConfig.decayFactor()/epsilon()`, `TimingRecorder`, `WindowListener.onWindow()` |
> | C3 | Tái lập lỗi D39 (mất `tid` gốc) | ✅ giữ `int[] tids`; liveness đúng theo `tid`; invariant test xanh |
> | C4 | Không biểu diễn được `W=∞` (minOcc=0) | ✅ cờ `infinite`, **không** cấp phát mảng; `minOcc=0 ≡ V1` bit-for-bit |
> | C5 | Sort canonical giảm dần | ✅ sort **tăng dần stable** theo thứ tự tạo node |
> | C6 | DUBO bound group toàn cục | ✅ DUBO/Z(X) tính **per-node** |
> | C7 | ForkJoin dùng sai + `CopyOnWriteArrayList` | ✅ ForkJoin theo node; **gộp sau join** |
> | C8 | Tài liệu lỗi thời | ✅ đã đồng bộ (xem §C8) |
> | C9 | Xung đột `String` vs `int` ở builder | ✅ builder dùng `Transaction` (`String[]`); dictionary nội bộ engine |
>
> Phần mô tả chi tiết bên dưới **giữ nguyên làm hồ sơ lịch sử**.

### C1 — V3 tuyên bố "tối ưu" nhưng **không biên dịch**
`mvn -pl dhopm-v3-optimized` trả **~30 lỗi**, ví dụ:
- `DHOListBuilderOptimized.java:74,126` — `for (int itemId : tx.items())` nhưng `Transaction.items()` trả `String[]` (`Transaction.java:18`).
- `DHOListBuilderOptimized.java:55`, `MinerOptimized.java:108`, `ReconstructorOptimized.java:75` — `ForkJoinPool.invokeAll(Collection<RecursiveAction>)` **không tồn tại** overload đó.
- `MinerOptimized.java:187` — `ForkJoinTask` không import.
- `DecayLookup.java:19` — thiếu `import dhopm.common.window.WindowMath`.

⇒ V3 chưa từng được compile chung với codebase ⇒ mọi tuyên bố hiệu năng vô nghĩa.

### C2 — V3 viết theo **API nền giả định**, lệch khỏi `dhopm-common` thật
- `MiningEngineV3.java:70,72,74,149,154,201` gọi `config.f()` — thực tế `MiningConfig` dùng `decayFactor()` (`MiningConfig.java:24`).
- `MiningEngineV3.java:154` gọi `config.epsilonCmp()` — thực tế là `epsilon()`.
- `MiningEngineV3.java:80-81` dùng `MiningConfig.DEFAULT_EPSILON_CMP` / `DEFAULT_EPSILON_WINDOW` — **không tồn tại**.
- `MiningEngineV3.java:103` dùng `TimingRecorder.Record` — **không tồn tại** (`TimingRecorder.java` không có inner class `Record`).
- `MiningEngineV3.java:190` gọi `windowListener.onWindowUpdate(...)` — tên đúng là `onWindow(...)` (`WindowListener.java:13`).
- `MiningEngineV3.java:40` — chưa override `Engine.name()`.

⇒ V3 được code **tách rời**, không dựa trên hợp đồng hiện hành (D6).

### C3 — V3 tái lập **đúng lỗi D39** mà V2 đã sửa (nghịch lý lớn nhất)
Plan 03 §4.3 tuyên bố: *"bỏ Handle ⇒ không còn tham chiếu chết ⇒ INV-H tự động thoả"*. **Sai.**
- `DHONodeOptimized` (`:13-16`) chỉ giữ `txSlot`, **không giữ `tid` gốc**.
- Kiểm sống/chết là **tautology**: `window.txId[slot] == window.txId[slot]` (`MetricCalculator.java:23,40`; tương tự `DHONodeOptimized.java:50,73`); `ConditionalListBuilderOptimized.java:103-105` dùng heuristic `txId[slot] != 0`.
- Khi `W` hữu hạn và `TL > W`, slot `tid mod W` **bị tái sử dụng**: entry đã chết vẫn thấy `alive[slot]==1` ⇒ bị coi là **sống** ⇒ `head` không tiến đúng ⇒ **support/DO sai âm thầm**.
- V2 làm **đúng**: `Entry.java:27-29` kiểm `ref1.tx != null && ref1.tid == tid`.

⇒ Ngược lại tuyên bố: V3 **kém an toàn hơn V2**, không phải an toàn hơn.

### C4 — V3 không biểu diễn được **cửa sổ vô hạn** (vi phạm chính INV-I)
- `WindowMath.INFINITE = Long.MAX_VALUE` (`WindowMath.java:22`).
- `MiningEngineV3.java:70-73` ép `long INFINITE` về `int windowSize` (**lossy conversion — lỗi biên dịch**) rồi `new WindowBufferOptimized(windowSize)`; constructor cấp phát mảng cỡ `windowSize` (`WindowBufferOptimized.java:22-26`).
- ⇒ Chế độ `minOcc=0` (paper) **không thể cấp phát** (OOM nếu ép kiểu được). V2 xử lý paper mode bằng `ref1 = null`, **không dùng buffer** (`WindowBuffer.java` javadoc; `Entry.paper`).

⇒ "minOcc=0 ⇒ V3 ≡ V1" (N1/INV-I) **chưa có cơ sở**, và thiết kế buffer cố định `W` chưa hỗ trợ chế độ paper.

### C5 — V3 đảo thứ tự sort canonical
- Canonical `00-OVERALL-PLAN.md` §2.2 GĐ2: sort support **tăng dần**, stable, tie-break **thứ tự tạo node**.
- `ReconstructorOptimized.java:84-86`: sort **giảm dần** (`-n.support`), tie-break bằng `itemId`.
- ⇒ lệch C3 canonical ⇒ rủi ro khác kết quả bit-for-bit so với V1/V2.

### C6 — V3 tính DUBO bound **sai/không đặc trưng node**
- `ReconstructorOptimized.java:98-143`: gộp `groupCount/groupLastTid` trên **tất cả node** rồi gán **cùng một giá trị DUBO cho mọi node**; `Z(X)` luôn gán `Z(f,TL)` (không phải `Σ_{t∈T_sống(X)} f^(TL−t)`).
- ⇒ "bound chặt hơn `min(DUBO, Z(X))`" (điểm mạnh được quảng cáo) **không được hiện thực đúng**. Bound còn **lỏng hơn** V2 (V2 tính per-node).

### C7 — "Level 2 ForkJoin" chưa tối ưu, thậm chí phản tác dụng
- Thu hoạch pattern bằng `CopyOnWriteArrayList` (`MinerOptimized.java:42`) và `patterns.add(...)` mỗi pattern ⇒ **copy mảng mỗi lần thêm**, tốn kém khi nhiều pattern — ngược mục tiêu tối ưu.
- `MinerOptimized.java:108` dùng `pool.invokeAll(...)` sai; `:187` `ForkJoinTask.invokeAll(...)` chưa import; nhánh nhỏ gọi `.compute()` trực tiếp (không thực sự work-stealing).
- Reconstruction `ReconstructorOptimized.java:75-79` dùng `invokeAll` + `join` lẫn lộn.
⇒ Chưa chứng minh nhanh hơn V2; một số lựa chọn **làm chậm đi**.

### C8 — Tài liệu trạng thái **lỗi thời & mâu thuẫn nhau**
| Nơi | Ghi (trước 2026-10-10) | Thực tế | Đã xử lý |
|---|---|---|---|
| `README.md` (gốc) | V3 "🕓 Chưa code" | V3 đã có ~13 file main (dở) | ✅ sửa 2026-10-10 |
| `docs/Overview.md` §6 | "56 test xanh"; G2 "Đang tiếp theo", G3 "Chưa lập plan" | hiện **105 test** (55 common + 32 V1 + 18 V2); V2 **đã xong**; plan 03 đã có | ✅ sửa 2026-10-10 |
| `docs/reports/SYNTHESIS-REPORT.md` §1.2/§3.1 | V2 "Chưa bắt đầu" | V2 **đã xong + 18 test** | ✅ thêm ERRATA 2026-10-10 |
| `docs/plans/03` header | (plan) V3 chưa thực hiện | đã có code draft không build |

### C9 — Xung đột hợp đồng giữa dữ liệu & builder trong V3
`MiningEngineV3.loadBatch` (`:124`) map `String`→`itemId` **tại engine**, nhưng `DHOListBuilderOptimized.loadTransaction` (`:74`) lại duyệt `int itemId : tx.items()` (là `String`). Hai lớp giả định `Transaction` ở **hai kiểu khác nhau** ⇒ không thể cùng biên dịch.

---

## 6. Vậy "V3 tối ưu hơn V2" đúng hay sai?

**Cập nhật 2026-10-11 — V3 đã đúng và tối ưu rõ rệt sau khi sửa lỗi nhân bản tác vụ.** Cụ thể:

| Tiêu chí plan 03 kỳ vọng | Thực tế 2026-10-11 |
|---|---|
| N1: `minOcc=0` ≡ V1 | ✅ bit-for-bit (`minOccZeroIsBitForBitV1`) |
| N1: `minOcc>0` ≡ V2 | ✅ bit-for-bit (`v3EqualsV2AcrossCombos`, TC10–TC18) |
| N2: nhanh hơn V2 | ✅ **rõ rệt**: **2.3–2.9×** (thưa/∂=0.01) và **4.5–28×** (dày/∂=0.05); bằng nhau khi short-circuit |
| N3: memory ≤ V2 | ✅ **đạt** — heap V3 thấp hơn ở mọi workload đo được (retail 144<227; mushroom 117<327; …) |
| N9: "không còn object Handle ⇒ rủi ro tham chiếu chết biến mất" | ✅ đúng (liveness theo `tid` giữ nguyên, không tautology) |
| M4: bộ test invariance | ✅ 16 test (invariance + window behavior) |
| M6: docs + benchmark report | ✅ báo cáo này + §4 |

**Kết luận:** về **tính đúng**, V3 đạt (bit-for-bit, deterministic). Về **tối ưu**, V3 **nhanh hơn V2 rõ rệt** sau khi sửa lỗi nhân bản tác vụ mining (mỗi task chỉ xử lý node của mình); lợi ích đến từ **SoA + bỏ `Handle` + loại bỏ công việc dư**, **không** phải song song. V3 nay **vượt mốc G3** "nhanh hơn V2 ≥3/4 dataset" (tỉ lệ nhỏ nhất đo được 2.3×, vượt xa ngưỡng 1.2×).

---

## 7. Khuyến nghị

> Cập nhật 2026-10-11: các mục 1–7 dưới đây **đã được thực hiện** khi viết lại V3. Danh sách giữ lại làm hồ sơ.

1. ~~**Sửa build trước tiên**~~ — ✅ **xong**: V3 biên dịch, toàn reactor 121 test xanh.
2. ~~**Khôi phục tính an toàn D39 ở V3**~~ — ✅ **xong**: giữ `int[] tids`, liveness theo `tid`.
3. ~~**Sửa chế độ paper (minOcc=0)**~~ — ✅ **xong**: cờ `infinite`, không cấp phát mảng.
4. ~~**Khôi phục canonical**~~ — ✅ **xong**: sort tăng dần stable; DUBO/Z(X) per-node.
5. ~~**Bỏ `CopyOnWriteArrayList`**~~ — ✅ **xong**: gộp sau join.
6. ~~**Viết test V3**~~ — ✅ **xong**: 16 test invariance/window behavior (bit-for-bit V1/V2).
7. ~~**Đồng bộ tài liệu trạng thái**~~ — ✅ **xong 2026-10-10**.

### Việc còn lại (đề xuất)
- **Đo peak memory bằng JMX/JFR** thay vì `Runtime` (con số heap một phần bị `List<Transaction>` của bench chi phối).
- **Tinh chỉnh song song:** `FORK_THRESHOLD`/chọn pool — hiện `workers=12` có thể **chậm hơn `w1`** ở cửa sổ nhỏ.
- **Microbenchmark từng điểm tối ưu** (on/off) để áp tiêu chí C9 (`speedup ≥ 1.2×` / `memory ↓ ≥ 20%`).
- **Dataset rất dày** (pumsb, connect, chess) timeout ở ∂ nhỏ cho **cả** V2/V3 — dùng ∂ lớn hơn / limit nhỏ hơn.
- **Bổ sung README V3 + CLI** nếu muốn V3 dùng được như V1/V2 (hiện chỉ có đường bench `--engine v3`).

---

## 8. Phụ lục — Bảng đối chiếu file then chốt

| Chủ đề | V2 (đúng) | V3 (vấn đề) |
|---|---|---|
| Liveness D39 | `Entry.java:27-29` (`ref1.tid == tid`) | `MetricCalculator.java:23,40` (tautology); `ConditionalListBuilderOptimized.java:103-105` |
| Cửa sổ | `WindowBuffer.java:20-21` (chỉ hữu hạn, handle) | `WindowBufferOptimized.java:22-26` (cấp phát theo W; ∞ fail) |
| head/dead prefix | `DHONode.java:75-79` | `DHONodeOptimized.java:69-78` (dựa liveness sai) |
| Sort canonical | V2 `Reconstructor` tăng dần | `ReconstructorOptimized.java:84-86` giảm dần |
| DUBO bound | V2 per-node trong 1 vòng duyệt | `ReconstructorOptimized.java:98-143` group toàn cục |
| Harvest pattern | gộp sau join | `MinerOptimized.java:42` CopyOnWriteArrayList |
| Hợp đồng | `Engine`/`WindowListener`/`TimingRecorder` hiện hành | ~~`MiningEngineV3.java` dùng `f()`,`epsilonCmp()`,`.Record`,`onWindowUpdate`~~ — **đã sửa 2026-10-11** |

---

*Kết thúc báo cáo. **Đã cập nhật 2026-10-11** (§0, §4, §6, §7) sau khi **sửa lỗi nhân bản tác vụ mining** và đo lại: V3 build xanh, 121 test toàn reactor, bit-for-bit với V1/V2, **nhanh hơn V2 2.3–28×** và heap thấp hơn.*
