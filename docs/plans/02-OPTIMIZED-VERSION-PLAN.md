# DHOPM – Kế hoạch Phiên bản 2: Optimized

> Phiên bản tập trung **tối ưu cấu trúc dữ liệu** (List/Set, mã hoá item thành ID, kết hợp ID+name, primitive array thay cho object) và **tinh chỉnh threading (Level 2)**. NFR đầy đủ: runtime, peak memory, throughput, latency theo batch, so sánh trực tiếp với V1. **Kết quả phải trùng V1** (mốc golden từ V1) theo canonical spec.

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-PLAN-002 |
| **Version** | 1.0 (Draft) |
| **Phụ thuộc** | `00-OVERALL-PLAN.md` (canonical C1–C6, khung threading Level 2) ; `01-STANDARD-VERSION-PLAN.md` (V1 = golden) |
| **Source** | `docs/root/1-s2_0-S095219762600792X-main.md`, `docs/root/Nhom01_VDChayTay.md` |

---

## 1. Mục tiêu

1. **Cải thiện hiệu năng thực sự** so với V1 trên các dataset FIMI (đo được bằng benchmark).
2. Áp dụng **Level 2 threading**: work-stealing, construction song song, chia cây con ở độ sâu linh hoạt.
3. Giữ **kết quả trùng V1** (double đầy đủ, mapping theo C5).
4. Cung cấp **báo cáo benchmark đầy đủ** (runtime 3 giai đoạn, peak memory, throughput, latency batch, scalability).

## 2. Phạm vi

- Toàn bộ thuật toán như V1 nhưng dựng lại cấu trúc dữ liệu nội bộ (thay thế chứ không chỉ "thêm mẹo").
- Giữ OOP-nhẹ (class tối giản), **chưa** đi tới Data-Oriented full (dành V3).
- NFR đầy đủ + benchmark + báo cáo.

## 3. NFR (đầy đủ)

| NFR | Yêu cầu |
|---|---|
| N1 | Đúng đắn = TC1–TC8 pass + **trùng V1** trên 8 TC |
| N2 | Runtime: nhanh hơn V1 trên cùng dataset/tham số (giao dịch 1-х ≥ tốt hơn); ghi rõ ratio |
| N3 | Peak memory: ≤ V1 (mục tiêu giảm rõ; đo JMX/JFR) |
| N4 | Throughput: số DOP/s; so sánh V1 vs V2 |
| N5 | Latency theo batch: ghi số liệu từng phần 1/5 |
| N6 | Scalability: `kosarak.dat` cắt 200K→990K, theo số transaction |
| N7 | Determinism: tái lập kết quả bất kể pool size/worker |
| N8 | Cấu hình: ∂, f, worker count, ε, bật/tắt construction-parallel qua CL |

---

## 4. Quyết định tối ưu Cấu trúc Dữ liệu (trọng tâm V2)

### 4.0 Định hướng kiến trúc V2 (so với GoF)

- V1 dùng GoF (plan 01). V2: **nếu phát hiện cấu trúc/kiến trúc/thiết kế tốt hơn GoF** cho mục tiêu hiệu năng thì **áp dụng**; nếu không có, **thực hiện theo plan** (giữ phần hợp lý của GoF, thay phần không). Quyết định này phải được **ghi rõ trong tài liệu design của V2** (tại sao giữ/thay từng cấu trúc).
- Yêu cầu đặc thù V2 trong tài liệu design (viết song song khi code tại giai đoạn G2): với **mỗi điểm tối ưu** phải ghi **tại sao chọn** và **nó như thế nào so với V1** (số liệu/rationale), tái dùng kết quả microbenchmark.

Bảng so sánh quyết định Standard → Optimized (mỗi dòng là một "điểm tối ưu khảo sát"):

| Vấn đề | V1 (Standard) | V2 (Optimized — đề xuất khảo sát) | Ghi chú |
|---|---|---|---|
| Item biểu diễn | `record Item(String)` | **`int itemId`** + từ điển `String→int` (Intern/dictionary), giữ một lần name, không so chuỗi trong vòng nóng | Vòng lặp mining so/hash int thay vì String |
| Tra cứu node | `LinkedHashMap<Item, Node>` | `int itemId` → chỉ số node (mảng `Node[]` growable / `HashMap<Integer,Integer>` tiết kiệm) | Giảm object key boxing |
| Entry của node | `ArrayList<Occurrence>` (record) | **SoA: `int[] tid; int[] len`** trên mỗi node (hai mảng parallel, đóng gói trong node) — hoặc nếu hợp, gộp `tid` cùng `len` 1 mảng `long[]` (pack 32-bit) | Giảm overhead object/ref; cache locality |
| Support | `occurrences.size()` | `count_nodes[i]` (nguyên) | Tránh đếm lại |
| DO | `double doValue` | `double[] doValues` tập trung (nếu toàn list quản lý) | — |
| Sort theo support | sort object `Comparator` | **sort int[]** theo giá trị support (primitive sort; stable theo thứ tự tạo bằng `Object[]`-đối chiếu hoặc bảng id→pos) | Giảm boxing |
| DUBO nhóm length | `TreeMap<Integer,…>` mỗi lần tính | **dùng mảng bucket length** (length nhỏ thì mảng cố định; hoặc 1 lần sắp occurrence theo length + scan) | Tránh khởi tạo cấu trúc mỗi node |
| Giao 2 node (two-pointer) | duyệt `ArrayList<Occurrence>` | duyệt trực tiếp `int[] tid` | — |
| Conditional list | tạo object mới | tái sử dụng buffer/arena nơi an toàn; node vẫn là "bản ghi gọn" | Chú ý INV-D |
| `Math.pow` | gọi lại mỗi lần | **bảng tra nhanh decay:** `decayLookup[k] = f^k` cho `k=0..TL` (đã biết TL; chống tính lại cùng exponent) | Chỉ khi TL không quá lớn; nếu TL rất lớn dùng tính nhanh bằng log/exp phân đoạn |
| Kết quả | `record ResultPattern(items, do, occurrences)` | nén: `int[] items` + `double do` + `int[] tids` (tuỳ nhu cầu báo cáo) | Nếu không cần giữ transactions → bỏ để tiết kiệm |

> **Nguyên tắc bắt buộc:** mọi thay đổi cấu trúc **không được đổi thứ tự tính toán** của một node (C5) và không được đổi tập kết quả (INV-E). Trạng thái quyết định cuối (chọn phương án nào trong mỗi dòng) được chốt sau khi **đo microbenchmark** giữa hai phương án con — đưa vào report.

### 4.1 Khảo sát riêng "List vs Set"
- Ở global list: node dùng tra cứu nhanh → **mảng theo itemId** (≈ "set/associative" bằng index) tốt hơn List tuyến tính khi số item lớn (Retail: 16k item).
- Conditional list: số lượng node nhỏ, **giữ List theo thứ tự kết hợp** (không cần tra cứu theo item) → không cần Set/Map.
- Khuyến nghị rõ ràng: **global = mảng/map index theo itemId; conditional = List** (kết luận này được kiểm chứng bằng benchmark).

### 4.2 Khảo sát riêng "Có cần ID không" & "ID kết hợp name"
- ID thuần (`int`) dùng trong mọi tính toán; name giữ cho **đầu ra/đọc dữ liệu** (2 chiều: `id→name` khi in, `name→id` khi nạp). Không bao giờ lưu name trong node.
- Bảng "ID + name" một chiều tại IO-layer; tách khỏi lớp tính toán → giảm padding.

---

## 5. Khung Threading Level 2 (bắt buộc)

```
Pool: ForkJoinPool (work-stealing) hoặc ExecutionService + thuật toán chia việc,
      số worker = availableProcessors (cấu hình qua CL).

1. Construction song song (OPTIONAL, có switch):
   - chia batch thành K đoạn theo số transaction (K ≈ workers).
   - mỗi worker xây "partial node" (per-item: tid[], len[] cho đoạn của mình).
   - merge theo itemId: ghép các dải TID theo thứ tự → đảm bảo INV-B (entries theo TID tăng dần).
   - Nếu đo được không tốt hơn đơn luồng → tắt switch (ghi trong report).

2. Reconstruction:
   - mỗi node một nhiệm vụ (như V1), nhưng chạy trên ForkJoinPool; node ≥ ngưỡng TID count
     tự chia đôi DÃY ENTRY? KHÔNG — vi phạm C5. Giữ nguyên: mỗi node một task.
   - tối ưu: pre-tính decayLookup trước khi dispatch (mỗi thread đọc chung).

3. Mining:
   - mỗi cây con gốc một nhiệm vụ (như V1) NHƯNG:
     - nếu cây con (ước lượng qua số node conditional) vượt ngưỡng → chia tiếp theo độ sâu
       (task con cho cây con của node j), dùng ForkJoinTask / invokeAll ở mức ranh giới,
       đệ quy nội bộ vẫn tuần tự giữa hai điểm chia → cân bằng tải tốt hơn.
     - sink kết quả: gộp kết quả từng task (join) — deterministic; hoặc nếu dùng concurrent set
       phải đảm bảo thứ tự so sánh cuối theo canonical (C6) để INV-E không bị ảnh hưởng.

4. Chống bùng nổ:
   - ngưỡng kích thước task tối thiểu (min subtree size) để không tạo quá nhiều task nhỏ.
```

**Lưu ý deterministic:** C5 chỉ cấm chia tách phép cộng của MỘT node. Chia cây con (nhiều node khác nhau) là an toàn. Kết quả gộp phải theo thứ tự so sánh chuẩn (C6) → tập DOP bất biến.

---

## 6. Công việc & Milestone (V2)

**M1 – Chuẩn bị & đo lường baseline**
- [ ] Chạy V1 benchmark trên các dataset cục bộ `dataset/` (mushroom, retail trước; thêm chess/connect/kosarak/pumsb/pumsb_star), chia 5 phần, ∂ theo bảng 5.2 → **baseline**.
- [ ] Dựng sẵn `dhopm-bench` (đo 3 giai đoạn, peak memory, throughput, latency batch, median ≥3 chạy).

**M2 – Cải tiến cấu trúc dữ liệu (tuần tự trước, song song sau)**
- [ ] Dictionary `String→int` + `int itemId`; thay toàn bộ so sánh String bằng int.
- [ ] Node → SoA (`int[] tid`, `int[] len`); support = count; bỏ `record Occurrence` trong vòng nóng.
- [ ] Global list → index theo itemId (mảng/map); conditional list → List theo thứ tự kết hợp.
- [ ] Decay lookup `f^k` (k=0..TL) — đánh giá giới hạn TL.
- [ ] Sort support bằng primitive (tránh boxing) nhưng **giữ thứ tự ổn định = thứ tự tạo node** (C3).
- [ ] DUBO dùng mảng bucket length thay TreeMap.
- [ ] Microbenchmark từng đổi thay → ghi report; chỉ giữ phương án tốt hơn.
- [ ] Ghi **tài liệu design từng thành phần/hàm** (trong module): mỗi tối ưu nêu **tại sao chọn + so với V1 như thế nào** (rationale + số liệu), và (mục 4.0) kết luận giữ/thay cấu trúc GoF.

**M3 – Threading Level 2**
- [ ] Chuyển sang ForkJoinPool; Reconstruction theo node trên pool.
- [ ] Mining chia cây con theo ngưỡng độ sâu + min-task; join kết quả deterministic.
- [ ] (Optional) Construction song song + merge theo TID; có switch bật/tắt.
- [ ] Test determinism với pool size {1,2,4,cpu}.

**M4 – Nghiệm thu đúng đắn (bắt buộc)**
- [ ] TC1–TC8: pass và **trùng V1** (so double đầy đủ, không chỉ 4 chữ số).
- [ ] Đối chiếu từng trường hợp: DO node, DUBO, danh sách DOP giống V1.

**M5 – Benchmark đầy đủ & báo cáo**
- [ ] Chạy **7 dataset cục bộ** trong `dataset/` (chess/connect/kosarak/mushroom/pumsb/pumsb_star/retail), ∂ theo bảng 5.2 plan tổng thể, f=0.9, chia 5 phần incremental.
- [ ] Scalability: cắt `kosarak.dat` theo số dòng (200K→990K), ghi runtime & memory theo kích thước.
- [ ] So sánh V1 vs V2: runtime (3 giai đoạn), peak memory, throughput, latency batch, scalability; ratio.
- [ ] Báo cáo benchmark (`dhopm-bench/reports/`) — kèm môi trường phần cứng, cách đo, số lần chạy (≥3, median).

**M6 – Hoàn tất & bộ tài liệu project V2**
- [ ] **Bộ tài liệu riêng của project `dhopm-v2-optimized`** (đặt trong module): README (chạy/cấu hình tối ưu), **design cấu trúc & từng thành phần/hàm** kèm lý do chọn từng tối ưu và so với V1, test plan & kết quả, benchmark report đầy đủ.

## 7. Tiêu chí "Done" (V2)

- [ ] TC1–TC8 pass và trùng V1 (double đầy đủ).
- [ ] Determinism qua nhiều pool size.
- [ ] Benchmark đầy đủ hoàn tất; V2 nhanh hơn V1 ở ≥ 3/4 dataset (hoặc có phân tích rõ vì sao không).
- [ ] Report rõ từng quyết định tối ưu kèm số liệu trước/sau.
- [ ] Bộ tài liệu project V2 đầy đủ (M6).

## 8. Rủi ro & Giảm thiểu

| Rủi ro | Giảm thiểu |
|---|---|
| Primitive array + boxing gây lỗi tính đúng | Giữ interface kiểm tra: chạy TC + so V1 sau MỖI thay đổi cấu trúc |
| Construction song song vi phạm INV-B | Merge theo dải TID; test tính toán lại DO node sau merge |
| TL lớn → bảng decay quá nhiều phần tử | Đánh giá giới hạn; dùng cách tính phân đoạn hoặc log/exp nhanh; ghi rõ trong report |
| Chia cây quá nhỏ → overhead task | Ngưỡng min subtree (đo bằng thực nghiệm) |
| Determinism khi sink kết quả | Gộp sau join + so sánh canonical (C6) |

---

*Kết thúc plan V2. Đi tiếp `03-EXTREME-VERSION-PLAN.md` (ý tưởng).*