# DHOPM – Kế hoạch Phiên bản 2: Epsilon / Window

> Phiên bản **mở rộng ngữ nghĩa** của V1: thêm **ε (sai số hệ thống / ngưỡng dưới của DO)** ⇒ **cửa sổ suy giảm** `W(f,ε)`, `minSup` **hai giai đoạn**, cơ chế **handle hai tầng `ref1 → ref2`** để evict **O(1)**, bound **`min(DUBO, Z(X))`** và **short-circuit theo trần DO**.
>
> Ở `ε = 0`, V2 **bám sát V1 bit-for-bit** (INV-I) — đây là điều kiện nghiệm thu bắt buộc.

---

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-PLAN-002 |
| **Version** | 1.0 (Draft) |
| **Bí danh** | **V2 — Epsilon / Window** |
| **Module** | `implementation/dhopm-v2-epsilon` |
| **Phụ thuộc** | `00-OVERALL-PLAN.md` §2.3, §2.4, §2.5, §2.7 (C7–C12, INV-G…INV-J) · `01-STANDARD-VERSION-PLAN.md` |
| **Ý tưởng nguồn** | `docs/Draft Idea.txt` (tác giả) |
| **Quyết định** | D9, D10, D11, D12, D14 trong `00-OVERALL-PLAN.md` §10 và `docs/DECISIONS.md` |
| **Định vị threading** | Level 1 (giống V1) — V2 tập trung **đúng ngữ nghĩa**, chưa tối ưu |
| **Trạng thái** | 🕔 Chưa bắt đầu — **giai đoạn ưu tiên số 1** sau G1 |

### Revision History

| Phiên bản | Mô tả |
|---|---|
| 1.0 | Lập plan V2 từ `docs/Draft Idea.txt`, chuẩn hoá theo `00-OVERALL-PLAN.md` v2.0 |

---

## 1. Mục tiêu

1. **Chứng minh giá trị của ε**: biến "đọc toàn bộ lịch sử stream" thành "duy trì cửa sổ trượt hữu hạn" — bộ nhớ và thời gian gần như **độc lập độ dài stream**.
2. **Định nghĩa & hiện thực** `W(f,ε)`, `N_eff`, `minSup` hai giai đoạn, GĐ0 evict O(1) bằng handle hai tầng.
3. **Giữ đúng bất biến bảo toàn**: `ε = 0 ⇒ V2 ≡ V1 ≡ paper` (INV-I); `|DO_win − DO_full| ≤ ε` (INV-G).
4. **Đo lường**: số lần evict, entry sống/chết, độ lệch DO so với full-recompute, so sánh V1 ↔ V2 trên cùng `(∂, f, ε, dataset)`.
5. **Cải thiện pruning**: `UB'(X) = min(DUBO(X), Z(X))` — câu trả lời trực tiếp cho hạn chế tự thừa nhận của paper.

## 2. Vì sao cần V2 (bài toán gốc)

Trong công thức DO, một transaction ở tuổi `k` đóng góp `O(X,T) × f^k`. Với `f` nhỏ và stream dài:

- Số transaction ở **đuôi xa** có `f^k < ε` gần như **không đóng góp gì** cho bất kỳ DO nào, nhưng vẫn phải **đọc, lưu, duyệt** mỗi lần.
- DHO-List phình theo N ⇒ **O(N)** bộ nhớ, **O(N)** thời gian quét.

Hệ quả: ngay cả khi đã có tối ưu tốt (V3/V4), **phần tử không thay đổi được** là việc xử lý dữ liệu không có giá trị. V2 xử lý đúng ở tầng **ngữ nghĩa**, trước khi tối ưu kỹ thuật.

> `docs/Draft Idea.txt` mô tả hiện tượng này bằng ví dụ: item `A` xuất hiện 1000 lần ngoài cửa sổ + 1 lần trong cửa sổ ⇒ `support(A) = 1`, bị loại ở C2 ⇒ **1000 entry đó chẳng để làm gì**. V2 giữ cho việc đó **O(1)** thay vì quét lại từng node.

## 3. Phạm vi

**Trong phạm vi (V2):**
- `WindowMath` + `ParameterValidator` trong `dhopm-common` (định nghĩa dùng chung).
- GĐ0 — cửa sổ & evict O(1) bằng handle `ref1 → ref2`; `head` offset cho entry chết.
- `minSup` hai giai đoạn; `WindowInfo` (kích thước cửa sổ, số transaction hiệu dụng).
- Bound `UB'(X) = min(DUBO(X), Z(X))`; short-circuit toàn cục theo trần `Z(f,TL)`.
- TC9–TC18; hồi quy TC1–TC8 (`ε=0`) trên V2.
- CLI: `window`, `validate`, `sweep`; `WindowAwareEngine` để quan sát vòng đời cửa sổ.
- Báo cáo đo ε: evict count, tỉ lệ entry sống, độ lệch DO, ablation V1 ↔ V2.

**Ngoài phạm vi (để dành V3/V4):**
- Bỏ xích handle, dùng chỉ số `int` vào circular buffer (V3).
- SoA/primitive array, precomputed decay, lookup `f^k` (V3).
- `ForkJoinPool`, chia cây sâu, song song hoá Construction (V3).
- Data-oriented thuần, pipeline 3 pha, memory-mapped I/O (V4).

## 4. NFR

| NFR | Yêu cầu |
|---|---|
| **N1 Tương đương paper** | `ε = 0` (hoặc `W ≥ TL`) ⇒ **bit-for-bit ≡ V1** (INV-I); TC1–TC8 xanh trên V2 |
| **N2 Sai số cửa sổ** | Mọi pattern: `DO_win(X) ≥ DO_full(X) − ε − ε_cmp` và `|DO_win − DO_full| ≤ ε + ε_cmp` (INV-G) |
| **N3 Evict O(1)** | Xoá 1 transaction khỏi cửa sổ tốn **O(1)**, không phụ thuộc số node |
| **N4 Bộ nhớ độc lập N** | Peak memory **gần như phẳng** khi stream dài ra (đo được, ≥10× số transaction) |
| **N5 Determinism** | Cùng `(∂, f, ε, TL)` ⇒ cùng tập DOP, bất kể pool size (INV-E, C5) |
| **N6 Thread-safety** | Evict đơn luồng trước mọi pha song song; không race trên handle |
| **N7 Validator** | Cấu hình sai ⇒ **báo lỗi rõ ràng trước khi chạy**; `∂` ngoài miền khả thi ⇒ cảnh báo kèm số liệu |
| **N8 Quan sát được** | Có API báo cáo: số lần evict, entry sống/chết, `W`, `N_eff`, `minSup`, trần `Z` |

## 5. Thuật toán & thiết kế chi tiết

### 5.1 Giai đoạn GĐ0 — Cửa sổ & Evict (mới)

Canonical (xem `00-OVERALL-PLAN.md` §2.2). Triển khai:

```
WindowMath.computeWindow(f, epsilon):
    if epsilon == 0        -> WindowInfo.INFINITE   (W = ∞)
    if f == 1.0            -> WindowInfo.INFINITE   (W = ∞)
    if epsilon >= 1/(1-f)  -> throw IllegalArgumentException  (W = 0 → cửa sổ rỗng)
    W = ceil( ln(epsilon * (1 - f)) / ln(f) )
    if W > maxWindow       -> throw IllegalArgumentException  (cấp phát không thiện thực)
    return W

// ⚠️ D38: maxPartial có HAI công thức — validator phải dùng công thức CHÍNH XÁC
WindowMath.maxPartialExact(f, epsilon, TL):
    W       = computeWindow(f, epsilon)          // ∞ nếu epsilon==0 hoặc f==1
    N_eff   = min(TL, W)                          // W=∞ → N_eff = TL
    Z       = (f == 1.0) ? TL : (1 - f^TL) / (1 - f)
    return Z / N_eff                              // ∂ tối đa khả thi, hữu hạn

// Chỉ để ước lượng / in bảng tra cứu (TL → ∞). KHÔNG dùng để chặn tham số.
WindowMath.maxPartialAsymptotic(f, epsilon):
    W = computeWindow(f, epsilon)
    return (W is ∞) ? 1.0 : 1.0 / ((1 - f) * W)
```

> ⚠️ **Vì sao phải tách hai công thức (D38).** `maxPartialAsymptotic` là **giá trị ở giới hạn `TL → ∞`**, tức giá trị tại **ranh giới pha 2**. Ở `TL` ngắn (pha 1) nó **sai lệch một bậc độ lớn**, và luôn **nhỏ hơn** giá trị chính xác ⇒ dùng nhầm để chặn tham số sẽ **chặn oan**:
>
> | `f` | `ε` | `W` | `TL` | `N_eff` | `Z(f,TL)` | **chính xác** `Z/N_eff` | **xấp xỉ** `1/((1−f)W)` | Chênh lệch |
> |---|---|---|---|---|---|---|---|---|
> | 0.9 | 1e-6 | 153 | **4** | 4 | 3.439 | **85.98 %** | 6.54 % | **×13.1** |
> | 0.9 | 1e-3 | 88 | **20** | 20 | 8.784 | **43.92 %** | 11.36 % | **×3.9** |
> | 0.9 | 1e-3 | 88 | **88** | 88 | 9.9996 | 11.36 % | 11.36 % | ×1.00 (đã bằng) |
>
> Nếu validator dùng nhầm xấp xỉ ⇒ **chặn oan** các cấu hình hợp lệ với stream ngắn. Nếu in nhầm xấp xỉ thành "chính xác" ⇒ người đọc tưởng `∂` phải ≤ 6.5 % trong khi thực tế ở `TL=4` có thể tới 86 %.
>
> ⇒ Lệnh `window` (chạy **trước** khi có dataset, không biết `TL`) **chỉ được trả** `maxPartialAsymptotic` với hậu tố `…Asymptotic` và `…Exact = null`; xem `05-BACKEND-CLIAPI-PLAN.md` §7.

```
WindowBuffer  (vòng tròn, W slot, index = tid mod W)
    slot[tid mod W] = Handle { tid, len, Transaction tx }

GĐ0 khi nạp transaction mới có TID = m  (ĐƠN LUỒNG — INV-E):
    if (m - tid) >= W   -> BO QUA: không ghi entry cho transaction này
    if (m - W) >= 1     -> evict(tid = m - W):   handle.tx = null      // O(1)
```

**Quyết định thiết kế (C10):** evict **chỉ** trên các transaction **đã từng ghi entry**. Transaction nằm ngoài cửa sổ ngay từ đầu (rất phổ biến khi nạp lần đầu `TL ≫ W`) thì **không ghi** ⇒ không có gì để xoá ⇒ rẻ nhất.

**Vì sao evict chạy trong `loadBatch`, không chỉ `mineNow`:** nếu chỉ evict ở `mineNow`, giữa hai lần mine sẽ tích tụ transaction đã chết trong DHO-List ⇒ bộ nhớ phình theo số batch. Với evict ngay khi nạp, DHO-List giữ **≤ W transaction** ngay từ mọi thời điểm.

### 5.2 Handle hai tầng `ref1 → ref2` (C11, INV-H)

```
Entry của Node (ref1)  ──►  Handle (ref2)  ──►  Transaction { tid, len }
                              (WindowBuffer)         tx = null  ⇔  đã evict
```

| Thao tác | Cách làm | Chi phí |
|---|---|---|
| **Evict** | `window.handle(tid).tx = null` | **O(1)**, không cần biết node nào chứa `tid` |
| **Phát hiện entry chết** | Khi node duyệt entry (GĐ2/DUBO/GĐ3), kiểm `entry.ref1 != null && entry.ref1.tx != null && entry.ref1.tid == entry.tid` | **O(1)/entry**, **ẩn vào pha đang chạy** — không có pha quét riêng |
| **Loại entry chết** | `head++` (entry chết luôn là **tiền tố**) | **O(1) amortized** |
| **Support** | `support(X) = size(X) − head(X)` | **O(1)** |

**Vì sao phải hai tầng (lập luận bắt buộc):** *truy vấn "sống hay chết" không được dereference một tham chiếu có thể đã bị vô hiệu hóa*. Nếu một tầng, `entry.tx = null` rồi đọc `entry.tx.cột` là **dereference con trỏ/đối tượng chết** — hành vi không xác định trong C/C++ (segfault; `try/catch` không cứu được). Với hai tầng, `ref1` **luôn trỏ tới một Handle còn sống** (identity bất biến, chỉ payload bị clear) ⇒ đọc `handle.tx == null` **luôn an toàn**. `ref2` chính là **handle** — đúng ý `docs/Draft Idea.txt`.

#### ⚠️ 5.2.1 Bẫy tái sử dụng slot — `Entry` **bắt buộc** lưu `tid` (D39)

Cơ chế "identity Handle bất biến" ở trên **chỉ đúng nếu Handle không bị tái sử dụng**. Nhưng `WindowBuffer` là vòng tròn `tid mod W` ⇒ **tái dùng đúng object Handle cũ** cho transaction mới:

```
  entry.ref1 ──► handle                    // CÙNG object, đã bị tái sử dụng
  handle.tx   = transaction MỚI (tid = m) // ≠ null ⇒ node TƯỞNG entry cũ còn sống!
```

Node nào chưa duyệt tới entry đó sẽ đọc ra **sai transaction** ⇒ `tid` sai, `|X|/|T|` sai, `DO` sai — lỗi **âm thầm, không crash**, khó phát hiện bằng mắt.

**⇒ Thiết kế bắt buộc:**

```java
// Entry của một node — 3 trường, KHÔNG được bỏ tid
record Entry(Handle ref1, int ref2, long tid) {}

// Quy tắc sống (dùng ở MỌI nơi đọc entry: GĐ2, DUBO, GĐ3 two-pointer)
boolean isLive(Entry e) {
    return e.ref1 != null && e.ref1.tx != null && e.ref1.tid == e.tid;
}
```

| Phương án | Đánh giá |
|---|---|
| **1 — Entry lưu `tid`, kiểm khớp** | ✅ **đã chọn.** Rẻ, không đổi cấu trúc `WindowBuffer`, giữ được identity Handle cho C11 |
| 2 — Generation counter trên Handle | Nặng hơn (thêm số + phép so sánh) |
| 3 — Không tái dùng slot | ❌ phá vỡ `O(1)` evict và bộ nhớ `O(W)` |

Phương án 1 biến INV-H thành: *"không bao giờ dereference tham chiếu đã vô hiệu hóa **và** luôn kiểm `ref2.tid == entry.tid` trước khi tin là còn sống"*.

**Tại sao entry chết luôn là tiền tố:** entry được append theo TID tăng dần (INV-B); evict theo TID tăng dần ⇒ sau `head` mọi entry đều sống. ⇒ compaction chỉ cần **một chỉ số `head`**, không dịch mảng (V3 sẽ bỏ xích `ref1` để tiết kiệm con trỏ).

**Bộ nhớ:** `O(W × avgLen)` cho cửa sổ + `O(|I|)` cho node. So với V1 (`O(N × avgLen)`): **bộ nhớ độc lập độ dài stream**.

### 5.3 `minSup` hai giai đoạn (C9)

```
N_eff(TL) = min(TL, W)
minSup(TL) = ∂ × N_eff(TL)
```

| Giai đoạn | Điều kiện | `minSup` |
|---|---|---|
| Pha 1 | `TL < W` | `∂ × TL` — tăng dần, **≡ paper** |
| Pha 2 | `TL ≥ W` | `∂ × W` — **đóng băng** |
| `W = ∞` | — | `∂ × TL` — ≡ paper |

**Tính tại `mineNow`** (INV-F), lưu vào `WindowInfo` để CLI/app quan sát.

**Vì sao bắt buộc phải dùng `N_eff` chứ không phải `TL`:** nếu giữ `∂ × TL` sau khi đã cắt cửa sổ, ngưỡng tiếp tục tăng theo độ dài stream trong khi dữ liệu chỉ còn `W` transaction ⇒ số pattern **tuyến tính → 0** ⇒ thuật toán "chết dần" dù dữ liệu vẫn còn giá trị. Ngưỡng phải **đo trên cùng đơn vị với dữ liệu đang dùng**.

### 5.4 Trần DO & short-circuit (§2.5)

**Validator + short-circuit toàn cục:**

```
Z(f, TL) = Σ_{k=0..TL-1} f^k = (1 - f^TL)/(1 - f)      (f = 1 -> TL)

nếu  minSup > Z(f, TL) + ε_cmp:
    -> kết quả RỖNG, trả về ngay (O(1)), kèm cảnh báo:
       "∂=…, f=… ⇒ minSup=… > Z=… ; không thể có DOP. Giảm ∂ hoặc tăng f."
    -> miền khả thi:  ∂ <= 1 / ((1 - f) * W)
```

**Bound theo node (chặt hơn DUBO):**

```
Z(X) = Σ_{t ∈ T_sống(X)} f^(TL - t)
DO(Y) <= Z(X)   ∀ Y ⊇ X   (vì T(Y) ⊆ T(X) và |Y| <= |Td|)
=> UB'(X) = min( DUBO(X), Z(X) )      thay cho prune bằng DUBO(X) đơn thuần
```

- `Z(X)` tính **cùng vòng duyệt entry** như DUBO ⇒ gần như miễn phí.
- `min(DUBO, Z(X)) ≤ DUBO` luôn ⇒ **chỉ prune thêm, không bao giờ prune sai**.
- Đây là bound chặt nhất trong họ "khối lượng suy giảm" ⇒ giải quyết hạn chế *"cần kỹ thuật prune tốt hơn"* mà paper tự nêu.

### 5.5 DUBO sau evict — vì sao Lemma 2 vẫn đúng

Có một lo ngại hiển nhiên: DUBO dựa trên giả định "các transaction còn sống", nhưng sau evict thì tập transaction thay đổi. Kết luận:

- Mọi tính toán (DO, DUBO, `Z(X)`, support) đều thực hiện **chỉ trên tập entry sống** ⇒ toàn bộ phép tính nằm trên **cùng một instance** (cửa sổ hiện tại).
- Vì vậy **Lemma 2 của paper vẫn đúng** cho thể hiện đang được duyệt.
- DUBO **vẫn là trên trên**: evict chỉ bỏ các transaction có đóng góp `< ε` ⇒ sai số ≤ ε (INV-G).

### 5.6 Threading Level 1

Giữ nguyên khung Level 1 của V1 (§5 trong `01-STANDARD-VERSION-PLAN.md`):
- GĐ0 evict: **đơn luồng**.
- GĐ1 Construction: **đơn luồng** (giữ INV-B: entry append TID tăng dần ⇒ entry chết là tiền tố).
- GĐ2 Reconstruction: song song **mỗi node 1 task**; trong task đó dời `head` của chính node (O(1)).
- GĐ3 Mining: song song **mỗi cây con gốc 1 task**; DFS nội bộ tuần tự.
- Gộp kết quả **sau join** ⇒ deterministic.

### 5.7 Cấu trúc lớp (hướng dẫn; chi tiết chốt khi code)

| Lớp | Trách nhiệm |
|---|---|
| `dhopm.common.window.WindowMath` | **công thức `W(f,ε)`** (dùng chung) |
| `dhopm.common.window.WindowInfo` | record `(windowSize, effectiveTransactions, minSup, maxDO, evictions, liveEntries, deadEntries)` |
| `dhopm.common.window.ParameterValidator` | kiểm tra miền tham số + cảnh báo `∂` khả thi |
| `dhopm.common.contract.WindowAwareEngine` | `addWindowListener` (mới) |
| `dhopm.v2.window.Handle` | `record`/class giữ `tid, len, tx` — payload clear khi evict |
| `dhopm.v2.window.WindowBuffer` | circular buffer W slot; `handle(tid)`, `evict(tid)` |
| `dhopm.v2.dho.Entry` | `record(ref1: Handle, ref2: int)` — `ref2` là **chỉ số entry trong node** (mục đích đoán lại / debug) |
| `dhopm.v2.dho.DHONode` | `head`, `size`, `entries`, `doValue`, `support() = size − head` |
| `dhopm.v2.dho.DHOList` | như V1 + `head` bookkeeping |
| `dhopm.v2.metrics.ZCalculator` | `Z(f,TL)` toàn cục, `Z(X)` theo node |
| `dhopm.v2.mining.DUBOCalculator` | `DUBO(X)` + `Z(X)` trong cùng vòng duyệt ⇒ `UB'(X)` |
| `dhopm.v2.mining.Miner` | DFS canonical + C2 dùng support sống + prune `UB'` |
| `dhopm.v2.MiningEngineV2` | pipeline GĐ0→GĐ3 |

**Ghi chú thiết kế:** `Entry` giữ **cả `ref1` (Handle) và `ref2` (chỉ sô entry)** đúng như `Draft Idea.txt` đề xuất — `ref2` giúp viết test và hỗ trợ debug, không bắt buộc cho thuật toán.

## 6. Bộ test (TC9–TC18) & hồi quy

### 6.1 Hồi quy bắt buộc (chạy trước mọi test khác)

| Test | Kỳ vọng |
|---|---|
| **TC1–TC8 với `ε = 0` trên V2** | **bit-for-bit ≡ V1** (INV-I) |
| **TC9** | Cùng dữ liệu/tham số TC1, `ε = 0` ⇒ kết quả bit-for-bit = TC1 |
| **TC10** | Chọn `ε` sao cho `W ≥ TL` ⇒ ≡ paper |
| **TC12** | `f = 1` + `ε > 0` ⇒ `W = ∞` ⇒ ≡ TC5 |

### 6.2 Test ngữ nghĩa cửa sổ

| TC | Mục đích | Kỳ vọng |
|---|---|---|
| **TC11** | `W ≪ TL` | mọi `DO_win` lệch `DO_full` **≤ ε** (INV-G); số DOP ≤ kết quả full |
| **TC13** | **entry chết là tiền tố** | `head` dịch đúng sau nhiều lần evict; `support = size − head`; không sót entry |
| **TC14** | **minSup 2 pha** | `TL` đi qua mốc `W`: `minSup` tăng tới `∂×W` rồi **đóng băng** |
| **TC16** | **validator + short-circuit** | `∂×N_eff > Z(f,TL)` ⇒ ∅ **tức thì** + cảnh báo có số liệu |
| **TC17** | **ví dụ "item A"** của `Draft Idea.txt` | A ngoài cửa sổ 1000 lần + trong cửa sổ 1 lần ⇒ `support_sống = 1 < minSup` ⇒ bị C2 loại ở GĐ2 |
| **TC18** | **window vs full-recompute** trên nhiều `(∂, f, ε)` | `∀X: DO_win(X) ≥ DO_full(X) − ε − ε_cmp`; mọi DOP_full hoặc là DOP_win, hoặc lệch ≤ ε |

### 6.3 Test trường hợp biên tham số (bắt buộc)

Bảng trường hợp biên ở `00-OVERALL-PLAN.md` §6.4 (E1–E10) phải có test tương ứng — đặc biệt:
- **E7:** `ε ≥ 1/(1−f)` ⇒ **ném lỗi cấu hình** rõ ràng.
- **E8:** `∂ = 0` ⇒ `minSup = 0` ⇒ khả thi nhưng **cảnh báo bùng nổ output**.
- **E2:** `f = 1, ∂ = 1` ⇒ `W = ∞`, `minSup = TL`, `Z = TL` ⇒ khả thi lý thuyết nhưng **suy biến** → cảnh báo.
- **E5:** `∂ = 0.15` với `N_eff = W` ⇒ `minSup` vượt trần ⇒ ∅ (minh hoạ ∂=0.15 chỉ dùng được khi `N_eff` nhỏ — TC1).

## 7. CLI cho V2

| Lệnh | Mục đích V2 |
|---|---|
| `mine`/`detail`/`stream`/`golden`/`inspect` | như V1, **cộng** thông tin cửa sổ trong output |
| **`window`** | In bảng tra cứu `W(f,ε)`, `N_eff`, `minSup`, trần `Z(f,TL)`, miền `∂` khả thi — **không cần dataset** |
| **`validate`** | Kiểm tra cấu hình, báo cảnh báo (ε quá lớn, ∂ ngoài miền, W vượt `maxWindow`) — trước khi chạy |
| **`sweep`** | Quét nhiều tổ hợp `(ε, ∂, f)` → bảng kết quả (số DOP, runtime, memory, độ dài TB) |
| `golden` | Chạy **TC1–TC18** (TC9–TC18 chỉ V2+) |

## 8. Công việc & Milestone (V2)

**M1 – Nền tảng cửa sổ trong `dhopm-common`**
- [ ] `WindowMath.computeWindow(f, ε)` + `WindowMath.maxDO(f, TL)` + `WindowInfo` + `ParameterValidator`.
- [ ] `WindowMathTest`: đối chiếu bảng tra cứu `W` (plan tổng thể §2.3.1) và bảng `∂` khả thi.
- [ ] Đổi tên tham số `epsilon` cũ → `epsilonCmp` trong `MiningConfig`; thêm `epsilon` (cửa sổ).
- [ ] `WindowAwareEngine` + `WindowListener` vào contract.

**M2 – Handle & Evict (GĐ0)**
- [ ] `Handle`, `WindowBuffer` (circular, W slot), `evict(tid)` O(1).
- [ ] `Entry(ref1, ref2)`, `DHONode` có `head`/`size`, `support = size − head`.
- [ ] GĐ0 trong `MiningEngineV2.loadBatch`: bỏ qua ghi entry ngoài cửa sổ; evict khi `m − W ≥ 1`.

**M3 – minSup 2 pha & Reconstruction**
- [ ] `minSup = ∂ × N_eff` (2 pha), tính ở `mineNow` (INV-F).
- [ ] `Reconstructor` dời `head`, tính DO chỉ trên entry sống; threading Level 1.

**M4 – Bound & Mining**
- [ ] `ZCalculator` (`Z(X)`, `Z(f,TL)`), `DUBOCalculator` trả `UB'(X) = min(DUBO, Z(X))`.
- [ ] Short-circuit toàn cục khi `minSup > Z(f,TL)+ε_cmp` + cảnh báo.
- [ ] `Miner` DFS: C2 dùng support sống; prune bằng `UB'`.

**M5 – Nghiệm thu đúng đắn**
- [ ] TC1–TC8 (`ε=0`) trên V2 **bit-for-bit ≡ V1**.
- [ ] TC9–TC18 xanh; E1–E10 xanh.
- [ ] Determinism qua nhiều pool size.

**M6 – Đo lường & tài liệu**
- [ ] Đo evict count, live/dead entries, độ lệch DO, ablation V1 ↔ V2.
- [ ] CLI `window`/`validate`/`sweep`.
- [ ] Bộ tài liệu riêng `dhopm-v2-epsilon` + báo cáo đo ε.

## 9. Nghiệm thu & "Done" (V2)

- [ ] **`ε = 0` ⇒ V2 ≡ V1 bit-for-bit** (INV-I).
- [ ] `|DO_win − DO_full| ≤ ε` (INV-G) trên toàn bộ benchmark.
- [ ] Evict **O(1)**; bộ nhớ đỉnh **gần như phẳng** khi stream dài (NFR-4, đo được ≥10× N).
- [ ] TC9–TC18 + E1–E10 xanh.
- [ ] Validator báo lỗi/cảnh báo đúng; short-circuit trả ∅ tức thì.
- [ ] CLI `window`/`validate`/`sweep` chạy được.
- [ ] Bảng ablation V1 ↔ V2 có số liệu evict, memory, runtime, độ lệch.
- [ ] Bộ tài liệu V2 đầy đủ.

## 10. Rủi ro (V2)

| Rủi ro | Xử lý |
|---|---|
| **ε làm sai kết quả so với paper** | `ε=0 ⇒ ≡ V1` (INV-I) + TC9–TC10 + TC18 kiểm độ lệch |
| **Handle 2 tầng viết sai → đọc tham chiếu chết** | INV-H; test TC13; **không `try/catch` để bắt dereference chết** |
| Evict không đồng bộ giữa các node | Evict đơn luồng trước mọi pha song song (C10); dời `head` nội bộ mỗi node ⇒ không phụ thuộc thứ tự |
| DUBO không còn hợp lệ sau evict | DUBO/Z tính **chỉ trên entry sống** ⇒ Lemma 2 vẫn đúng (mục 5.5) |
| Bộ nhớ phình giữa hai lần mine | Evict trong `loadBatch` (mục 5.1) |
| **∂ người dùng chọn nằm ngoài miền khả thi** | Validator + short-circuit + lệnh `window` in miền khả thi |
| ε quá lớn ⇒ `W=0` | Validator từ chối (E7) |

---

*Chi tiết thuật toán: `00-OVERALL-PLAN.md` §2. Đi tiếp `03-OPTIMIZED-VERSION-PLAN.md`.*