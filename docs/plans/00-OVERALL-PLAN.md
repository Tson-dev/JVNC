# DHOPM/DOPM – Kế hoạch Tổng thể (4 phiên bản, có cửa sổ suy giảm minOcc)

> Tài liệu gốc (source of truth) cho toàn bộ nỗ lực: **định nghĩa chuẩn hoá thuật toán** (kể cả cơ chế cửa sổ suy giảm theo minOcc), khung threading dùng chung, cấu trúc dự án, bộ test nghiệm thu và lộ trình triển khai.
>
> **Phiên bản 2.0 thay đổi nền móng dự án**: bổ sung **hệ số minOcc (ngưỡng dưới của DO)** và **cửa sổ suy giảm (damped window)** — từ đó `minSup` không còn phụ thuộc kích thước cả dataset, và DHO-List chỉ giữ phần dữ liệu *còn giá trị*. Số phiên bản tăng từ 3 → **4**.

---

## Document Header

| Mục | Giá trị |
|---|---|
| **Document Name** | Kế hoạch tổng thể DHOPM – 4 phiên bản, cửa sổ suy giảm minOcc |
| **Document ID** | DHOPM-PLAN-000 |
| **Version** | **2.0 (Draft)** |
| **Status** | Draft – chờ review |
| **Nguồn ý tưởng minOcc** | `docs/Draft Idea.txt` (tác giả) → đã chuẩn hoá thành mục 2 & bất biến INV-G..INV-J |
| **Source Documents** | [1] Paper gốc: `docs/root/1-s2_0-S095219762600792X-main.md`<br/>[2] Ví dụ chạy tay + TC1–TC8: `docs/root/Nhom01_VDChayTay.md`<br/>[3] Tài liệu draft cũ đã lưu trên branch `archive/legacy-draft` — tham khảo, KHÔNG phải mốc |
| **Ngôn ngữ triển khai** | Java 25 (LTS) |
| **Môi trường** | Windows (win32) |

### Revision History

| Phiên bản | Mô tả |
|---|---|
| 1.0 | Khởi tạo: chuẩn hóa thuật toán, khung threading, cấu trúc dự án, lộ trình 3 phiên bản |
| 1.1 | Dataset cục bộ; cấu trúc Tài liệu & Workflow (SRS, UI Layout, tài liệu từng module, plan từng giai đoạn); giai đoạn G4 debug/compare app |
| 1.2 | Java 25; lưu trữ tài liệu draft cũ; V1 áp dụng GoF, V2 cân nhắc cấu trúc vượt GoF, V3 không cần pattern; mỗi giai đoạn sản xuất tài liệu design từng thành phần/hàm; util chung; log/benchmark độc lập thuật toán |
| 1.3 | Giao tiếp gỡ lỗi (mục 4.2 + D6): công cụ chỉ nói chuyện qua API ổn định ở `dhopm-common`; CLI chuẩn hoá bộ lệnh `mine/detail/stream/golden/inspect` |
| 1.4 | Giao tiếp Frontend ↔ Backend (mục 4.3 + D7/D8): frontend qua 1 Manager CLI/API duy nhất; thay `TsonV1Bridge` bằng client theo đặc tả JSONL; quản lý tài nguyên tách plan riêng |
| **2.0** | **Thay đổi nền móng (xem mục 0).** (a) Thêm **minOcc = ngưỡng occupancy / ngưỡng dưới của DO** ⇒ kích thước cửa sổ `W(f,minOcc)`; (b) `minSup = ∂ × N_eff` với `N_eff = min(TL, W)` — **2 giai đoạn**, hết tăng khi cửa sổ đầy; (c) DHO-List chỉ giữ transaction **trong cửa sổ**; (d) Cơ chế **handle 2 tầng `ref1 → ref2`** để evict O(1) mà không phải rà từng node; (e) Bound mới `UB'(X) = min(DUBO(X), Z(X))` + **short-circuit trần DO** `Z(f,TL)`; (f) **minOcc = 0 ⇒ V2 ≡ V1 ≡ paper**; (g) 3 → **4 phiên bản** (V1 paper/GoF · V2 minOcc · V3 tối ưu minOcc · V4 cực đoan minOcc); (h) Đặt tên tách bạch: `minOcc` = cửa sổ, `epsilon` (ε) = sai số so sánh; (i) Bổ sung bộ test TC9–TC18 (cửa sổ/minOcc) + bảng test tham số; (j) Tách `docs/DECISIONS.md` làm nơi chốt quyết định |

---

## 0. Tóm tắt thay đổi vì sao (đọc mục này trước)

### 0.1 Vấn đề được nêu trong `docs/Draft Idea.txt`

> *"Thuật toán có ngưỡng trên nhưng **không có ngưỡng dưới** dẫn đến hệ quả là phải đọc toàn bộ dataset mà nhiều giá trị DO của các transaction không có đóng góp, vì hệ số suy giảm f quá nhỏ làm DO nhỏ theo."*

Vấn đề này **thay đổi nền móng**, vì nó biến bài toán từ *"lưu và duyệt toàn bộ lịch sử stream"* thành *"duy trì một **cửa sổ trượt** có kích thước hữu hạn, đủ để tính DO chính xác trong sai số minOcc"*.

### 0.2 Hệ quả trực tiếp

| Hệ quả | Trước (bám sát paper) | Sau (có minOcc) |
|---|---|---|
| Kích thước dữ liệu phải giữ | O(N × avgLen) — **toàn bộ lịch sử** | O(W × avgLen) — **chỉ cửa sổ** |
| `minSup` | `∂ × TL` → tăng vô hạn theo độ dài stream | `∂ × min(TL, W)` → **đóng băng** khi cửa sổ đầy |
| Số DOP khi stream dài ra | có xu hướng → 0 | ổn định hơn (minSup không tăng nữa) |
| Chi phí mining | tăng theo N | gần như **độc lập N** |
| Bộ nhớ đỉnh | tăng theo N | gần như **không đổi theo N** |
| Cơ chế xóa entry | không có | **handle 2 tầng `ref1 → ref2`**, evict O(1), phát hiện chết "miễn phí" trong lúc duyệt |

### 0.3 Điểm cần đặc biệt lưu ý (rủi ro thiết kế)

> **Vì `N_eff` phụ thuộc `minOcc`, nên `minOcc` thay đổi *tập DOP*, không chỉ thay đổi chi phí.**
> Vì vậy `minOcc` là **tham số ngữ nghĩa** (cùng họ với `∂` và `f`), phải khai báo cùng `∂`, `f` trong **mọi** test, golden và benchmark. Mọi so sánh kết quả giữa các phiên bản phải dùng **cùng bộ `(∂, f, minOcc)`**.
> Trạng thái "bám sát paper" được bảo toàn bằng quy tắc: **`minOcc = 0 ⇒ W = ∞ ⇒ V2 ≡ V1 ≡ paper`** (xem INV-I).

### 0.4 Quy ước tên ký hiệu

| Ký hiệu | Tên | Ghi chú |
|---|---|---|
| `∂` | tỉ lệ ngưỡng | `partial` trong code/CLI |
| `f` | hệ số suy giảm | `decayFactor` |
| `minOcc` | **ngưỡng occupancy / ngưỡng dưới của DO (MỚI)** | `minOcc` trong code/CLI |
| `ε` | sai số so sánh ngưỡng (C4) | `epsilon` (1e-9) |
| `W` | **kích thước cửa sổ** | *Trong `Draft Idea.txt` gọi là `Z`. Tài liệu này dùng `W` để tránh trùng với `Z(f,TL)` bên dưới.* |
| `N_eff` | DB hiệu dụng = số transaction trong cửa sổ | *(`Draft Idea.txt` gọi là `winSup`)* |
| `Z(f,TL)` | **khối lượng suy giảm** = tổng trọng số decay của cả stream | `Σ_{k=0..TL-1} f^k` — cũng là **trần của mọi DO** |

---

## 1. Mục đích & Phạm vi

### 1.1 Mục đích

Xây **một hệ thống gồm 4 project engine** (4 phiên bản của cùng họ thuật toán) + benchmark harness + (giai đoạn cuối) debug/compare app. Tăng dần mức tối ưu, **mỗi phiên bản trả lời một câu hỏi khác nhau**:

| # | Bí danh | Module | Định vị | Câu hỏi phiên bản này trả lời | Mục tiêu cốt lõi |
|---|---|---|---|---|---|
| **V1** | **Standard** | `dhopm-v1-standard` | **Oracle** — bám sát paper, GoF | *"Thuật toán paper chạy đúng không?"* | Đúng theo canonical (minOcc≡0); qua TC1–TC8; threading Level 1; **đóng băng** làm mốc |
| **V2** | **MinOcc / Window** | `dhopm-v2-minocc` | **Semantics mới** | *"Cửa sổ suy giảm cho ta được gì, mất gì?"* | Thêm minOcc + cửa sổ + handle + minSup 2 pha; **minOcc=0 phải ≡ V1**; đo độ lỗi ≤ minOcc và mức tiết kiệm |
| **V3** | **Optimized** | `dhopm-v3-optimized` | **Kỹ thuật** | *"Với cùng ngữ nghĩa, cấu trúc dữ liệu nào nhanh hơn?"* | Level 2 threading; SoA/primitive; bỏ xích handle; decay lookup; benchmark đầy đủ vs V1/V2 |
| **V4** | **Extreme** | `dhopm-v4-extreme` | **Trần hiệu năng** | *"Có thể nhanh hơn bao nhiêu nữa?"* | Data-oriented thuần; pipeline 3 pha; memory-mapped I/O; **chỉ giữi thứ đo được lợi** |

> **Ưu tiên (chốt theo tác giả): V1 (đã có) → V2 → V3 → V4.**

### 1.2 Phạm vi

- **Bao trùm:** chuẩn hóa thuật toán **có cửa sổ minOcc**, mô hình threading, cấu trúc dự án (4 engine + harness + CLI Manager + app), định dạng đầu vào, bộ test, benchmark, lộ trình, rủi ro, cấu trúc tài liệu.
- **Không bao gồm:** thiết kế class chi tiết (ở plan 01–04), chi tiết SRS (ở `docs/srs/`), thiết kế chi tiết G4/G6 (lập plan khi tới giai đoạn).

### 1.3 Quan hệ với tài liệu cũ

- **2 tài liệu trong `docs/root/` là mốc đúng đắn duy nhất cho thuật toán gốc** (công thức, thứ tự xử lý, TC1–TC8).
- Toàn bộ tài liệu draft cũ đã ở branch `archive/legacy-draft`, **KHÔNG là mốc**.
- `docs/Draft Idea.txt` là **nguồn ý tưởng minOcc** — được chuẩn hoá vào mục 2 của tài liệu này; các quyết định suy ra được ghi vào `docs/DECISIONS.md`.

---

## 2. Chuẩn hóa Thuật toán (Canonical Spec – bắt buộc cho cả 4 phiên bản)

> Mục đích: định nghĩa **một** cách chạy duy nhất để 4 phiên bản cho kết quả so sánh được. Mọi triển khai phải tuân theo; nếu có khác biệt, quy định ở đây thắng.

### 2.1 Tham số đầu vào

| Ký hiệu | Ý nghĩa | Miền | Mặc định | Ghi chú |
|---|---|---|---|---|
| `∂` (`partial`) | Tỉ lệ ngưỡng **trên DB hiệu dụng** | `∂ ∈ [0,1]` | `0.15` | Đơn vị của nó bây giờ là **số transaction trong cửa sổ**, không phải cả dataset |
| `f` (`decayFactor`) | Hệ số suy giảm | `f ∈ (0,1]` | `0.9` | `f = 1` → không suy giảm ⇒ **cửa sổ là ∞** ⇒ thuật toán thành HOP truyền thống |
| `minOcc` (`minOcc`) | **Ngưỡng occupancy / ngưỡng dưới của DO** | `minOcc ∈ [0, 1)` | V1: `0`<br/>V2+: `1e-6` | Xác định kích thước cửa sổ `W`. **`minOcc = 0` ⇒ không cửa sổ ⇒ ≡ paper**. Xem ghi chú bên dưới về *mặc định* |
| `ε` (`epsilon`) | Sai số so sánh ngưỡng (quyết định cũ C4) | `> 0` | `1e-9` | Sai số **kỹ thuật** thuần, tách khỏi tham số ngữ nghĩa `minOcc` |
| `W` | Kích thước cửa sổ | **derived** | auto | `W = W(f, minOcc)` — mục 2.3. Cho phép override (`windowOverride`) chỉ để nghiên cứu (xem cảnh báo bên dưới) |
| `N_eff` | DB hiệu dụng | **derived** | `min(TL, W)` | mục 2.4 |

**Quy tắc biên (canonical):**
- Transaction rỗng / độ dài 0 → **loại từ khi nhập**, không tính vào tổng số transaction.
- Item trùng trong 1 transaction → chuẩn hoá về tập phân biệt.
- `TID` không tăng dần → **báo lỗi ngay (fail-fast, INV-A)**.
- `∂ ∉ [0,1]`, `f ∉ (0,1]`, `minOcc < 0`, `minOcc ≥ 1`, `ε ≤ 0` → **báo lỗi khi cấu hình**.
- **`minOcc ≥ 1/(1−f)` (với `f<1`) ⇒ `W = 0` ⇒ cửa sổ rỗng ⇒ báo lỗi cấu hình** (xem 2.6 – V3).
- **`∂ = 0` ⇒ `minSup = 0` ⇒ mọi pattern đều là DOP** → cho phép nhưng **cảnh báo** (output có thể bùng nổ).
- `W` vượt `maxWindow` (mặc định `10^7`) → **báo lỗi cấu hình** (tránh cấp phát không thể thiện thực).

> ⚠️ **Chính sách "mặc định" của `minOcc` — phải hiểu đúng (C12 + D28).** Có **hai tầng khác nhau**, không mâu thuẫn:
>
> | Tầng | Ai đặt | Quy tắc |
> |---|---|---|
> | **Thư viện / lập trình viên** | `MiningConfig` | Có giá trị mặc định: V1 = `0`, V2+ = `1e-6`. Gọi API mà không truyền ⇒ dùng mặc định **của phiên bản đó**. |
> | **Giao thức Manager / app** | Người dùng, qua CLI `--minOcc` hoặc JSON request | **Bắt buộc khai báo tường minh.** Thiếu ⇒ **lỗi yêu cầu**, không tự đoán (D28). |
>
> Lý do tách hai tầng: mặc định của *thư viện* giữ tiện cho người viết test; còn *giao thức* không được im lặng vì `minOcc` thay đổi **tập DOP** (C12) ⇒ hai phiên chạy lưu kết quả khác nhau mà người dùng không biết. Ở mọi lần in kết quả/benchmark, **`minOcc` phải được in ra bắt buộc** kèm theo.

### 2.2 Pipeline chuẩn (4 giai đoạn — GĐ0 là mới)

```
GĐ0 – CỬA SỔ & EVICTION  (chỉ V2+; V1: bỏ trống, W = ∞)
   với mỗi transaction mới có TID = m (đơn luồng, theo INV-A):
     nếu (m − tid) ≥ W  →  KHÔNG ghi entry cho transaction này (rẻ nhất: chưa từng tồn tại)
     nếu (m − W) ≥ 1    →  evict(tid = m − W):  handle[that].tx = null      // O(1)

GĐ1 – Xây/Cập nhật Global DHO-List:  (one-scan, chỉ quét phần mới)
   với mỗi transaction T theo TID tăng dần:
     nếu T nằm trong cửa sổ (TL − T.tid < W):
       với mỗi item i ∈ T (theo thứ tự xuất hiện đầu tiên trong T):
         node = createOrGetNode(i)             // thứ tự tạo = lần xuất hiện đầu tiên
         node.addEntry( handle(i) )            // ref1 → ref2 (mục 2.7)
   TL = T.tid ; totalScanned++                 // totalScanned đếm MỌI tx, kể cả ngoài cửa sổ

GĐ2 – Reconstruction:
   với mỗi node:
     bỏ tiền tố entry đã chết (head offset, O(1) – xem 2.7)
     DO(node) = Σ entry sống (1/|T|) × f^(TL − tid)      // TUẦN TỰ theo entry (C5)
   sắp xếp node theo support SỐNG tăng dần, ổn định (tie-break = thứ tự tạo node)

GĐ3 – Mine (DFS pattern-growth):
   Mine(currentList, prefix):
     với i = 0..n-1 (node_i theo thứ tự hiện tại):
       nếu support_sống(node_i) < minSup → SKIP node (bỏ hẳn, C2)
       nếu DO(node_i) ≥ minSup − ε → thêm (prefix ∪ {item_i}) vào kết quả
       ubo = UB'(node_i) = min( DUBO(node_i), Z(node_i) )        // mục 2.5
       nếu ubo < minSup − ε → SKIP mở rộng (prune)
       ngược lại:
         ncl = ConditionalList mới
         với j = i+1..n-1:
           node = giao(node_i, node_j)              // two-pointer, TID chung, TID tăng dần
           nếu giao rỗng → bỏ qua
           ngược lại → thêm vào ncl, DO(node) = Σ (|prefix|+2)/|T| × f^(TL−tid)
         nếu ncl không rỗng → kết quả ∪= Mine(ncl, prefix ∪ {item_i})
```

> **Lưu ý GĐ0:** evict **chỉ** làm việc trên các transaction **đã từng được ghi entry**. Transaction nằm ngoài cửa sổ ngay từ đầu thì **không ghi** → không có gì để xóa. Đây là trường hợp rẻ nhất và xảy ra với dữ liệu nạp lần đầu với `TL ≫ W`.

### 2.3 Công thức chuẩn

| Ký hiệu | Công thức |
|---|---|
| Occupancy | `O(X, Td) = |X| / |Td|` |
| Decay factor | `dF(Td) = f^(TL − Td)` |
| Damped occupancy | `DO(X) = Σ O(X, Td) × f^(TL − Td)` trên các `Td` **còn sống** chứa X |
| DOP | `X ∈ DOPs ⇔ DO(X) ≥ minSup` |
| DUBO | `DUBO(X,k) = f^(TL − T_k) × Σ_{i≥k} n_i × (l_k / l_i)`; `DUBO(X) = max_k DUBO(X,k)` — nhóm `k` theo độ dài `|T|` tăng dần, `(n_k, T_k)`, **chỉ tính trên entry sống** |
| **Khối lượng suy giảm (toàn stream)** | `Z(f,TL) = Σ_{k=0..TL−1} f^k = (1 − f^TL)/(1 − f)`; `f=1 ⇒ Z = TL` |
| **Khối lượng suy giảm (một node)** | `Z(X) = Σ_{t ∈ T_sống(X)} f^(TL − t)` |
| **Kích thước cửa sổ** | `W(f,minOcc) = min{ W ≥ 0 : Σ_{k=W..TL−1} f^k ≤ minOcc }` |
| **DB hiệu dụng** | `N_eff(TL) = min(TL, W)` |
| **minSup (2 giai đoạn)** | `minSup(TL) = ∂ × N_eff(TL)` |

#### 2.3.1 Công thức kích thước cửa sổ (đóng)

Với `0 < f < 1` và `0 < minOcc < 1/(1−f)`:

```
Σ_{k=W}^{TL−1} f^k ≤ Σ_{k=W}^{∞} f^k = f^W / (1 − f) ≤ minOcc
   ⟺  f^W ≤ minOcc(1 − f)
   ⟺  W ≥ ln( minOcc(1−f) ) / ln f
   ⟹  W(f, minOcc) = ⌈ ln( minOcc(1−f) ) / ln f ⌉
```

| Trường hợp | `W` | Nghĩa |
|---|---|---|
| `minOcc = 0` | **∞** | Không chấp nhận sai số ⇒ **≡ paper** (không cửa sổ) |
| `f = 1` | **∞** | Không suy giảm ⇒ mọi transaction đều có giá trị ⇒ **≡ HOP** (TC5) |
| `0 < minOcc < 1/(1−f)` | `⌈ln(minOcc(1−f))/ln f⌉` | Công thức chính |
| `minOcc ≥ 1/(1−f)` | **0** | Cửa sổ rỗng ⇒ **lỗi cấu hình** |
| `f` → 0 | nhỏ (≈ 3–7) | Suy giảm cực mạnh ⇒ cửa sổ rất ngắn |

**Bảng tra cứu nhanh `W` (đã tính sẵn, dùng cho test):**

| f \ minOcc | 1e-3 | 1e-6 | 1e-9 | 1e-12 |
|---|---|---|---|---|
| 0.5 | 11 | 21 | 31 | 41 |
| 0.8 | 39 | 70 | 101 | 132 |
| **0.9** | **88** | **153** | **219** | **285** |
| 0.95 | 194 | 328 | 463 | 598 |
| 0.99 | 1 146 | 1 833 | 2 521 | 3 208 |
| 0.999 | 13 809 | 20 713 | 27 618 | 34 522 |

**Xấp xỉ cần nhớ:** `W ≈ ln(1/minOcc) / ln(1/f)` (bỏ yếu tố `(1−f)`) — dùng để ước lượng nhanh, **không dùng để tính**.

### 2.4 minSup hai giai đoạn (quyết định cốt lõi của `docs/Draft Idea.txt`)

```
N_eff(TL) = min(TL, W)
minSup(TL) = ∂ × N_eff(TL)
```

| Giai đoạn | Điều kiện | `minSup` | Hành vi |
|---|---|---|---|
| **Pha 1 — cửa sổ chưa đầy** | `TL < W` | `∂ × TL` | tăng dần theo TL, **giống hệt paper** |
| **Pha 2 — cửa sổ đầy** | `TL ≥ W` | `∂ × W` | **đóng băng**, không tăng nữa |
| **Không cửa sổ** | `W = ∞` | `∂ × TL` | ≡ paper |

**Vì sao cần:** nếu giữ `minSup = ∂ × TL` sau khi đã cắt cửa sổ, ngưỡng sẽ tiếp tục tăng theo độ dài stream trong khi dữ liệu chỉ còn `W` transaction ⇒ số pattern **tuyến tính → 0** ⇒ thuật toán "chết dần" dù dữ liệu vẫn còn giá trị. Ngưỡng phải **đo trên cùng đơn vị với dữ liệu đang dùng** — đó là `N_eff`.

### 2.5 Trần DO & bound chặt hơn (bổ sung)

Vì `X ⊆ Td` nên `|X| / |Td| ≤ 1` **luôn**, suy ra:

```
DO(X) ≤ Σ_{t ∈ T(X)} f^(TL−t) ≤ Z(f, TL)
```

**(a) Trần toàn cục (validator + short-circuit).** Nếu `minSup > Z(f,TL)` thì tập DOP **chắc chắn rỗng**:
- **short-circuit O(1)**, không duyệt cây DFS.
- Báo cho người dùng: *"`∂ = … , f = … ⇒ minSup = … > Z = … ; không thể có DOP. Giảm ∂ hoặc tăng f."*
- Miền `∂` khả thi **chính xác** (hữu hạn, dùng cho validator): **`∂ ≤ Z(f,TL) / N_eff`** — suy ra trực tiếp từ `minSup = ∂ × N_eff ≤ Z(f,TL)`.
  - Với `TL ≫ W` thì `Z(f,TL) → 1/(1−f)` ⇒ **xấp xỉ dùng để ước lượng**: `∂ ≲ 1 / ((1−f) · W)` (bảng ở mục 6.4).
  - ⚠️ **Phân biệt bắt buộc (D38)**: `1/((1−f)·W)` là **giá trị ở giới hạn `TL → ∞`**, tức tại **ranh giới pha 2**; nó **luôn ≤ giá trị chính xác** (⇒ dùng nhầm để chặn tham số sẽ **chặn oan**). Ví dụ `f=0.9, minOcc=1e-6, TL=4`: xấp xỉ 6.54 %, chính xác `Z(0.9,4)/4 = 3.439/4 = 85.98 %` — lệch **×13**. ⇒ Validator **luôn dùng công thức chính xác `Z(f,TL)/N_eff`**; bảng miền khả thi ở mục 6.4 dùng xấp xỉ và **đã ghi nhãn**.

**(b) Bound theo node (chặt hơn DUBO).** Với mọi superset `Y ⊇ X`: `T(Y) ⊆ T(X)` và `|Y| ≤ |Td|`, nên

```
DO(Y) ≤ Σ_{t ∈ T(X)} f^(TL − t) = Z(X)
   ⟹  UB'(X) = min( DUBO(X), Z(X) )     thay cho prune bằng DUBO(X) đơn thuần
```

- `Z(X)` tính **cùng một vòng duyệt entry** như DUBO ⇒ gần như miễn phí.
- `Z(X)` là **bound chặt nhất trong họ "khối lượng suy giảm"** ⇒ `min(DUBO, Z(X)) ≤ DUBO` luôn ⇒ **chỉ prune thêm, không bao giờ prune sai**.
- Đây là câu trả lời trực tiếp cho hạn chế *"cần kỹ thuật prune tốt hơn"* mà paper tự nêu.

### 2.6 Trần theo từng pattern (để giải thích trong app)

Với một pattern cụ thể X, trần khả thi là `cap(X) = (|X| / l_min(X)) × Z(X)` với `l_min(X)` = độ dài nhỏ nhất trong các transaction **còn sống** chứa X. Dùng để **giải thích** trong app: *"pattern này không thể nào đạt ngưỡng vì DO tối đa của nó chỉ là 0.43, còn minSup = 1.2"* — không dùng để prune (không suy ra được trên tập con chưa biết).

### 2.7 Cơ chế evict: handle hai tầng `ref1 → ref2`

**Bài toán:** khi transaction `Ti` rời khỏi cửa sổ, *mọi* node chứa entry của `Ti` đều phải bỏ entry đó. Rà từng node để tìm entry mang `Ti` tốn `O(|I|)`.

**Giải pháp (từ `docs/Draft Idea.txt`):**

```
Entry của Node (ref1)  ──►  Handle (ref2)  ──►  Transaction { tid, len }
                              (nằm trong WindowBuffer, vòng tròn, W slot)
```

- **Evict:** `handle = window.get(tid); handle.tx = null;` → **O(1)**, không cần biết node nào chứa nó.
- **Phát hiện:** khi một node duyệt entry vốn đã phải duyệt (GĐ2 reconstruction, DUBO, GĐ3 two-pointer), nó đọc `ref1.tx` → thấy `null` ⇒ biết entry chết ⇒ **tự loại khỏi mình**. ⇒ **không tốn phí truy vấn riêng; chi phí được "ẩn" vào pha đang chạy**.
- **Lý do phải có hai tầng (định nghĩa chuẩn):** *truy vấn "sống hay chết" không được dereference một tham chiếu có thể đã bị vô hiệu hóa.* Với một tầng, `entry.tx = null` rồi đọc `entry.tx.cột` là dereference con trỏ/đối tượng chết — **không an toàn giữa các ngôn ngữ** (C/C++: hành vi không xác định / segfault; `try/catch` không cứu được). Với hai tầng, `ref1` **luôn trỏ tới một Handle còn sống** (identity bất biến, chỉ payload bị clear) ⇒ đọc `handle.tx == null` **luôn an toàn**. `ref2` chính là **handle**.
- **Bất biến kèm theo:** entry được append theo TID tăng dần (INV-B) và bị evict theo TID tăng dần ⇒ **các entry chết luôn tạo thành một tiền tố** ⇒ compaction bằng **một chỉ số `head`**, **O(1) amortized**, không phải dịch mảng.
  - `support(X) = size(X) − head(X)` ⇒ tính support **O(1)**.
- **Chi phí:** evict `O(1)`; phát hiện `O(1)` mỗi entry (nhân với số pha duyệt entry); bộ nhớ `O(W × avgLen) + O(|I|)`.

> ⚠️ **Bẫy tái sử dụng slot — bắt buộc phải xử lý (phát hiện khi rà soát 2.7).**
>
> Vòng tròn `tid mod W` **tái sử dụng đúng object Handle cũ** cho transaction mới. Nếu một entry của node vẫn giữ `ref1` trỏ tới Handle đó (node chưa duyệt tới ⇒ chưa dọn), thì sau khi slot bị ghi đè:
>
> ```
>   entry.ref1 → handle                    // CÙNG object, đã bị tái sử dụng
>   handle.tx  = transaction MỚI (tid = m) // ≠ null ⇒ node TƯỞNG entry cũ còn sống!
> ```
>
> ⇒ Node đọc ra **sai transaction**, `tid` sai, `DO` sai, `|X|/|T|` sai — lỗi **âm thầm, không crash**, cực khó phát hiện.
>
> **Cách sửa bắt buộc (chọn 1):**
>
> | # | Cách | Nhận xét |
> |---|---|---|
> | **1 (đã chọn)** | **Entry lưu thêm `tid`**: `record Entry(Handle ref1, int ref2, long tid)`; khi duyệt, coi là sống **chỉ khi** `handle != null && handle.tid == entry.tid`. | Sửa tận gốc, rẻ, **không đổi cấu trúc `WindowBuffer`**. Giữ được identity Handle cho C11. |
> | 2 | Generation counter: `handle.generation++` mỗi lần tái sử dụng; entry lưu generation đã thấy. | Nặng hơn (thêm số + phép so sánh). |
> | 3 | Không tái sử dụng slot — cấp Handle mới mỗi tx. | Phá vỡ `O(1)` / bộ nhớ `O(W)` ⇒ **loại**. |
>
> ⇒ Bổ sung bất biến: **"đọc handle luôn phải kiểm `tid` khớp"** — nằm trong INV-H.

**Vị trí evict (bắt buộc, để giữ INV-E):** evict chạy **đơn luồng**, trong `loadBatch` và **trước** mọi pha song song của `mineNow`. Việc *phát hiện* (dời `head`) xảy ra trong pha song song nhưng **mỗi node tự dời head của chính nó** ⇒ không có phụ thuộc thứ tự giữa các node ⇒ deterministic.

### 2.8 Quyết định chuẩn (C1–C12)

| # | Điểm mơ hồ | Quyết định chuẩn | Căn cứ |
|---|---|---|---|
| C1 | Hệ số suy giảm trong DUBO | **Một** decay factor `f^(TL − T_k)` của nhóm k cho **toàn bộ tổng** | Paper mục 3.6 + chạy tay; `DUBO(CD)=1.6402`, `DUBO(CDE)=1.181` ở TL=8, f=0.9 |
| C2 | `support < minSup` trong Mine | **Bỏ hẳn node** (không đánh giá DOP, không mở rộng) | Pseudocode + chạy tay (G bị bỏ). **Với cửa sổ: dùng support SỐNG — vẫn đúng, vì `DO(X) ≤ Z(X) ≤ support_sống(X)`** |
| C3 | Tie-break khi support bằng nhau | Sắp xếp **ổn định** theo support tăng dần, giữ thứ tự **tạo node** | Chạy tay phần 6.2, 8.2 |
| C4 | Độ chính xác số thực | `double`; so sánh ngưỡng dùng `≥ minSup − ε`, `ε = 1e-9`; chỉ làm tròn 4 chữ số khi hiển thị/test | Bảng TC |
| C5 | Thứ tự cộng dồn DO/DUBO | Một node luôn cộng **tuần tự trên một thread**; DO theo entry TID tăng dần; DUBO theo nhóm độ dài tăng dần. **Không chia phép cộng của một node cho nhiều thread** | Đảm bảo bit-for-bit giữa các phiên bản |
| C6 | Bản sắc pattern | Pattern = **tập item**; hiển thị/so sánh theo thứ tự item xác định | Chạy tay (AEG/GAE) |
| **C7** | **Ý nghĩa của minOcc** | minOcc là **ngưỡng dưới của DO**: mọi transaction có đóng góp tổng `< minOcc` bị coi là **không có giá trị** và không được lưu | `Draft Idea.txt` |
| **C8** | **Công thức cửa sổ** | `W = ⌈ln(minOcc(1−f))/ln f⌉`; `minOcc=0` hoặc `f=1` ⇒ `W = ∞`; `minOcc ≥ 1/(1−f)` ⇒ lỗi cấu hình | Mục 2.3.1 |
| **C9** | **Đơn vị của minSup** | `minSup = ∂ × N_eff`, `N_eff = min(TL, W)` — **2 giai đoạn** (mục 2.4). Không dùng `∂ × TL` khi đã cắt cửa sổ | `Draft Idea.txt` |
| **C10** | **Evict** | Chỉ evict transaction **đã từng được ghi entry**; transaction ngoài cửa sổ ngay từ đầu thì **không ghi**; evict **đơn luồng, trước mọi pha song song** | Mục 2.2 (GĐ0) |
| **C11** | **Cơ chế đồng bộ node** | Handle 2 tầng `ref1 → ref2`; **entry lưu kèm `tid` và bắt buộc kiểm `tid` khớp** khi đọc (chống tái sử dụng slot, mục 2.7); entry chết là **tiền tố** ⇒ compaction bằng `head`; support = `size − head` | Mục 2.7 |
| **C12** | **Danh tính kết quả** | Tập DOP được xác định bởi bộ tham số **`(∂, f, minOcc, TL)`**. So sánh kết quả giữa các phiên bản/phiên chạy **bắt buộc dùng cùng bộ tham số này**. Nếu dùng `windowOverride` (chỉ để nghiên cứu) thì **`W` thực tế cũng thuộc danh tính** | Suy ra từ C9 + 2.3 |

### 2.9 Bất biến toàn cục

| # | Bất biến |
|---|---|
| **INV-A** | TID tăng dần nghiêm ngặt trên toàn stream |
| **INV-B** | Entry của một node luôn được thêm theo TID tăng dần (append-only) |
| **INV-C** | DO chỉ có nghĩa tại đúng `(TL, f, minOcc)` đang dùng; đổi dữ liệu/tham số ⇒ phải reconstruct lại |
| **INV-D** | Conditional list **không được sắp xếp lại** sau khi dựng (giữ thứ tự kết hợp từ list cha) |
| **INV-E** | Kết quả 4 phiên bản phải cho cùng tập DOP khi cùng tham số (đối chiếu C4–C6, C12) |
| **INV-F** | `minSup` được tính tại **thời điểm `mineNow`**, không phải lúc `loadBatch` |
| **INV-G** | **Sai số cửa sổ:** với mọi pattern X, `DO_win(X) ≥ DO_full(X) − minOcc` và `|DO_win(X) − DO_full(X)| ≤ minOcc` (với minOcc > 0). Khi `minOcc = 0` thì bất biến này thành **đẳng thức** |
| **INV-H** | **Không bao giờ dereference tham chiếu đã vô hiệu hóa.** Mọi kiểm tra sống/chết phải đi qua Handle (`ref1 → ref2` → đọc `ref2.tx`) **và kiểm `ref2.tid == entry.tid`** — cần mệnh đề thứ hai vì slot vòng tròn bị tái sử dụng (mục 2.7). |
| **INV-I** | **Tương đương paper:** với `minOcc = 0` (hoặc `W ≥ TL`), **mọi phiên bản V2/V3/V4 phải cho kết quả bit-for-bit giống V1**. Đây là test hồi quy bắt buộc của G2/G3/G4 |
| **INV-J** | **Monotonicity của support trong cửa sổ:** `support_sống(X)` không giảm khi stream nối tiếp **nếu không có transaction nào rời cửa sổ**; khi có, support có thể giảm — đây là hành vi **đúng** và C2 vẫn hợp lệ (vì `DO ≤ Z(X) ≤ support_sống`) |

---

## 3. Khung Threading dùng chung

Cả 4 phiên bản **đều phải** dùng Thread + worker. Khung chung định nghĩa 4 mức:

| Mức | Phiên bản | Đặc điểm khung |
|---|---|---|
| **Level 1** | V1, **V2** | `ExecutorService` fixed pool, `Runtime.availableProcessors()`. Reconstruction song song theo từng node; Mining song song theo từng **cây con gốc**; Construction **đơn luồng** (giữ INV-B). Gộp kết quả **sau join**. Evict đơn luồng trước mọi pha. |
| **Level 2** | V3 | `ForkJoinPool` (work-stealing) + phân tải động. Construction song song theo batch (ráp lại theo TID, giữ INV-B); chia cây con **sâu hơn** theo ngưỡng kích thước tối thiểu; tái dùng pool giữa các giai đoạn. |
| **Level 3** | V4 | Pipeline data-oriented 3 pha xen kẽ, worker chuyên trách theo giai đoạn, sink lock-free. |
| **Level 1** | `dhopm-bench` | Benchmark chạy tuần tự có điều khiển, không tối ưu tự thân. |

**Nguyên tắc chung:**
- Luôn tuân INV-E / C5: chỉ chia các **đơn vị công việc độc lập**; ranh giới là *node* (Reconstruction), *cây con* (Mining), *nhóm batch* (Construction).
- Số worker mặc định = số lõi; cấu hình được.
- **Thread/worker pool là util dùng chung** trong `dhopm-common`, cả 4 module tái dùng.
- **Logging & benchmark độc lập với thuật toán:** đo/ghi nằm **ngoài hot path** (qua wrapper/decorator ở tầng contract). Tắt log ⇒ gần như zero-overhead.

---

## 4. Cấu trúc Dự án

```
implementation/
├── pom.xml                     # parent (Java 25)
├── dhopm-common/               # DÙNG CHUNG, KHÔNG chứa logic thuật toán version nào
│   ├── src/main/java/…          #   io: TransactionSource, FIMI reader, text reader, (utility reader)
│   │                            #   config: MiningConfig (∂, f, minOcc, ε, workers, windowOverride)
│   │                            #   contract: Engine, PhaseAwareEngine, ProgressAwareEngine,
│   │                            #            TimedEngine, WindowInfo, Pattern, MineResult
│   │                            #   util: WorkerPool, Log, TimingRecorder
│   │                            #   window: WindowMath (công thức W), ParameterValidator  ← MỚI
│   └── src/test/java/…          #   TestKit: golden TC1–TC8 (minOcc=0) + TC9–TC18 (minOcc>0), WindowMathTest
├── dhopm-v1-standard/          # V1 — Oracle, bám sát paper (minOcc≡0), GoF, Level 1
├── dhopm-v2-minocc/           # V2 — Cửa sổ minOcc + handle ref1/ref2 + minSup 2 pha   ← MỚI
├── dhopm-v3-optimized/         # V3 — Tối ưu khi đã có minOcc
├── dhopm-v4-extreme/           # V4 — Tối ưu cực đoan khi đã có minOcc (tạo khi bắt đầu G4)
├── dhopm-bench/                # Harness đo runtime/memory/throughput + **bảng ablation V1..V4**
├── dhopm-cli/                  # Manager CLI/API duy nhất (mục 4.3) — tạo ở G5
└── dhopm-app/                  # Module UI riêng — tạo ở G6
```

**Nguyên tắc (giữ nguyên + bổ sung):**
- `dhopm-common` chia sẻ **đầu vào, util chung và hạ tầng đo lường** — **không** chia sẻ logic thuật toán.
- **Công thức cửa sổ và validator tham số nằm ở `dhopm-common`** (`dhopm.common.window.WindowMath`) vì đó là **định nghĩa dùng chung**, không phải tối ưu của version nào. Nhờ vậy V1 cũng dùng được để **in cảnh báo** mà không thay đổi hành vi.
- 4 thuật toán là **4 module**; `dhopm-app` tách biệt để tránh làm chậm/freeze UI khi mining.

### 4.1 Cấu trúc Tài liệu & Workflow

| Tầng | Tài liệu | Vị trí | Trạng thái |
|---|---|---|---|
| **Quyết định** | **Sổ quyết định (ADR log)** — nơi chốt & ghi lý do | `docs/DECISIONS.md` | ✅ **MỚI** |
| Chiến lược (plan) | Kế hoạch tổng thể (canonical + roadmap + workflow) | `docs/plans/00-OVERALL-PLAN.md` | ✅ v2.0 |
| Plan từng phiên bản | V1 / V2 / V3 / V4 | `docs/plans/01…04-*` | ✅ |
| Plan giao tiếp Frontend↔Backend | Manager CLI/API + đặc tả giao thức | `docs/plans/05-BACKEND-CLIAPI-PLAN.md` | 🕔 Draft |
| Phân tích draft backend | Phân tích `Draft backend CLIAPI.txt` | `docs/plans/06-BACKEND-CLIAPI-DRAFT-ANALYSIS.md` | 🕔 Draft |
| Ý tưởng gốc | Bản nháp tác giả về minOcc | `docs/Draft Idea.txt` | ✅ giữ nguyên làm mốc ý tưởng |
| Yêu cầu tổng | **SRS tổng thể** (4 engine + harness + app) | `docs/srs/DHOPM-SRS.md` | ✅ |
| Thiết kế trình bày | **UI Layout** | `docs/srs/DHOPM-UI-LAYOUT.md` | ✅ |
| Tài liệu mốc | Paper + ví dụ chạy tay | `docs/root/` | ✅ |
| Plan + tài liệu từng giai đoạn | Plan chi tiết G0…G6 + tài liệu ra | `docs/phases/` | 🕔 tạo dần |
| Tài liệu riêng từng module engine | README + design từng thành phần/hàm + test plan + benchmark report | Trong `implementation/dhopm-vX/` | 🕔 tạo khi chạy giai đoạn |

**Quy tắc workflow (bổ sung):**
1. Mỗi giai đoạn (G0→G6) bắt đầu bằng **plan riêng** (`docs/phases/P<i>-G<i>.md`) rồi mới code.
2. Mỗi giai đoạn sản xuất **tài liệu cấu trúc & design từng thành phần/hàm** trong module — nội dung nghĩ ra lúc làm, không cố định trước. V2/V3/V4 kèm **lý do chọn tối ưu + so với phiên bản trước**.
3. **Thiết kế theo giai đoạn:** V1 áp dụng **GoF**; V2 giữ GoF ở mức hợp lý (tập trung **đúng ngữ nghĩa cửa sổ**, chưa tối ưu); V3 áp dụng cấu trúc tốt hơn GoF nếu có chứng cứ; V4 không cần pattern.
4. **Mọi quyết định mới phải ghi vào `docs/DECISIONS.md`** (mã `D<n>`), không quyết lại trong từng plan.
5. **Giao tiếp gỡ lỗi = API, không truy cập nội bộ** (4.2).

### 4.2 Giao tiếp giữa công cụ và module thuật toán (CLI / API)

| # | Nguyên tắc |
|---|---|
| P1 | Công cụ (CLI, `dhopm-bench`, app) **không bao giờ** truy cập bên trong module thuật toán. Mọi thao tác qua **API ổn định** trong `dhopm-common.contract` + util chung |
| P2 | API mở: `Engine` (`loadBatch`/`mineNow`), `PhaseAwareEngine`+`PhaseListener`, `ProgressAwareEngine`+`MiningProgressListener`, `TimedEngine`, `WindowAwareEngine`+`WindowListener` (mới: báo kích thước cửa sổ, số entry sống/chết, số lần evict) |
| P3 | Tắt listener/log → **zero/rất thấp overhead** |
| P4 | CLI là **một dạng client của API**, cùng bộ lệnh cho cả 4 version |
| P5 | Bộ lệnh chuẩn hoá: `mine` · `detail` · `stream` · `golden` · `inspect` · **`window`** (mới) · **`sweep`** (mới) · **`validate`** (mới) |
| P6 | Gộp nhiều nguồn xuất; không trộn vào hot path |

**Lệnh mới (chi tiết trong plan 02, mục 7):**

| Lệnh | Mục đích |
|---|---|
| `window` | In bảng tra cứu `W(f,minOcc)`, `N_eff`, `minSup`, trần `Z(f,TL)`, miền `∂` khả thi — **không cần dataset** (trả lời câu hỏi "với minOcc,∂,f này thì ra cái gì?") |
| `validate` | Kiểm tra cấu hình + báo cảnh báo (minOcc quá lớn, ∂ vô khả thi, W vượt `maxWindow`) **trước khi** chạy |
| `sweep` | Quét nhiều tổ hợp `(minOcc, ∂, f)` → bảng kết quả (số DOP, runtime, memory, độ dài TB) |

### 4.3 Giao tiếp Frontend ↔ Backend (Manager CLI/API)

> **Động cơ (phân công 2 người):** backend = thuật toán 4 version + CLI/API (Sơn), frontend = JavaFX Visualizer (Tâm, repo riêng `2312441-sudo/javanc`).

| # | Nguyên tắc |
|---|---|
| F1 | Frontend **không gọi trực tiếp engine**, không Maven dependency, không `import dhopm.*` |
| F2 | Giao việc/lấy kết quả **chỉ qua 1 Manager CLI/API duy nhất** của backend. Hợp đồng = **đặc tả giao thức**, không share class Java |
| F3 | Manager tách yêu cầu con theo version, điều phối, đồng bộ, tổng hợp theo bộ lệnh chuẩn |
| F4 | Quản lý tài nguyên = **plan riêng** (D8): `ResourceManager → native API → WindowsModule/MacModule` |
| F5 | Manager là **client của API mở `dhopm-common`**; hot path thuật toán không đổi |
| F6 | Kết quả trả về theo **đúng bộ lệnh chuẩn P5** + cấu trúc `Pattern`/`MineResult` của `dhopm-common` |

**Ranh giới đã làm:** CLI 5 lệnh + `--limit` + `ProgressAwareEngine`.
**Chưa làm:** `--json`/`serve`, registry, `JobManager`, `SessionManager`, `ResultAggregator`, contract cho frontend → theo lộ trình MA của plan 05.

---

## 5. Dữ liệu Đầu vào & Định dạng

### 5.1 Định dạng chuẩn hoá

| Kiểu | Định dạng | Mục đích |
|---|---|---|
| **Text (TID tường minh)** | `<TID> <item1> <item2> …` | TC1–TC18 (viết inline trong test) |
| **FIMI** | `<item1> <item2> …`; TID = số thứ tự dòng (1-based) | Benchmark với dataset chuẩn |
| **ZIP** | `.zip` chứa **đúng 1** file dataset | Tiếp kiệm dữ liệu; reader giải nén trực tiếp, không ghi tạm ra đĩa |
| **Utility-FIMI** | `<items…> : <utility> : <quantities…>` | **Chưa triển khai** — chưa có reader, chưa có dữ liệu (xem D42) |

- Dòng trống hoặc bắt đầu `#` → bỏ qua.
- Batch (DB) trong mô phỏng stream = một **khối transaction liên tục**.

> 📦 **Chính sách extension của `--dataset`** (thi hành bởi `DatasetFile.requireSupported`):
>
> | Extension | Cách mở |
> |---|---|
> | `.dat` `.txt` `.text` `.csv` `.tsv` | Text thuần |
> | `.zip` | Giải nén entry dataset, yêu cầu **đúng 1 entry** |
> | `.rar` `.7z` | **Từ chối** — chỉ hỗ trợ `.zip` (JDK đọc native, portable) |
> | *(không có)* | **Từ chối** — đường dẫn bắt buộc có extension |
>
> So khớp extension không phân biệt hoa/thường (`.DAT` = `.dat`).

### 5.2 Dataset

**Nghiệm thu đúng đắn:** chỉ dùng TC1–TC18 (dữ liệu inline, không cần file).

**Benchmark** — 10 dataset FIMI chuẩn (đã validate ở G0):

| Dataset | Dòng (trans) | Items | Avg len | Loại | Gợi ý ∂ (thang cũ) |
|---|---|---|---|---|---|
| `accidents.dat` | 340 183 | 468 | 33.81 | **Dataset của paper [1]** | 3 % |
| `chainstore.dat` | 1 112 949 | 46 086 | 7.23 | **Sparse, lớn nhất** | 0.05 % |
| `chess.dat` | 3 196 | 75 | 37.0 | Dense | 35 % |
| `connect.dat` | 67 557 | 129 | 43.0 | Dense | 30 % |
| `kosarak.dat` | 990 002 | 41 270 | 8.1 | Sparse | 0.05 % |
| `mushroom.dat` | 8 124 | 119 | 23.0 | Dense (khớp paper) | 6 % |
| `newMushroom.dat` | 8 416 | 119 | 23.0 | **≠ `mushroom.dat`** — biến thể khác | 6 % |
| `pumsb.dat` | 49 046 | 2 113 | 74.0 | Rất dense | 30 % |
| `pumsb_star.dat` | 49 046 | 2 088 | 50.5 | Rất dense | 30 % |
| `retail.dat` | 88 162 | 16 470 | 10.3 | Sparse (khớp paper) | 0.1 % |

> ⚠️ **`newMushroom.dat` KHÔNG phải bản đổi tên của `mushroom.dat`.** Cả hai có đúng 8 124 tập phân
> biệt nhưng **không giao nhau một tập nào** (mọi tập đều khác nhau) và `newMushroom.dat` có thêm
> 292 dòng lặp. Nguyên nhân là **gán item id khác** (ví dụ `mushroom.dat` bắt đầu `1 3 10 13 …`,
> còn `newMushroom.dat` bắt đầu `1 5 12 21 …`), không phải lệch số thứ tự. **Không được so
> benchmark hai dataset này với nhau.**

- **Scalability:** `kosarak.dat` (200K→990K, cắt theo số dòng) **và** `chainstore.dat` (1.11M — lớn nhất).
- **Synthetic dự phòng (tuỳ chọn):** bộ sinh cấu hình T10I4DxK.
- **Protocol mô phỏng incremental (theo paper):** chia mỗi dataset thành **5 phần bằng nhau**, nạp tuần tự, mỗi bước đo.
- `dataset/default.dat`: 8 transaction demo (A–G), dùng thử nhanh, **không thuộc** bộ benchmark.
- **`dataset/zip/`:** bản nén của cả 11 file `.dat` (kể cả `default.dat`). Nén tốt nhất đạt
  4–6 % (`connect.dat` 8.8 MB → 0.35 MB); `chainstore.dat` 43.4 MB → 17.4 MB. CLI đọc trực tiếp.

> ⚠️ **Lưu ý về ∂ (rất quan trọng — đọc mục 6.4).** Cột "Gợi ý ∂" ở trên lấy từ paper và **không dùng được nguyên trạng** với công thức `minSup = ∂ × N_eff` khi `N_eff = W` nhỏ. Phải chọn `∂` trong miền khả thi `∂ ≤ 1/((1−f)·W)`. Đây là việc của G2/G3.

---

## 6. Bộ Test Chuẩn

### 6.1 Nhóm A — TC1–TC8 (bám sát paper, **chạy với `minOcc = 0`**)

| TC | Dữ liệu | Tham số | Kỳ vọng |
|---|---|---|---|
| TC1 | 8 TID | f=0.9, ∂=15 % → minSup=1.2 | `{AE=1.2601, F=1.2553}` |
| TC2 | như TC1 | ∂=20 % → 1.6 | `{}` |
| TC3 | như TC1 | ∂=10 % → 0.8 | 15 DOP |
| TC4 | như TC1 | f=0.8, ∂=15 % | `{}` |
| TC5 | như TC1 | **f=1.0**, ∂=15 % | 9 HOP |
| TC6 | chỉ DB0 | f=0.9, ∂=25 % → 1.0, TL=4 | `{FCD=1.0000, CD=1.4812, CDE=1.2218}` |
| TC7 | custom 10 TID | f=0.9, ∂=15 % → 1.5 | 9 DOP |
| TC8 | 5 TID, mỗi giao dịch 1 item | f=0.9, ∂=30 % → 1.5, TL=5 | `{A=2.4661}` |

> **Quy tắc:** TC1–TC8 **luôn chạy với `minOcc = 0`** ở **mọi** phiên bản. Đây là test hồi quy **INV-I**.

### 6.2 Nhóm B — TC9–TC18 (ngữ nghĩa cửa sổ minOcc, **chỉ V2+**)

| TC | Mục đích | Kỳ vọng |
|---|---|---|
| **TC9** | **Tương đương paper:** cùng dữ liệu/tham số TC1 nhưng `minOcc = 0` | kết quả **bit-for-bit** = TC1 |
| **TC10** | **Cửa sổ rộng hơn stream:** chọn `minOcc` sao cho `W ≥ TL` | ≡ paper |
| **TC11** | **Cửa sổ hẹp:** `W ≪ TL` | mọi `DO_win` lệch `DO_full` **≤ minOcc** (INV-G); số DOP ≤ kết quả full |
| **TC12** | **`f = 1` + `minOcc > 0`** | `W = ∞` ⇒ ≡ TC5 |
| **TC13** | **Entry chết là tiền tố:** nạp nhiều batch để có evict, kiểm tra `head` dịch đúng, `support = size − head` | hợp lệ, không sót entry |
| **TC14** | **minSup 2 pha:** `TL` đi qua mốc `W` | `minSup` tăng tới `∂×W` rồi **đóng băng** |
| **TC15** | **Validator:** `minOcc ≥ 1/(1−f)` | ném lỗi cấu hình rõ ràng |
| **TC16** | **Validator + short-circuit:** `∂ × N_eff > Z(f,TL)` | kết quả rỗng **tức thì** + cảnh báo có nội dung |
| **TC17** | **Ví dụ "item A"** của `Draft Idea.txt`: A xuất hiện 1000 lần ngoài cửa sổ + 1 lần trong cửa sổ | `support_sống(A) = 1 < minSup` ⇒ A bị C2 loại ở GĐ2 |
| **TC18** | **Đối chiếu window vs full-recompute** trên nhiều tổ hợp `(∂,f,minOcc)` | `∀X: DO_win(X) ≥ DO_full(X) − minOcc − ε` và mọi DOP_full vẫn là DOP_win hoặc lệch ≤ minOcc |

### 6.3 Cấu trúc nghiệm thu chung

1. `GoldenRunner`: chạy engine bất kỳ trên TC, so tập kết quả theo C4–C6, C12.
2. **Hồi quy chéo phiên bản:** `V2(minOcc=0) ≡ V1`, `V3(minOcc=0) ≡ V1`, `V4(minOcc=0) ≡ V1`; và với `minOcc>0`: `V3(minOcc) ≡ V2(minOcc)`, `V4(minOcc) ≡ V3(minOcc)` (**double đầy đủ**).
3. `DeterminismAssert`: cùng tham số, pool `{1,2,4,cpu}` ⇒ bit-for-bit.

### 6.4 Bảng chọn tham số & test trường hợp biên (bắt buộc theo yêu cầu của tác giả)

> Câu hỏi mẫu trong `Draft Idea.txt`: *"nếu minOcc = 10⁻³, ∂ = 1, f = 1 thì window_size = ?"* → trả lời bằng công thức C8, kiểm bằng `WindowMathTest`.

**Trường hợp biên (bắt buộc có test):**

| # | f | minOcc | ∂ | W | N_eff (TL lớn) | minSup | Trần `Z(f,TL)` | Kết luận |
|---|---|---|---|---|---|---|---|---|
| E1 | 0.9 | **0** | 0.15 | ∞ | TL | `0.15·TL` | 10 | **≡ paper** (chế độ mặc định của V1) |
| E2 | **1.0** | 1e-3 | **1.0** | **∞** | TL | `1.0·TL` | `TL` | khả thi về lý thuyết; chỉ pattern có `DO = TL` mới đạt ⇒ **suy biến, cảnh báo** |
| E3 | 0.9 | 1e-3 | **1.0** | 88 | 88 | **88** | **10** | **⇒ ∅ chắc chắn** → short-circuit + cảnh báo "∂ vượt miền khả thi (≤ 11.4 %)" |
| E4 | 0.9 | 1e-6 | 1.0 | 153 | 153 | 153 | 10 | ⇒ ∅ chắc chắn (≤ 6.54 %) |
| E5 | 0.9 | 1e-6 | 0.15 | 153 | 153 | **22.95** | 10 | ⇒ ∅ chắc chắn — **minh hoạ việc ∂=0.15 chỉ dùng được khi N_eff nhỏ (TC1)** |
| E6 | 0.9 | 1e-6 | 0.005 | 153 | 153 | 0.765 | 10 | khả thi; ngưỡng có nghĩa |
| E7 | 0.9 | **≥ 0.1** | 0.15 | **0** | 0 | — | 10 | **⇒ lỗi cấu hình** (minOcc ≥ 1/(1−f)) |
| E8 | 0.9 | 1e-6 | **0** | 153 | 153 | 0 | 10 | khả thi nhưng **mọi pattern đều là DOP** → cảnh báo bùng nổ output |
| E9 | 0.9 | 1e-6 | 0.15 | 153 | **4** (TL=4 < W) | **0.6** | **3.44** | **pha 1**: `minSup = ∂×TL` ⇒ ≡ paper. Miền khả thi **chính xác** = `Z/4 = 3.439/4 = 86 %` (xấp xỉ 6.54 % — xem D38) |
| E10 | 0.9 | 1e-6 | **0.06** | 153 | 153 | 9.18 | 10 | khả thi *hẳn*, nhưng chỉ pattern có `DO ≥ 9.18` ⇒ **gần như toàn bộ giỏ** (đây chính là ngưỡng 6 % của mushroom) |

**Miền `∂` khả thi** — cột cuối là **giá trị xấp xỉ** `1/((1−f)·W)` (xem cảnh báo ở mục 2.5a: giá trị chính xác là `Z(f,TL)/N_eff`):

| f | W (minOcc=1e-3) | W (minOcc=1e-6) | W (minOcc=1e-9) | `∂` tối đa ≲ (minOcc=1e-3 / 1e-6 / 1e-9) |
|---|---|---|---|---|
| 0.8 | 39 | 70 | 101 | 12.8 % / 7.14 % / 4.95 % |
| **0.9** | 88 | 153 | 219 | 11.4 % / 6.54 % / 4.57 % |
| 0.95 | 194 | 328 | 463 | 10.3 % / 6.10 % / 4.32 % |
| 0.99 | 1 146 | 1 833 | 2 521 | 8.73 % / 5.46 % / 3.97 % |

*(Ghi chú: `∂` tối đa dao động trong dải hẹp khoảng **4–13 %** suốt dải `f`, `minOcc` — đây là tính chất có lợi của công thức `minSup = ∂ × N_eff`: **miền `∂` khả thi rộng và ổn định**, không phụ thuộc kích thước dataset.)*

**Đối chiếu ngưỡng của paper với công thức mới** (`f = 0.9`, `minOcc = 1e-6` ⇒ `W = 153`, `N_eff = 153`, trần `Z = 10`):

| Paper | ∂ của paper | minSup theo công thức mới | Kết luận |
|---|---|---|---|
| Mushroom 6 % | 0.06 | 9.18 | khả thi *hẳn* nhưng chỉ pattern chiếm gần hết giỏ mới đạt ⇒ **giải thích vì sao chạy 300 s mà ra rất ít pattern** |
| Retail 0.10 % | 0.001 | 0.153 | **có nghĩa** (item cần ~2 lần xuất hiện trong cửa sổ) |
| Synthetic 0.15 % | 0.0015 | 0.23 | **có nghĩa** |
| **Accidents 50 %** | 0.5 | 76.5 | **⇒ ∅ chắc chắn** (vượt trần 10) |
| **pumsb 30 %** | 0.3 | 45.9 | **⇒ ∅ chắc chắn** |
| **connect 30 %** | 0.3 | 45.9 | **⇒ ∅ chắc chắn** |

> **Kết luận cần đưa vào báo cáo (chưa phải cáo buộc):** với cách hiểu `minSup = ∂ × N_eff` và giả thuyết suy giảm, **3/6 ngưỡng của paper rơi vào vùng bất khả thi**. Đây là **giả thuyết cần kiểm chứng** (đọc lại định nghĩa `minSup` trong paper; hoặc chạy lại và đếm số pattern). Xem `docs/reports/` khi có kết quả G2.

---

## 7. Benchmark

| Metric | Cách đo |
|---|---|
| Runtime tổng & theo giai đoạn | Wall-clock `System.nanoTime`; tách GĐ0/GĐ1/GĐ2/GĐ3 |
| **Số lần evict / entry bị loại** | **mới** — đo trực tiếp hiệu quả của minOcc |
| **Entry sống / tổng entry** | **mới** — tỉ lệ dữ liệu "còn giá trị" |
| Peak memory | JMX (`MemoryPoolMXBean`) hoặc JFR |
| Throughput | số DOP / giây |
| Latency theo batch | thời gian mỗi phần 1/5 dataset |
| Scalability | `kosarak` 200K→990K **và** `Chainstore` (1.11M) |
| **Độ lệch ngữ nghĩa** | **mới** — `max_X | DO_win(X) − DO_full(X) |` phải ≤ minOcc (kiểm chứng INV-G ngoài test nhỏ) |
| **Bảng ablation V1→V4** | **mới** — cùng `(∂, f, minOcc, dataset)`, 4 dòng: runtime, memory, #DOP, độ lệch |
| So sánh chéo phiên bản | theo bảng 5.2, `f = 0.9`, `minOcc` cố định, chia 5 phần, ≥3 lần lấy median |

**Tham số benchmark mặc định:** `f = 0.9`, `minOcc = 1e-6`, `∂` **chọn trong miền khả thi** mục 6.4 (không dùng ngưỡng của paper một cách máy móc); chia 5 phần incremental; chạy ≥3 lần lấy median; **ghi rõ phần cứng, JDK, số worker**.

---

## 8. Lộ trình & Phụ thuộc

| Giai đoạn | Nội dung | Sản phẩm | Chờ |
|---|---|---|---|
| **C0. Planning** | Lập plan tổng thể + 4 plan phiên bản; SRS; UI Layout; **`DECISIONS.md`** | `docs/plans/*`, `docs/srs/*`, `docs/DECISIONS.md` | — |
| **G0. Khởi động** ✅ | Maven structure; `dhopm-common` seed; TestKit TC1–TC8; validate 10 dataset | `dhopm-common` + TestKit | C0 |
| **G1. V1 Standard** ✅ | Level 1 threading, GoF, qua TC1–TC8, benchmark sơ bộ, tài liệu V1, CLI 5 lệnh, D6 | `dhopm-v1-standard` | G0 |
| **G2. V2 MinOcc/Window** | `WindowMath` + validator trong `dhopm-common`; handle 2 tầng; evict O(1); `minSup` 2 pha; bound `min(DUBO,Z(X))`; short-circuit trần; **TC9–TC18**; `window`/`validate`/`sweep` CLI; **INV-I hồi quy**; đo ablation V1 vs V2 | `dhopm-v2-minocc` + báo cáo đo minOcc | G1 |
| **G3. V3 Optimized** | Cùng ngữ nghĩa V2, tối ưu: bỏ xích handle (int index), SoA/primitive, decay lookup, ForkJoin Level 2; benchmark đầy đủ | `dhopm-v3-optimized` + benchmark report | G2 |
| **G4. V4 Extreme** | Data-oriented thuần, pipeline 3 pha, memory-mapped I/O; chỉ giữ gì đo được lợi | `dhopm-v4-extreme` + báo cáo | G3 |
| **G5. Manager CLI/API** | `dhopm-cli`: `--json` one-shot + `serve` JSONL, registry, `JobManager`, `SessionManager`, `ResultAggregator`; đặc tả giao thức = contract cho `javanc` | `dhopm-cli` + protocol | song song G3/G4 |
| **G6. Debug/Compare App** | `dhopm-app` (module UI riêng, loading/mining screen): chọn ≥1 engine, so sánh, debug nội bộ, **hiển thị cửa sổ minOcc và vòng đời pattern** | `dhopm-app` | G3 |
| **G7. Dữ liệu mở rộng (tuỳ chọn)** | Reader `Utility-FIMI` (chưa có dữ liệu, xem D42) | Utility-FIMI đọc được | có thể làm sớm hơn G2 |

> **Chốt:** hiện tại ưu tiên **G2**. G3 → G4 → G6 theo thứ tự. G5 (Manager) có thể làm song song với G3/G4 vì không chặn thuật toán. **Chi tiết G2 ở `docs/plans/02-MINOCC-WINDOW-VERSION-PLAN.md` và `docs/phases/P2-G2.md`.**

---

## 9. Rủi ro & Giảm thiểu

| Rủi ro | Giảm thiểu |
|---|---|
| Mơ hồ thuật toán giữa paper và chạy tay | Đã khóa tại mục 2 (C1–C12); mọi thắc mắc phải quy về mốc [1]/[2], ghi thành **bổ sung canonical** + `DECISIONS.md` |
| **minOcc làm sai kết quả so với paper** | Quy tắc `minOcc = 0 ⇒ ≡ paper` (INV-I) + bộ hồi quy TC1–TC8 ở mọi phiên bản + TC11/TC18 kiểm `\|ΔDO\| ≤ minOcc` |
| **minOcc biến thành "tham số ngữ nghĩa" gây khó so sánh** | C12 bắt buộc khai báo `(∂, f, minOcc, TL)` cùng nhau; lệnh `window` in ra giá trị suy ra để không phải tính tay |
| **Handle 2 tầng viết sai → đọc tham chiếu chết** | INV-H; test TC13; **không dùng `try/catch` để bắt lỗi dereference** |
| Evict không đồng bộ giữa các node | Evict đơn luồng trước mọi pha song song (C10); phát hiện là head-offset nội bộ mỗi node ⇒ không phụ thuộc thứ tự |
| DUBO không còn hợp lệ sau evict | DUBO tính **chỉ trên entry sống** (C1 + 2.3) ⇒ toàn bộ tính toán diễn ra trên **cùng một instance** (cửa sổ hiện tại) ⇒ Lemma 2 vẫn đúng |
| Kết quả lệch khi song song hoá | INV-E + C5; `DeterminismAssert` |
| ~~Dataset FIMI thiếu dataset của paper~~ | ✅ **Đã có** `accidents.dat` (dataset của paper) + `chainstore.dat` (1.11M) trong bộ benchmark |
| Sai số số thực | C4 (`ε = 1e-9`); tolerance riêng khi so bảng 4 chữ số |
| `∂` người dùng chọn nằm ngoài miền khả thi | Validator + short-circuit (§2.5a) + lệnh `window` in miền khả thi |

---

## 10. Quyết định đã chốt

| # | Quyết định | Kết luận |
|---|---|---|
| D1 | Build tool | **Maven (đa module)** |
| D2 | Vị trí dự án code | **`implementation/`** trong repo `D:\JVNC\JVNC` |
| D3 | `Default Project/` + `draft/` cũ | Đã lưu trữ branch `archive/legacy-draft`, xoá khỏi main; không tái dùng |
| D4 | Thread pool mặc định | = `availableProcessors()`; override qua CLI/config |
| D5 | Format input mặc định | **FIMI** + **text** (TC) + **ZIP**; `utility-FIMI` = **planned, chưa triển khai** (D42) |
| D6 | Công cụ ↔ thuật toán | **Chỉ qua API ổn định ở `dhopm-common`**; CLI = client của API; bộ lệnh chuẩn (4.2) |
| D7 | Frontend ↔ Backend | Frontend qua **1 Manager CLI/API duy nhất** (4.3); hợp đồng = đặc tả giao thức |
| D8 | Quản lý tài nguyên | **Plan riêng**: `ResourceManager → native API → WindowsModule/MacModule` |
| **D9** | **minOcc là gì** | **`minOcc` (occupancy threshold) = ngưỡng occupancy do người dùng đặt, đóng vai trò ngưỡng DƯỚI của DO**; quyết định cửa sổ. Tham số `epsilon` (ε, 1e-9) giữ cho **sai số so sánh** |
| **D10** | **Công thức cửa sổ** | `W = ⌈ln(minOcc(1−f))/ln f⌉`; `minOcc=0` hoặc `f=1` ⇒ `W = ∞`; `minOcc ≥ 1/(1−f)` ⇒ lỗi cấu hình |
| **D11** | **Đơn vị của minSup** | `minSup = ∂ × N_eff`, `N_eff = min(TL, W)` — **2 giai đoạn** |
| **D12** | **Cơ chế evict** | **Handle 2 tầng `ref1 → ref2`**; evict O(1); phát hiện chết trong lúc duyệt entry; entry chết là tiền tố ⇒ `head` offset |
| **D13** | **Số phiên bản** | **4**: V1 paper/GoF · V2 minOcc · V3 tối ưu minOcc · V4 cực đoan minOcc. Ưu tiên V1(✅) → V2 → V3 → V4 |
| **D14** | **Tương đương paper** | `minOcc = 0` (hoặc `W ≥ TL`) ⇒ **mọi phiên bản phải bit-for-bit ≡ V1** (INV-I) |
| **D15** | **Nơi chốt quyết định** | `docs/DECISIONS.md` là nguồn chân lý; các plan không tự quyết lại |

---

## 11. Tiêu chí Hoàn thành Tổng thể

- [ ] `docs/DECISIONS.md` có mọi quyết định `D1…D15` + lý do + trạng thái.
- [ ] SRS + UI Layout cập nhật cho 4 phiên bản và ngữ nghĩa minOcc.
- [ ] TestKit **TC1–TC18** xanh trên V1 (TC1–TC8), V2, V3, V4.
- [ ] **INV-I**: `minOcc=0` ⇒ V2/V3/V4 bit-for-bit ≡ V1.
- [ ] **INV-G**: `|DO_win − DO_full| ≤ minOcc` trên toàn bộ dataset benchmark.
- [ ] V2 có đủ cơ chế: `WindowMath`, validator, handle 2 tầng, evict O(1), minSup 2 pha, bound `min(DUBO, Z(X))`, short-circuit trần.
- [ ] **Bảng ablation V1→V4** trên ≥4 dataset (dense + sparse), median ≥3 lần, có cột "độ lệch ≤ minOcc".
- [ ] V3 có microbenchmark từng tối ưu + kết luận giữ/thay kèm số liệu.
- [ ] V4 chỉ giữ tối ưu **đo được lợi**; có báo cáo "giữ / bỏ" từng ý tưởng.
- [ ] Mỗi module engine có bộ tài liệu riêng (README, design từng thành phần/hàm, test plan, benchmark report).
- [ ] Scalability: `kosarak` 200K→990K **và** `Chainstore`; **memory đỉnh gần như phẳng theo N** (bằng chứng cụ thể cho giá trị của minOcc).
- [ ] CLI: `mine/detail/stream/golden/inspect/window/validate/sweep` trên cả 4 version.
- [ ] G5: Manager CLI/API + đặc tả giao thức = contract cho frontend `javanc`.
- [ ] G6: app chọn ≥1 engine, so sánh, debug nội bộ, **hiển thị cửa sổ minOcc và vòng đời pattern**.

---

*Kết thúc Plan tổng thể. Chi tiết từng phiên bản tại `01-STANDARD-VERSION-PLAN.md`, `02-MINOCC-WINDOW-VERSION-PLAN.md`, `03-OPTIMIZED-VERSION-PLAN.md`, `04-EXTREME-VERSION-PLAN.md`. Giao tiếp Frontend↔Backend tại `05-BACKEND-CLIAPI-PLAN.md` (phân tích draft: `06-BACKEND-CLIAPI-DRAFT-ANALYSIS.md`). Quyết định tại `../DECISIONS.md`.*