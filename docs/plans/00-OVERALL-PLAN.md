# DHOPM/DOPM – Tổng thể Kế hoạch Triển khai (3 Phiên bản)

> Kế hoạch tổng thể cho phép phát triển **3 phiên bản** của hệ thống khai phá mẫu chiếm dụng cao có suy giảm (DHOPM/DOPM) bằng **Java 25**, với yêu cầu bắt buộc **Thread + worker** để tối ưu CPU, và **kết quả của 3 phiên bản phải trùng khớp nhau**.
>
> Đây là tài liệu gốc (source of truth) cho toàn bộ nỗ lực: định nghĩa **chuẩn hóa thuật toán**, khung threading dùng chung, cấu trúc dự án, bộ test nghiệm thu, và lộ trình triển khai.

---

## Document Header

| Mục | Giá trị |
|---|---|
| **Document Name** | Kế hoạch tổng thể DHOPM – 3 phiên bản |
| **Document ID** | DHOPM-PLAN-000 |
| **Version** | 1.0 (Draft) |
| **Status** | Draft – chờ review |
| **Source Documents** | [1] Paper gốc: `docs/root/1-s2_0-S095219762600792X-main.md`<br/>[2] Ví dụ chạy tay + TC1–TC8: `docs/root/Nhom01_VDChayTay.md`<br/>[3] Tài liệu draft cũ (3 design + `Default Project/` + `draft/`) đã **lưu trữ trên branch `archive/legacy-draft`** — tham khảo, KHÔNG phải mốc |
| **Ngôn ngữ triển khai** | Java 25 (LTS) |
| **Môi trường** | Windows (win32), kiến trúc hiện tại của máy dev |

### Revision History

| Phiên bản | Mô tả |
|---|---|
| 1.0 | Khởi tạo: chuẩn hóa thuật toán, khung threading, cấu trúc dự án, lộ trình 3 phiên bản |
| 1.1 | Cập nhật dataset cục bộ (7 file FIMI, khớp benchmark); bổ sung cấu trúc Tài liệu & Workflow (SRS tổng thể, UI Layout, tài liệu riêng từng project engine, plan riêng từng giai đoạn), thêm giai đoạn G4 debug/compare app |
| 1.2 | **Java 25** thay Java 17; chuyển toàn bộ tài liệu draft cũ (3 design, `Default Project/`, `draft/`) sang **branch `archive/legacy-draft`** và xóa khỏi main (ghi chú tại đây); V1 áp dụng **GoF**, V2 cân nhắc **cấu trúc vượt GoF** (không có thì theo plan), V3 không cần pattern; mỗi giai đoạn sản xuất **tài liệu cấu trúc & design từng thành phần/hàm** (V2/V3 kèm lý do chọn tối ưu + so V1); app = **3 module engine + UI module riêng**, chọn **≥1 engine** để so sánh, util chung (benchmark/thread/worker) chia sẻ cho cả 3, log/benchmark **độc lập với thuật toán** |

---

## 1. Mục đích & Phạm vi

### 1.1 Mục đích

Phát triển **một hệ thống gồm 3 project engine** (3 phiên bản DHOPM) cùng **harness benchmark** và (ở giai đoạn cuối) một **debug/compare app** kết nối 3 thuật toán. Tăng dần mức tối ưu:

| # | Bí danh | Định vị | Mục tiêu cốt lõi |
|---|---|---|---|
| V1 | **Standard** | Code đúng, dễ hiểu, dễ bảo trì | Đi từ đặc tả tới kết quả đúng mốc TC1–TC8; NFR tối thiểu; threading cơ bản |
| V2 | **Optimized** | Tối ưu cấu trúc dữ liệu & tinh chỉnh threading | Đạt benchmark tốt nhất có thể trong khuôn khổ OOP-nhẹ; NFR đầy đủ; đo lường + so sánh |
| V3 | **Extreme** | Tối ưu cực đoan, giảm/loại bỏ OOP | **Chỉ ở mức ý tưởng** tại thời điểm này; chi tiết hoá sau khi V1, V2 xong |

### 1.2 Phạm vi

- Tài liệu này bao trùm: chuẩn hóa thuật toán, mô hình threading dùng chung, cấu trúc dự án (3 project engine + harness + app), dữ liệu đầu vào, bộ test, benchmark, lộ trình, rủi ro, **cấu trúc tài liệu & workflow**.
- Không bao gồm: chi tiết thiết kế class của từng phiên bản (ở 3 plan con 01/02/03), chi tiết SRS (ở `docs/srs/DHOPM-SRS.md`), chi tiết bố cục UI (ở `docs/srs/DHOPM-UI-LAYOUT.md`), chi tiết debug app (lập plan tại G4).

### 1.3 Mối quan hệ với các tài liệu cũ

- **2 tài liệu trong `docs/root/` là mốc đúng đắn duy nhất** cho thuật toán (công thức, thứ tự xử lý, kết quả kỳ vọng TC1–TC8).
- **Toàn bộ tài liệu draft cũ đã được chuyển sang branch `archive/legacy-draft`** và **xóa khỏi main** (theo quyết định của chủ dự án): 3 tài liệu design cũ (`DHOPM-Domain-Specification.md`, `DHOPM-Infrastructure-OOP-Design.md`, `DHOPM-Java-Infrastructure-Design.md`), prototype `Default Project/`, và `draft/`. Chúng chỉ dùng làm tham khảo trên branch đó, **KHÔNG là mốc**. Việc lập plan và triển khai bắt đầu từ mốc [1]/[2], làm lại từ đầu.

---

## 2. Chuẩn hóa Thuật toán (Canonical Spec – bắt buộc cho cả 3 phiên bản)

> Mục đích của mục này: định nghĩa **một** cách chạy duy nhất để 3 phiên bản cho **kết quả trùng khớp**. Mọi triển khai phải tuân theo; nếu có khác biệt giữa các nguồn, quy định tại đây là **ưu tiên cao nhất**.

### 2.1 Dữ liệu đầu vào & tham số

| Khoản | Định nghĩa |
|---|---|
| Transaction | Tập **item phân biệt**, có `TID` nguyên tăng dần. `|T|` = số item phân biệt. |
| Batch (DB) | Một nhóm transaction đến cùng đợt; `TID` các batch nối tiếp nhau. |
| Item | Định danh duy nhất trong phạm vi dataset (chuỗi; nội bộ có thể mã hoá số nguyên – V2/V3). |
| `∂` | Ngưỡng phần trăm do người dùng đặt, `0 ≤ ∂ ≤ 1`. |
| `f` | Hệ số suy giảm, `0 < f < 1` (riêng `f = 1.0` dùng cho TC5 = HOP truyền thống). |
| `minSup` | `= ∂ × (tổng số transaction đã quét)`, tính tại thời điểm khai phá. |
| `TL` | `TID` của transaction mới nhất đã quét. |

**Quy tắc biên (canonical):**
- Transaction rỗng / giao dịch độ dài 0 → **bị loại từ khi nhập** (chống chia 0) và không tính vào tổng số transaction.
- Item trùng trong 1 transaction → chuẩn hoá về tập phân biệt.
- `TID` không tăng dần → **báo lỗi** (dừng, fail-fast).
- `∂` ngoài `[0,1]`, `f` ngoài `(0,1)` → **báo lỗi khi cấu hình**.

### 2.2 Pipeline chuẩn (3 giai đoạn)

```
GĐ1 – Xây/Cập nhật Global DHO-List:      (one-scan, chỉ quét phần mới)
   với mỗi transaction T theo TID tăng dần:
     với mỗi item i ∈ T (theo thứ tự xuất hiện trong T):
       node = createOrGetNode(i)            // thứ tự tạo = lần xuất hiện đầu tiên
       node.addEntry( (T.tid, |T|) )        // append, TID tăng dần
   cập nhật tổng transaction, TL; minSup = ∂ × tổng

GĐ2 – Reconstruction (khi có yêu cầu khai phá):
   với mỗi node:
     DO(node) = Σ mỗi entry (1/|T|) × f^(TL − tid)     // tính TUẦN TỰ theo entry, bắt đầu từ 0
   sắp xếp node theo support tăng dần, ổn định (tie-break = thứ tự tạo node)

GĐ3 – Mine (DFS pattern-growth):
   Mine(currentList, prefix):
     với i = 0..n-1 (node_i theo thứ tự hiện tại):
       nếu support(node_i) < minSup → SKIP node (bỏ qua hoàn toàn)
       nếu DO(node_i) ≥ minSup     → thêm (prefix ∪ {item_i}) vào kết quả, kèm DO
       dubo = DUBO(node_i)
       nếu dubo < minSup → SKIP mở rộng (prune)
       ngược lại:
         ncl = ConditionalList mới
         với j = i+1..n-1:
           node = giao(node_i, node_j)          // two-pointer, theo TID chung
           nếu giao rỗng → bỏ qua
           ngược lại → thêm node vào ncl, DO(node) = Σ (|prefix|+2)/|T| × f^(TL−tid)
         nếu ncl không rỗng → kết quả ∪= Mine(ncl, prefix ∪ {item_i})
```

### 2.3 Công thức chuẩn

| Ký hiệu | Công thức |
|---|---|
| Occupancy | `O(X, Td) = |X| / |Td|` |
| Decay factor | `dF(Td) = f^(TL − Td)` |
| Damped occupancy | `DO(X) = Σ O(X, Td) × f^(TL − Td)` trên các Td chứa X |
| DOP | `X ∈ DOPs ⇔ DO(X) ≥ minSup` |
| DUBO | Nhóm các entry theo độ dài `|T|` (tăng dần): nhóm `k` có `(n_k, T_k)` với `n_k` = số entry, `T_k` = TID lớn nhất trong nhóm. `DUBO(X,k) = f^(TL − T_k) × Σ_{i≥k} n_i × (l_k / l_i)`; `DUBO(X) = max_k DUBO(X,k)` |

**Quyết định hóa chuẩn (khóa các điểm mơ hồ — để 3 phiên bản trùng kết quả):**

| # | Điểm mơ hồ | Quyết định chuẩn | Căn cứ |
|---|---|---|---|
| C1 | Hệ số suy giảm trong DUBO | **Dùng một decay factor duy nhất** `f^(TL − T_k)` của nhóm k cho **toàn bộ tổng** (Định nghĩa 6 + Sub-procedure 3 + ví dụ chạy tay). Hệ quả: `DUBO(CD) = 1.6402`, `DUBO(CDE) = 1.181` ở TL=8, f=0.9. | Paper [1] mục 3.6 + chạy tay [2] |
| C2 | `support < minSup` trong Mine | **Bỏ qua hoàn toàn node** (không đánh giá DOP, không mở rộng). | Pseudocode Mine line 03 [1] + chạy tay [2] (G bị bỏ) |
| C3 | Tie-break khi support bằng nhau | **Sắp xếp ổn định** theo support tăng dần; giữ thứ tự **tạo node** (lần xuất hiện đầu tiên). | Chạy tay [2] phần 6.2, 8.2 |
| C4 | Độ chính xác số thực | Tính bằng `double`; so sánh ngưỡng dùng `≥ minSup − ε`, `ε = 1e-9`. Chỉ làm tròn 4 chữ số khi **hiển thị/test**. | TC bảng [2] |
| C5 | Thứ tự cộng dồn DO/DUBO | Một node luôn được tính **tuần tự trên một thread**: DO theo entry TID tăng dần; DUBO theo nhóm length tăng dần. **Không bao giờ chia tách phép cộng của một node cho nhiều thread** → đảm bảo bit-for-bit trùng nhau giữa các phiên bản. | Thiết kế chuẩn hoá |
| C6 | Bản sắc pattern | Pattern = **tập item** (bất kể ký tự sắp xếp). Khi hiển thị/so sánh, chuẩn hoá theo thứ tự item xác định (theo thứ tự tạo node hoặc theo mã id). | Chạy tay [2] (AEG/GAE) |

### 2.4 Bất biến toàn cục

- **INV-A**: TID tăng dần nghiêm ngặt trên toàn stream.
- **INV-B**: Entry của một node luôn được thêm theo TID tăng dần (append-only).
- **INV-C**: DO chỉ có nghĩa tại đúng `(TL, f)` đang dùng; thay đổi data → phải reconstruct lại từ 0.
- **INV-D**: Conditional list **không được sắp xếp lại** sau khi dựng (giữ thứ tự kết hợp từ list cha).
- **INV-E**: Kết quả 3 phiên bản phải cho cùng tập DOP (đối chiếu theo C4–C6).

---

## 3. Khung Threading dùng chung (tăng độ tinh vi theo phiên bản)

Cả 3 phiên bản **đều phải** dùng Thread + worker. Khung chung định nghĩa 3 "mức hoàn thiện" và ánh xạ vào phiên bản.

| Mức | Phiên bản | Đặc điểm khung |
|---|---|---|
| **Level 1** | V1 Standard | `ExecutorService` (fixed pool, `Runtime.availableProcessors()`). Song song: Reconstruction theo từng node + Mining theo từng **cây con gốc** (mỗi prefix root một task). Construction **đơn luồng** (đơn giản, tránh race trên map toàn cục). Các task độc lập; thu kết quả về tập `ConcurrentLinkedHashSet` hoặc merge khi join. |
| **Level 2** | V2 Optimized | Work-stealing (`ForkJoinPool`) + phân tải động. Có: construction song song theo batch (chia batch, ráp lại theo TID, giữ INV-B), chia nhỏ cây con **sâu hơn** theo ngưỡng độ sâu, tái sử dụng pool giữa các giai đoạn, chống bùng nổ task, tinh chỉnh kích thước công việc tối thiểu. |
| **Level 3** | V3 Extreme | (Ý tưởng) Pipeline data-oriented, worker chuyên trách theo giai đoạn, sink kết quả lock-free, loại bỏ object bao ngoài. Chi tiết hóa sau. |

**Nguyên tắc chung của khung:**
- Luôn tuân INV-E / C5: chỉ chia các **đơn vị công việc độc lập**; nếu cần ranh giới, ranh giới là *node* (Reconstruction), *cây con* (Mining), *nhóm batch* (Construction).
- Số worker mặc định = số lõi; cấu hình được qua tham số.
- Khóa giao diện "công việc" (task) tối thiểu để V1–V3 cài được khung giống nhau nhưng triển khai khác nhau.
- **Thread/worker pool là util dùng chung** (đặt trong `dhopm-common`), cả 3 module engine tái dùng — không tự viết riêng từng version.
- **Logging & benchmark độc lập với thuật toán:** việc đo/ghi thời gian, ghi log nằm **ngoài hot path** (qua wrapper/decorator ở tầng contract, dùng util chung); khi tắt log thì không thêm chi phí — tránh "vì ghi log nên thuật toán chạy chậm hơn".

---

## 4. Cấu trúc Dự án đề xuất

> **Đã chốt:** dự án Maven **đa module** tại `D:\JVNC\JVNC\implementation\` (trong repo git hiện có, cạnh `docs/`, `dataset/`) — xem mục 10 (D1, D2).

```
implementation/
├── pom.xml                     # parent (Java 25)
├── dhopm-common/               # DÙNG CHUNG, KHÔNG chứa thuật toán của version
│   ├── src/main/java/…          #   io: TransactionSource, reader FIMI, reader text
│   │                            #   config: MiningConfig (∂, f, workers, ε)
│   │                            #   contract: giao diện Engine (loadBatch, mineNow, result)
│   │                            #   util DÙNG CHUNG: thread/worker pool, benchmark utils,
│   │                            #     logging (async, KHÔNG nằm trong hot path của thuật toán)
│   └── src/test/java/…          #   TestKit: golden TC1–TC8, IncrementalDriver, DeterminismAssert
├── dhopm-v1-standard/          # Module Engine 1 (Level 1 threading + GoF) – tài liệu riêng
├── dhopm-v2-optimized/         # Module Engine 2 (Level 2 threading + DS tối ưu) – tài liệu riêng
├── dhopm-v3-extreme/           # Module Engine 3 (tạo khi bắt đầu giai đoạn 3; hiện chỉ ghi ý tưởng trong plan 03)
├── dhopm-bench/                # Harness đo runtime/memory/throughput (dùng util chung của dhopm-common)
└── dhopm-app/                  # Module UI riêng (G4) – debug/compare app, dùng 3 engine qua module; có loading screen
```

**Nguyên tắc:**
- `dhopm-common` chia sẻ **đầu vào, util chung và hạ tầng đo lường** — **không** chia sẻ logic thuật toán → ngăn "rò rỉ" tối ưu giữa các phiên bản, giữ phép so sánh công bằng.
- **Util chung (thread/worker, benchmark, logging) do cả 3 module engine tái dùng** (không viết lại riêng từng version).
- **Logging & benchmark độc lập với thuật toán:** instrument qua lớp bọc (wrapper/decorator) ở tầng contract, không chèn vào hot path → việc ghi log/đo không làm thuật toán chạy chậm hơn; khi tắt log thì gần như zero-overhead.
- 3 algorithm là **3 module** (về bản chất như 3 project nhỏ, có tài liệu riêng); `dhopm-app` (UI) là module tách biệt để tránh làm chậm/freeze giao diện khi mining.

### 4.1 Cấu trúc Tài liệu & Workflow

> **Nguyên tắc chốt:** mỗi version thuật toán là **một project riêng** (module riêng) nên có **bộ tài liệu riêng**. Hệ thống tổng có **SRS tổng thể** và **thiết kế UI**. Giai đoạn hiện tại **chỉ lập plan**; **mỗi giai đoạn** khi bắt đầu sẽ có **plan cụ thể + tài liệu cụ thể của giai đoạn đó**.

Bản đồ tài liệu (layered):

| Tầng | Tài liệu | Vị trí | Trạng thái |
|---|---|---|---|
| Chiến lược (plan) | Kế hoạch tổng thể (canonical + road + workflow) | `docs/plans/00-OVERALL-PLAN.md` | ✅ Đã viết |
| Plan từng phiên bản | V1 / V2 / V3 | `docs/plans/01|02|03-*.md` | ✅ Đã viết |
| Yêu cầu tổng | **SRS tổng thể** (3 engine + harness + app) | `docs/srs/DHOPM-SRS.md` | ⬜ Viết ở giai đoạn planning |
| Thiết kế trình bày | **UI Layout** (so sánh gì, đồ thị gì, giá trị nào) | `docs/srs/DHOPM-UI-LAYOUT.md` | ⬜ Viết ở giai đoạn planning |
| Tài liệu mốc | Paper + ví dụ chạy tay | `docs/root/` | ✅ (mốc đúng đắn) |
| Draft cũ (đã lưu trữ) | 3 design + `Default Project/` + `draft/` — chỉ tham khảo | Branch `archive/legacy-draft` | ✅ (đã xóa khỏi main) |
| Tài liệu riêng từng module engine | README + **design cấu trúc & từng thành phần/hàm** + test plan & kết quả + benchmark report | Trong module `implementation/dhopm-vX/` | 🕔 Tạo khi chạy giai đoạn G1/G2/G3 tương ứng (**nội dung nghĩ ra trong lúc làm**; V2/V3 kèm **lý do chọn tối ưu + so với V1**) |
| Plan + tài liệu từng giai đoạn | Plan chi tiết của G0/G1/G2/G3/G4 + tài liệu ra của giai đoạn đó (trong đó có design từng component/function) | `docs/phases/` (tạo dần) | 🕔 Tạo khi bắt đầu mỗi giai đoạn |

**Quy tắc workflow:**
1. Mỗi giai đoạn triển khai (G0→G4) bắt đầu bằng việc tạo **plan riêng** (`docs/phases/P<i>-<tên>.md`) rồi mới code.
2. Mỗi giai đoạn sản xuất **tài liệu cấu trúc & design từng thành phần/hàm** (bên trong module của giai đoạn) — nội dung chi tiết được **nghĩ ra trong lúc làm tại giai đoạn đó**, không cố định trước. V2 và V3 còn phải giải thích **tại sao chọn tối ưu đó** và **nó như thế nào so với V1**.
3. **Thiết kế theo giai đoạn:** V1 áp dụng **design pattern GoF** (thấy ở đâu hợp lý; không ép buộc từng pattern); V2 — nếu có **cấu trúc/kiến trúc/thiết kế tốt hơn GoF** thì áp dụng, nếu không thì thực hiện theo plan; V3 **không cần** (đã cực đoan sẵn).
4. Mỗi module engine sản xuất **bộ tài liệu riêng** trong module của nó (README, design, test, report) — kết thúc giai đoạn chỉ được xem là xong khi đủ tài liệu theo tiêu chí hoàn thành.
5. **Debug app (G4)** sửa đổi/thao tác chi tiết thuật toán → là **yêu cầu hệ thống** (đã ghi trong SRS mục 9) nhưng **plan/thiết kế chi tiết chỉ lập khi đến G4**. App chọn **≥1 engine** để chạy/so sánh (min 1 = chạy đơn); UI tách module riêng, có **loading/mining screen** để tránh freeze — chi tiết bàn khi làm app/UI.

---

## 5. Dữ liệu Đầu vào & Định dạng

### 5.1 Định dạng chuẩn hoá

| Kiểu | Định dạng | Mục đích |
|---|---|---|
| **Text (TID tường minh)** | Mỗi dòng: `<TID> <item1> <item2> …` | TC1–TC8 (có thể viết inline trong test) |
| **FIMI** | Mỗi dòng: `<item1> <item2> …` (số nguyên, cách khoảng trắng); TID suy ra = số thứ tự dòng (1-based) | Benchmark với dataset chuẩn |

- Dòng trống hoặc bắt đầu `#` → bỏ qua.
- Batch (DB) trong mô phỏng stream = một **khối transaction liên tục**; trình điều khiển đưa từng khối vào `loadBatch`.

### 5.2 Dataset

- **Nghiệm thu đúng đắn:** chỉ dùng TC1–TC8 (dữ liệu inline, không cần file).
- **Benchmark:** dataset đã có sẵn cục bộ tại `dataset/` (định dạng FIMI chuẩn: mỗi dòng là một transaction, các item là số nguyên cách khoảng trắng, **TID suy ra = số thứ tự dòng (1-based)**). Bảng thống kê đã đo thực tế:

| Dataset | Dòng (trans) | Items | Avg len | Loại | Gợi ý ∂ (default) | minSup ≈ |
|---|---|---|---|---|---|---|
| `chess.dat` | 3,196 | 75 | 37.0 | Dense | 35% | 1,118 |
| `connect.dat` | 67,557 | 129 | 43.0 | Dense | 30% | 20,267 |
| `kosarak.dat` | 990,002 | 41,270 | 8.1 | Sparse (lớn nhất, dùng cho scalability) | 0.05% | 495 |
| `mushroom.dat` | 8,124 | 119 | 23.0 | Dense (trùng paper [1]) | 6% | 487 |
| `pumsb.dat` | 49,046 | 2,113 (max id 7,116) | 74.0 | Rất dense | 30% | 14,714 |
| `pumsb_star.dat` | 49,046 | 2,088 | 50.5 | Rất dense | 30% | 14,714 |
| `retail.dat` | 88,162 | 16,470 | 10.3 | Sparse (trùng paper [1]) | 0.1% | 88 |

> Ghi chú: ∂ ở bảng là **mặc định gợi ý** (để so sánh công bằng giữa V1/V2/V3), có thể tinh chỉnh tại G1/G2 sao cho sinh lượng DOP đủ lớn mà thời gian chạy hợp lý. Paper [1] dùng thêm Accidents, Webview và synthetic T10I4DxK — **chưa có** ở đây; thay vào đó:
> - **Scalability:** dùng `kosarak.dat` (≈990K trans) làm dataset lớn, có thể cắt theo số dòng (200K→990K) như cách paper dùng T10I4DxK.
> - **Synthetic dự phòng (tuỳ chọn):** bộ sinh dữ liệu cấu hình T10I4DxK giữ nguyên nếu cần thêm dữ liệu.
- Protocol mô phỏng incremental (theo paper): chia mỗi dataset thành **5 phần bằng nhau**, nạp tuần tự từng phần, mỗi bước đo.
- `dataset/default.dat`: dataset demo (định dạng text, item là chuỗi `A`–`G`, 8 transaction = dữ liệu ví dụ chạy tay), dùng để **thử nghiệm nhanh**, **không thuộc** bộ benchmark.
- Cần 1 bước nhỏ trong G0: **validate** các file dataset đọc qua FIMI reader và tái sinh bảng thống kê (dùng để đối chiếu).

---

## 6. Bộ Test Chuẩn (Golden — áp dụng cho cả 3 phiên bản)

Nghiệm thu đúng đắn dùng 8 test case ở **Phần 11 của [2]**:

| TC | Dữ liệu | Tham số | Kỳ vọng |
|---|---|---|---|
| TC1 | 8 TID (DB0+DB1+DB2) | f=0.9, ∂=15% → minSup=1.2 | `{AE=1.2601, F=1.2553}` |
| TC2 | như TC1 | ∂=20% → 1.6 | `{}` |
| TC3 | như TC1 | ∂=10% → 0.8 | 15 DOP (bảng đầy đủ trong [2]) |
| TC4 | như TC1 | f=0.8, ∂=15% | `{}` |
| TC5 | như TC1 (HOP) | f=1.0, ∂=15% | 9 HOP (bảng trong [2]) |
| TC6 | chỉ DB0 | f=0.9, ∂=25% → minSup=1.0, TL=4 | `{FCD=1.0000, CD=1.4812, CDE=1.2218}` |
| TC7 | custom 10 TID | f=0.9, ∂=15% → minSup=1.5 | 9 DOP (bảng trong [2]) |
| TC8 | 5 TID mỗi giao dịch 1 item | f=0.9, ∂=30% → minSup=1.5, TL=5 | `{A=2.4661}` |

**Cấu trúc nghiệm thu chung (TestKit):**
1. `GoldenRunner`: chạy một engine bất kỳ trên TC, so sánh tập kết quả với golden theo C4–C6 (so AGENT `double` theo ε/tolerance 1e-6 khi so với bảng 4 chữ số; so **toàn vẹn** giữa 3 phiên bản với nhau theo double đầy đủ).
2. Mốc "trùng kết quả": V2 phải trùng V1 trên cả 8 TC; V3 phải trùng V2 khi có.

---

## 7. Benchmark (NFR của V2, xem thêm plan 02)

| Metric | Cách đo |
|---|---|
| Runtime tổng & theo giai đoạn | Wall-clock qua `System.nanoTime`; tách: load/construction, reconstruction, mining. |
| Peak memory | JMX (`MemoryPoolMXBean`) hoặc JFR; đo mức tối đa trong chu kỳ chạy. |
| Throughput | số DOP / giây (toàn bộ chu kỳ). |
| Latency theo batch | thời gian xử lý mỗi phần 1/5 dataset (incremental). |
| Scalability | `kosarak.dat` 200K→990K: runtime & memory theo số transaction. |
| So sánh chéo | V1 vs V2 vs V3 (khi có) cùng dataset/tham số/ngưỡng theo **bảng 5.2** (7 dataset cục bộ + ∂ gợi ý) với f=0.9. |

**Tham số benchmark mặc định:** dùng bảng ∂ ở mục 5.2 (chess 35%, connect 30%, kosarak 0.05%, mushroom 6%, pumsb 30%, pumsb_star 30%, retail 0.1%); `f = 0.9`; chia 5 phần incremental; chạy ≥3 lần lấy median.

---

## 8. Lộ trình & Phụ thuộc

Giai đoạn | Nội dung | Sản phẩm đầu ra | Chế độ chờ
---|---|---|---
**C0. Planning (hiện tại)** | Lập plan tổng thể + 3 plan phiên bản; **SRS tổng thể**; **UI Layout** | `docs/plans/*`, `docs/srs/DHOPM-SRS.md`, `docs/srs/DHOPM-UI-LAYOUT.md` | — |
**G0. Khởi động** | (tạo `docs/phases/P0-G0.md` trước) Xác nhận các quyết định mở (mục 10); kiểm tra toolchain JDK25+Maven; tạo structure Maven; seed `dhopm-common` (io/config/TestKit TC1–TC8); **validate 7 dataset trong `dataset/`** (FIMI reader + tái sinh bảng thống kê 5.2) | Structure + TestKit chạy được + dataset validated | C0 |
**G1. V1 Standard** | (tạo plan + tài liệu giai đoạn) Làm theo plan 01: triển khai đầy đủ, **áp dụng design pattern GoF**, Level 1 threading, qua TC1–TC8, benchmark sơ bộ; **design cấu trúc & từng thành phần/hàm** trong module | `dhopm-v1-standard` hoàn chỉnh (code + README + design + test + report) | G0 |
**G2. V2 Optimized** | (tạo plan + tài liệu giai đoạn) Làm theo plan 02: tối ưu DS, Level 2 threading, benchmark đầy đủ so V1; **nếu có cấu trúc/kiến trúc tốt hơn GoF thì áp dụng, không thì theo plan**; **design nêu rõ tại sao chọn tối ưu + so với V1** | `dhopm-v2-optimized` + báo cáo benchmark | G1 (dùng V1 làm golden) |
**G3. V3 Extreme** | (tạo plan + tài liệu giai đoạn) Làm theo plan 03 (hiện ý tưởng; chi tiết hóa tại G3): DOD, loại bỏ OOP, tối đa song song; **không cần design pattern** (đã cực đoan); **design nêu rõ tại sao + so với V2** | `dhopm-v3-extreme` + báo cáo so sánh | G2 |
**G4. Debug/Compare App** | (lập **plan riêng khi tới giai đoạn** – đã ghi yêu cầu trong SRS mục 9) Xây `dhopm-app` (module UI riêng, có **loading/mining screen** tránh freeze): chọn **≥1 engine** để chạy/so sánh, debug nội bộ, cho phép **sửa đổi/thao tác chi tiết thuật toán** | `dhopm-app` (GUI) + bộ tài liệu của nó | Sau G2 (thứ tự cụ thể bàn khi G4 bắt đầu) |

> **Chốt theo bạn:** giai đoạn hiện tại **chỉ lập plan**; mỗi giai đoạn về sau sẽ có **plan cụ thể + tài liệu cụ thể của giai đoạn đó**. V1 → V2 → V3 có **phụ thuộc tuần tự**; V3 chỉ chi tiết hóa sau khi V1, V2 xong. Debug app (G4) bàn luận/lập plan **sau khi đến giai đoạn làm app**.

---

## 9. Rủi ro & Giảm thiểu

| Rủi ro | Giảm thiểu |
|---|---|
| Mơ hồ thuật toán giữa paper và chạy tay (đã có Q1–Q10 trong draft cũ) | Đã khóa tại mục 2.3 (C1–C6); mọi thắc mắc thêm phải quy về mốc [1]/[2], ghi lại thành **bổ sung canonical** |
| Kết quả lệch khi song song hóa (thứ tự cộng dồn) | INV-E + C5: một node luôn tính trên một thread; test DeterminismAssert |
| Thread + map toàn cục khi construction song song (V2) | Chỉ V2 trở lên mới song song construction; phải ráp theo TID giữ INV-B; V1 giữ đơn luồng |
| Dataset FIMI thiếu Accidents/Webview/T10I4DxK (so paper) | Dataset đã có sẵn 7 file cục bộ; thay thế scalability bằng `kosarak.dat`; bộ sinh synthetic giữ làm dự phòng (mục 5.2) |
| Sai số số thực / epsilon | C4: ε=1e-9 cho quyết định; tolerance riêng khi so với bảng 4 chữ số |
| Out-of-scope đè nặng tài liệu design cũ | Chỉ tham khảo; không tái sử dụng code/prototype |

---

## 10. Quyết định (đã chốt trước G0)

| # | Quyết định | Kết luận |
|---|---|---|
| D1 | Build tool | **Maven (đa module)** |
| D2 | Vị trí dự án code | **`implementation/`** trong repo `D:\JVNC\JVNC` (cạnh `docs/`, `dataset/`) |
| D3 | Xử lý `Default Project/` + `draft/` cũ | **Đã lưu trữ trên branch `archive/legacy-draft` và xóa khỏi main**. Không tái sử dụng; không liên quan build |
| D4 | Thread pool mặc định | = **`availableProcessors()`**; override qua CLI/config. **Ghi chú debug app (G4):** nên có option cho user chọn số thread `> 0` tùy ý (chỉ là option — cân nhắc khi làm debug app) |
| D5 | Format input mặc định cho CLI/benchmark | **FIMI** (TID = số dòng) + **text** (TID tường minh) cho TC |

---

## 11. Tiêu chí Hoàn thành Tổng thể

- [ ] Có SRS tổng thể + UI Layout (tài liệu planning).
- [ ] TestKit (TC1–TC8) xanh trên cả V1, V2 (và V3 khi có).
- [ ] V1, V2 cho **tập DOP trùng khớp** (kiểm mapping double đầy đủ).
- [ ] Mỗi module engine có **bộ tài liệu riêng** (README, **design cấu trúc & từng thành phần/hàm**, test, benchmark report; V2/V3 kèm **lý do chọn tối ưu + so sánh với phiên bản trước**).
- [ ] V1 áp dụng design pattern GoF; V2 (nếu có) áp dụng cấu trúc tốt hơn GoF, không thì theo plan; V3 không cần pattern.
- [ ] V2 benchmark xong trên tối thiểu 4 dataset cục bộ (gồm dense + sparse) + scalability `kosarak.dat`; so sánh V1 vs V2.
- [ ] V3 có plan chi tiết + triển khai (giai đoạn cuối).
- [ ] G4: debug/compare app (có plan riêng khi đến giai đoạn) — module UI riêng + loading screen, chọn **≥1 engine** so sánh (min 1 = chạy đơn), cho phép sửa đổi chi tiết thuật toán.

---

*Kết thúc Plan tổng thể. Chi tiết từng phiên bản tại `01-STANDARD-VERSION-PLAN.md`, `02-OPTIMIZED-VERSION-PLAN.md`, `03-EXTREME-VERSION-PLAN.md`.*