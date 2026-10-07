# DHOPM — Đề xuất hướng phát triển (đã cập nhật theo hướng cửa sổ ε)

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-STRATEGY-000 |
| **Version** | **1.0 (Draft)** |
| **Status** | ⚠️ **Bản thảo ý tưởng — KHÔNG phải quyết định.** Mọi thứ ở đây là nhận định, đề xuất, phỏng đoán; tác giả tự chọn hoặc bác bỏ |
| **Mục đích** | Trả lời 4 câu: *phát triển thành gì, như thế nào, tại sao, giải quyết vấn đề gì* — dựa trên `docs/` hiện có + paper gốc + code G1 + ý tưởng ε trong `docs/Draft Idea.txt` |
| **Đọc kèm** | `plans/00-OVERALL-PLAN.md` (canonical **C1–C12, INV-A..J**), `plans/02-EPSILON-WINDOW-VERSION-PLAN.md`, `reports/G1-V1-STANDARD-BAOCAO.md` §4.5, `dhopm-v1-standard/docs/benchmark.md`, `root/1-s2_0-S095219762600792X-main.md` |
| **Ngày** | 2026-10-03 (cập nhật ε) |

### Revision History

| Phiên bản | Mô tả |
|---|---|
| 0.1 | Bản đề xuất đầu: 3 đề xuất A/B/C, 3 hướng A/B/C, định vị lại 3 phiên bản, chốt 9 câu hỏi |
| **1.0** | **Viết lại theo hướng mới**: `docs/Draft Idea.txt` đã được **chốt thành V2** (cửa sổ ε). Toàn bộ §3.6/§7/§8 được hợp nhất vào **G2–V2**. Số phiên bản 3 → **4**. Câu hỏi Q3 (ngưỡng `∂`) **đã có câu trả lời** ở `plans/00` §2.4–2.5 |

---

## 0. Cách đọc tài liệu này

Tài liệu này **không thay thế** `plans/00` (nguồn chuẩn thuật toán) và **không phải kế hoạch triển khai**. Nó là một lần tự đánh giá: *với những gì đang có, đang thiếu, dự án nên trở thành cái gì*.

Mọi phát hiện kỹ thuật ở §3 đều kèm cách kiểm chứng, không khẳng định suông. Chỗ nào tôi chỉ *phỏng đoán* thì ghi rõ **"giả thuyết"**. Chỗ nào đã đo rồi thì ghi rõ **"đã đo"** và kèm số liệu từ `benchmark.md`.

> **Thay đổi quan trọng từ v0.1:** ý tưởng §3.6 (window decay) **không còn là đề xuất "nên làm"** — nó đã được tác giả **chốt** và chuẩn hoá thành **V2 = `dhopm-v2-epsilon`**, chi tiết ở `plans/02-EPSILON-WINDOW-VERSION-PLAN.md`. Vì vậy §3.6/§7/§8 dưới đây được viết lại theo hướng "thực thi", không phải "đề xuất".

---

## 1. Chẩn đoán hiện trạng

### 1.1 Cái đã có (vững)

| Hạng mục | Trạng thái |
|---|---|
| Mốc thuật toán | Paper EAAI 2026 (Cho et al.) + bảng chạy tay TC1–TC8 → canonical **C1–C12, INV-A..J** đã khóa. Đây là tài sản lớn nhất của repo. |
| `dhopm-common` | Đọc FIMI/text, `MiningConfig`, contract (`Engine`/`PhaseAware`/`ProgressAware`/`Timed`), `WorkerPool`, TestKit TC1–TC8. 56 test xanh. |
| `dhopm-v1-standard` | Chạy đúng: TC1–TC8 PASS, deterministic bit-for-bit qua workers {1,2,4,cpu}, Level‑1 threading thật. **Đóng băng làm oracle.** |
| `dhopm-bench` | Harness incremental 5 phần, CSV. |
| Dữ liệu | **10 dataset FIMI chuẩn** (trong đó có **accidents.dat 340 183 tx — dataset của chính paper** — và **chainstore.dat 1 112 949 tx**), kèm bản nén trong `dataset/zip/`. |
| Tài liệu | Nhìn chung đầy đủ, có tư duy (plan 00 viết tốt, đặc biệt mục 2 và 4.2/4.3). Đã thêm `DECISIONS.md`. |

**Kết luận phần này:** phần *đúng thuật toán* thì đã ổn. Vấn đề không nằm ở chỗ "làm đúng hay chưa".

### 1.2 Cái đang hở

| # | Vấn đề | Bằng chứng | Trạng thái sau ε |
|---|---|---|---|
| H1 | **Ngưỡng `∂` trong paper không dùng được trên thang đo DO** → mọi benchmark hiện tại đang đo *"tìm rỗng"* | `benchmark.md` §Kết luận: DO bị chặn trên ≈10 ở f=0.9; retail ∂=0.1% → minSup=88 ≫ 10 → **0 pattern sau 87 s**; mushroom ∂=6% → minSup=487 → **0 pattern sau 300 s** | ✅ **Đã có công thức trong lý thuyết** (`plans/00` §2.3–2.5), **cần đo ở G2** |
| H2 | **Không có "trần" nào trong thuật toán** để dừng sớm khi kết quả chắc chắn rỗng | `Miner.java:52-55` chỉ prune bằng DUBO; DUBO ≤ support nên **không bao giờ cắt** khi minSup lớn | ✅ **Đưa vào V2** (short-circuit toàn cục + `UB'(X)=min(DUBO,Z(X))`) |
| H3 | **Thuật toán không tận dụng decay trong cấu trúc dữ liệu** → chi phí mining vẫn tăng theo độ dài stream | DHO-List giữ **mọi** entry của **mọi** transaction; reconstruction quét lại toàn bộ mỗi `mineNow()` | ✅ **Đây chính là V2** (cửa sổ ε, evict O(1), handle 2 tầng) |
| H4 | **Chưa có đối thủ so sánh nào.** V1/V2/V3/V4 là 4 bản cài cùng một họ thuật toán | `plans/00` §1.1; paper so với **HOMI-D / HEP-D**, dự án chưa có cái nào | ⏸ **Hoãn** — ε là việc đáng làm trước; baseline để sau |
| H5 | **Chưa chứng minh được "one scanning"** | `IncrementalTest` chỉ kiểm "load 2 phần == load hết" | ⏸ **Cửa sổ ε làm tình huống này dễ kiểm chứng hơn** (thêm/bớt entry theo vòng đời) |
| H6 | **Ước lượng roadmap vượt xa giá trị** | `plans/04` MA1–MA4, `plans/05` G-1..G-9 | ⏸ **Rút gọn** ở `plans/05` |
| H7 | **Tài liệu tự mâu thuẫn**: 2 thiết kế CLI khác nhau; `P1-G1` checkbox toàn `[ ]`; 6 file rỗng | `docs/Document/CLI/**` vs `G1 report §4.2` | ⏳ **Đang dọn** (xem Phụ lục C) |
| H8 | **Utility-FIMI chưa có reader** | D42 — planned, chưa triển khai | ⏸ Tuỳ chọn (G7) |
| H9 | Không có CI, `LICENSE`, `run.bat` hardcode JDK path, không có Maven wrapper | `.github/`; `run.bat:11` | ⏳ Rẻ, làm sớm |

---

## 2. Câu hỏi gốc: dự án này để làm gì?

Trước khi chọn hướng kỹ thuật, phải chốt mục đích. Tôi thấy 3 mục đích khả dĩ, và **tài liệu hiện tại đang phục vụ đồng thời cả 3** — đó là nguyên nhân vì sao mọi thứ bị giãn.

| | **M1 — Đồ án học phần** | **M2 — Đồ án tốt nghiệp / nghiên cứu** | **M3 — Sản phẩm / demo** |
|---|---|---|---|
| **Tiêu chí thành công** | Đúng rubrics: GoF, **4 phiên bản**, đủ tài liệu, test xanh | Có kết quả mới + so sánh được + trích lẫn được | Người dùng thấy được giá trị trong 5 phút |
| **"4 version"** | Bắt buộc (rubrics) | Nên bỏ, thay bằng biến thể thuật toán | Thừa |
| **V4 Extreme** | Rất tốt (minh hoạ data-oriented design) | Lãng phí thời gian, không giá trị khoa học | Không ai quan tâm |
| **Baseline HOMI-D/HEP-D** | Không cần | **Bắt buộc** | Không cần |
| **App** | Làm ở cuối cho đủ rubric | Làm ở cuối, chỉ để minh hoạ kết quả | **Là phần chính** |
| **Bộ dataset** | 1–2 cái đủ | ≥ 4 cái + scalability | 1 cái + stream mô phỏng |
| **Deadline áp lực** | Cứng | Dài | Trung bình |

### 2.1 Khuyến nghị

Dự án này **có thể nghiêng 70% về M2 và 30% về M1, giữ M3 làm "lớp trình bày"** — tức là:

- Giữ bộ khung module + GoF ở V1 (thoả M1, chi phí gần 0 vì đã có).
- Nhưng **dồn năng lực vào phần còn trống của M2**: cửa sổ ε, bound, trần DO, cấu trúc dữ liệu, baseline.
- App (M3) được làm **sớm và mỏng** — chỉ để *cho thấy được cái mới*, không phải để có đủ màn hình.

Lý do: hiện tại dự án **rất giỏi phần "làm đúng" và rất yếu phần "chứng minh điều gì đó"**. Đó là đúng mức hấp dẫn cho M1, nhưng chưa đủ cho M2, và chưa có gì để show cho M3.

---

## 3. Phát hiện kỹ thuật quan trọng nhất

Đây là phần giá trị nhất của cả repo. **Cả ba phát hiện đều đã được chốt vào V2** — mục này ghi lại lập luận để hiểu *vì sao*.

### 3.1 Trần vật lý của DO

Với `DO(X) = Σ_{t ∈ T(X)} (|X| / |T_t|) · f^(TL − t)` và `0 < f < 1`:

```
DO(X) ≤ Σ_{t=1..TL} f^(TL − t) = (1 − f^TL) / (1 − f) ≡ Z(f, TL)
```

Vì `|X|/|T_t| ≤ 1`. Với TL lớn, `Z → 1/(1−f)`:

| f | Z ≈ 1/(1−f) | half-life (số giao dịch) | cửa sổ W ở ε=1e-6 |
|---|---|---|---|
| 0.8 | 5 | 3.1 | 70 |
| 0.9 | 10 | 6.6 | 153 |
| 0.95 | 20 | 13.5 | 328 |
| 0.99 | 100 | 69 | 1 833 |

*(Ghi chú: bản v0.1 ghi cửa sổ "≈132/270/1375" — đó là công thức xấp xỉ `ln(1/ε)/ln(1/f)` **thiếu yếu tố `(1−f)`**. Công thức chính xác trong `plans/00` §2.3.1 cho 70/153/328/1833. Đã sửa.)*

*(Đã đo phần f=0.9: `benchmark.md` ghi DO chặn trên ≈10, nửa đời ≈7 — khớp.)*

### 3.2 Hệ quả 1: `∂` phải nằm trong miền khả thi

Với `minSup = ∂ × N_eff`, cần `∂ × N_eff ≤ Z(f,TL)` ⇒ **`∂ ≤ 1/((1−f)·W)`**.

| f | W (ε=1e-3) | W (ε=1e-6) | W (ε=1e-9) | `∂` tối đa (ε=1e-3 / 1e-6 / 1e-9) |
|---|---|---|---|---|
| 0.8 | 39 | 70 | 101 | 12.8 % / 7.14 % / 4.95 % |
| **0.9** | 88 | 153 | 219 | 11.4 % / 6.54 % / 4.57 % |
| 0.95 | 194 | 328 | 463 | 5.15 % / 6.10 % / 4.32 % |
| 0.99 | 1 146 | 1 833 | 2 521 | 0.87 % / 5.46 % / 3.97 % |

Đối chiếu ngưỡng của paper (f=0.9, ε=1e-6 ⇒ W=153, N_eff=153, Z=10):

| Paper | ∂ của paper | minSup mới | Kết luận |
|---|---|---|---|
| Mushroom 6 % | 0.06 | 9.18 | khả thi *hẳn* nhưng chỉ pattern gần toàn giỏ mới đạt ⇒ **giải thích vì sao chạy 300 s mà ra rất ít pattern** |
| Retail 0.10 % | 0.001 | 0.153 | **có nghĩa** |
| Synthetic 0.15 % | 0.0015 | 0.23 | **có nghĩa** |
| **Accidents 50 %** | 0.5 | 76.5 | **⇒ ∅ chắc chắn** |
| **pumsb 30 %** | 0.3 | 45.9 | **⇒ ∅ chắc chắn** |
| **connect 30 %** | 0.3 | 45.9 | **⇒ ∅ chắc chắn** |

> **Điểm mới so với bản v0.1:** vì `N_eff` không giảm tuyến tính theo N như `∂ × N`, **miền `∂` khả thi rộng và ổn định** (~4–13 % bất kể `f`, `ε`, kích thước dataset). Đây là thuộc tính có lợi của công thức `minSup = ∂ × N_eff`: **∂ trở thành tham số có nghĩa, không cần tinh chỉnh theo dataset**.

> **Giả thuyết (vẫn cần kiểm chứng, không phải cáo buộc):** các ngưỡng của paper có thể là ngưỡng **occupancy không damping** (kế thừa từ HEP — với O(X) thì O(X) ≤ N nên `∂ × N` là hợp lệ), trong khi đo bằng DO thì bị chặn ở `1/(1−f)`.

**Cách kiểm chứng (rẻ, làm ở G2):**
1. Chạy mushroom ∂=6 %, đếm số DOP → **đã đo: 0**.
2. Chạy mushroom/retail với `minSup` trong miền khả thi → xem số pattern và runtime.
3. Kiểm tra paper có báo cáo **số lượng pattern** tìm được không. Nếu không → hỏi tác giả bài, hoặc tự kết luận có giới hạn và ghi rõ.

### 3.3 Hệ quả 2: thuật toán không có trần ⇒ tốn thời gian tìm rỗng

Khi `minSup > Z(f,TL)`, **kết quả chắc chắn là rỗng**. Nhưng code hiện tại vẫn đi khám phá toàn bộ cây:
- C2 `support < minSup → skip`: `support` lớn hơn `minSup` rất nhiều → **không skip**.
- Prune `DUBO < minSup`: DUBO ≤ support → cũng không prune.
- ⇒ DFS chạy hết, 87 s / 300 s, **trả về `{}`**.

Đã đo: `retail` phần 5 = **86 949 ms → 0 pattern**; `mushroom` phần 5 = **300 751 ms → 0 pattern**.

Một check 3 dòng ở `mineNow()` biến 87 s thành ~0 ms **và** biến "chạy 5 phút rồi ra rỗng" thành thông báo lỗi rõ ràng. Đây là **limitation #1 mà paper tự nêu** — ở đây nó không chỉ khó chọn, mà **không có cách nào biết mình đã chọn sai nếu không có validator**.

### 3.4 Đề xuất A — Chuẩn hoá thang đo (`nDO`) · **đã hạ cấp xuống tuỳ chọn**

> **Thay đổi so với v0.1:** bản 0.1 xếp `nDO` là "ứng viên số 1". Công thức `minSup = ∂ × N_eff` đã giải quyết được phần lớn vấn đề mà không cần thêm tham số. Vì vậy `nDO` **không còn cần thiết** ở V2.

`nDO(X) = DO(X)/Z(f,TL)`, `minSup_norm = θ × Z(f,TL)` — vẫn là một **lựa chọn đáng cân nhắc** nếu sau G2 thấy `∂` vẫn khó chọn. Nhưng nó **thêm tham số** và **phá tính đơn giản** của `∂ × N_eff`. Đề xuất: **chỉ mở nếu G2 cho thấy cần**, và luôn giữ chế độ `RAW` làm mặc định.

### 3.5 Đề xuất B — Bound chặt hơn (`Z`-bound) · **đã chốt vào V2**

**(B1) Short-circuit toàn cục:** nếu `minSup > Z(f,TL)` → trả `{}` ngay, O(1). Kèm thông báo cho người dùng kèm số liệu.

**(B2) Bound theo từng node.** Với mọi superset `Y ⊇ X`: `T(Y) ⊆ T(X)` và `|Y|/|T_t| ≤ 1`, nên

```
DO(Y) ≤ Σ_{t ∈ T(X)} f^(TL − t) ≡ Z(X)
   ⟹  UB'(X) = min( DUBO(X), Z(X) )      thay cho prune bằng DUBO(X) đơn thuần
```

- `Z(X)` tính **cùng một vòng duyệt entry** với DUBO → gần như miễn phí.
- Phải `min` — luôn ≤ DUBO ⇒ **chỉ prune thêm, không bao giờ prune sai**.
- Đây chính là **limitation #2 của paper** ("cần kỹ thuật prune tốt hơn") — và nó xuất phát từ việc **chú ý rằng measure có damping ⇒ có trần**, điều paper không dùng.

### 3.6 Ý tưởng C — Cửa sổ ε · **ĐÃ CHỐT THÀNH V2** ⭐

Ý tưởng mạnh nhất: **thay đổi đẳng cấp độ phức tạp**, không chỉ hằng số. Nguồn: `docs/Draft Idea.txt` (tác giả).

Ý tưởng: transaction ở tuổi `k` đóng góp tổng ≤ `f^k`. Tổng phần bị bỏ (giữ `W` transaction cuối, tuổi `0..W−1`) là

```
Σ_{k=W}^{∞} f^k = f^W/(1−f) ≤ ε   ⟹   W = ⌈ ln(ε(1−f)) / ln f ⌉
```

**Ba điều chỉnh quan trọng so với bản v0.1:**

1. **Công thức cửa sổ phải có hệ số `(1−f)`.** Bản 0.1 dùng `t0 = TL + 1 + ln(ε·(1−f))/ln f`, đúng, nhưng bảng "≈132" ở f=0.9, ε=1e-6 là sai vì bỏ qua `(1−f)`. Giá trị đúng là **153**.
2. **`minSup` phải dùng `N_eff = min(TL, W)`, không phải `∂ × TL`.** Nếu giữ `∂ × TL` sau khi đã cắt cửa sổ, ngưỡng tiếp tục tăng theo độ dài stream trong khi dữ liệu chỉ còn `W` transaction ⇒ **số pattern tuyến tính → 0** ⇒ thuật toán "chết dần". Đây là điểm mà bản v0.1 **chưa nắm rõ**.
3. **Evict phải O(1) ⇒ cần handle 2 tầng.** Bản v0.1 nói "bỏ entry có tid < t0" — cách đó là **O(|I|)** mỗi lần evict (phải rà từng node). `docs/Draft Idea.txt` đã chỉ ra cách đúng: giữ `ref1 → ref2(handle)`, evict bằng cách clear payload `ref2`; node phát hiện dead handle trong lúc đang traverse; vì evict theo TID tăng dần nên **entry chết luôn là tiền tố** ⇒ compaction bằng một chỉ số `head`, O(1) amortized. Xem `plans/02` §5.2.

**Tính đúng đắn (lập luận được, cần test):**
- Mọi thứ (DO, DUBO, DFS) trở thành **thuật toán chạy trên sub-stream trong cửa sổ với `TL` không đổi** ⇒ Lemma 2 vẫn đúng trong instance đó.
- Sai số duy nhất là `|DO_full − DO_win| ≤ ε` cho **mọi** pattern (INV-G).
- C2 vẫn hợp lệ: `sup_win(X) < minSup ⇒ DO(X) ≤ Z(X) ≤ sup_win(X) < minSup`.

**Hiệu quả dự kiến (phỏng đoán — cần đo ở G2):**

| | Hiện tại (V1) | Với cửa sổ (ε=1e-6, f=0.9, W=153) |
|---|---|---|
| Entries trong DHO-List | O(N × avgLen) | O(W × avgLen) |
| Reconstruction mỗi `mineNow` | O(tổng entries) | O(W × avgLen) |
| Chi phí mining | phụ thuộc N | **gần như độc lập N** |
| Peak memory | tăng theo N | **gần như không đổi theo N** |
| `retail` 88 162 tx | 86 949 ms | kỳ vọng **< 1 s** |
| `kosarak` 990 002 tx | (chưa chạy) | kỳ vọng **< 2 s** |

Nghĩa là: **paper cần ~600 s cho 1 000 000 transaction synthetic, trong khi phần dữ liệu "còn sống" chỉ ~153 transaction.**

⚠️ **Cạm bẫy đã xử lý:** evict làm thay đổi `n_i` và `T_k` trong DUBO. Cách xử lý chốt: **DUBO tính chỉ trên entry sống** ⇒ toàn bộ phép tính nằm trên cùng một instance ⇒ Lemma 2 vẫn đúng (xem `plans/02` §5.5). Bắt buộc có **test so eviction với full recompute** trên tất cả dataset (TC18).

### 3.7 Vì sao ba phát hiện này đáng làm

| | Nhỏ | Trung bình | Lớn |
|---|---|---|---|
| **A — nDO** | ✅ 1 giờ | | ⚠️ **đã hạ cấp xuống tuỳ chọn** |
| **B — bound** | ✅ 1–2 ngày | ✅ **giải quyết limitation #2 + làm lộ ra H1** | |
| **C — cửa sổ ε** | | | ✅ **giải quyết limitation #3 + #4, đổi đẳng cấp phức tạp — V2** |

---

## 4. Hướng phát triển

### Hướng A — "Bản tái tạo + mở rộng có giá trị học thuật" *(đang thực hiện qua G2)*

**Nội dung:** chứng minh bản cài đặt trung thực với paper; giải quyết mâu thuẫn ngưỡng; cửa sổ ε + bound + trần DO; cài baseline HOMI-D/HEP-D; sweep `(∂, f, ε)`; tái tạo 1–2 hình trong paper.

- **Giải quyết vấn đề gì:** "các thuật toán occupancy-damping có thật sự dùng được không, và với tham số nào?" — câu hỏi bất kỳ ai muốn dùng DOP đều phải trả lời.
- **Vì sao:** rủi ro thấp nhất, giá trị cao nhất theo công sức.
- **Đối thủ:** không có gì "đẹp" để show.

### Hướng B — "Công cụ trực quan hoá vòng đời pattern"

**Nội dung:** chạy 1 dataset như stream; pattern **ra đời → lớn lên → tắt dần → biến mất**; click vào 1 pattern → xem **tại sao nó đạt/không đạt**.

- **Giải quyết vấn đề gì:** damping là khái niệm *trừu tượng*; không có gì thuyết phục bằng việc **thấy** một pattern mờ dần rồi biến mất.
- **Phụ thuộc:** cửa sổ ε **làm cho Hướng B dễ hơn trước** — pattern biến mất vì *rời cửa sổ* (có lý do rõ ràng, hiển thị được), không phải vì ngưỡng tăng.

### Hướng C — "Phòng thí nghiệm hiệu năng / đấu trường thuật toán"

**Nội dung:** giữ 4 version, siết benchmark, plug-in registry, xuất CSV/JSON.

- **Không khuyến nghị làm chính:** đo V1 vs V2 vs V3 đo **chất lượng code Java**, không đo **đóng góp của paper**.
- **Phần nên giữ:** bộ đo nghiêm ngặt + cơ chế benchmark tái lập được — dùng nó **phục vụ Hướng A**.

### 4.1 Khuyến nghị

**A trước, B mỏng sau, C chỉ giữ phần đo lường.**

> **Nếu chỉ làm được một việc:** làm **V2 (cửa sổ ε) + bound**. Đó là phần code nhỏ nhất mà đổi cả tính chất của hệ thống, và tạo ra một câu kết luận khoa học cụ thể.

---

## 5. Định vị 4 phiên bản

| Module | Vai trò | Định vị |
|---|---|---|
| `dhopm-v1-standard` | **Oracle** | Bám sát paper, `ε = 0`. **Đóng băng.** Không thêm feature (trừ validator). Mọi thứ khác phải khớp nó khi `ε=0` (INV-I). |
| `dhopm-v2-epsilon` | **Ngữ nghĩa mới** | Cửa sổ ε + handle 2 tầng + minSup 2 pha + `UB'(X)`. **Ưu tiên số 1.** |
| `dhopm-v3-optimized` | **Kỹ thuật** | Tối ưu cấu trúc dữ liệu + Level 2 threading. **Cùng ngữ nghĩa V2**, chỉ khác cách làm. Profile trước, tối ưu sau — ưu tiên `DuboCalculator` (TreeMap mỗi node mỗi tầng) vì đây nhiều khả năng là hotspot số 1. |
| `dhopm-v4-extreme` | **Trần hiệu năng** | Data-oriented thuần, Level 3. Chỉ giữ ý tưởng **đo được lợi**. Ghi rõ: đây là bài tập tối ưu hiệu năng, không phải đóng góp khoa học. |

**Lưu ý:** `nDO` và baseline HOMI-D/HEP-D **không** nhét vào V3/V4. Nếu làm, phải là **module riêng** (`dhopm-baseline`) vì chúng là *thuật toán khác*, không phải *phiên bản khác*.

---

## 6. Kiến trúc đích & những thứ nên **bỏ**

### 6.1 Kiến trúc đích

```
implementation/
├── dhopm-common       # reader · config · contract · testkit · util · window math (giữ, + WindowMath + validator)
├── dhopm-v1-standard  # oracle theo canonical, ε ≡ 0 (đóng băng)
├── dhopm-v2-epsilon   # CỬA SỔ ε + handle ref1/ref2 + minSup 2 pha + UB'(X)   (MỚI — ưu tiên số 1)
├── dhopm-v3-optimized # tối ưu cấu trúc dữ liệu + Level 2                 (khi làm)
├── dhopm-v4-extreme   # data-oriented thuần + Level 3                      (khi làm)
├── dhopm-bench        # đo nghiêm ngặt + sweep (∂, f, ε)                  (giữ, mở rộng)
├── dhopm-cli          # Manager mỏng: --json (one-shot) + serve JSONL stdio
└── dhopm-app          # UI: 2 tab (Trends + Explain)                       (rút gọn)
```

### 6.2 Anti-roadmap — những thứ nên **không làm**

| # | Mục | Lý do bỏ / đẩy |
|---|---|---|
| 1 | **SPI / plugin discovery / `PluginRegistry`** | Có **4** engine đã biết trước tên. Hardcode một list factory là đủ và *rõ hơn*. YAGNI. |
| 2 | **`ResourceManager` → native Windows/macOS** (D8/MA4) | Java không có API quota CPU portable. Nhu cầu thật đã giải quyết bằng `workers` + `--limit`. |
| 3 | **Recovery / `JobLedger` / journal** | Sau khi có cửa sổ ε, một lần `mine` là vài giây ⇒ "khởi động lại biết đang làm gì" thành vấn đề giả. |
| 4 | **Daemon TCP / remote LAN** | Mặc định: **JSONL qua stdio** — đủ cho cả demo lẫn test. |
| 5 | **`JobManager` / `ResultAggregator` / config 3 cấp** | Có giá trị khi có nhiều engine, nhiều người gọi, chạy lâu. Hiện tại: 1 người gọi, 1 máy, chạy ngắn. |
| 6 | **FE10 "bật/tắt C1–C12 lúc chạy"** | Đây là *xây IDE nghiên cứu*. **Giữ ở dạng "bảng tham số nâng cao"**: `f`, `∂`, `ε`, `workers`. Toggling *ngữ nghĩa canonical* → bỏ hoặc đẩy sang "chế độ giảng dạy". |
| 7 | **10 dataset nặng trong `mvn test`** | Tách sang profile/tag (`-Pheavy`). |
| 8 | **Bổ sung `webview`** | Nên lấy (nhỏ, độ dài biến thiên → minh hoạ được sức mạnh prune của DUBO). |
| 9 | **Utility-based mining (HUIM)** | Là **họ thuật toán khác**. Để thành đề tài/mở rộng sau. |

---

## 7. Danh mục hạng mục cụ thể

| ID | Hạng mục | Giải quyết | Cách làm | Rủi ro | Giá trị | Trạng thái |
|---|---|---|---|---|---|---|
| **R1** | **Cửa sổ ε + `Z`-bound + trần DO** | H1/H2/H3; limitation #2/#3/#4 | **V2** — `WindowMath`, handle 2 tầng, evict O(1), minSup 2 pha, `UB'(X)`, short-circuit; TC9–TC18 | Handle 2 tầng viết sai; DUBO sau evict | ★★★★★ | ⏳ **đã chốt, đang chờ code (G2)** |
| **R2** | Đọc Utility-FIMI | H8 → mở rộng sang HUIM | `UtilityTransactionReader` | Chậm test nặng → tách profile | ★★★★★ | ⏸ G7 |
| **R3** | **`nDO` / ngưỡng chuẩn hoá** | limitation #1 | Đã **hạ cấp**: `∂ × N_eff` đã đủ | Phá TC1–TC8 nếu đổi mặc định | ★★☆☆☆ | ⏸ tuỳ chọn sau G2 |
| **R4** | **Validator + cảnh báo `∂` khả thi** | H1, limitation #1 | `validate` command + `window` tra bảng; trả mã lỗi ổn định | — | ★★★★★ | ⏳ trong V2 |
| **R5** | Tái tạo 1–2 hình của paper | Chứng minh trung thực | Script chạy dataset × ngưỡng, xuất CSV | Số liệu "xấu" **là kết quả** | ★★★★☆ | ⏳ sau G2 |
| **R6** | **Sweep `(∂, f, ε)`** + operator's guide | limitation #1 ở dạng dùng được | `sweep` command | Bảng to | ★★★★☆ | ⏳ trong V2 |
| **R7** | **Vòng đời pattern trong stream** | H5 | So 2 batch liên tiếp → `added`/`removed`/`survived` + **churn**; cửa sổ ε làm việc này tự nhiên hơn | Cần định nghĩa "tắt" | ★★★★☆ | ⏳ sau G2 |
| **R8** | Instrumentation chất lượng prune | Biến "V2 nhanh hơn" từ *claim thời gian* → *claim cấu trúc* | Counter đặt sau `if (enabled)` | Counter trong hot path | ★★★★☆ | ⏳ trong V2 |
| **R9** | App mỏng 2 tab | Hướng B | Trends + Explain | Phụ thuộc R1+R7 | ★★★★☆ | ⏳ G6 |
| **R10** | Dọn tài liệu + `DECISIONS.md` + CI | H7, H9 | Phụ lục C | Không có | ★★★☆☆ | ⏳ **đang làm** |
| **R11** | V3 tối ưu theo profile thật | Hiệu năng | Profile sau V2; ưu tiên `DuboCalculator`, rồi SoA, rồi decay lookup | Tối ưu hoá mù | ★★★☆☆ | ⏸ G3 |
| **R12** | Baseline HOMI-D + HEP-D | H4 | Module riêng `dhopm-baseline` | Cổng hơn V1 | ★★★★☆ | ⏸ sau G2 |
| **R13** | V4 Extreme | Bài tập tối ưu | Theo plan 04 | Không giá trị khoa học | ★★☆☆☆ | ⏸ G4 |

**Thứ tự đề xuất:** **R1 + R4** (V2) → R10 → R5/R7/R8 → R6 → R9 → R11 → R12.

---

## 8. Lộ trình đề xuất

Mỗi mốc đều **độc lập tạo ra giá trị** — nếu hết thời gian thì dừng ở đâu cũng không phí.

### G2 — Cửa sổ ε + chứng minh (ưu tiên số 1) · *"Đổi đẳng cấp phức tạp"*
- **R1** (cửa sổ ε + bound + trần) · **R4** (validator) · **R8** (instrumentation)
- **Xong khi:** bảng "trước / sau" cho ≥4 dataset; `kosarak` 200K→990K **phẳng** thay vì tăng; `mushroom ∂=6%` in ra cảnh báo thay vì chạy 300 s; xác định được giả thuyết §3.2 đúng hay sai; viết được 1 đoạn kết luận.
- Chi tiết: `plans/02-EPSILON-WINDOW-VERSION-PLAN.md`, `phases/P2-G2.md`

### G3 — Tối ưu trên nền ε (2 tuần)
- **R11** — profile thật, SoA/primitive, bỏ lớp Handle, Level 2 threading
- **Xong khi:** ≡ V2 về kết quả (kể cả `ε > 0`); nhanh hơn V2 ở ≥ 3/4 dataset.

### G4 — Extreme (không đặt hạn)
- **R13** — data-oriented thuần, Level 3
- **Xong khi:** ≡ V3; có báo cáo "giữ / bỏ từng ý tưởng".

### G5 — Manager CLI/API (song song G3/G4)
- Theo `plans/05-BACKEND-CLIAPI-PLAN.md` MA1–MA3.

### G6 — Trình diễn (2–3 tuần) · *"Thấy được, hiểu được"*
- **R7** + **R9** (app 2 tab)
- **Xong khi:** nạp `retail.dat` theo stream → thấy pattern sinh/lớn/tắt; click 1 pattern → hiểu vì sao đạt hoặc vì sao bị prune.

### G7 — Dữ liệu mở rộng (tuỳ chọn)
- **R2** — đọc Utility-FIMI → mở rộng sang **HUIM**.

**Quy tắc vàng:** *một mốc chỉ xong khi nó tạo ra một câu hoặc một con số viết được ra báo cáo — không xong vì "code chạy".*

---

## 9. Definition of Done

| Mức | Tiêu chí |
|---|---|
| **Bắt buộc** | TC1–TC18 xanh · **ε=0 ⇒ V2/V3/V4 bit-for-bit ≡ V1** · INV-G giữ · median ≥3 lần + ghi phần cứng/JDK/workers · mọi quyết định có trong `DECISIONS.md` · không còn tài liệu mâu thuẫn · tài liệu nói đúng trạng thái thực tế |
| **Cửa sổ ε** | Evict O(1) · bộ nhớ đỉnh gần như phẳng khi stream dài (≥10× N) · `window`/`validate` trả số liệu đúng · bảng ablation V1→V4 có cột "độ lệch ≤ ε" |
| **Nếu chọn Hướng A** | Tái tạo được ≥1 hình của paper · giải thích được mâu thuẫn ngưỡng bằng số đo · có ≥1 mở rộng với phân tích before/after |
| **Nếu chọn Hướng B** | Chạy được stream và thấy pattern bị damping · giải thích được 1 pattern không đạt · app không đứng hình khi mining |

---

## 10. Rủi ro

| Rủi ro | Dấu hiệu sớm | Giảm thiểu |
|---|---|---|
| **ε làm sai kết quả so với paper** | Test TC18 lệch > ε | `ε=0 ⇒ ≡ V1` (INV-I) + TC18 so full-recompute |
| **Handle 2 tầng viết sai → đọc tham chiếu chết** | Crash lúc evict | INV-H; test TC13; **không `try/catch`** |
| **∂ ngoài miền khả thi** | Benchmark ra 0 pattern | Validator + short-circuit + `window` in miền khả thi |
| **Sunk cost vào V4-Extreme** | Tốn >2 tuần mà chưa có số liệu | Đặt "deadline giả" cho G2; trượt thì V4 về phụ lục |
| **Giả thuyết H1 sai** (paper đúng, code sai) | Đọc lại định nghĩa `minSup` mới thấy khác | `ε=0` giữ nguyên đường RAW. Sai thì chỉ mất 1 ngày |
| Kết quả "xấu" khi tái tạo paper | Số pattern 0 ở ngưỡng của paper | Đây **là phát hiện**, không phải lỗi. Ghi rõ phương pháp và phạm vi kết luận |
| App làm trễ nghiên cứu | Chưa xong G2 mà đã dựng UI | **App chỉ bắt đầu sau khi G2 có bảng số** |
| Phạm vi creep sang HUIM | Bắt đầu nói về `utility` trong `Transaction` | Reader bỏ utility, **không** đụng model |

---

## 11. Các câu hỏi còn cần tác giả trả lời

| # | Câu hỏi | Trạng thái | Khuyến nghị |
|---|---|---|---|
| ~~Q1~~ | Mục đích thật của dự án? | 🕔 **chưa chốt** | Nếu học phần → giữ 4 version + GoF. Nếu TN/khoa luận → đẩy V4 xuống cuối, thêm R12 baseline. **Quyết định lớn nhất.** |
| Q2 | Có được phép **đưa kết quả ra ngoài** (báo cáo, hội thảo)? | 🕔 chưa chốt | Nên — đó là lý do để làm R5/R6 |
| ~~Q3~~ | `∂` sẽ được hiểu là gì? | ✅ **ĐÃ CHỐT** | **`∂` = tỉ lệ ngưỡng trên `N_eff`**, miền khả thi `∂ ≤ 1/((1−f)·W)`; validator báo cảnh báo khi vượt. `nDO` thành tuỳ chọn (R3). Xem `plans/00` §2.3–2.5. |
| Q4 | Có thời gian làm tiếp sau mốc hiện tại không? | 🕔 chưa chốt | Cần câu trả lời để biết G2 có khả thi không |
| Q5 | Frontend (`javanc`) có chạy trên **máy khác** không? | 🕔 chưa chốt | Nếu không → stdio JSONL, **bỏ TCP/daemon** |
| Q6 | `mode` trong `Mine Commend.md` (`step`, `log`) còn giữ? | 🕔 chưa chốt | **`log` nên bỏ** (phá NFR zero-overhead). `step` chính là tính năng Explain của R9 → gộp vào đó |
| Q7 | `docs/Document/**` là thiết kế CLI **cũ** hay **ý muốn mới**? | 🕔 chưa chốt | Nếu ý mới → phải chọn 1 trong 2 thiết kế, không giữ cả hai |
| Q8 | `mushroom.dat` hay `newMushroom.dat`? | ✅ đã chốt | **Cả hai** đều giữ; **không so trực tiếp với nhau** — chúng có cùng 8 124 tập phân biệt nhưng **không giao nhau một tập nào** (gán item id khác nhau, `newMushroom.dat` có thêm 292 dòng lặp) |
| Q9 | Sau đợt này có muốn làm **utility-based** (HUIM)? | 🕔 chưa chốt | Nếu có → **tách project ngay** |

---

## Phụ lục A — Công thức & số liệu tham chiếu

```
DO(X)       = Σ_{t∈T(X)} (|X| / |T_t|) · f^(TL − t)
Z(f,TL)     = Σ_{k=0..TL−1} f^k = (1 − f^TL)/(1 − f)          ← trần của DO
Z(X)        = Σ_{t∈T(X)} f^(TL − t)                            ← bound theo node
UB'(X)      = min( DUBO(X), Z(X) )                             ← bound chặt hơn
W(f,ε)      = ⌈ ln(ε(1−f)) / ln f ⌉                           ← kích thước cửa sổ
               ε = 0 hoặc f = 1 ⇒ W = ∞                        ← ≡ paper
               ε ≥ 1/(1−f) ⇒ lỗi cấu hình
N_eff(TL)   = min(TL, W)
minSup(TL)  = ∂ · N_eff(TL)                                    ← 2 giai đoạn
∂ khả thi   ≤ 1 / ((1−f) · W)
half-life   = ln 2 / ln(1/f)   giao dịch
```

Kiểm chứng nhanh các bất biến (đã có test trong TestKit):

1. `DO(X) ≤ Z(f,TL)` cho mọi X, mọi f.
2. `Z(X) ≤ sup_win(X)` (⇒ C2 hợp lệ trên bản cửa sổ).
3. `DO_win(X) ≥ DO_full(X) − ε` và `|DO_win − DO_full| ≤ ε` mọi X (INV-G).
4. `UB'(X) ≥ DO(Y)` mọi superset Y (Lemma 2 mở rộng).
5. `ε = 0 ⇒ V2 ≡ V1` bit-for-bit (INV-I).

---

## Phụ lục B — Điểm cần xác minh với paper

| # | Paper nói | Ta đo được | Ghi chú |
|---|---|---|---|
| B1 | Mushroom `∂ = 6%` | minSup cũ = 487 > Z = 10 ⇒ **0 pattern**; theo công thức mới minSup = 9.18 | mâu thuẫn thang đo — cần kiểm chứng |
| B2 | Accidents: **942 items** | `accidents.dat` có **468** item, 340 183 tx (khớp chính xác số tx của paper), avg len 33.81 (khớp 33.808) | nhiều khả năng **lỗi trong Table 2 của paper** — nên ghi rõ trong báo cáo |
| B3 | Retail: **16 469 items** | `retail.dat`: 16 470 | lệch 1 (đã đối chiếu, không phải lỗi đọc file) |
| B4 | Webview 59 602 tx / 497 items | **chưa có** | nên lấy (nhỏ, độ dài biến thiên → tốt minh hoạ prune) |
| B5 | DHOPM nhanh hơn HOMI-D/HEP-D | chưa có baseline để so | R12 |
| B6 | Số pattern tìm được | **paper không báo cáo** | đây là lý do giả thuyết §3.2 **chưa kiểm chứng được từ paper** |

---

## Phụ lục C — Việc dọn tài liệu (H7)

| Việc | Cụ thể | Trạng thái |
|---|---|---|
| Xoá / điền | `Overview.md` (rỗng) · `spec/Spec.md` (cắt dở) · `Document/Compoment/{Factor,Limit,Mode,Parial}.md` (4 file rỗng) · `Document/CLI/Config/List Config.md` (rỗng) | ⏳ |
| Sửa chính tả | `Compoment` → `Component` · `Mine Commend.md` → `MineCommand.md` · `dateset`→`dataset`, `parial`→`partial` | ⏳ |
| Giải quyết mâu thuẫn CLI | `docs/Document/CLI/**` (positional) vs `G1 report §4.2` (flag-based đã implement). **Chọn 1** | ⏳ cần Q7 |
| Thống nhất trạng thái | `phases/P1-G1.md` đánh dấu `[x]` + thêm dòng trạng thái ở đầu mỗi phase plan | ⏳ |
| **`DECISIONS.md`** | ADR log, ID `D1…Dn` + trạng thái + lý do. Các plan chỉ trỏ tới decision thay vì tự quyết lại | ✅ **đã tạo** |
| Thang tài liệu | `DECISIONS.md` (quyết định) → `plans/` (chiến lược) → `srs/` (yêu cầu) → `phases/` (kế hoạch thực thi) → `module/docs/` (as-built) → `reports/` (kết quả). Mỗi tài liệu chỉ 1 vai trò | ✅ |
| TEMP plan | **Đã xoá** `TEMP-PLAN-DATASET-UTILITY-READER.md`; quyết định chuyển vào `plans/00` §5 | ✅ |
| Repo hygiene | GitHub Actions (build + test JDK 25) · `LICENSE` + ghi chú điều khoản dùng dataset FIMI · `mvnw` + `.mvn/` để không hardcode JDK path như `run.bat:11` | ⏳ |

---

## Tóm tắt một câu

> Dự án đã **làm đúng thuật toán** và **thiếu lý do để tồn tại**; việc đáng làm nhất là **trả lời ba câu hỏi mà chính paper thừa nhận chưa có lời giải** — *chọn `∂, f, ε` bằng gì*, *prune thế nào cho thật*, *xử lý stream dài ra sao* — và **ý tưởng ε của tác giả đã trả lời cả ba câu trong một công thức duy nhất**; V2 là nơi biến nó thành code, V3/V4 là nơi biến nó thành tốc độ.

---

*Bản thảo ý tưởng cho tác giả tham khảo. Mọi phát hiện kỹ thuật đều kèm cách kiểm chứng; các dự đoán định lượng đã được đánh dấu rõ là phỏng đoán. Các quyết định đã chốt nằm trong `plans/00-OVERALL-PLAN.md` và `DECISIONS.md`.*