# DHOPM – Kế hoạch Phiên bản 3: Optimized (tối ưu trên nền cửa sổ ε)

> Phiên bản tập trung **tối ưu cấu trúc dữ liệu** (List/Set, mã hoá item thành ID, kết hợp ID+name, primitive array thay cho object) và **tinh chỉnh threading (Level 2)**. NFR đầy đủ: runtime, peak memory, throughput, latency theo batch, so sánh trực tiếp với V2. **Kết quả phải trùng V2** (mốc từ V2/V1) theo canonical spec.

## Document Header

| Mục | Giá trị |
|   |   |
| **Document ID** | DHOPM PLAN 003 |
| **Version** | 1.1 (Draft) |
| **Bí danh** | **V3 — Optimized** |
| **Module** | `implementation/dhopm v3 optimized` |
| **Phụ thuộc** | `00 OVERALL PLAN.md` (canonical C1–C12, khung threading Level 2); `02 EPSILON WINDOW VERSION PLAN.md` (V2 = mốc ngữ nghĩa ε) |
| **Source** | `docs/root/1 s2_0 S095219762600792X main.md`, `docs/root/Nhom01_VDChayTay.md` |
| **Định vị threading** | Level 2 (ForkJoinPool, work stealing) |

### Revision History

| Phiên bản | Mô tả |
|   |   |
| 1.0 | Lập plan V2 Optimized (C1–C6) |
| **1.1** | Đổi thành **V3**, mốc so sánh chuyển từ V1 → **V2**, thêm tối ưu cửa sổ: **bỏ xích handle (`ref1` → chỉ số `int` vào circular buffer)**, **SoA cho window buffer**, **precomputed decay lookup theo tuổi `k = TL − tid`**. `ε` là tham số bắt buộc |



## 1. Mục tiêu

1. **Cải thiện hiệu năng thực sự** so với V2 trên các dataset FIMI (đo được bằng benchmark).
2. Áp dụng **Level 2 threading**: work stealing, construction song song, chia cây con ở độ sâu linh hoạt.
3. Giữ **kết quả trùng V2** (double đầy đủ, mapping theo C5) — kể cả khi `ε > 0`.
4. **Tận dụng cửa sổ ε**: vì `W` hữu hạn ⇒ mọi mảng đều **giới hạn theo W** ⇒ không cần cấp phát theo N ⇒ bộ nhớ phẳng.
5. Cung cấp **báo cáo benchmark đầy đủ** + **bảng ablation V1→V2→V3**.

## 2. Phạm vi

  Toàn bộ thuật toán như V2 nhưng dựng lại cấu trúc dữ liệu nội bộ (thay thế chứ không chỉ "thêm mẹo").
  Giữ OOP nhẹ (class tối giản), **chưa** tới Data Oriented full (dành V4).
  NFR đầy đủ + benchmark + báo cáo.

## 3. NFR (đầy đủ)

| NFR | Yêu cầu |
|   |   |
| **N1 Đúng đắn** | **ε = 0** ⇒ bit for bit ≡ V1/V2; **ε > 0** ⇒ ≡ V2 (TC1–TC18 + E1–E10) |
| N2 Runtime | nhanh hơn V2 trên cùng dataset/tham số; ghi rõ ratio |
| N3 Peak memory | ≤ V2 (mục tiêu giảm rõ; đo JMX/JFR) |
| N4 Throughput | số DOP/s |
| N5 Latency theo batch | ghi số liệu từng phần 1/5 |
| N6 Scalability | `kosarak` 200K→990K + `chainstore`; **memory phẳng theo N** |
| N7 Determinism | tái lập bất kể pool size/worker |
| N8 Cấu hình | ∂, f, **ε**, worker count, bật/tắt construction parallel qua CLI |
| **N9 Không rò handle** | sau tối ưu, **không còn đối tượng Handle** ⇒ INV H tự động thoả (chỉ còn tra cứu `txId` trong bảng trạng thái) |

## 4. Quyết định tối ưu Cấu trúc Dữ liệu (trọng tâm V3)

### 4.0 Định hướng kiến trúc V3 (so với GoF)

  V1 dùng GoF; V2 giữ phần hợp lý. V3: **nếu phát hiện cấu trúc/kiến trúc/thiết kế tốt hơn GoF** cho mục hiệu năng thì **áp dụng**; nếu không có, **thực hiện theo plan**. Quyết định này phải được **ghi rõ trong tài liệu design của V3** (tại sao giữ/thay từng cấu trúc).
  Với **mỗi điểm tối ưu** phải ghi **tại sao chọn** và **so với V2/V1 như thế nào** (rationale + số liệu), tái dùng kết quả microbenchmark.

Bảng so sánh quyết định V2 → V3 (mỗi dòng là một "điểm tối ưu khảo sát"):

| Vấn đề | V2 (Epsilon) | V3 (Optimized — đề xuất khảo sát) | Ghi chú |
|   |   |   |   |
| Item biểu diễn | `record Item(String)` | **`int itemId`** + từ điển `String→int` (Intern/dictionary), giữ một lần name, không so chuỗi trong vòng nóng | Vòng lặp mining so/hash int thay vì String |
| Tra cứu node | `LinkedHashMap<Item, Node>` | `int itemId` → chỉ số node (`Node[]` growable) | Giảm object key boxing |
| **Handle / evict** | `Entry.ref1 → Handle` (đối tượng, 2 con trỏ) | **`Entry.txSlot: int`** (chỉ số vào `WindowBuffer`) + `int[] aliveFlags`; **bỏ hoàn toàn lớp Handle** | Xem mục 4.3 |
| **WindowBuffer** | `Handle[]` (object/slot) | **SoA: `int[] txId; short[] txLen; long[] alive; double[] decay`** — index = `tid mod W` | Vì `W` hữu hạn ⇒ kích thước cố định, cache friendly |
| Entry của node | `Entry` record | **SoA: `int[] txSlot; int[] ref2`** trên mỗi node | Giảm overhead object/ref; locality |
| Support | `size − head` | `txSlot.length − head` (nguyên thuộc, O(1)) | Tránh đếm lại |
| DO | `double doValue` | `double[] doValues` tập trung (nếu toàn list quản lý) | — |
| Sort theo support | sort object `Comparator` | **sort int[]** theo giá trị support (primitive sort; stable theo thứ tự tạo) | Giảm boxing |
| DUBO nhóm length | `TreeMap<Integer,…>` mỗi lần tính | **mảng bucket length** | Tránh khởi tạo cấu trúc mỗi node |
| Giao 2 node (two pointer) | duyệt `ArrayList<Occurrence>` | duyệt trực tiếp `int[] txSlot` rồi tra `WindowBuffer` | — |
| Conditional list | tạo object mới | tái sử dụng buffer/arena nơi an toàn; node vẫn là "bản ghi gọn" | Chú ý INV D |
| `Math.pow` | gọi lại mỗi lần | **bảng tra nhanh decay:** `decayLookup[k] = f^k` cho `k=0..W 1` (giới hạn theo **W**, không theo TL!) | Chỉ cần tới `W 1` vì tuổi lớn nhất trong cửa sổ là `W 1` |
| Kết quả | `record ResultPattern(items, do, occurrences)` | nén: `int[] items` + `double do` | Nếu không cần giữ transactions → bỏ |

> **Nguyên tắc bắt buộc:** mọi thay đổi cấu trúc **không được đổi thứ tự tính toán** của một node (C5) và **không được đổi tập kết quả** (INV E) — kể cả khi `ε > 0`. Trạng thái quyết định cuối (chọn phương án nào trong mỗi dòng) chốt sau khi **đo microbenchmark** giữa hai phương án con — đưa vào report.

### 4.1 Khảo sát riêng "List vs Set"
  Ở global list: node dùng tra cứu nhanh → **mảng theo itemId** (≈ "set/associative" bằng index) tốt hơn List tuyến tính khi số item lớn (Retail: 16k item).
  Conditional list: số lượng node nhỏ, **giữ List theo thứ tự kết hợp** (không cần tra cứu theo item) → không cần Set/Map.
  Khuyến nghị: **global = mảng/map index theo itemId; conditional = List**.

### 4.2 Khảo sát riêng "Có cần ID không" & "ID kết hợp name"
  ID thuần (`int`) dùng trong mọi tính toán; name giữ cho **đầu ra/đọc dữ liệu** (2 chiều: `id→name` khi in, `name→id` khi nạp). Không bao giờ lưu name trong node.
  Bảng "ID + name" một chiều tại IO layer; tách khỏi lớp tính toán → giảm padding.

### 4.3 Tối ưu cửa sổ: bỏ lớp Handle (điểm tối ưu **đặc thù V3**)

Ở V2, mỗi entry giữ `ref1 → Handle` (một con trỏ, mỗi Handle lại giữ `tid`, `len`, `tx`). Ở V3:

  Entry chỉ giữ **`txSlot: int`** = `tid mod W`.
  Tra sống/chết + độ dài bằng cách tra `WindowBuffer` (SoA, contiguous) theo `txSlot`.
  **Evict** = `alive[txSlot] = 0` ⇒ **O(1)**, không cần xoá object nào.
  **Không còn tham chiếu nào tới Transaction đã evict** ⇒ rủi ro dereference tham chiếu chết (INV H) **biến mất hoàn toàn**.

**Đánh đổi:** phải tra bảng tra cứu thêm khi đọc entry ⇒ thêm 1 lần load. Vì bảng nhỏ, liên tục, và `W` cố định ⇒ thường **rẻ hơn** pointer chase. Phải đo.

> **Lưu ý:** đây chính là bài học của `docs/Draft Idea.txt` về "ref2 là handle": V2 giữ handle để **an toàn theo ngôn ngữ**, V3 thay bằng **chỉ số + bảng trạng thái** để vừa nhanh vừa an toàn hơn.

### 4.4 Bảng decay theo **tuổi** thay vì theo TID

Vì tuổi của transaction trong cửa sổ là `k = TL − tid ∈ [0, W−1]`, bảng tra chỉ cần **W phần tử**, không cần TL. Với `ε = 0` (W = ∞), fallback về cách tính nhanh theo log/exp phân đoạn và **ghi rõ giới hạn** trong report.

## 5. Khung Threading Level 2 (bắt buộc)

```
Pool: ForkJoinPool (work stealing) hoặc ExecutionService + thuật toán chia việc,
      số worker = availableProcessors (cấu hình qua CLI).

0. Evict/GĐ0: ĐƠN LUỒNG (C10, INV E) — không đổi.

1. Construction song song (OPTIONAL, có switch):
     chia batch thành K đoạn theo số transaction (K ≈ workers).
     mỗi worker xây "partial node" (per item: txSlot[] cho đoạn của mình).
     merge theo itemId: ghép các dải TID theo thứ tự → đảm bảo INV B (entries theo TID tăng dần).
     Nếu đo được không tốt hơn đơn luồng → tắt switch (ghi trong report).

2. Reconstruction:
     mỗi node một nhiệm vụ (như V1/V2), nhưng chạy trên ForkJoinPool;
     node ≥ ngưỡng TID count tự chia đôi DÃY ENTRY? KHÔNG — vi phạm C5. Giữ nguyên: mỗi node một task.
     tối ưu: pre tính decayLookup trước khi dispatch (mỗi thread đọc chung).

3. Mining:
     mỗi cây con gốc một nhiệm vụ (như V1/V2) NHƯNG:
       nếu cây con (ước lượng qua số node conditional) vượt ngưỡng → chia tiếp theo độ sâu
       (task con cho cây con của node j), dùng ForkJoinTask / invokeAll ở mức ranh giới,
       đệ quy nội bộ vẫn tuần tự giữa hai điểm chia → cân bằng tải tốt hơn.
       sink kết quả: gộp kết quả từng task (join) — deterministic; hoặc nếu dùng concurrent set
       phải đảm bảo thứ tự so sánh cuối theo canonical (C6) để INV E không bị ảnh hưởng.

4. Chống bùng nổ:
     ngưỡng kích thước task tối thiểu (min subtree size) để không tạo quá nhiều task nhỏ.
```

**Lưu ý deterministic:** C5 chỉ cấm chia tách phép cộng của MỘT node. Chia cây con (nhiều node khác nhau) là an toàn. Kết quả gộp phải theo thứ tự so sánh chuẩn (C6) → tập DOP bất biến. **Phát hiện entry chết (dời `head`) vẫn là việc riêng của từng node** ⇒ không phụ thuộc thứ tự.

## 6. Công việc & Milestone (V3)

**M1 – Chuẩn bị & đo lường baseline**
  [ ] Chạy **V2** benchmark trên các dataset cục bộ `dataset/` (mushroom, retail trước; thêm chess/connect/kosarak/pumsb/pumsb_star), chia 5 phần, **ε cố định**, ∂ **trong miền khả thi** (bảng 6.4 plan tổng thể) → **baseline V2**.
  [ ] Dựng sẵn `dhopm bench` (đo 3 giai đoạn, peak memory, throughput, latency batch, median ≥3 chạy).

**M2 – Cải tiến cấu trúc dữ liệu (tuần tự trước, song song sau)**
  [ ] Dictionary `String→int` + `int itemId`; thay toàn bộ so sánh String bằng int.
  [ ] Node → SoA (`int[] txSlot`, `int[] ref2`); support = length − head; bỏ `Entry` record trong vòng nóng.
  [ ] **Bỏ lớp Handle**: `txSlot` + `int[] aliveFlags` trong `WindowBuffer` (mục 4.3).
  [ ] **WindowBuffer SoA** (`int[] txId; short[] txLen; double[] decay`).
  [ ] Global list → index theo itemId (mảng/map); conditional list → List theo thứ tự kết hợp.
  [ ] **Decay lookup theo tuổi `k = TL − tid`** (mục 4.4); fallback cho W = ∞.
  [ ] Sort support bằng primitive (tránh boxing) nhưng **giữ thứ tự ổn định = thứ tự tạo node** (C3).
  [ ] DUBO dùng mảng bucket length thay TreeMap.
  [ ] Microbenchmark từng đổi thay → ghi report; chỉ giữ phương án tốt hơn.
  [ ] Ghi **tài liệu design từng thành phần/hàm** (trong module): mỗi tối ưu nêu **tại sao chọn + so với V2/V1 như thế nào**.

**M3 – Threading Level 2**
  [ ] Chuyển sang ForkJoinPool; Reconstruction theo node trên pool.
  [ ] Mining chia cây con theo ngưỡng độ sâu + min task; join kết quả deterministic.
  [ ] (Optional) Construction song song + merge theo TID; có switch bật/tắt.
  [ ] Test determinism với pool size {1,2,4,cpu}.

**M4 – Nghiệm thu đúng đắn (bắt buộc)**
  [ ] **ε = 0**: TC1–TC8 bit for bit ≡ V1/V2.
  [ ] **ε > 0**: TC9–TC18 + E1–E10, so **double đầy đủ** với V2.
  [ ] Đối chiếu từng trường hợp: DO node, DUBO, `Z(X)`, `UB'(X)`, danh sách DOP giống V2.

**M5 – Benchmark đầy đủ & báo cáo**
  [ ] Chạy **10 dataset cục bộ** trong `dataset/` (accidents/chainstore/chess/connect/kosarak/mushroom/newMushroom/pumsb/pumsb_star/retail), f=0.9, **ε cố định**, ∂ trong miền khả thi, chia 5 phần incremental.
  [ ] Scalability: cắt `kosarak.dat` theo số dòng (200K→990K) **và** `chainstore.dat` (lớn nhất, 1.11M); ghi runtime & memory theo kích thước → chứng minh **memory phẳng theo N**.
  [ ] So sánh V1 ↔ V2 ↔ V3: runtime (3 giai đoạn), peak memory, throughput, latency batch, scalability; ratio.
  [ ] Báo cáo benchmark (`dhopm bench/reports/`) — kèm môi trường phần cứng, cách đo, số lần chạy (≥3, median).

**M6 – Hoàn tất & bộ tài liệu project V3**
  [ ] **Bộ tài liệu riêng của project `dhopm v3 optimized`** (đặt trong module): README (chạy/cấu hình tối ưu), **design cấu trúc & từng thành phần/hàm** kèm lý do chọn từng tối ưu, test plan & kết quả, benchmark report đầy đủ.

## 7. Tiêu chí "Done" (V3)

  [ ] **ε = 0** ⇒ ≡ V1/V2; **ε > 0** ⇒ ≡ V2 (double đầy đủ).
  [ ] Determinism qua nhiều pool size.
  [ ] **Không còn đối tượng Handle**; evict vẫn O(1).
  [ ] Benchmark đầy đủ hoàn tất; V3 nhanh hơn V2 ở ≥ 3/4 dataset (hoặc có phân tích rõ vì sao không).
  [ ] Report rõ từng quyết định tối ưu kèm số liệu trước/sau.
  [ ] Bộ tài liệu project V3 đầy đủ (M6).

## 8. Rủi ro & Giảm thiểu

| Rủi ro | Giảm thiểu |
|   |   |
| Primitive array + boxing gây lỗi tính đúng | Giữ interface kiểm tra: chạy TC + so V2 sau MỖI thay đổi cấu trúc |
| Construction song song vi phạm INV B | Merge theo dải TID; test tính toán lại DO node sau merge |
| TL lớn → bảng decay quá nhiều phần tử | Chỉ cần W phần tử (mục 4.4); khi W = ∞ dùng tính phân đoạn; ghi rõ giới hạn |
| Bỏ Handle làm tăng chi phí tra cứu | Microbenchmark so 2 phương án (con trỏ vs chỉ số); chỉ giữ phương án nhanh hơn |
| Chia cây quá nhỏ → overhead task | Ngưỡng min subtree (đo bằng thực nghiệm) |
| Determinism khi sink kết quả | Gộp sau join + so sánh canonical (C6) |



*Kết thúc plan V3. Đi tiếp `04 EXTREME VERSION PLAN.md`.*
