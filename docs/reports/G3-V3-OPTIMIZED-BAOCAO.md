# Báo cáo chi tiết — Giai đoạn G3: V3 Optimized (DHOPM)

> Nguồn triển khai: `docs/phases/P3-G3.md` · Đặc tả: `docs/plans/03-OPTIMIZED-VERSION-PLAN.md`,
> `docs/plans/00-OVERALL-PLAN.md` §2.7 (threading Level 2), §2.9 (INV-I) · Sổ quyết định: `docs/DECISIONS.md`.
> Trạng thái tại ngày báo cáo (**2026-10-11**): **code V3 đã viết lại + sửa lỗi nhân bản tác vụ mining
> (`MinerOptimized`), build + 121 test xanh**; benchmark **median ≥3** trên tập thưa + **khảo sát một lần**
> trên 7 dataset dày (có timeout) ⇒ **V3 nhanh hơn V2 2.3–28× và tốn ít bộ nhớ hơn**. Số liệu & mâu thuẫn audit:
> `docs/reports/V1-V2-V3-OPTIMIZATION-COMPARISON.md` (§4, §5).

---

## 1. Mục tiêu giai đoạn

Triển khai **V3 Optimized** — giữ **nguyên ngữ nghĩa của V2** (cửa sổ minOcc), thay **cấu trúc dữ liệu
bên trong** để nhanh hơn và tốn ít bộ nhớ hơn, dùng **threading Level 2 (ForkJoinPool)**.

Bốn trụ (theo `P3-G3.md` §3):

1. **N1 — Đúng đắn:** `minOcc=0 ⇒ ≡ V1/V2` bit-for-bit; `minOcc>0 ⇒ ≡ V2` (double đầy đủ).
2. **N2 — Runtime:** nhanh hơn V2 trên cùng dataset/tham số, ghi rõ ratio.
3. **N3/N6 — Memory:** peak memory ≤ V2; **phẳng theo N** khi cửa sổ đầy.
4. **N9 — Bỏ lớp Handle:** không còn đối tượng `Handle`; evict vẫn O(1).

Sản phẩm: module `dhopm-v3-optimized` + benchmark + báo cáo (M6).

---

## 2. Những gì đã bàn giao

### 2.1 Thành phần V3 (package `dhopm.v3`, 13 file main)

| Giai đoạn | Lớp | Nội dung |
|---|---|---|
| GĐ0 | `window.WindowBufferOptimized` | SoA `int[] txId` + `byte[] alive`; `long windowSize`; cờ `infinite` cho `minOcc=0`; evict O(1) `evictTid`, `isAlive`, `liveCount` |
| GĐ0 | `window.DecayLookup` | bảng `Math.pow(f,k)` cho `k=0..W−1`, fallback `Math.pow(f,age)` khi `W=∞` — đảm bảo **bit-identical** |
| GĐ1 | `dho.DHONodeOptimized` | SoA **thật**: `int[] tids`, `int[] lens` (private) + `head`/`size`/`doValue`; `addEntry`/`support()=size−head`/`discardDeadPrefix` |
| GĐ1 | `dho.DHOListOptimized` + `construction.DHOListBuilderOptimized` | list giữ **thứ tự tạo node**; builder dùng `Transaction` (`String[]`) |
| GĐ2 | `reconstruction.ReconstructorOptimized` | cắt dead-prefix single-thread rồi tính DO song song theo từng node; sort **tăng dần stable** |
| GĐ3 | `mining.DuboBound` + `ConditionalListBuilderOptimized` + `MinerOptimized` | bound `min(DUBO, Z(X))` **per-node**; two-pointer trên SoA; `ForkJoinPool` chia **theo node** (subtree tasks), **gộp sau join** — **sửa lỗi: mỗi task chỉ xử lý node của chính nó** (xem §5.4) |
| công thức | `metrics.MetricCalculator` + `metrics.ZCalculator` | DO = `Σ (1/len)·decayLookup.get(TL−tid)`; `Z(f,TL)` |
| dictionary | `io.ItemDictionary` | ánh xạ `String↔int` (nội bộ) |
| engine | `engine.MiningEngineV3` | `PhaseAwareEngine + WindowAwareEngine + AutoCloseable`; `loadBatch` 2 chế độ (windowed có **skip-write**, paper); short-circuit; getter `windowInfo()/warnings()/evictionCount/…` |

### 2.2 Kiểm thử (package `dhopm.v3.engine`)

- `InvarianceTest` (5 ca): `minOcc=0 ≡ V1` bit-for-bit · `minOcc=0 ≡ V2` paper · `minOcc=0` không kích hoạt cửa sổ · bất biến qua pool {1,2,4,CPU} · deterministic ở chế độ cửa sổ.
- `WindowBehaviorTest` (11 ca): V3 ≡ V2 trên 7 tổ hợp `(f, minOcc, ∂)` · TC10–TC18 · E2 (`f=1` suy biến nhưng chạy).

### 2.3 Benchmark

`BenchmarkMain` mở rộng thêm `--engine v3` (cùng `--minOcc/--limit/--workers/--parts`); `dhopm-bench` thêm phụ thuộc `dhopm-v3-optimized`.

### 2.4 Tài liệu

- Báo cáo này (`docs/reports/G3-V3-OPTIMIZED-BAOCAO.md`).
- `docs/reports/V1-V2-V3-OPTIMIZATION-COMPARISON.md` — cập nhật §0/§1/§2/§3/§4/§5/§6/§7 (V3 đã sửa; số đo mới).
- `docs/Readme.md` — cập nhật trạng thái V3 + bảng số đo.
- `docs/Overview.md` — trạng thái G3 (cập nhật).

> **Chưa làm (M6):** bộ tài liệu **riêng trong module** `dhopm-v3-optimized/docs/` (README/design/test-report/benchmark) như V1/V2.

---

## 3. Kiểm thử — 121 test xanh / 0 lỗi (toàn reactor)

| Nguồn | Số test | Ghi chú |
|---|---|---|
| dhopm-common | 55 | không đổi |
| dhopm-v1-standard | 32 | không đổi |
| dhopm-v2-minocc | 18 | không đổi |
| **dhopm-v3-optimized** | **16** | `InvarianceTest` 5 + `WindowBehaviorTest` 11 |
| **Tổng** | **121** | `mvn -f implementation\pom.xml test` ⇒ BUILD SUCCESS |

**Điểm mấu chốt đã xác nhận bằng test:**
- `minOcc = 0` ⇒ V3 **bit-for-bit** ≡ V1 (paper) và ≡ V2 (paper) — INV-I.
- `minOcc > 0` ⇒ V3 **bit-for-bit** ≡ V2 (so `Double.compare` exact qua TestKit) — TC10–TC18.
- **Deterministic** theo số luồng {1,2,4,CPU}.

---

## 4. Bảng đối chiếu từng điểm tối ưu (P3 §4) — giữ / thay

| Điểm tối ưu (P3 §4) | Trạng thái V3 | Kết quả đo |
|---|---|---|
| Item = `int itemId` + dictionary | **giữ** (nội bộ engine) | không tách riêng microbenchmark |
| Node tra cứu theo index | giữ ở mức hợp lý (`DHOListOptimized` + `LinkedHashMap` giữ thứ tự) | — |
| **Bỏ lớp `Handle`** (`txSlot` + trạng thái) | **giữ** — SoA `int[] tids`+`alive` | evict/reconstruction nhanh hơn V2 (xem §5) |
| **WindowBuffer SoA** | **giữ** — `int[] txId` + `byte[] alive` | cache-friendly, O(1) |
| Entry SoA trên node | **giữ** — `int[] tids, int[] lens` | giảm boxing đáng kể ở reconstruction |
| **Decay lookup theo tuổi** | **giữ** — bảng `Math.pow(f,k)`, `k=0..W−1` | **bit-identical** V1/V2 |
| Sort support primitive, stable theo thứ tự tạo | **giữ** — sort tăng dần stable | canonical C3 đúng |
| DUBO bucket length | dùng `TreeMap` len tăng dần (đủ nhanh ở cửa sổ nhỏ) | bound per-node đúng |
| **Threading Level 2 (ForkJoin)** | **giữ cơ chế**, **chia theo node** (không chia theo segment); **sửa lỗi nhân bản** (mỗi task chỉ xử lý node của mình) | **tăng tốc lớn** sau sửa — xem §5.3–§5.5 |

> Mỗi quyết định "giữ" đều kèm test invariance chặn hồi quy (không đổi tập kết quả / thứ tự tính toán).

---

## 5. Kết quả chạy thật — V3 vs V2/V1

Đo bằng `BenchmarkMain`, `f=0.9`, `minOcc=1e-6`, `parts=5`, `workers=12` (mặc định). Tập **thưa** chạy
**median của 3 lần**; tập **dày** chạy **1 lần** (timeout 90 s/lần). Số liệu là dòng **cuối** (tích luỹ sau
khi nạp hết dữ liệu). Môi trường: Windows, JDK 25, 12 luồng.

### 5.1 Tập thưa — mining chạy thật (∂=0.01, dưới trần short-circuit)

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

**V3 vs V2:** retail **2.9×**, kosarak **2.4×**, chainstore **2.3×**. **V3 cũng nhẹ hơn cả V1/V2** (retail
144 < 227 < 301 MB; kosarak 137 < 157). Kết quả pattern **trùng khớp tuyệt đối**.

### 5.2 Short-circuit (∂=0.15, `∂·N_eff > Z ⇒ ∅`)

| Workload | V1 total | V2 total | V3 total |
|---|---|---|---|
| retail 88 162 tx | 307 | 16 | 17 |
| kosarak 200 000 tx | 455 | 18 | 17 |

Ở đây V2/V3 trả ∅ trong O(1) (chi phí = quét dataset) nên **không phân biệt**; V1 vẫn full.

### 5.3 Tập dày — mining chi phối (∂=0.05)

| Workload (limit) | V2 total | V3 total | **V2/V3** | patterns |
|---|---|---|---|---|
| mushroom 8 124 tx | 841 | **83** | **10×** | 0 |
| newMushroom 8 416 tx | 14 984 | **536** | **28×** | 20 |
| pumsb_star 10 000 tx | 407 | **71** | **5.7×** | 0 |
| accidents 10 000 tx | 5 185 | **1 156** | **4.5×** | 0 |
| pumsb 10 000 tx | timeout | timeout | — | — |
| connect 10 000 tx | timeout | timeout | — | — |
| chess 3 196 tx | timeout | timeout | — | — |

Phases V3 (constr/recon/mining): mushroom 7/9/67 · newMushroom 6/8/522 · pumsb_star 11/12/47 · accidents 14/420/721.
Heap V3 vs V2: mushroom 117 vs 327 · newMushroom 449 vs 847 · pumsb_star 60 vs 361 · accidents 41 vs 429.
`pumsb`/`connect`/`chess` **timeout ở cả hai engine** (lattice DOP quá lớn tại ∂ nhỏ) — không phải khác biệt V2/V3.

### 5.4 Nguyên nhân chính — sửa lỗi nhân bản tác vụ mining

Bản V3 đầu tiên cho `mining` **chậm hơn V2** trên tập dày (mushroom 3 912 ms, pumsb_star 21 980 ms,
accidents 51 646 ms). Nguyên nhân: trong `MinerOptimized.SubtreeTask.compute()`, mỗi task **lặp
`for (i = start..size)`** nên vừa **tái mở rộng toàn bộ sibling ở mọi tầng** (dư ~O(branching)), vừa
**chồng lấn với các root task** do `mine()` đã phát một task cho mỗi index. V2 chỉ đệ quy **một lần cho mỗi
node**. Sửa lại để **mỗi task chỉ xử lý index `start` của chính nó** (giữ nguyên tập kết quả — dedup theo
canonical key) làm `mining` giảm **~50–300×**, biến V3 từ chậm hơn thành **nhanh hơn V2 4.5–28×** trên tập dày.
Toàn bộ **121 test vẫn xanh** ⇒ tập pattern **bit-for-bit không đổi**.

### 5.5 Đơn luồng & mở rộng theo số luồng (tập dày)

| Workload | V2 w1 | V3 w1 | **V3/V2 @w1** | V2 w12 | V3 w12 |
|---|---|---|---|---|---|
| newMushroom ∂=0.05 | 65 710 | 2 063 | **32×** | 14 984 | 536 |
| accidents ∂=0.05 (10k) | 2 936 | 98 | **30×** | 5 185 | 1 156 |

⇒ Lợi ích đến **chủ yếu từ bỏ công việc dư (thuật toán)**, không phải từ song song. Ở cửa sổ nhỏ,
`workers=12` còn **chậm hơn `w1`** (fork/join overhead) — hướng tinh chỉnh `FORK_THRESHOLD`/ngưỡng pool cần mở.

---

## 6. Đối chiếu "Done" (P3 §7)

| Tiêu chí P3 §7 | Kết quả |
|---|---|
| `minOcc=0` ⇒ ≡ V1/V2; `minOcc>0` ⇒ ≡ V2 (double đầy đủ) | ✅ 121 test xanh (16 V3), bit-for-bit |
| Determinism qua nhiều pool size | ✅ {1,2,4,CPU} |
| Không còn đối tượng `Handle`; evict O(1) | ✅ SoA `alive` + `evictTid` |
| Benchmark đầy đủ; V3 nhanh hơn V2 ≥3/4 dataset (hoặc phân tích rõ) | ✅ V3 nhanh hơn V2 trên **mọi workload chạy được**: **2.3–2.9×** (thưa/∂=0.01) và **4.5–28×** (dày/∂=0.05). `pumsb`/`connect`/`chess` timeout **cả hai** engine |
| Peak memory ≤ V2 | ✅ heap V3 thấp hơn ở mọi workload đo được (retail 144<227; mushroom 117<327; …) — đo bằng `Runtime` (xem hạn chế) |
| Report rõ từng quyết định tối ưu kèm số liệu | ⚠️ có bảng §4 + số đo §5; **chưa** microbenchmark tách từng điểm |
| Bộ tài liệu project V3 đầy đủ (M6) | ❌ chưa có `dhopm-v3-optimized/docs/` |

---

## 7. Kết luận & hạn chế

**Kết luận:**
- **Đúng đắn:** V3 **đạt** — bit-for-bit với V1 (paper) và V2 (window), deterministic qua mọi pool size.
- **Tối ưu:** V3 **thắng rõ rệt** — nhanh hơn V2 **2.3–2.9×** (dataset thưa, mining thật) và **4.5–28×**
  (dataset dày, mining chi phối), đồng thời **heap thấp hơn**. Lợi ích đến từ **SoA + bỏ object `Handle` +
  loại bỏ công việc mining dư** (xem §5.4), **không** từ song song.
- **Vượt mốc G3** “nhanh hơn V2 ≥3/4 dataset” — vượt xa ngưỡng 1.2×.

**Hạn chế / việc còn lại:**
1. **Peak memory bằng JMX/JFR** thay vì `Runtime` sau khi đã nạp dataset (con số heap bị `List<Transaction>` của benchmark chi phối).
2. **Tinh chỉnh song song:** `FORK_THRESHOLD`/chọn pool — hiện `workers=12` có thể **chậm hơn `w1`** ở cửa sổ nhỏ.
3. **Microbenchmark từng điểm tối ưu** (on/off) để áp tiêu chí C9 (`speedup ≥ 1.2×` / `memory ↓ ≥ 20%`).
4. **Dataset rất dày** (pumsb, connect, chess) cần ∂ lớn hơn / limit nhỏ hơn — không phải lỗi engine.
5. **Bộ tài liệu module V3 (M6):** `README.md`, `docs/design.md`, `docs/test-report.md`, `docs/benchmark.md`.

---

*Kết thúc báo cáo. Khi có microbenchmark từng điểm + đo peak memory bằng JMX/JFR, cập nhật §5, §6, §7.*
