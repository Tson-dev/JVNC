# DHOPM/DOPM – Infrastructure Specification (Hướng tiếp cận OOP)

> Tài liệu này mô tả **kiến trúc và thiết kế đối tượng (Object-Oriented)** cho hệ thống khai phá DHOPM/DOPM, dựa trên tài liệu đặc tả miền `DHOPM-Domain-Specification.md`. Các thành phần được mô tả ở mức **logic, độc lập ngôn ngữ** (không có cú pháp của riêng Java/C#/Python). Tài liệu triển khai Java cụ thể sẽ được tạo riêng sau này.

---

## Document Header

| Mục | Giá trị |
|---|---|
| **Document Name** | DHOPM / DOPM – Infrastructure Specification (OOP Design) |
| **Document ID** | DHOPM-INF-001 |
| **Version** | 0.1 (Draft thử nghiệm) |
| **Status** | Draft – chờ review |
| **Source Documents** | [1] `DHOPM-Domain-Specification.md` (đặc tả miền – nguồn chính)<br/>[2] Paper nguồn (`1-s2_0-S095219762600792X-main.md`)<br/>[3] Ví dụ chạy tay (`Nhom01_VDChayTay.md`) |

### Revision History

| Phiên bản | Mô tả |
|---|---|
| 0.1 | Draft đầu tiên – thiết kế OOP thử nghiệm, ngôn ngữ trung lập |

---

# 1. Scope & Mục tiêu

## 1.1 Mục tiêu

- Cung cấp một **bản thiết kế kiến trúc** (ở mức đối tượng/trách nhiệm) để triển khai thuật toán theo hướng lập trình OOP.
- Mô tả: kiến trúc tổng thể, các thành phần (component/class), **đối tượng mỗi thành phần như thế nào, làm gì, input/output của các phương thức chính**.
- Làm rõ luồng phối hợp giữa các đối tượng cho 4 bối cảnh: xây dựng, cập nhật, tái cấu trúc, khai phá.
- Đảm bảo **truy vết được** về các Domain Capability / LDR trong tài liệu miền.

## 1.2 Ranh giới

Tài liệu này **quyết định**:

- Phân chia trách nhiệm (responsibility) giữa các đối tượng.
- Kiến trúc lớp (layers) và luồng gọi giữa các đối tượng.
- Các phương thức chính: tên gợi nhớ, tham số, kiểu trả về (dạng trừu tượng), ngữ nghĩa.

Tài liệu này **chưa quyết định**:

- Cú pháp code cụ thể (Java/C#/…).
- Lựa chọn framework, dependency injection, logging, config, DB.
- Chi tiết tối ưu hoá bộ nhớ/hiệu năng mang tính tinh chỉnh (ghi lại thành ghi chú).

## 1.3 Nguyên tắc thiết kế

- **P1 – Một lần quét (one-scan):** dữ liệu chỉ được đọc từ nguồn đúng một lần khi nạp.
- **P2 – Trạng thái luôn sẵn sàng:** sau khi quét, toàn bộ thông tin cần thiết nằm trong bộ nhớ (repository), đáp ứng DR-01…DR-14 ở mức logic.
- **P3 – Append-only:** cập nhật chỉ thêm thông tin mới, không quét lại; DO chỉ được tái tính ở giai đoạn reconstruction (R4 của capability 5.5).
- **P4 – Tách bạch đọc dữ liệu / tính toán / điều phối:** đọc ở tầng nhập liệu; sửa đổi trạng thái ở tầng model; nghiệp vụ ở tầng service; điều phối ở tầng engine.
- **P5 – Đối tượng bất biến cho "bản ghi" (value objects):** Transaction, Occurrence, Parameters là immutable – giảm lỗi khi truyền giữa các class.
- **P6 – Minh bạch về nguồn tài liệu:** mỗi thành phần đều truy vết về mục tương ứng của tài liệu miền (≈ derive từ domain).

---

# 2. Kiến trúc tổng thể

## 2.1 Tổng quan lớp (layers)

```
┌─────────────────────────────────────────────────────────────┐
│  INPUT LAYER (Nhập liệu)                                     │
│  • TransactionSource: đọc transaction từ nguồn dữ liệu tuần tự│
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│  MODEL LAYER (Mô hình dữ liệu – đối tượng bất biến / state)   │
│  • Transaction • Occurrence • Parameter                      │
│  • DHONode • DHOList (global) • ConditionalDHOList           │
│  • LengthGroup • ResultPattern                               │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│  CORE SERVICE LAYER (Nghiệp vụ thuật toán)                   │
│  • DHOListBuilder (xây/cập nhật global list)                 │
│  • Reconstructor (tính DO + sắp xếp)                         │
│  • MetricCalculator (O, DO, dF)                              │
│  • DUBOCalculator (cận trên)                                 │
│  • ConditionalListBuilder (giao transaction → mẫu mở rộng)    │
│  • Miner (DFS pattern growth)                                │
└──────────────────────────────┬──────────────────────────────┘
                               ▼
┌─────────────────────────────────────────────────────────────┐
│  ORCHESTRATION LAYER (Điều phối)                             │
│  • MiningEngine: vòng đời – nạp DB → update TL/minSup →       │
│    reconstruct → mine → trả kết quả                          │
└─────────────────────────────────────────────────────────────┘
```

## 2.2 Luồng gọi tổng thể (sequence cấp cao)

```
MiningEngine
   │ 1. source = new TransactionSource(...)
   │ 2. list = new DHOList()
   │ 3. processBatch(batch):
   │      → DHOListBuilder.scanAndAppend(list, transactions)
   │      → engine.updateTL(); engine.updateMinSup(totalSize, ∂)
   │ 4. mining requested:
   │      → Reconstructor.reconstruct(list, tl, f)
   │      → Miner.mine(list, emptyPrefix, tl, f, minSup) → DOPs
   │ 5. trả DOPs (ResultPattern list) cho người gọi
```

## 2.3 Bản đồ trách nhiệm (capability → component)

| Domain Capability | Thành phần OOP chính |
|---|---|
| Record Transaction | `TransactionSource`, `Transaction` |
| Record Item Occurrence | `DHOListBuilder` + `DHONode.addOccurrence` |
| Maintain Pattern Statistics | `DHONode` (support), `DHOList.createOrGetNode` |
| Calculate Occupancy | `MetricCalculator.computeOccupancy` |
| Calculate Damped Occupancy | `MetricCalculator.computeDampedOccupancy` |
| Generate Extended Pattern | `ConditionalListBuilder` |
| Evaluate Pattern | `Miner.evaluate` |
| Estimate Upper Bound | `DUBOCalculator` |
| Prune Search Space | `Miner` (quyết định cắt) + `DUBOCalculator` |
| Traverse Pattern Space | `Miner` (DFS duyệt) |
| Discover DOPs | `Miner`, `ResultPattern`, `MiningEngine` |

---

# 3. Thiết kế chi tiết từng thành phần

Quy ước ký hiệu phương thức (dạng trừu tượng):

```
tênPhươngThức(thamSố: Kiểu) → Kiểu trả về
   Mô tả ngữ nghĩa.
```

Mọi ví dụ số trong mục này dùng bộ dữ liệu chuẩn của tài liệu miền (f=0.9, TL=8, ∂=15%, minSup=1.2).

---

## 3.1 `Parameter` (value object – bất biến)

**Trách nhiệm:** đóng gói các tham số người dùng và trạng thái tiến trình. Đáp ứng DR-14, DR-05.

**Thuộc tính chính:**

| Tên | Kiểu (trừu tượng) | Ý nghĩa |
|---|---|---|
| `delta` | số thực | `∂` – tỷ lệ ngưỡng (0 ≤ ∂ ≤ 1) |
| `decayFactor` | số thực | `f` – hệ số suy giảm (0 < f < 1) |
| `totalTransactionCount` | số nguyên | tổng số giao dịch đã quét |
| `latestTid` | số nguyên | `TL` – TID mới nhất |

**Phương thức:**

```
computeMinSup(totalTransactionCount: int) → double
   minSup = ∂ × totalTransactionCount. (F8 – tài liệu miền)
```

**Ghi chú:** nên để `minSup` tính mới mỗi lần cần dùng thay vì lưu trạng thái dễ lỗi thời (theo R3 của Evaluate Pattern).

---

## 3.2 `Transaction` (value object – bất biến)

**Trách nhiệm:** biểu diễn một giao dịch khi đọc từ nguồn và được quét. Đáp ứng DR-01…DR-03.

**Thuộc tính chính:**

| Tên | Kiểu | Ý nghĩa |
|---|---|---|
| `tid` | số nguyên | TID (tăng dần, duy nhất) |
| `items` | tập các item | các item phân biệt của giao dịch |
| `length` | số nguyên | `|Td|` – suy ra từ `items` |

**Phương thức:**

```
contains(item: Item) → boolean
   Giao dịch có chứa item không? (dr-01 mức transaction)
```

**Bất biến (invariant):** `length == items.size()`, các item phân biệt (R3 – Record Transaction).

---

## 3.3 `Occurrence` (value object – bất biến)

**Trách nhiệm:** biểu diễn **một lần xuất hiện** của một item/mẫu trong một giao dịch: gồm TID và độ dài giao dịch. Đáp ứng DR-06, DR-08.

**Thuộc tính chính:**

| Tên | Kiểu | Ý nghĩa |
|---|---|---|
| `tid` | số nguyên | TID của giao dịch |
| `transactionLength` | số nguyên | độ dài `|Td|` của giao dịch |

Tương ứng nhận thức miền: cặp `(TID, TLen)` trong tài liệu nguồn.

---

## 3.4 `DHONode`

**Trách nhiệm:** hồ sơ của **một item** trong DHO-List: tên item, tập các lần xuất hiện, support, và giá trị DO hiện hành. Đáp ứng DR-06, DR-07, DR-12.

**Thuộc tính chính:**

| Tên | Kiểu | Ý nghĩa |
|---|---|---|
| `item` | Item | tên/định danh item (mẫu nút lá) |
| `occurrences` | tập `Occurrence` | mọi lần xuất hiện đã quét |
| `support` | số nguyên | số giao dịch chứa item |
| `doValue` | số thực | DO hiện hành (sau reconstruction mới có giá trị) |

**Phương thức:**

```
addOccurrence(occ: Occurrence) → void
   Bổ sung một lần xuất hiện; tăng support 1; không sửa các lần xuất hiện cũ.
   (R1, R3 – Record Item Occurrence; R1 – Maintain Pattern Statistics)

resetDO() → void
   Đặt doValue = 0. (bước tái cấu trúc)

accumulateDO(entry: Occurrence, dF: double) → void
   doValue += (1 / entry.transactionLength) × dF.
   (tương ứng F1×F4 mức nút đơn)

nodeSupports(minSup: double) → boolean
   support ≥ minSup ? (quyết định tiền tố trong mining – R2 – Prune Search Space)
```

**Ghi chú ủy quyền:** việc "tính DO" nằm ở `MetricCalculator`/`Reconstructor`; `DHONode` chỉ lưu và cập nhật giá trị. Giữ node "ngu" (anemic-ish) gọn trách nhiệm, dễ test.

---

## 3.5 `DHOList` (danh sách toàn cục)

**Trách nhiệm:** quản lý tập hợp các `DHONode` tương ứng mọi item đã quét; cho phép tra cứu/tạo/sắp xếp. Đáp ứng DR-04…DR-13 ở mức danh sách toàn cục.

**Thuộc tính chính:**

| Tên | Kiểu | Ý nghĩa |
|---|---|---|
| `nodes` | tập hợp `DHONode` có thứ tự | các hồ sơ item |

**Phương thức:**

```
findNode(item: Item) → DHONode | null
   Truy vết hồ sơ item (trả null nếu chưa có).

createOrGetNode(item: Item) → DHONode
   Nếu chưa có → tạo node mới (doValue=0, support=0, rỗng occurrences) và thêm vào danh sách.
   Ngược lại trả về node hiện có. (R2, R3 – Record Item Occurrence)

sortBySupportAscending() → void
   Sắp xếp nodes theo support tăng dần. (DR-13; bước tái cấu trúc)

iterator() → luồng duyệt DHONode
   Duyệt theo thứ tự hiện tại (dùng bởi Reconstructor và Miner).
```

**Ghi chú lược đồ triển khai** (bàn cho tầng implementation sau, không bắt buộc bây giờ): nên dùng cấu trúc tra cứu item→node cho `findNode` (ví dụ bảng băm) **và** giữ thứ tự list độc lập để sắp xếp; tài liệu Java sẽ quyết định cụ thể.

---

## 3.6 `ConditionalDHOList`

**Trách nhiệm:** đại diện cho một **DHO-List con của một nhánh khai phá** (các mẫu có cùng tiền tố). Về cấu trúc giống `DHOList`; nét riêng là chỉ chứa các mẫu "mở rộng" và được tạo bởi `ConditionalListBuilder`. Đáp ứng DR-11.

**Phương thức thừa kế/dùng lại:** `findNode`, `sortBySupportAscending`, `iterator` (kế thừa từ `DHOList` nếu là lớp con, hoặc tái dùng cùng cấu trúc bên trong tùy tầng implementation).

---

## 3.7 `TransactionSource`

**Trách nhiệm:** đọc các `Transaction` từ nguồn dữ liệu (file, stream, …) **tuần tự, mỗi giao dịch một lần** – enforce P1. Đáp ứng DR-02, DR-03.

**Phương thức:**

```
open(sourceDescriptor) → void
   Chuẩn bị nguồn đọc.

readNext() → Transaction | null
   Trả về giao dịch tiếp theo theo thứ tự TID tăng dần; null khi hết.

close() → void
   Giải phóng nguồn.
```

---

## 3.8 `DHOListBuilder`

**Trách nhiệm:** thực hiện giai đoạn **Xây dựng / Cập nhật** danh sách toàn cục: quét transaction, ghi nhận sự xuất hiện, tạo node mới nếu cần, tăng support. Đáp ứng các capability 5.1, 5.2, 5.3. (Truy vết Bước 1–2 của Algorithm Workflow.)

**Phương thức:**

```
scanAndAppend(list: DHOList, transactions: luồng Transaction, currentTidBase: int) → void
   Với mỗi transaction T:
     • node = list.createOrGetNode(mỗi item trong T)
     • node.addOccurrence(new Occurrence(T.tid, T.length))
   Chỉ quét phần mới; không đụng dữ liệu cũ. (P3; Bước 1.1/2.1 workflow)
```

**Ghi chú cohesions:** builder `không biết` TL/minSup; chỉ ghi nhận dữ liệu thô.

---

## 3.9 `Reconstructor`

**Trách nhiệm:** thực hiện giai đoạn **Tái cấu trúc**: tính lại `DO` cho từng node theo `TL` và `f` hiện tại, rồi sắp xếp theo support tăng dần. Đáp ứng capability 5.5 (mức danh sách) + DR-12, DR-13. (Truy vết Bước 3.1–3.2 workflow.)

**Phương thức:**

```
reconstruct(list: DHOList, tl: int, f: double) → void
   Với mỗi node trong list:
     • node.resetDO()
     • cho mỗi occurrence occ trong node:
         dF = pow(f, tl - occ.tid)
         node.accumulateDO(occ, dF)
   list.sortBySupportAscending()
```

**Yêu cầu đúng đắn:**
- DO phải **luôn được tính lại từ 0** (không cộng dồn giá trị cũ) vì `TL` đã đổi (R4 – Calculate Damped Occupancy).
- Thứ tự sau sắp xếp quyết định thứ tự duyệt của `Miner`.

---

## 3.10 `MetricCalculator`

**Trách nhiệm:** cung cấp các phép đo thuần túy (pure functions) – occupancy, hệ số suy giảm, damped occupancy. Đáp ứng capability 5.4, 5.5, F1–F6.

**Phương thức:**

```
decayFactor(f: double, tl: int, tid: int) → double
   f^(TL − Td). (F4)

computeOccupancy(patternLength: int, transactionLength: int) → double
   |X| / |Td|. (F1)

computeDampedOccupancy(patternLength: int, transactionLength: int, dF: double) → double
   (|X| / |Td|) × dF. (F5)
```

**Ghi chú:** giữ thuần túy (không trạng thái) giúp đơn vị hoá dễ và khớp 1-1 với công thức trong tài liệu miền.

---

## 3.11 `DUBOCalculator`

**Trách nhiệm:** tính cận trên `DUBO(X)` cho một `DHONode` (mẫu) dựa trên tập lần xuất hiện của nó. Đáp ứng capability 5.8, F9, DR-09, DR-10.

**Phương thức:**

```
compute(node: DHONode, tl: int, f: double) → double
   Bước 1: gom các occurrence theo transactionLength (nhóm LengthGroup)
          với mỗi nhóm giữ: length, count = số occurrence, lastTid = TID lớn nhất.
          (DR-09, DR-10)
   Bước 2: sắp xếp nhóm theo length tăng dần → L = {l1…lu}.
   Bước 3: dubo = 0
          với mỗi nhóm k (1…u):
             sum = 0
             với mỗi nhóm i ≥ k:
                sum += countᵢ × (l_k / l_i) × pow(f, tl − T_k)
             dubo = max(dubo, sum)
   Bước 4: trả dubo.  (F9)
```

**Ràng buộc đúng đắn:** phải thỏa `DO(Y) ≤ DUBO(X)` với mọi siêu mẫu `Y` của `X` – nếu vi phạm nghĩa là lỗi cắt tỉa gây mất mát mẫu (R4 – Estimate Upper Bound; Obj-3).

---

## 3.12 `ConditionalListBuilder`

**Trách nhiệm:** xây dựng **mẫu mở rộng** và một `ConditionalDHOList` cho mức kế tiếp: từ hai node (mẫu tiền tố `X` và mẫu ứng viên `Y`) tìm các **giao dịch chung**, tạo `DHONode` mới cho `X ∪ Y` kèm DO đã tính. Đáp ứng capability 5.6, DR-11.

**Phương thức:**

```
build(nextLevelList: ConditionalDHOList, left: DHONode, right: DHONode,
      prefixLength: int, tl: int, f: double) → DHONode | null
   • Duyệt song song các occurrence của left và right (đã được sắp theo TID).
   • Với mỗi TID chung:
       tạo Occurrence(tidChung, transactionLength bên trái)
       dF = pow(f, tl − tidChung)
       (prefixLength + 2) / transactionLength × dF → cộng vào doValue của node mới.
   • Nếu không có TID chung → trả null (không thêm gì).
   • Ngược lại tạo node mới (item = item của right) đưa vào nextLevelList.
   (Bước 3.3.c workflow)
```

**Ghi chú:** `prefixLength` là độ dài mẫu tiền tố `X`; độ dài mẫu mới = `|X|+1` phục vụ tính occupancy (nhìn theo F5 với đối số pattern length).

---

## 3.13 `Miner`

**Trách nhiệm:** thực hiện **khai phá DFS pattern-growth**: duyệt danh sách, đánh giá DOP, quyết định mở rộng/cắt tỉa, gọi đệ quy trên conditional list. Đáp ứng capability 5.7, 5.9, 5.10, 5.11. (Truy vết Bước 3.3 workflow; tương đương `Mine(CL, Pref, TL)` nguồn.)

**Thuộc tính chính (cấu hình 1 lần khi tạo):**

| Tên | Ý nghĩa |
|---|---|
| `minSup` | ngưỡng hiện tại |
| `tl` | TID mới nhất |
| `f` | hệ số suy giảm |

**Phương thức:**

```
mine(currentList: DHOList | ConditionalDHOList, prefix: tập Item) → tập ResultPattern
   Với mỗi node i trong currentList (theo thứ tự đã sắp):
     • nodeSupports(minSup)? nếu không → bỏ qua node.        (R2 – Prune)
     • Compute DO trực tiếp từ node.doValue (chỉ hợp lệ với global list sau reconstruction);
       Với conditional: nhờ DO được builder tính sẵn khi tạo node.
     • Nếu DO ≥ minSup → tạo ResultPattern(prefix ∪ {i}, DO, transactions) → thêm vào kết quả.
       (R1, R2 – Evaluate Pattern)
     • dubo = DUBOCalculator.compute(node, tl, f)
         - dubo ≥ minSup →
             ncl = ConditionalDHOList mới
             với mỗi node j đứng sau i trong currentList:
                merged = ConditionalListBuilder.build(ncl, node_i, node_j, |prefix|, tl, f)
                (merged == null → bỏ nhánh)
             nếu ncl không rỗng →
                kết quả += mine(ncl, prefix ∪ {i})
         - ngược lại → cắt tỉa nhánh (dừng không mở rộng).   (R1 – Prune)
   trả về tập kết quả của nhánh này.
```

**Đảm bảo (invariant):** mỗi mẫu chỉ xuất hiện một lần (R2 – Discover DOPs); việc cắt tỉa không làm mất mẫu hợp lệ (R3 – Prune Search Space).

---

## 3.14 `ResultPattern` (value object – bất biến)

**Trách nhiệm:** biểu diễn một kết quả DOP.

**Thuộc tính chính:**

| Tên | Kiểu | Ý nghĩa |
|---|---|---|
| `items` | tập Item | mẫu `X` |
| `doValue` | double | `DO(X)` |
| `transactions` | tập TID (hoặc các Occurrence) | các giao dịch chứa mẫu – phục vụ báo cáo/kiểm thử |

---

## 3.15 `MiningEngine` (Orchestrator)

**Trách nhiệm:** điều phối toàn bộ vòng đời; giữ `Parameter`, `DHOList` toàn cục; gọi builder khi nạp DB; gọi reconstruct + mine khi có yêu cầu; trả kết quả. (Truy vết toàn bộ Algorithm Workflow mục 7 của tài liệu miền.)

**Thuộc tính chính:**

| Tên | Kiểu | Ý nghĩa |
|---|---|---|
| `parameter` | `Parameter` | ∂, f, tổng số giao dịch, TL |
| `globalList` | `DHOList` | danh sách toàn cục – **giữ xuyên suốt** các đợt dữ liệu |
| `source` | `TransactionSource` | nguồn dữ liệu hiện tại |
| `minSup` | double | tính lại khi nạp DB mới |

**Phương thức:**

```
init(delta: double, f: double) → void
   Khởi tạo parameter; globalList rỗng; totalTransactionCount=0; TL chưa xác định.
   (Bước 0 workflow)

loadBatch(transactions: luồng Transaction) → void
   • DHOListBuilder.scanAndAppend(globalList, transactions)
   • parameter.totalTransactionCount += số giao dịch
   • parameter.latestTid = TID lớn nhất vừa thấy (lấy từ source/transaction cuối)
   • minSup = parameter.computeMinSup(total…)        (Mục 4.4–F8)
   (Bước 1/2 workflow)

mineNow() → tập ResultPattern
   • Reconstructor.reconstruct(globalList, tl, f)
   • miner = new Miner(minSup, tl, f)
   • return miner.mine(globalList, rỗng)
   (Bước 3 workflow)
```

---

# 4. Mô tả luồng phối hợp chi tiết

## 4.1 Kịch bản A – Nạp DB ban đầu (DB0)

```
engine.loadBatch(T1..T4)
   → DHOListBuilder.scanAndAppend(globalList, T1..T4)
        T1: tạo node A, C, D, E; addOccurrence(1,4); support=1 mỗi node do addOccurrence tự tăng.
        T2: A,E đã có → addOccurrence(2,3); F → tạo node mới.
        T3: tạo B; C,D,E addOccurrence(3,4).
        T4: C,D,F addOccurrence(4,3).
   → totalTransactionCount=4; latestTid=4.
```

**Kết quả mong đợi (khớp nguồn – Phần 5):** support A=2, B=1, C=3, D=3, E=3, F=2; mỗi node có đúng tập occurrence.

## 4.2 Kịch bản B – Cập nhật DB1, DB2

```
engine.loadBatch(T5..T6) ; engine.loadBatch(T7..T8)
   → chỉ quét phần mới; node đã có chỉ addOccurrence.
   → T8: tạo node G (mới).
   → total=8; latestTid=8; minSup = 0.15×8 = 1.2.
```

**Kiểm chứng:** không có node nào bị quét lại; occurrences cũ giữ nguyên (P3).

## 4.3 Kịch bản C – Reconstruction (TL=8, f=0.9)

```
engine.mineNow()
   → Reconstructor.reconstruct(globalList, 8, 0.9)
        node A: resetDO; (1/4)×0.9⁷ + (1/3)×0.9⁶ + (1/4)×0.9¹ + (1/3)×0.9⁰ = 0.8551
        node G: (1/3)×0.9⁰ = 0.3333
        … (khớp Phần 8.1 nguồn)
   → sortBySupportAscending → G(1) ≺ B(3) ≺ A(4) ≺ C(4) ≺ D(4) ≺ E(5) ≺ F(5)
```

## 4.4 Kịch bản D – Khai phá DFS (minSup=1.2)

```
Miner.mine(globalList, ∅)
   G : support=1 <1.2 → bỏ qua.
   B : DO = 0.7371 <1.2; DUBO(B) = 1.8 ≥1.2 → mở rộng:
        BA, BC, BD, BE, BF → ConditionalListBuilder tạo node,
        DO < minSup; BF: DUBO=1.0935 <1.2 → cắt.
   A : DO=0.8551; DUBO=3.5 → mở rộng:
        AE: DO=1.2601 ≥1.2 → DOP ✓
        AC, AD, AF: không đạt/cắt.
   C : mở rộng CD, CE, CF … không DOP; CDE cắt (DUBO=1.181).
   D : mở rộng DE, DF … không DOP.
   E : DO=1.0477 <1.2; mở rộng EF không đạt.
   F : DO=1.2553 ≥1.2 → DOP ✓ (node cuối, không mở rộng).
→ Kết quả: {AE: 1.2601, F: 1.2553}  (khớp Phần 9–10 nguồn)
```

## 4.5 Kịch bản E – Conditional list mức 2 (minh họa đệ quy)

```
Nhánh C: miner.mine(condList C, {C})
   CD: DO=0.9718 (khớp 9.1), DUBO(CD)=1.6402 ≥1.2 → mở rộng
        CDE: ConditionalListBuilder.build(nodeCD, nodeCE, prefixLen=2, 8, 0.9)
             → TID chung {1,3}; DO = 0.8016; DUBO(CDE)=1.181 <1.2 → cắt.
        CDF: TID chung rỗng → null (không thêm).
   CE: DO=0.5344, DUBO=1.181 <1.2 → cắt.
   CF: DO=0.8874, DUBO=1.1482 <1.2 → cắt.
```

---

# 5. Hợp đồng Input/Output tóm tắt (bảng)

| Component | Phương thức | Input | Output | Ghi chú (liên hệ miền) |
|---|---|---|---|---|
| `Parameter` | `computeMinSup` | totalCount | double | F8 |
| `Transaction` | `contains` | item | boolean | DR-01 |
| `DHONode` | `addOccurrence` | Occurrence | void | 5.2.R1/R3; 5.3.R1 |
| `DHONode` | `accumulateDO` | Occurrence, dF | void | F5 (nút đơn) |
| `DHOList` | `createOrGetNode` | item | DHONode | 5.2.R2/R3; 5.3 |
| `DHOList` | `sortBySupportAscending` | – | void | DR-13; 5.10.R1 |
| `TransactionSource` | `readNext` | – | Transaction\|null | P1; DR-02/03 |
| `DHOListBuilder` | `scanAndAppend` | list, transactions | void | Bước 1–2 workflow |
| `Reconstructor` | `reconstruct` | list, tl, f | void | 5.5; Bước 3.1–3.2 |
| `MetricCalculator` | `computeDampedOccupancy` | lenX, lenT, dF | double | F5 |
| `DUBOCalculator` | `compute` | node, tl, f | double | 5.8; F9; DR-09/10 |
| `ConditionalListBuilder` | `build` | ncl, left, right, prefixLen, tl, f | DHONode\|null | 5.6; DR-11 |
| `Miner` | `mine` | list, prefix | tập ResultPattern | 5.7/5.9/5.10/5.11 |
| `MiningEngine` | `loadBatch` | transactions | void | Bước 1/2 |
| `MiningEngine` | `mineNow` | – | tập ResultPattern | Bước 3 |

---

# 6. Bất biến & các trường hợp biên

## 6.1 Bất biến hệ thống

- **INV-1:** `parameter.latestTid` luôn bằng TID lớn nhất đã quét.
- **INV-2:** tổng support các node ≥ số giao dịch * số item trung bình; không được vượt quá (không đếm trùng item trong cùng giao dịch – R3 Record Transaction).
- **INV-3:** sau reconstruction, thứ tự node trong globalList theo support tăng dần trước khi vào mining.
- **INV-4:** `DO` của node chỉ có nghĩa tại đúng `TL`/`f` dùng để tính; sau `loadBatch` phải tái reconstruct trước khi đọc (R4 – 5.5).

## 6.2 Trường hợp biên & xử lý đề xuất

| Tình huống | Đối tượng chịu trách nhiệm | Hành vi dự kiến |
|---|---|---|
| Giao dịch rỗng (0 item) | `TransactionSource`/`Transaction` | Loại bỏ hoặc từ chối (độ dài = 0 làm phép chia lỗi). |
| Item trùng trong transaction | `Transaction` (normalize khi tạo) | Chuẩn hóa thành tập item phân biệt; length = size sau normalize. |
| TID không tăng dần | `MiningEngine` | Báo lỗi rõ ràng (vi phạm P1/thứ tự). |
| Card user nhập ∂ hoặc f ngoài khoảng | `Parameter` | Kiểm tra ràng buộc khi init (0≤∂≤1; 0<f<1) → ném/báo lỗi. |
| TID chung không tồn tại khi mở rộng | `ConditionalListBuilder` | Trả null; node không được thêm (kịch bản E: CDF). |
| DUBO < minSup ngay node đầu | `Miner` | Cắt tỉa, không tạo conditional list. |
| Không có DOP nào | `Miner` | Trả tập rỗng (hợp lệ, không phải lỗi). |
| Số học làm tròn tới hạn DO≈minSup | `MetricCalculator`/`Parameter` | Ghi nhận quy ước làm tròn (đề xuất: so sánh với sai số ε nhỏ hoặc dùng kiểu thập phân đủ chính xác) – cần thống nhất ở tài liệu Java. |

---

# 7. Mở rộng & công việc tiếp theo

- **TODO-1:** tài liệu **Module** – triển khai trong bối cảnh cụ thể (khi có yêu cầu).
- **TODO-2:** tài liệu **Java Design/Implementation** – quyết định: ngôn ngữ, framework, mapping class ↔ kiểu dữ liệu Java (sẽ dịch các kiểu trừu tượng ở mục 3 thành class/interface/record Java), xử lý số thực, logging, v.v.
- **TODO-3:** kiểm thử module theo các Test Case trong `Nhom01_VDChayTay.md` (Phần 11) – các kịch bản A–E ở mục 4 chính là test acceptance cấp module.
- **TODO-4:** cân nhắc thống nhất thứ tự ưu tiên khi support bằng nhau (hiện đang theo nguồn: A≺C≺D) – ghi rõ trong tài liệu Java để kết quả tái tạo được bit-for-bit.

---

*Hết tài liệu.*