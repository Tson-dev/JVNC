# DHOPM/DOPM – Domain Specification (Đặc tả Miền)

> Tài liệu này mô tả **cần biết gì và cần làm gì** để giải bài toán khai phá mẫu chiếm dụng cao có suy giảm (Damped High Occupancy Pattern Mining – DHOPM, tài liệu nội bộ còn gọi là DOPM). Tài liệu **không** mô tả phải lưu trữ dữ liệu như thế nào hoặc cài đặt thuật toán như thế nào.

---

## Document Header

| Mục | Giá trị |
|---|---|
| **Document Name** | DHOPM / DOPM – Domain Specification |
| **Document ID** | DHOPM-DOM-001 |
| **Version** | 1.0 |
| **Status** | Draft – chờ review |
| **Source Documents** | [1] *Damped window based high occupancy pattern mining with one scanning of data streams* – Engineering Applications of Artificial Intelligence 174 (2026) 114511 (bản PDF convert sang Markdown: `1-s2_0-S095219762600792X-main.md`)<br/>[2] *Phân tích thuật toán DOPM và ví dụ chạy tay* – tài liệu nội bộ (`Nhom01_VDChayTay.md`) |
| **Ngôn ngữ** | Độc lập ngôn ngữ lập trình (Java, C#, Python, … để tiêu chuẩn hóa) |

### Revision History

| Phiên bản | Ngày | Người sửa | Mô tả |
|---|---|---|---|
| 1.0 | Dự thảo lần đầu | – | Khởi tạo tài liệu từ 2 nguồn [1] và [2] |

---

## Document Scope

### Purpose

Cung cấp một nguồn tham chiếu duy nhất (source of truth) về **miền bài toán** DHOPM/DOPM, bao gồm các khái niệm, quy tắc, mô hình toán học, dữ liệu đầu vào, các khả năng (capabilities), yêu cầu dữ liệu logic và luồng công việc của thuật toán – phục vụ cho việc hiểu đúng và triển khai trong tương lai.

### Scope

Tài liệu bao gồm:

- Mô hình miền (Domain Model).
- Mô hình toán học (Mathematical Model).
- Định dạng dữ liệu đầu vào.
- Các khả năng nghiệp vụ (Domain Capabilities) kèm quy tắc xử lý.
- Yêu cầu dữ liệu logic (Logical Data Requirements).
- Luồng công việc thuật toán (Algorithm Workflow).
- Ví dụ chạy tay minh họa (Walkthrough Example).
- Bảng tổng hợp công thức và thuật ngữ.

---

## Architectural Boundary

### Purpose of This Document

Đây là tài liệu **đặc tả miền**, mô tả DOMAIN của hệ thống khai phá DHOPM ở mức khái niệm – thuật toán làm gì, cần dữ liệu gì, theo những quy tắc gì, sinh ra kết quả gì.

### What This Document Defines

- **Domain Concepts** – các khái niệm thuộc miền: Transaction, Pattern, Transaction Database, Incremental Database, DHO-List (mức logic), DOP, …
- **Domain Rules** – các quy tắc nghiệp vụ ràng buộc cách các khái niệm tương tác.
- **Logical Data Requirements** – dữ liệu tối thiểu hệ thống phải có khả năng xác định/truy xuất để thực hiện thuật toán.
- **Domain Capabilities** – các năng lực mà hệ thống phải cung cấp để giải bài toán.

### What This Document Does Not Define

Tài liệu này **không** quyết định:

- Ngôn ngữ lập trình (Java, C#, Python, Go, …).
- Kiến trúc phần mềm (layers, packages, modules, dependency injection, …).
- Cấu trúc dữ liệu triển khai (HashMap, Tree, List, Array, Record, Object, …).
- API, giao diện lập trình.
- Database hay mô hình lưu trữ.
- Thiết kế class, interface, hàm cụ thể.

Các khía cạnh này được bàn trong tài liệu **Infrastructure** và sau này là tài liệu **Java Design / Implementation**.

### Interpretation Guideline

Mọi thành phần trong tài liệu này phải được hiểu là **logical concepts** (khái niệm logic), không phải **implementation concepts** (khái niệm cài đặt).

**Ví dụ:** "DHO-List" ở đây là một *khái niệm miền* mô tả khả năng lưu trữ và truy xuất thông tin phục vụ khai phá – không được hiểu là một danh sách liên kết, một cấu trúc Node/Entry cụ thể trong code.

---

# 1. Overview

## 1.1 Introduction

DHOPM (Damped High Occupancy Pattern Mining) là thuật toán khai phá **mẫu chiếm dụng cao có suy giảm (DOP)** trên dữ liệu dòng (data stream) dạng gia tăng (incremental database).

Điểm khác biệt cốt lõi của thuật toán:

- Khai phá theo **occupancy** (độ chiếm dụng) – tỷ lệ một mẫu chiếm trong các giao dịch chứa nó – thay vì chỉ theo tần suất (support).
- Áp dụng **mô hình cửa sổ suy giảm (damped window model)**: dữ liệu càng gần hiện tại càng có trọng số cao hơn. Nhờ đó, mẫu "hot" trong quá khứ nhưng không phù hợp xu hướng gần đây sẽ bị loại khỏi kết quả.
- Hoạt động trong môi trường **dữ liệu dòng**: dữ liệu đến theo từng đợt ($DB_0$, $DB_1$, $DB_2$, …), và hệ thống chỉ cần **quét mỗi giao dịch đúng một lần** (one-scan) khi nó được thêm vào.

**Mục tiêu:** tìm tất cả mẫu có **Damped Occupancy** `DO(X)` đạt tối thiểu ngưỡng `minSup`.

**Kết quả đầu ra:** tập `DOPs` gồm các mẫu thỏa mãn điều kiện trên, kèm theo giá trị đo lường tương ứng.

## 1.2 Workflow

Thuật toán vận hành theo **3 giai đoạn chính**, lặp lại mỗi khi có đợt dữ liệu mới và có yêu cầu khai phá:

```
[$DB_0$] ──► Giai đoạn 1: Xây dựng DHO-List toàn cục (quét một lần)
            │
            ▼
[$DB_1$, $DB_2$, …] ──► Giai đoạn 1b: Cập nhật DHO-List toàn cục (chỉ quét phần mới)
            │
            ▼
        Người dùng yêu cầu khai phá ?
            │ có
            ▼
        Giai đoạn 2: Tái cấu trúc DHO-List
            • Tính lại DO cho từng nút theo T_L hiện tại
            • Sắp xếp nút theo Support tăng dần
            │
            ▼
        Giai đoạn 3: Khai phá DOP (DFS, pattern growth)
            • Đánh giá DO ≥ minSup ? → X là DOP
            • Đánh giá DUBO ≥ minSup ? → mở rộng / cắt tỉa
            │
            ▼
        Kết quả: tập DOPs
```

## 1.3 Objectives

- **Obj-1 – Dữ liệu gia tăng, một lần quét:** mỗi giao dịch chỉ được quét đúng một lần khi xuất hiện; việc thêm đợt dữ liệu mới không được phép đòi quét lại dữ liệu cũ.
- **Obj-2 – Trọng số theo thời gian:** kết quả khai phá phải phản ánh xu hướng dữ liệu gần đây thông qua hệ số suy giảm.
- **Obj-3 – Không mất mát mẫu:** mọi mẫu có `DO ≥ minSup` phải xuất hiện trong tập kết quả; việc cắt tỉa (pruning) phải không loại bỏ nhầm mẫu hợp lệ.
- **Obj-4 – Hiệu quả khai phá:** giảm không gian tìm kiếm nhờ cận trên suy giảm DUBO mà vẫn đảm bảo tính đầy đủ.

---

# 2. Domain Model

## 2.1 Item

- Một **item** là một "đơn vị" tối giản xuất hiện trong giao dịch, ví dụ $A$, $B$, $C$, …
- Mỗi item có một tên định danh duy nhất trong phạm vi dữ liệu.
- Item là khối xây dựng cơ bản để tạo thành pattern.

## 2.2 Transaction

- Một **transaction** (giao dịch) là một tập hợp các item cùng xuất hiện với nhau, ví dụ $T_1 = \{A, C, D, E\}$.
- Mỗi transaction có **$T_{ID}$** (Transaction ID) – định danh thứ tự đến, tăng dần theo thời gian.
- Mỗi transaction có **độ dài $|T_d|$** = số item có trong giao dịch.
- Transaction là đơn vị dữ liệu nhỏ nhất mà thuật toán xử lý khi quét.

## 2.3 Pattern

- Một **pattern** là một tập hợp các item, ví dụ $\{A\}$, $\{B, C\}$, $\{A, E\}$.
- Độ dài mẫu $|X|$ = số item của mẫu.
- Mẫu $X$ xuất hiện trong transaction $T$ nếu mọi item của $X$ đều có trong $T$.
- Một super-pattern được tạo bằng cách mở rộng sub-pattern với các các item khác.
- Pattern có thể được ký hiệu gọn thành $AE$ (tức $\{A, E\}$).

## 2.4 Transaction Database / Database

- Một **database (DB)** là một tập hợp (batch) các transaction, ví dụ $DB_0$ gồm $T_1..T_4$.
- Tổng số giao dịch đã quét trong toàn bộ CSDL được dùng để tính ngưỡng `minSup`.
- Database là đơn vị dữ liệu được "nạp" vào hệ thống theo từng đợt.

## 2.5 Incremental Database / Data Stream

- **Incremental database (CSDL gia tăng)** là tập hợp các DB đến tuần tự theo thời gian: $DB_0, DB_1, DB_2,…$.
- Khi một đợt DB mới xuất hiện, hệ thống phải phản ánh thông tin mới vào trạng thái hiện có **mà không quét lại** các transaction đã xử lý.
- **$T_L$** – $T_{ID}$ của transaction mới nhất đã được quét – đóng vai trò mốc thời gian "hiện tại" để tính hệ số suy giảm.

## 2.6 DHO-List (logical concept)

- **DHO-List** là một khái niệm miền mô tả khả năng của hệ thống trong việc **lưu giữ và truy xuất thông tin cần thiết để khai phá**.
- Nó phải đáp ứng được các nhu cầu logic sau:
  - Theo dõi trạng thái của từng item đã quét.
  - Cho biết, với một item/mẫu `X`: nó xuất hiện ở **những transaction nào ($T_{ID}$)** và độ dài của các transaction đó.
  - Duy trì **Support** (số giao dịch chứa `X`).
  - Duy trì một giá trị đo gọi là **DO** (damped occupancy) cho từng nút.
  - Cho phép **sắp xếp theo Support tăng dần** và **tái tính DO** khi mốc thời gian `T_L` thay đổi.
  - Cho phép **xây dựng DHO-List điều kiện (conditional)** cho mẫu dài hơn bằng cách tìm các transaction chung giữa hai mẫu.
- DHO-List xuyên suốt 3 giai đoạn: xây dựng → tái cấu trúc → khai phá.
- Có hai vai trò của DHO-List trong miền:
  - **Global DHO-List**: phản ánh toàn bộ dữ liệu đã quét; là nơi khởi đầu cho mỗi phiên khai phá.
  - **Conditional DHO-List**: phản ánh thông tin của riêng một nhánh khai phá (mẫu tiền tố) – được tạo ra trong quá trình đệ quy.

## 2.7 DOP (Damped High Occupancy Pattern)

- **DOP** là một mẫu có **Damped Occupancy** đạt hoặc vượt ngưỡng tối thiểu: `DO(X) ≥ minSup`.
- **DOPs** là tập hợp tất cả các DOP tìm được sau một phiên khai phá.

---

# 3. Mathematical Model

Ký hiệu chuẩn: $X$ – mẫu; $T_d$ – một giao dịch; $|X|$, $|T_d|$ – độ dài; $f$ – hệ số suy giảm; $T_L$ – $T_{ID}$ mới nhất; $minSup$ – ngưỡng tối thiểu.

## 3.1 Support

- **Description:** Số giao dịch chứa mẫu $X$ trong database.
- **Formula:**

$$
\operatorname{Sup}(X) = \left|\left\{ T_d \in DB \mid X \subseteq T_d \right\}\right|
$$

- **Purpose:** Đếm tần suất xuất hiện; dùng làm cơ sở tính ngưỡng và là thông tin để sắp xếp thứ tự khai phá.
- **Example:** với DB gồm `$T_1$..$T_8$`, item `A` xuất hiện trong `$T_1$, $T_2$, $T_7$, $T_8$` nên `Sup(A) = 4`.

## 3.2 Occupancy

- **Description:** Độ chiếm dụng của mẫu `X` trong một giao dịch – tỷ lệ mà `X` chiếm trong giao dịch. Tổng occupancy là cộng dồn trên mọi giao dịch chứa `X`. Đây là thước đo "mẫu quan trọng vì chiếm tỷ lệ cao trong giao dịch của nó".
- **Formula:**

$$
O(X, T_d) = \frac{|X|}{|T_d|}
$$

$$
O(X) = \sum_{\substack{X \subseteq T_d \in DB}} O(X, T_d)
$$


- **Purpose:** Đo mức độ chiếm ưu thế của mẫu trong giao dịch; là nền tảng của thước đo chính DO.
- **Example:** với mẫu `{A,E}` xuất hiện trong `$T_1$(|$T_1$|=4)` và `$T_2$(|$T_2$|=3)`:

  `O(AE, $T_1$) = 2/4 = 0.5000`, `O(AE, $T_2$) = 2/3 = 0.6667`, `O(AE) = 1.1667`.

## 3.3 Upper Bound of Occupancy (UBO)

- **Description:** Cận trên của Occupancy dùng để cắt tỉa. Occupancy **không** thỏa tính phản đơn điệu (thêm item có thể làm tăng occupancy), vì vậy cần một cận trên `UBO(X)` sao cho với mọi siêu mẫu `X'` của `X`: `O(X') ≤ UBO(X)`.
- **Formula:**

  Gọi `L = {l1, l2, …, lu}` là tập các độ dài giao dịch phân biệt (sắp tăng) của các giao dịch chứa `X`; `ni` = số giao dịch có độ dài `li`.

$$
UBO(X, x) = \sum_{i=x}^{u} n_i \times \frac{l_x}{l_i}
$$

$$
UBO(X) = \max_{1 \leq x \leq u} UBO(X, x)
$$

- **Purpose:** Cắt tỉa không gian tìm kiếm: nếu `UBO(X) < minSup` thì mọi siêu mẫu của `X` đều không thể là HOP/DOP, cắt bỏ nhánh.
- **Example:** item `E` xuất hiện trong `$T_1$(4), $T_2$(3), $T_3$(4)`: `L = {3, 4}`, `n1=1 (l=3)`, `n2=2 (l=4)`.

  `UBO(E,1) = 1×(3/3) + 2×(3/4) = 2.5`, `UBO(E,2) = 2×(4/4) = 2.0` → `UBO(E) = 2.5`.

## 3.4 Decaying Factor

- **Description:** Hệ số suy giảm biểu thị mức độ "giảm tầm quan trọng" của một giao dịch cũ so với giao dịch mới. Giao dịch càng cũ (`T_L − Td` càng lớn) thì hệ số càng nhỏ.
- **Formula:**

$$
dF(T_d) = f^{T_L-T_d}
$$

$$
f \in (0,1)\ define\ by\ user
$$

- **Purpose:** Áp dụng "cửa sổ suy giảm" để ưu tiên dữ liệu gần đây – thành phần lõi của thước đo DO.
- **Example:** với `f = 0.9`, `T_L = 8`: `dF($T_1$) = 0.9⁷ ≈ 0.4783`, `dF($T_8$) = 0.9⁰ = 1.0`.

## 3.5 Damped Occupancy (DO)

- **Description:** **Thước đo chính** của bài toán – occupancy được nhân với hệ số suy giảm theo thời gian. Giao dịch gần đây có occupancy cao sẽ đóng góp lớn vào DO; giao dịch cũ dù occupancy cao cũng bị suy giảm đóng góp.
- **Formula:**

$$
DO(X, T_d) = O(X, T_d) \times f^{(T_L - T_d)}
$$

$$
DO(X) = \sum_{\substack{X \subseteq T_d \in DB}} DO(X, T_d)
$$

- **Purpose:** Đánh giá mức độ "chiếm dụng theo xu hướng mới". Mẫu đạt `DO(X) ≥ minSup` là DOP.
- **Example:** mẫu `AE`, `f=0.9`, `T_L=8`, xuất hiện trong `$T_1$(4), $T_2$(3), $T_8$(3)`:

  `DO(AE,$T_1$) = 2/4 × 0.9⁷ ≈ 0.2392`; `DO(AE,$T_2$) = 2/3 × 0.9⁶ ≈ 0.3543`; `DO(AE,$T_8$) = 2/3 × 0.9⁰ ≈ 0.6667` → `DO(AE) ≈ 1.2601`.

## 3.6 Damped High Occupancy Pattern (DOP)

- **Description:** Mẫu được xếp là DOP khi thước đo Damped Occupancy đạt ngưỡng tối thiểu.
- **Formula:**

$$
X \in DOPs \iff DO(X) \geq minSup
$$

$$
minSup = \partial \times |DB|, \qquad \partial \in [0,1]
$$


- **Purpose:** Định nghĩa điều kiện kết quả đầu ra.
- **Example:** `∂ = 15%`, `|DB| = 8` → `minSup = 1.2`. `DO(AE) = 1.2601 ≥ 1.2` → `AE ∈ DOPs`. `DO(E) = 1.0477 < 1.2` → `E ∉ DOPs`.

## 3.7 Damped Upper Bound Occupancy (DUBO)

- **Description:** Cận trên của Damped Occupancy, tích hợp hệ số suy giảm; dùng để cắt tỉa an toàn trong khai phá (không gây mất mát mẫu).
- **Formula:**

  Với $L = {l_1, …, l_u}$ sắp tăng là tập độ dài giao dịch chứa $X$; $n_k$ = số giao dịch độ dài $l_k$; $Tk$ = $T_{ID}$ lớn nhất trong nhóm giao dịch độ dài $lk$; $T_L = T_{ID}$ mới nhất toàn CSDL.

$$
DUBO(X, k) = \sum_{i=k}^{u} n_i \times \frac{l_k}{l_i} \times f^{(T_L - T_k)} = f^{(T_L - T_k)} \times \sum_{i=k}^{u} n_i \times \frac{l_k}{l_i}
$$

$$
DUBO(X) = \max_{1 \leq k \leq u} DUBO(X, k)
$$

- **Purpose:** Quyết định mở rộng hay cắt tỉa: nếu `DUBO(X) < minSup` thì mọi siêu mẫu của `X` không thể là DOP → cắt. Tính cận này đảm bảo `DO(Y) ≤ DUBO(X)` với mọi siêu mẫu `Y` của `X`.
- **Example:** item `{G}` chỉ xuất hiện trong `$T_8$(|$T_8$|=3)`: `L={3}`, `n1=1`, `$T_1$=8`.

  `DUBO(G,1) = 1×(3/3)×0.9⁰ = 1.0 < minSup (=1.2)` → pruning. Ngược lại `{B}` (độ dài giao dịch `{4,2,4}`) cho `DUBO(B) = 1.8 ≥ 1.2` → tiếp tục.

---

# 4. Input Data

## 4.1 Transaction Format

Mỗi transaction là một dòng dữ liệu gồm:

- **$T_{ID}$** – định danh thứ tự đến (số nguyên, tăng dần, duy nhất).
- **Items** – danh sách các item thuộc giao dịch (các item phân biệt).
- **Độ dài |T|** – được suy ra = số item phân biệt của giao dịch.

Ví dụ:

| $T_{ID}$ | Items | \|T\| |
|---|---|---|
| $T_1$ | A, C, D, E | 4 |
| $T_2$ | A, E, F | 3 |

## 4.2 Database Format

- Một **database (DB)** là một nhóm các transaction được nạp cùng một đợt.
- Thứ tự transaction trong DB phải bảo toàn thứ tự $T_{ID}$ tăng dần.

## 4.3 Incremental Database Format

- CSDL đầu vào được cung cấp theo tuần tự các đợt: $DB_0$ (ban đầu), rồi $DB_1$, $DB_2$, …
- Mỗi đợt gồm một hoặc nhiều transaction mới; $T_{ID}$ của chúng tiếp nối sau các giao dịch đã có.

**Ví dụ (từ nguồn):**

| Đợt | $T_{ID}$ | Items | \|T\| |
|---|---|---|---|
| $DB_0$ | $T_1$ | A, C, D, E | 4 |
| | $T_2$ | A, E, F | 3 |
| | $T_3$ | B, C, D, E | 4 |
| | $T_4$ | C, D, F | 3 |
| $DB_1$ | $T_5$ | B, F | 2 |
| | $T_6$ | D, E, F | 3 |
| $DB_2 $| $T_7$ | A, B, C, F | 4 |
| | $T_8$ | A, E, G | 3 |

## 4.4 Algorithm Parameters

| Tham số | Ký hiệu | Ý nghĩa | Ràng buộc |
|---|---|---|---|
| Ngưỡng phần trăm | $∂$ | Tỷ lệ ngưỡng tối thiểu do người dùng đặt | $0 \leq ∂ \leq 1$ |
| Hệ số suy giảm | $f$ | Hằng số suy giảm theo thời gian | `0 < f < 1` |
| Ngưỡng tối thiểu | $minSup$ | = $∂ × \|DB\|$, tính tại thời điểm khai phá | số thực ≥ 0 |
| $T_{ID}$ mới nhất | $T_L$ | $T_{ID}$ của transaction mới nhất đã quét | cập nhật theo dữ liệu |

---

# 5. Domain Capabilities

Mỗi năng lực (capability) mô tả một khả năng nghiệp vụ hệ thống phải cung cấp, kèm các Quy tắc xử lý (Processing Rules, đánh số R1, R2, …). Các quy tắc ở mức miền, không chứa chi tiết cài đặt.

## 5.1 Record Transaction

**Purpose:** Tiếp nhận một transaction mới từ dòng dữ liệu; xác định vị trí thời gian ($T_{ID}$) và độ dài của nó; chuẩn bị cho việc ghi nhận từng item.

**Processing Rules:**

- **R1.** Mỗi transaction mới phải được cấp một $T_{ID}$ duy nhất, tăng dần theo thứ tự đến.
- **R2.** Độ dài của transaction phải được xác định là số item phân biệt có trong nó.
- **R3.** Mọi item trong transaction phải được xử lý theo cùng một cách; một item xuất hiện trong cùng một transaction chỉ được ghi nhận một lần.

## 5.2 Record Item Occurrence

**Purpose:** Ghi nhận sự xuất hiện của mỗi item trong một transaction cụ thể, kèm ngữ cảnh ($T_{ID}$, độ dài giao dịch); đây là dữ liệu nền để truy vết và tính toán về sau.

**Processing Rules:**

- **R1.** Với mỗi item trong transaction, hệ thống phải ghi nhận một lần xuất hiện gắn với ($T_{ID}$, độ dài giao dịch).
- **R2.** Nếu item chưa từng được ghi nhận trước đó, hệ thống phải tạo hồ sơ mới cho item đó.
- **R3.** Nếu item đã có hồ sơ, hệ thống chỉ bổ sung lần xuất hiện mới; không làm thay đổi các lần xuất hiện cũ.

## 5.3 Maintain Pattern Statistics

**Purpose:** Duy trì và cập nhật các thống kê của item/mẫu – số giao dịch chứa nó (Support) và mọi ngữ cảnh xuất hiện – sao cho mọi lúc phản ánh đúng toàn bộ dữ liệu đã quét.

**Processing Rules:**

- **R1.** Khi một item xuất hiện trong transaction mới, Support của item phải tăng thêm 1 (trong cùng transaction, mỗi item chỉ tính một lần).
- **R2.** Support của item/mẫu bằng tổng số giao dịch đã quét có chứa nó.
- **R3.** Mọi lần xuất hiện phải giữ đủ thông tin ($T_{ID}$, độ dài giao dịch) để có thể tái tính toán các độ đo về sau.

## 5.4 Calculate Occupancy

**Purpose:** Tính độ chiếm dụng của một mẫu trong một giao dịch và cộng dồn trên các giao dịch chứa mẫu – thước đo nền tảng cho thuật toán.

**Processing Rules:**

- **R1.** Occupancy của mẫu `X` trong giao dịch `Td` bằng tỷ lệ giữa độ dài `|X|` và độ dài `|Td|`.
- **R2.** Tổng occupancy `O(X)` bằng tổng occupancy theo từng giao dịch chứa `X`.
- **R3.** Giao dịch không chứa `X` không được tính vào `O(X)`.

## 5.5 Calculate Damped Occupancy

**Purpose:** Tính thước đo chính `DO(X)` – kết hợp occupancy với hệ số suy giảm theo thời gian để ưu tiên dữ liệu gần đây.

**Processing Rules:**

- **R1.** Với mỗi giao dịch `Td` chứa `X`: `DO(X, Td) = O(X, Td) × f^(T_L − Td)`.
- **R2.** `DO(X)` bằng tổng `DO(X, Td)` trên mọi giao dịch chứa `X`.
- **R3.** Hệ số suy giảm phải dùng đúng `f` do người dùng cung cấp và đúng mốc `T_L` tại thời điểm tính.
- **R4.** Kết quả DO phải nhất quán với thời điểm khai phá: khi `T_L` đổi, các giá trị DO cũ không được coi là còn hiệu lực cho tới khi được tính lại.

## 5.6 Generate Extended Pattern

**Purpose:** Tạo mẫu dài hơn từ mẫu hiện có bằng cách kết hợp với một item khác (pattern growth), phục vụ duyệt không gian mẫu.

**Processing Rules:**

- **R1.** Mẫu mở rộng `X' = X ∪ {i}` với `i ∉ X`.
- **R2.** Kết quả chỉ được mở rộng trên những giao dịch chứa **đồng thời** cả `X` và `i`.
- **R3.** Chỉ những mẫu có khả năng sinh ra DOP mới được phép mở rộng (xem 5.9).

## 5.7 Evaluate Pattern

**Purpose:** Quyết định một mẫu khảo sát có phải là DOP hay không.

**Processing Rules:**

- **R1.** `X` là DOP khi và chỉ khi `DO(X) ≥ minSup`.
- **R2.** Kết luận DOP của một mẫu là độc lập: dù mẫu đó sau đó có được mở rộng hay bị cắt tỉa, kết luận vẫn được giữ.
- **R3.** `minSup` được tính một lần cho mỗi phiên khai phá, dựa trên tổng số giao dịch đã quét tại thời điểm đó.

## 5.8 Estimate Upper Bound

**Purpose:** Tính cận trên (UBO/DUBO) của một mẫu nhằm dự đoán khả năng sinh ra mẫu thỏa mãn khi tiếp tục mở rộng.

**Processing Rules:**

- **R1.** Chỉ xét các giao dịch chứa mẫu `X`.
- **R2.** Các giao dịch được gom theo độ dài; với mỗi độ dài `lk` cần biết: số giao dịch `nk` và $T_{ID}$ lớn nhất `Tk` trong nhóm.
- **R3.** `DUBO(X, k) = Σᵢ₌ₖᵘ ni × (lk/li) × f^(T_L − Tk)` và `DUBO(X) = maxₖ DUBO(X, k)`.
- **R4.** Cận trên phải thỏa: với mọi siêu mẫu `Y` của `X`, `DO(Y) ≤ DUBO(X)` (tính "an toàn" để cắt tỉa không mất mát).

## 5.9 Prune Search Space

**Purpose:** Loại bỏ các nhánh tìm kiếm không thể sinh ra DOP nhờ cận trên, giảm không gian khai phá mà không làm mất mẫu hợp lệ.

**Processing Rules:**

- **R1.** Nếu `DUBO(X) < minSup` → mọi siêu mẫu của `X` đều không thể là DOP → cắt toàn bộ nhánh mở rộng của `X`.
- **R2.** Mẫu có `Sup(X) < minSup` cũng không được dùng làm tiền tố mở rộng (theo luồng khai phá của nguồn).
- **R3.** Việc cắt tỉa chỉ được áp dụng khi đã đảm bảo không loại bỏ nhầm mẫu hợp lệ (đảm bảo tính đầy đủ kết quả).

## 5.10 Traverse Pattern Space

**Purpose:** Duyệt có hệ thống không gian mẫu theo chiều sâu (DFS) với kỹ thuật tiền tố (prefix), bảo đảm mọi mẫu tiềm năng được xem xét đúng một lần – không bỏ sót, không lặp.

**Processing Rules:**

- **R1.** Khởi đầu từ các item đơn, theo thứ tự Support tăng dần.
- **R2.** Mỗi mẫu mở rộng bằng cách kết hợp với các mẫu **đứng sau** nó trong thứ tự đã xác lập; không kết hợp ngược về trước.
- **R3.** Mở rộng chỉ tiếp tục khi cận trên của mẫu đạt ngưỡng (xem 5.9); nhánh không đạt thì dừng.
- **R4.** Việc duyệt phải phủ toàn bộ không gian mẫu khả thi của dữ liệu đã quét.

## 5.11 Discover DOPs

**Purpose:** Tổng hợp và trả về tập kết quả DOP sau khi hoàn tất khai phá.

**Processing Rules:**

- **R1.** Tập DOP gồm tất cả mẫu được đánh giá là thỏa `DO(X) ≥ minSup`.
- **R2.** Mỗi mẫu xuất hiện trong tập kết quả tối đa một lần.
- **R3.** Kết quả phải nhất quán với bộ tham số (`∂`, `f`), `T_L` và dữ liệu đã quét tại thời điểm khai phá.

---

# 6. Logical Data Requirements

Đây là những **yêu cầu dữ liệu logic** (thông tin hệ thống phải có khả năng biết/truy xuất) để thực hiện thuật toán. Chúng đặt ra ràng buộc cho tầng infrastructure nhưng **không áp đặt** infrastructure phải được xây dựng như thế nào.

| ID | Yêu cầu |
|---|---|
| **DR-01** | Hệ thống phải xác định được một transaction có chứa mẫu `X` hay không. |
| **DR-02** | Hệ thống phải xác định được độ dài của một transaction. |
| **DR-03** | Hệ thống phải xác định được $T_{ID}$ của một transaction. |
| **DR-04** | Hệ thống phải xác định được $T_{ID}$ mới nhất (`T_L`) trong toàn bộ dữ liệu đã quét. |
| **DR-05** | Hệ thống phải xác định được tổng số giao dịch đã quét (để tính `minSup`). |
| **DR-06** | Hệ thống phải truy xuất được mọi lần xuất hiện (kèm $T_{ID}$ và độ dài giao dịch) của một item/mẫu. |
| **DR-07** | Hệ thống phải đếm được Support của một item/mẫu. |
| **DR-08** | Hệ thống phải truy xuất được các giao dịch chứa một item/mẫu cùng độ dài tương ứng để tính occupancy. |
| **DR-09** | Hệ thống phải gom các lần xuất hiện theo độ dài giao dịch (để tính UBO/DUBO). |
| **DR-10** | Hệ thống phải xác định được $T_{ID}$ lớn nhất trong nhóm giao dịch có cùng độ dài. |
| **DR-11** | Hệ thống phải xác định được các transaction chung giữa hai item/mẫu (giao) để xây dựng mẫu mở rộng. |
| **DR-12** | Hệ thống phải biết trạng thái DO hiện tại của một item/mẫu và có thể tính lại khi `T_L` thay đổi. |
| **DR-13** | Hệ thống phải duy trì thứ tự các item/mẫu theo Support tăng dần phục vụ duyệt khai phá. |
| **DR-14** | Hệ thống phải biết các tham số do người dùng cung cấp (`∂`, `f`) tại thời điểm khai phá. |

---

# 7. Algorithm Workflow

Luồng thực thi end-to-end của một phiên làm việc:

**Bước 0 – Khởi tạo:** trạng thái trống; tổng số giao dịch = 0; `T_L` chưa xác định.

**Bước 1 – Nạp DB ban đầu ($DB_0$):**
1.1. Với mỗi transaction trong DB, theo thứ tự $T_{ID}$:
  - a) Ghi nhận transaction ($T_{ID}$, độ dài).
  - b) Với mỗi item: ghi nhận sự xuất hiện `($T_{ID}$, độ dài)`; tạo hồ sơ nếu item mới; cập nhật Support.
1.2. Cập nhật `T_L` = $T_{ID}$ mới nhất; cập nhật tổng số giao dịch.

**Bước 2 – Cập nhật khi có DB mới ($DB_1$, $DB_2$, …):**
2.1. Với mỗi transaction thuộc DB mới, lặp lại như 1.1 (chỉ quét phần mới).
2.2. Cập nhật `T_L`, tổng số giao dịch, và `minSup = ∂ × (tổng số giao dịch)`.

**Bước 3 – Khi có yêu cầu khai phá:**
3.1. **Tái cấu trúc DHO-List:** với mỗi item trong danh sách toàn cục:
  - a) Đặt lại DO = 0.
  - b) Với mỗi lần xuất hiện: tính occupancy theo giao dịch, nhân hệ số suy giảm với `T_L` hiện tại, cộng dồn vào DO.
3.2. Sắp xếp các item theo Support tăng dần.
3.3. **Khai phá (DFS, pattern growth):**
  - a) Duyệt lần lượt từng item theo thứ tự đã sắp; dùng nó làm tiền tố `prefix`.
  - b) Với mẫu `X = prefix ∪ {i}`:
    - Đánh giá `DO(X) ≥ minSup` → thêm `X` vào DOPs.
    - Đánh giá `DUBO(X) ≥ minSup` → tiếp tục mở rộng; ngược lại cắt tỉa.
  - c) Khi mở rộng: tìm các giao dịch chung giữa `X` và từng mẫu tiếp theo; xây dựng mẫu dài hơn; gọi đệ quy với tiền tố mới.
3.4. Trả về tập DOPs.

---

# 8. Walkthrough Example

Trích từ ví dụ chạy tay trong tài liệu nguồn [2] (Phần 5–10), dùng để kiểm chứng hiểu biết về thuật toán.

## 8.1 Tham số và dữ liệu

`f = 0.9`, `T_L = 8`, `∂ = 15%` → `minSup = 8 × 0.15 = 1.2`.

Dữ liệu: như bảng ở mục 4.3 (8 giao dịch, 3 đợt $DB_0$/$DB_1$/$DB_2$).

## 8.2 Bước 1 – Quét và xây dựng thông tin toàn cục

Sau khi quét hết `$T_1$..$T_8$`, mỗi item có tập các lần xuất hiện và Support như sau:

| Item | Support | Các lần xuất hiện ($T_{ID}$, \|T\|) |
|---|---|---|
| A | 4 | (1,4) (2,3) (7,4) (8,3) |
| B | 3 | (3,4) (5,2) (7,4) |
| C | 4 | (1,4) (3,4) (4,3) (7,4) |
| D | 4 | (1,4) (3,4) (4,3) (6,3) |
| E | 5 | (1,4) (2,3) (3,4) (6,3) (8,3) |
| F | 5 | (2,3) (4,3) (5,2) (6,3) (7,4) |
| G | 1 | (8,3) |

## 8.3 Bước 2 – Tái cấu trúc (tính DO với T_L=8, f=0.9)

Ví dụ minh họa cách tính cho 2 items:

- **A:** `DO(A) = (1/4)×0.9⁷ + (1/3)×0.9⁶ + (1/4)×0.9¹ + (1/3)×0.9⁰ = 0.1196 + 0.1771 + 0.2250 + 0.3333 = 0.8551`
- **F:** `DO(F) = (1/3)×0.9⁶ + (1/3)×0.9⁴ + (1/2)×0.9³ + (1/3)×0.9² + (1/4)×0.9¹ = 1.2553`

Kết quả DO các item + thứ tự sắp xếp theo Support tăng dần:

| Thứ tự | Item | Support | DO |
|---|---|---|---|
| 1 | G | 1 | 0.3333 |
| 2 | B | 3 | 0.7371 |
| 3 | A | 4 | 0.8551 |
| 4 | C | 4 | 0.7109 |
| 5 | D | 4 | 0.7559 |
| 6 | E | 5 | 1.0477 |
| 7 | F | 5 | 1.2553 |

Thứ tự duyệt: `G ≺ B ≺ A ≺ C ≺ D ≺ E ≺ F` (thứ tự item cùng Support, ví dụ A–C–D, theo thứ tự xác lập từ nguồn).

## 8.4 Bước 3 – Khai phá DFS (minSup = 1.2)

Các bước chính:

- **G:** `Sup = 1`, `DUBO(G) = 1.0 < 1.2` → cắt tỉa.
- **B:** `DO(B) = 0.7371` (chưa đủ DOP), `DUBO(B) = 1.8 ≥ 1.2` → mở rộng các nhánh BA, BC, BD, BE, BF; tất cả không đạt `minSup`, BF bị cắt bởi `DUBO(BF) = 1.0935 < 1.2`.
- **A:** `DO(A) = 0.8551`, `DUBO(A) = 3.5` → mở rộng; trong các nhánh:
  - **AE:** `DO(AE) = 1.2601 ≥ 1.2` → **LÀ DOP**.
  - Các nhánh còn lại (AC, AD, AF, …) đều bị cắt/không đạt.
- **C:** `DO(C) = 0.7109`, `DUBO(C) = 2.7` → mở rộng; các nhánh CD, CE, CF mở rộng nhưng không có DOP (CDE bị cắt bởi `DUBO(CDE) = 1.181 < 1.2`).
- **D:** `DO(D) = 0.7559`, `DUBO(D) = 2.835` → mở rộng; các nhánh DE, DF không đạt.
- **E:** `DO(E) = 1.0477 < 1.2` (không là DOP), `DUBO(E) = 4.5` → mở rộng; nhánh EF không đạt.
- **F:** `DO(F) = 1.2553 ≥ 1.2` → **LÀ DOP** (item cuối, không mở rộng thêm).

## 8.5 Kết quả cuối cùng

```
DOPs = { AE (DO = 1.2601),  F (DO = 1.2553) }
```

- Tổng số mẫu được duyệt: 32
- Tổng số mẫu bị cắt tỉa: 19
- Tổng số DOP: 2

Ghi chú minh họa ý tưởng "suy giảm": nếu không dùng hệ số suy giảm (`f = 1.0` – HOP truyền thống), các mẫu như `E (O = 1.5)` sẽ trở thành HOP dù chủ yếu xuất hiện ở dữ liệu cũ; với mô hình suy giảm (`f = 0.9`) chúng không còn là DOP.

---

# 9. Formula Reference

| STT | Tên | Công thức |
|---|---|---|
| F1 | Occupancy theo giao dịch | $$O(X, T_d) = \frac{\|X\|}{\|Td\|}$$ |
| F2 | Tổng Occupancy | `O(X) = Σ O(X, Td)`, mọi `Td` chứa `X` |
| F3 | Upper Bound of Occupancy | `UBO(X, x) = Σᵢ₌ₓᵘ ni × (lx/li)`; `UBO(X) = maxₓ UBO(X, x)` |
| F4 | Hệ số suy giảm | `dF(Td) = f^(T_L − Td)`, `0 < f < 1` |
| F5 | Damped Occupancy theo giao dịch | `DO(X, Td) = O(X, Td) × f^(T_L − Td)` |
| F6 | Tổng Damped Occupancy | `DO(X) = Σ DO(X, Td)` |
| F7 | Điều kiện DOP | `X ∈ DOPs ⇔ DO(X) ≥ minSup` |
| F8 | Ngưỡng tối thiểu | `minSup = ∂ × |DB|` |
| F9 | Damped Upper Bound Occupancy | `DUBO(X, k) = Σᵢ₌ₖᵘ nᵢ × (lk/li) × f^(T_L − Tk)`; `DUBO(X) = maxₖ DUBO(X, k)` |

---

# 10. Glossary

## 10.1 Symbol Reference

| Ký hiệu | Ý nghĩa |
|---|---|
| `DB` | Một đợt / một tập các giao dịch (database) |
| `Td` | Một giao dịch (transaction) có $T_{ID}$ = d |
| `T_L` | $T_{ID}$ của giao dịch mới nhất đã quét (latest $T_{ID}$) |
| `|Td|` | Độ dài của giao dịch Td |
| `I` | Tập các item phân biệt của toàn CSDL |
| `X` | Một mẫu (pattern), thường là tập item |
| `|X|` | Độ dài (số item) của mẫu X |
| `Sup(X)` | Số giao dịch chứa X (support) |
| `O(X)` | Occupancy của X |
| `UBO(X)` | Upper bound occupancy của X |
| `f` | Hệ số suy giảm (decaying factor constant) |
| `DO(X)` | Damped occupancy của X |
| `DUBO(X)` | Damped upper bound occupancy của X |
| `L` | Tập độ dài giao dịch phân biệt (sắp tăng) chứa X |
| `li` | Độ dài thứ i trong L |
| `ni` | Số giao dịch có độ dài `li` (hoặc `nk` cho độ dài `lk`) |
| `Tk` | $T_{ID}$ lớn nhất trong nhóm giao dịch độ dài `lk` |
| `∂` | Ngưỡng phần trăm do người dùng đặt |
| `minSup` | Ngưỡng tối thiểu khi khai phá |

## 10.2 Terminology Reference

| Thuật ngữ | Định nghĩa |
|---|---|
| Item | Đơn vị dữ liệu tối giản xuất hiện trong giao dịch |
| Transaction | Một tập item xuất hiện cùng nhau, có $T_{ID}$ và độ dài |
| Pattern | Một tập item được khảo sát |
| Support | Số giao dịch chứa mẫu |
| Occupancy | Tỷ lệ mẫu chiếm trong các giao dịch chứa nó |
| Decaying factor | Hệ số làm giảm trọng số của dữ liệu cũ theo thời gian |
| Damped Occupancy | Occupancy có nhân hệ số suy giảm – thước đo chính của bài toán |
| UBO | Cận trên của Occupancy dùng để cắt tỉa |
| DUBO | Cận trên của Damped Occupancy dùng để cắt tỉa |
| HOP | Mẫu chiếm dụng cao (High Occupancy Pattern) – không suy giảm |
| DOP | Mẫu chiếm dụng cao có suy giảm (Damped High Occupancy Pattern) |
| DOPs | Tập các DOP |
| DHO-List | Khái niệm logic về khả năng lưu giữ/truy xuất thông tin phục vụ khai phá |
| One-scan | Nguyên tắc chỉ quét mỗi giao dịch một lần, không quét lại dữ liệu cũ |
| Pruning | Cắt tỉa không gian tìm kiếm dựa trên cận trên |

---

*Hết tài liệu. Mọi thay đổi về ngữ nghĩa miền phải được cập nhật đồng bộ giữa tài liệu này và tài liệu Infrastructure.*