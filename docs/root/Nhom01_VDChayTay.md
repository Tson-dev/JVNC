# PHÂN TÍCH THUẬT TOÁN DOPM VÀ VÍ DỤ CHẠY TAY

Damped High Occupancy Pattern Mining

Công thức, quy trình khai phá và ví dụ minh họa chi tiết từng bước

Tài liệu phân tích nội bộ

## Mục lục

Mục lục
Bảng Thuật ngữ/Viết Tắt
Phần 1: Tổng quan
Dữ liệu mẫu:
Công thức 1: Occupancy
Công thức 2: Upper Bound of Occupancy
Công thức 3: Decaying Factor
Công thức 4: Damped Occupancy
Công thức 5: Damped High Occupancy Pattern
Công thức 6: Damped Upper Bound Occupancy
Công thức 7: Contruction DHO-List
Công thức 8: Reconstructiong DHO-List
Công thức 9: Mining Procedure
Phần 2: Flow 3 giai đoạn của thuật toán
Giai đoạn 1: Contruction và Update DHO-List
Giai đoạn 2: Recontruction DHO-List
Giai đoạn 3: Mining
Phần 4: Ví dụ 1 – Tính Occupancy, UBO, Damped Occupancy
4.1. Mẫu AE
4.2. Tính UBO cho mẫu E
Phần 5: Ví dụ 2 – Xây dựng Global DHO-List bằng quét DB0
5.1. Sau khi quét T1 = {A, C, D, E}, |T1| = 4
5.2. Sau khi quét T2 = {A, E, F}, |T2| = 3
5.3. Sau khi quét T3 = {B, C, D, E}, |T3| = 4
5.4. Sau khi quét T4 = {C, D, F}, |T4| = 3
5.5. Global DHO-List hoàn chỉnh
Phần 6: Ví dụ 3 – Tái cấu trúc Global DHO-List
6.1. Tính DO cho từng item
6.2. Sắp xếp theo Support tăng dần
Phần 7: Ví dụ 4 – Cập nhật DHO-List khi thêm DB1, DB2
7.1. Quét DB1 (T5, T6)
7.2. Quét DB2 (T7, T8)
7.3. Global DHO-List sau khi quét DB0+DB1+DB2
Phần 8: Recontruction sau khi thêm DB1, DB2
8.1. Tính lại DO với TL=8, f=0.9
8.2. Sắp xếp theo Support tăng dần
Phần 9: Ví dụ 5 – Khai phá DFS toàn diện
9.1. Bảng khai phá chi tiết từng bước DFS
9.2. Chi tiết tính toán cho các mẫu quan trọng
Mẫu AE (DOP)
Mẫu F (DOP)
Mẫu G (Bị cắt tỉa ngay)
Phần 10: Bảng tổng kết kết quả
10.1. Kết quả cuối cùng: Tập DOP
10.2. So sánh HOP truyền thống vs DOP
Phần 11: Test Cases cho dự án
Test Case 1: Ví dụ trong bài báo (cơ bản)
Test Case 2: Ngưỡng cao hơn (∂ = 20%)
Test Case 3: Ngưỡng thấp hơn (∂ = 10%)
Test Case 4: Hệ số suy giảm khác (f = 0.8)
Test Case 5: Không suy giảm (f = 1.0, HOP truyền thống)
Test Case 6: Chỉ quét DB0 (4 giao dịch đầu)
Test Case 7: Bộ dữ liệu tùy chỉnh (10 giao dịch)
Test Case 8: Trường hợp biên – Mỗi giao dịch 1 item

## Bảng Thuật ngữ/Viết Tắt

| Thuật ngữ / Viết Tắt | Định nghĩa |
|---|---|
| Occupancy | Độ chiếm dụng của một pattern |
| 𝑂(𝑋) | Occupancy của pattern X |
| Item | Là Item (Don't know how to explain this). |
| Pattern | Là một mẫu chứa các item vd:{A}, {B,C}, … |
| Transaction / T | Là một giao dịch trong DB chứa các Item |
| 𝐷𝐵 | Database – là 1 tập hoặc 1 batch các transaction |
| CSDL | Tập hợp các DB |
| UBO | Upper Bound of Occupancy – Cận trên của độ chiếm dụng |
| 𝑈𝐵𝑂(𝑋) | UBO của pattern X |
| HOP | High Occupancy Pattern – Mẫu chiếm dụng cao là pattern có 𝑂𝑐𝑐𝑢𝑝𝑎𝑛𝑐𝑦 ≥ 𝑚𝑖𝑛𝑆𝑢𝑝 |
| DOP | Damped HOP – là pattern có 𝐷𝑎𝑚𝑝𝑒𝑑 𝑂𝑐𝑐𝑢𝑝𝑎𝑛𝑐𝑦 ≥ 𝑚𝑖𝑛𝑆𝑢𝑝 |
| DOPs | Tập hợp của các DOP |
| Damped Occupancy | Độ chiếm dụng suy giảm |
| 𝐷𝑂(𝑋) | Độ chiếm dụng suy giảm của pattern X |
| DUBO | Damped UBO – Cận trên của độ chiếm dụng suy giảm |
| 𝐷𝑈𝐵𝑂(𝑋) | DUBO của pattern X |
| Support / Sup | Số item có trong một transaction hoặc số item có trong database |
| 𝑚𝑖𝑛𝑆𝑢𝑝 | Ngưỡng support tối thiểu – 𝑚𝑖𝑛𝑆𝑢𝑝 = 𝜕 × |𝐷𝐵| |
| 𝜕 | Del – là ngưỡng phần trăm support do người dùng đặt (float ∈ [0,1]) |
| DHO-List | Cấu trúc dữ liệu chứ các 𝑁𝑜𝑑𝑒, mỗi 𝑁𝑜𝑑𝑒 tương ứng với một item |
| 𝑁𝑜𝑑𝑒 | Là cấu trúc lưu trữ dữ liệu của một Item, với cấu trúc tổng thể là <𝑇𝐼𝐷, 𝑇𝐿𝐸𝑁> và Entry Set |
| Entry/Entry Set/ Entries | Với 𝑁𝑜𝑑𝑒 của pattern/item 𝑋, Entry là một tuple chứa thông tin cần thiết về transaction chứa 𝑋 |

## Phần 1: Tổng quan

### Dữ liệu mẫu:

Tham số: 𝑓 = 0.9 , 𝑇𝐿 = 8, 𝜕 = 15%, 𝑚𝑖𝑛𝑆𝑢𝑝 = 8 × 𝜕 = 1.2

| Khối DB | TID | Items | |𝑋| | 𝒇^(𝑻𝑳−𝑻𝒅) |
|---|---|---|---|---|
| 𝐷𝐵0 | 𝑇1 | A, C, D, E | 4 | 0.9^7 ≈ 0.4783 |
| | 𝑇2 | A, E, F | 3 | 0.9^6 ≈ 0.5314 |
| | 𝑇3 | B, C, D, E | 4 | 0.9^5 ≈ 0.5905 |
| | 𝑇4 | C, D, F | 3 | 0.9^4 ≈ 0.6561 |
| 𝐷𝐵1 | 𝑇5 | B, F | 2 | 0.9^3 = 0.7290 |
| | 𝑇6 | D, E, F | 3 | 0.9^2 = 0.8100 |
| 𝐷𝐵2 | 𝑇7 | A, B, C, F | 4 | 0.9^1 = 0.9000 |
| | 𝑇8 | A, E, G | 3 | 0.9^0 = 1.0000 |

### Công thức 1: Occupancy:

**Công thức**

𝑂(𝑋, 𝑇𝑑) = |𝑋| / |𝑇𝑑|

𝑂(𝑋) = ∑ 𝑂(𝑋, 𝑇𝑑), với 𝑋 ⊆ ∀𝑇𝑑 ∈ 𝐷𝐵

**Mô tả công thức**

Occupancy đo tỷ lệ mà pattern 𝑋 chiếm trong 𝑇𝑑.
• |𝑋| = số item trong mẫu 𝑋.
• |𝑇𝑑| = tổng số item trong 𝑇𝑑.
• Tổng 𝑂(𝑋) cộng dồn trên tất cả giao dịch chứa 𝑋 trong CSDL.

Ý nghĩa: Mẫu chiếm tỷ lệ càng cao trong giao dịch thì càng "quan trọng".

**Input**
• 𝑋: pattern cần tính
• 𝑇𝑑: giao dịch chứa X
• 𝐷𝐵: là CSDL hiện tại

**Output**
• 𝑂(𝑋, 𝑇𝑑): giá trị occupancy của X trong giao dịch Td (float ∈ (0, 1])
• O(X): tổng occupancy trên toàn bộ CSDL (float ≥ 0)

**Ví dụ**

Với {A, E} xuất hiện trong 𝑇1 = {𝐴, 𝐶,𝐷, 𝐸} và 𝑇2 = {𝐴, 𝐸, 𝐹}:
• 𝑂(𝐴𝐸, 𝑇1) = |{𝐴,𝐸}| / |𝑇1| = 2/4 = 0.5000
• 𝑂(𝐴𝐸, 𝑇2) = |{𝐴,𝐸}| / |𝑇2| = 2/3 = 0.6667
• 𝑂(𝐴𝐸) = 0.5000 + 0.6667 = 1.1667

Nhận xét: AE chiếm 50% giao dịch 𝑇1 và 66.7% giao dịch 𝑇2.

### Công thức 2: Upper Bound of Occupancy:

**Công thức**

𝑈𝐵𝑂(𝑋, 𝑥) = ∑ 𝑛𝑖 × (𝑙𝑥 / 𝑙𝑖), 𝑖 từ 𝑥 đến 𝑢

𝑈𝐵𝑂(𝑋) = max 1≤𝑥≤𝑢 {𝑈𝐵𝑂(𝑋, 𝑥)}

**Mô tả công thức**

UBO là cận trên của Occupancy, dùng để cắt tỉa.
Vì Occupancy KHÔNG thỏa tính phản đơn điệu (thêm item có thể TĂNG occupancy), nên cần UBO để đảm bảo: O(X') ≤ UBO(X) với mọi siêu mẫu X' của X.
• 𝐿 = {𝑙₁, 𝑙₂, . . . , 𝑙ᵤ}: tập các độ dài giao dịch phân biệt (sắp tăng)
• 𝑛ₓ = số giao dịch có độ dài 𝑙ₓ

Nếu 𝑈𝐵𝑂(𝑋) < 𝑚𝑖𝑛𝑆𝑢𝑝 -> mọi siêu mẫu của X đều không thể là HOP -> cắt bỏ.

**Input**
• X: pattern cần tính
• Tập giao dịch chứa X, từ đó tính:
– L: tập các độ dài phân biệt (sắp tăng)
– nₓ: số giao dịch tương ứng mỗi độ dài

**Output** 𝑈𝐵𝑂(𝑋) = giá trị cận trên (float ≥ 0). Dùng để so sánh với minSup.

**Ví dụ**

Item E xuất hiện trong 𝑇1, 𝑇2, 𝑇3 với 𝑠𝑢𝑝 lần lượt là {3,4,3} trên 𝐷𝐵0:
• 𝐿 = {3, 4}, 𝑛1 = 1, 𝑛2 = 2. Tương ứng với 1 transaction dài 3 và 2 transaction dài 4
• 𝑈𝐵𝑂(𝐸, 1) = 1 × 3/3 + 2 × 3/4 = 1 + 1.5 = 2.5
• 𝑈𝐵𝑂(𝐸, 2) = 2 × 4/4 = 2.0
• 𝑈𝐵𝑂(𝐸) = 𝑚𝑎𝑥(2.5, 2) = 2.5

### Công thức 3: Decaying Factor:

**Công thức** 𝑓^(𝑇𝐿−𝑇𝑑)

**Mô tả công thức**

𝑓 ∈ (0, 1) là hằng số suy giảm do người dùng đặt.
𝑇𝐿: của giao dịch mới nhất đã quét.
𝑇𝑑: của giao dịch đang xét.
Giao dịch càng cũ (Td càng nhỏ) → (TL − Td) càng lớn → 𝑓^(𝑇𝐿 − 𝑇𝑑) càng nhỏ → giá trị giảm.

**Input**
• 𝑓: hệ số suy giảm (VD: 0.9)
• 𝑇𝐿: mới nhất (VD: 8)
• 𝑇𝑑: Transaction cần tính (VD: 1, 2, ..., 8)

**Output** Hệ số suy giảm ∈ (0, 1].

**Ví dụ**

Với 𝑓 = 0.9, 𝑇𝐿 = 8:
• 𝑇1 (𝑜𝑙𝑑𝑒𝑠𝑡): 0.9^(8−1) = 0.9^7 ≈ 0.4783
• 𝑇4: 0.9^(8−4) = 0.9^4 ≈ 0.6561
• 𝑇7: 0.9^(8−7) = 0.9^1 = 0.9
• 𝑇8 (𝑛𝑒𝑤𝑒𝑠𝑡): 0.9^(8−8) = 0.9^0 = 1

### Công thức 4: Damped Occupancy:

**Công thức**

𝐷𝑂(𝑋, 𝑇𝑑) = 𝑂(𝑋, 𝑇𝑑) × 𝑓^(𝑇𝐿−𝑇𝑑)

𝐷𝑂(𝑋) = ∑ 𝐷𝑂(𝑋, 𝑇𝑑), với 𝑋 ⊆ ∀𝑇 ∈ 𝐷𝐵

**Mô tả công thức**

Damped Occupancy kết hợp Occupancy với hệ số suy giảm theo thời gian.
Đây là ĐỘ ĐO CHÍNH của bài báo.
• Giao dịch GẦN ĐÂY có occupancy cao → đóng góp lớn vào DO.
• Giao dịch CŨ dù có occupancy cao → bị suy giảm, đóng góp ít.
→ DO phản ánh xu hướng mới nhất của dữ liệu.

**Input**
• 𝑋: Pattern cần tính
• Hệ số 𝑓 và 𝑇𝐿
• Tập transaction chứa 𝑋 cùng 𝑠𝑢𝑝𝑝𝑜𝑟𝑡 của 𝑇𝑑

**Output**
• 𝐷𝑂(𝑋): tổng damped occupancy (float ≥ 0)
• Nếu 𝐷𝑂(𝑋) ≥ 𝑚𝑖𝑛𝑆𝑢𝑝 -> 𝑋 là DOP

**Ví dụ**

Mẫu AE, 𝑓 = 0.9, 𝑇𝐿 = 8. AE xuất hiện trong 𝑇1, 𝑇2, 𝑇8:
• 𝑇1 = {𝐴, 𝐶,𝐷, 𝐸}, |𝑇1| = 4:
𝐷𝑂 = 2/4 × 0.9^(8−1) = 0.5 × 0.4783 = 0.2392
• 𝑇2 = {𝐴, 𝐸, 𝐹},|𝑇| = 3:
𝐷𝑂 = 2/3 × 0.9^(8−2) = 0.6667 × 0.5314 = 0.3543
• 𝑇8 = {𝐴, 𝐸, 𝐺},|𝑇8| = 3:
𝐷𝑂 = 2/3 × 0.9^(8−8) = 0.6667 × 1.0000 = 0.6667
→ 𝐷𝑂(𝐴𝐸) = 0.2392 + 0.3543 + 0.6667 = 1.2601

Nhận xét: T8 (mới nhất) đóng góp 0.6667/1.2601 ≈ 53% tổng DO.

### Công thức 5: Damped High Occupancy Pattern:

**Công thức**

𝑋 𝑖𝑠 𝐷𝑂𝑃 ⟺ 𝐷𝑂(𝑋) ≥ 𝑚𝑖𝑛𝑆𝑢𝑝

𝐷𝑂𝑃𝑠 = { 𝑋 | 𝐷𝑂(𝑋) ≥ 𝑚𝑖𝑛𝑆𝑢𝑝 }

**Mô tả công thức**

DOP là mẫu có Damped Occupancy đạt ngưỡng tối thiểu.
• 𝜕 ∈ [0, 1]: tỷ lệ ngưỡng do người dùng đặt
• |𝐷𝐵|: tổng số giao dịch trong CSDL
• 𝑚𝑖𝑛𝑆𝑢𝑝: ngưỡng support tối thiểu
• DOPs: tập tất cả các mẫu thỏa mãn điều kiện

**Input**
• 𝑚𝑖𝑛𝑆𝑢𝑝
• 𝐷𝑂(𝑋)

**Output** DOPs = tập các mẫu DOP thỏa DO ≥ minSup.

**Ví dụ**

Với 𝜕 = 15%, |𝐷𝐵| = 8, 𝑚𝑖𝑛𝑆𝑢𝑝 = 1.2:
Kiểm tra từng mẫu:
• 𝐷𝑂(𝐴𝐸) = 1.2601 ≥ 1.2 → AE là DOP
• 𝐷𝑂(𝐹) = 1.2553 ≥ 1.2 → F là DOP
• 𝐷𝑂(𝐸) = 1.0477 < 1.2 → E không phải DOP
• 𝐷𝑂(𝐷𝐸) = 1.0744 < 1.2 → DE không phải DOP

Kết quả: DOPs = { 𝐴𝐸, 𝐹 }

### Công thức 6: Damped Upper Bound Occupancy:

**Công thức**

𝐷𝑈𝐵𝑂(𝑋, 𝑘) = ∑ 𝑛𝑖 × (𝑙𝑘 / 𝑙𝑖) × 𝑓^(𝑇𝐿−𝑇𝑘), 𝑖 từ 𝑘 đến 𝑢 = 𝑓^(𝑇𝐿−𝑇𝑘) × ∑ 𝑛𝑖 × (𝑙𝑘 / 𝑙𝑖)

𝐷𝑈𝐵𝑂(𝑋) = max 1 ≤ 𝑘 ≤ 𝑢 {𝐷𝑈𝐵𝑂(𝑋, 𝑘)}

**Mô tả công thức**

DUBO là cận trên của Damped Occupancy, dùng để cắt tỉa trong khai phá.
Khác UBO ở chỗ DUBO tích hợp hệ số suy giảm 𝑓.
• 𝐿 = {𝑙1, . . . , 𝑙𝑢}: tập độ dài giao dịch phân biệt chứa 𝑋 (sắp tăng)
• 𝑛ₖ: số giao dịch có độ dài 𝑙ₖ chứa 𝑋
• 𝑇ₖ: TID cuối (tương ứng với lớn nhất) trong các transaction có độ dài 𝑙ₖ
• 𝑇𝐿: TID mới nhất trong toàn bộ CSDL

Nếu 𝐷𝑈𝐵𝑂(𝑋) < 𝑚𝑖𝑛𝑆𝑢𝑝 → ∀𝑌 ∈ 𝑋, 𝑌 ∉ 𝐷𝑂𝑃𝑠

**Input**
• Pattern 𝑋, Hệ số 𝑓 và 𝑇𝐿
• Tập giao dịch chứa 𝑋, từ đó tính 𝐿 , 𝑛𝑘, 𝑇𝑘 của tập

**Output** 𝐷𝑈𝐵𝑂(𝑋): giá trị cận trên (float ≥ 0).

**Ví dụ**

Với 𝑓 = 0.9, 𝑇𝐿 = 8, 𝑚𝑖𝑛𝑆𝑢𝑝 = 1.2:
- Xét pattern {G} xuất hiện trong 𝑇8 với |𝑇8| = 3:
• 𝐿 = {3}, 𝑢 = 1, 𝑛1 = 1, 𝑇𝑘=1 = 8
• 𝐷𝑈𝐵𝑂(𝐺, 1) = 1 × (3/3) × 0.9^(8−8) = 1 × 1 × 1.0 = 1.0 < 𝑚𝑖𝑛𝑆𝑢𝑝 → Pruning

- Item {B} xuất hiện trong 𝑇3, 𝑇5, 𝑇7 với độ dài lần lượt tương ứng là {4,2,4}:
• 𝐿 = {2, 4}, 𝑛1 = 1, 𝑛2 = 2 với 𝑇1 = 5, 𝑇2 = 7
• 𝐷𝑈𝐵𝑂(𝐵, 1) = [1 × (2/2) + 2 × (2/4)] × 0.9^(8−5) = [1 + 1] × 0.729 = 1.458
• 𝐷𝑈𝐵𝑂(𝐵, 2) = 2 × 4/4 × 0.9^(8−7) = 2 × 0.9 = 1.8
• 𝐷𝑈𝐵𝑂(𝐵) = max({1.458, 1.8}) = 1.8 ≥ 1.2

### Công thức 7: Contruction DHO-List:

**Công thức**

Với mỗi giao dịch 𝑇𝑘, với mỗi item 𝑖 ∈ 𝑇𝑘:
Nếu 𝑁𝑜𝑑𝑒𝑖 chưa tồn tại → Add 𝑁𝑜𝑑𝑒(𝑁𝑎𝑚𝑒 = 𝑖,𝐷𝑂 = 0)
Thêm entry 〈𝑘, |𝑇𝑘|〉 vào tập entries của 𝑁𝑜𝑑𝑒𝑖
𝑆𝑢𝑝𝑝𝑜𝑟𝑡(𝑖) += 1

**Mô tả công thức**

DHO-List là cấu trúc dữ liệu danh sách dùng chứa các 𝑁𝑜𝑑𝑒.
Với mỗi 𝑁𝑜𝑑𝑒𝑖: Name, DO và Entry Set.
Mỗi entry là 〈𝑇𝐼𝐷, 𝑇𝐿𝐸𝑁〉.
Khi có dữ liệu gia tăng mới → chỉ quét phần mới, thêm entry.

**Input**
• 𝐷𝐵 gồm nhiều giao dịch 𝑇𝑘 = {𝑖𝑡𝑒𝑚₁, 𝑖𝑡𝑒𝑚₂, . . .}
• Mỗi giao dịch có 𝑇𝐼𝐷 = 𝑘 và độ dài |𝑇ₖ|

**Output**

Global DHO-List gồm nhiều nút, mỗi nút chứa:
• Name: tên item
• DO: giá trị (ban đầu = 0, tính lại ở bước Reconstruct)
• Entries: danh sách ⟨TID, TLen⟩

**Ví dụ**

Quét 𝑇1 = {𝐴, 𝐶,𝐷, 𝐸},|𝑇1| = 4:
• Tạo 𝑁𝑜𝑑𝑒𝐴 → thêm entry 〈1,4〉; 𝑆𝑢𝑝(𝐴)+= 1.
• Tạo 𝑁𝑜𝑑𝑒𝐶 → thêm entry 〈1,4〉; 𝑆𝑢𝑝(𝐶)+= 1.
• Tạo 𝑁𝑜𝑑𝑒𝐷 → thêm entry 〈1,4〉; 𝑆𝑢𝑝(𝐷)+= 1.
• Tạo 𝑁𝑜𝑑𝑒𝐸 → thêm entry 〈1,4〉; 𝑆𝑢𝑝(𝐸) += 1.

Quét 𝑇2 = {𝐴, 𝐸, 𝐹}, |𝑇2| = 3:
• 𝑁𝑜𝑑𝑒𝐴 đã có → thêm entry 〈2,3〉; 𝑆𝑢𝑝(𝐴)+= 1
• 𝑁𝑜𝑑𝑒𝐸 đã có → thêm entry 〈2,3〉; 𝑆𝑢𝑝(𝐸)+= 1
• Tạo 𝑁𝑜𝑑𝑒𝐹 → thêm entry 〈1,4〉; 𝑆𝑢𝑝(𝐹) += 1.

### Công thức 8: Reconstructiong DHO-List:

**Công thức**

Với mỗi 𝑁𝑜𝑑𝑒𝑖trong DHO-List:
Reset 𝐷𝑂(𝑖) = 0
Với mỗi entry ⟨TID, TLen⟩ trong 𝑁𝑜𝑑𝑒𝑖:
𝐷𝑂(𝑖) += (1/𝑇𝐿𝐸𝑁) × 𝑓^(𝑇𝐿 − 𝑇𝐼𝐷)

Các 𝑁𝑜𝑑𝑒 được sắp xếp tăng dần dựa theo 𝑆𝑢𝑝𝑝𝑜𝑟𝑡

**Mô tả công thức**

Khi có yêu cầu khai phá, 𝐷𝑂 được tính lại dựa trên 𝑇𝐿 hiện tại.
Sắp xếp tăng dần dựa theo Support: item ít xuất hiện đứng trước.

**Input** • DHO-List

**Output** • DHO-List đã tính 𝐷𝑂 mỗi 𝑁𝑜𝑑𝑒 và đã được sắp xếp

**Ví dụ**

Sau khi quét DB0+DB1+DB2, Với 𝑇𝐿 = 8, 𝑓 = 0.9:
Nút B: entries = {〈3,4〉,〈5,2〉,〈7,4〉}
𝐷𝑂(𝐵) = 1/4 × 0.9^5 + 1/2 × 0.9^3 + 1/4 × 0.9^1
= 0.1476 + 0.3645 + 0.2250 = 0.7371

Thứ tự sau sắp xếp:
𝐺(𝑆𝑢𝑝 = 1) ≺ 𝐵(3) ≺ 𝐵(4) ≺ 𝐶(4) ≺ 𝐷(4) ≺ 𝐸(5) ≺ 𝐹(5)

### Công thức 9: Mining Procedure

**Công thức**

DFS_Mining(prefix, DHO-List):
Với mỗi nút i trong DHO-List:
X = prefix ∪ {i}
Tính DO(X), DUBO(X)
Nếu DO(X) ≥ minSup → thêm X vào kết quả DOPs
Nếu DUBO(X) < minSup → CẮT TỈA (bỏ qua)
Ngược lại → Tạo Conditional DHO-List → Gọi đệ quy

**Mô tả công thức**

Duyệt cây tìm kiếm theo chiều sâu (DFS) với tiền tố prefix.
Conditional DHO-List được tạo bằng phép GIAO (intersection) các entry:
→ Tìm TID chung giữa 2 nút → tạo entry mới ⟨TID, TLen⟩.
Hai kiểm tra độc lập:
• DO ≥ minSup? → quyết định X có phải kết quả hay không.
• DUBO ≥ minSup? → quyết định có mở rộng X hay cắt tỉa.

**Input**
• DHO-List
• 𝑚𝑖𝑛𝑆𝑢𝑝

**Output** DOPs = tập tất cả các mẫu DOP thỏa mãn.

**Ví dụ**

Bắt đầu từ thứ tự: 𝐺 ≺ 𝐵 ≺ 𝐴 ≺ 𝐶 ≺ 𝐷 ≺ 𝐸 ≺ 𝐹. 𝑚𝑖𝑛𝑆𝑢𝑝 = 1.2

① G: 𝐷𝑂(𝐺) = 0.333,𝐷𝑈𝐵𝑂(𝐺) = 1.000 < 1.2 → Pruning
② B: 𝐷𝑂(𝐵) = 0.737,𝐷𝑈𝐵𝑂(𝐵) = 1.800 ≥ 1.2 → Mở rộng:
• BA: 𝐷𝑂(𝐵𝐴) = 0.450,𝐷𝑈𝐵𝑂(𝐵) = 0.900 < 1.2 → Pruning
• BC: 𝐷𝑂(𝐵𝐶) = 0.745,𝐷𝑈𝐵𝑂(𝐵𝐶) = 1.800 ≥ 1.2 → Mở rộng
• BF: 𝐷𝑂(𝐵𝐹) = 1.179,𝐷𝑈𝐵𝑂(𝐵𝐹) = 1.094 < 1.2 → Pruning
③ A: 𝐷𝑈𝐵𝑂(𝐴) = 3.500 → Mở rộng:
• AE: 𝐷𝑂(𝐴𝐸) = 1.2601 ≥ 1.2 → ĐẠT
• ...
⑦ F: 𝐷𝑂(𝐹) = 1.2553 ≥ 1.2 → ĐẠT

→ Kết quả: DOPs = { AE, F }

## Phần 2: Flow 3 giai đoạn của thuật toán

### Giai đoạn 1: Contruction và Update DHO-List

• Quét mỗi transaction 𝑇𝑘 trong 𝐷𝐵.
• Với mỗi item i trong 𝑇𝑘:
– Nếu chưa có nút cho i trong DHO-List -> tạo 𝑁𝑜𝑑𝑒 mới với 𝑁𝑎𝑚𝑒 = 𝑖,𝐷𝑂 = 0.
– Thêm entry ⟨𝑘, |𝑇ₖ|⟩ vào tập entries của 𝑁𝑜𝑑𝑒𝑖.
– Tăng 𝑆𝑢𝑝(𝑖) lên 1.
• Khi có 𝐷𝐵 mới: chỉ quét các Transaction mới, thêm entry mà không cần quét lại dữ liệu cũ.

### Giai đoạn 2: Recontruction DHO-List

• Với 𝑁𝑜𝑑𝑒𝑖trong DHO-List: Đặt lại 𝐷𝑂(𝑖) = 0, sau đó tính lại 𝐷𝑂(𝑖)
• Sắp xếp các nút theo thứ tự Support tăng dần.

### Giai đoạn 3: Mining

• Duyệt từng item theo thứ tự sắp xếp, chọn làm tiền tố (prefix).
• Với mỗi mẫu ứng viên X:
– Tính DO(X): nếu DO(X) ≥ minSup → X là DOP, đưa vào kết quả.
– Tính DUBO(X): nếu DUBO(X) < minSup → Pruning, không mở rộng X.
– Nếu DUBO(X) ≥ minSup → tạo Conditional DHO-List cho X, tiếp tục mở rộng bằng DFS.
• Giao hai DHO-List (intersection) để xây dựng Conditional DHO-List cho mẫu dài hơn:
Tìm TID chung → entry mới ⟨TID, TLen⟩.

## Phần 4: Ví dụ 1 – Tính Occupancy, UBO, Damped Occupancy

Xét trên 𝐷𝐵0, 𝑇𝐿 = 4, 𝑓 = 0.9, 𝜕 = 25% → 𝑚𝑖𝑛𝑆𝑢𝑝 = 4 × 25% = 1

### 4.1. Mẫu AE

AE xuất hiện trong T1 = {A,C,D,E} và T2 = {A,E,F}. Sup(AE) = 2.

Tính Occupancy:
𝑂(𝐴𝐸, 𝑇1) = |𝐴𝐸| / |𝑇1| = 2/4 = 0.5000
𝑂(𝐴𝐸, 𝑇2) = |𝐴𝐸| / |𝑇2| = 2/3 ≈ 0.6667
𝑂(𝐴𝐸) = 0.5 + 0.6667 = 1.1667

Tính Damped Occupancy (TL=4, f=0.9):
𝐷𝑂(𝐴𝐸, 𝑇1) = (2/4) × 0.9^(4−1) = 0.5 × 0.9^3 = 0.5 × 0.729 = 0.3645
𝐷𝑂(𝐴𝐸, 𝑇2) = (2/3) × 0.9^(4−2) = 0.6667 × 0.9^2 = 0.6667 × 0.81 = 0.5400
𝐷𝑂(𝐴𝐸) = 𝐷𝑂(𝐴𝐸, 𝑇1) + 𝐷𝑂(𝐴𝐸, 𝑇2) = 0.3645 + 0.5400 = 0.9045

Kết luận:
Dù 𝑂(𝐴𝐸) = 1.1667 > 𝑚𝑖𝑛𝑆𝑢𝑝, nhưng khi áp dụng hệ số suy giảm thì
𝐷𝑂(𝐴𝐸) = 0.9045 < 𝑚𝑖𝑛𝑆𝑢𝑝 nên AE không phải là DOP

### 4.2. Tính UBO cho mẫu E

E xuất hiện trong 𝑇1 (|𝑇1|=4), 𝑇2 (|𝑇2|=3), 𝑇3 (|𝑇3|=4).
Các độ dài phân biệt: 𝐿 = {3, 4} → 𝑙1 = 3, 𝑙2 = 4.
𝑛₁ = 1 (1 giao dịch có |𝑇| = 3), 𝑛₂ = 2 (2 giao dịch có |𝑇| = 4).

𝑈𝐵𝑂(𝐸, 1) = ∑ 𝑛ᵢ × (𝑙1/𝑙ᵢ), 𝑖=1..2 = 1 × (3/3) + 2 × (3/4) = 1 + 1.5 = 2.5
𝑈𝐵𝑂(𝐸, 2) = ∑ 𝑛ᵢ × (𝑙2/𝑙ᵢ), 𝑖=2..2 = 2 × (4/4) = 2.0
𝑈𝐵𝑂(𝐸) = 𝑚𝑎𝑥(2.5, 2.0) = 2.5

## Phần 5: Ví dụ 2 – Xây dựng Global DHO-List bằng quét DB0

Quét 4 giao dịch T1→T4 của DB0. Với mỗi giao dịch, từng item được xử lý tuần tự.

### 5.1. Sau khi quét T1 = {A, C, D, E}, |T1| = 4

• Tạo nút A: DO=0, entries = {⟨1, 4⟩}, Sup(A) = 1
• Tạo nút C: DO=0, entries = {⟨1, 4⟩}, Sup(D) = 1
• Tạo nút D: DO=0, entries = {⟨1, 4⟩}, Sup(D) = 1
• Tạo nút E: DO=0, entries = {⟨1, 4⟩}, Sup(E) = 1

### 5.2. Sau khi quét T2 = {A, E, F}, |T2| = 3

• Nút A đã tồn tại → thêm entry ⟨2, 3⟩. Sup(A)=2.
• Nút E đã tồn tại → thêm entry ⟨2, 3⟩. Sup(E)=2.
• Tạo nút F: DO=0, entries = {⟨2, 3⟩}. Sup(F)=1.

### 5.3. Sau khi quét T3 = {B, C, D, E}, |T3| = 4

• Tạo nút B: DO=0, entries = {⟨3, 4⟩}. Sup(B)=1.
• Nút C: thêm ⟨3, 4⟩. Sup(C)=2.
• Nút D: thêm ⟨3, 4⟩. Sup(D)=2.
• Nút E: thêm ⟨3, 4⟩. Sup(E)=3.

### 5.4. Sau khi quét T4 = {C, D, F}, |T4| = 3

• Nút C: thêm ⟨4, 3⟩. Sup(C)=3.
• Nút D: thêm ⟨4, 3⟩. Sup(D)=3.
• Nút F: thêm ⟨4, 3⟩. Sup(F)=2.

### 5.5. Global DHO-List hoàn chỉnh

| Item | Support | Entry Set (⟨TID, TLen⟩) |
|---|---|---|
| A | 2 | ⟨1, 4⟩ ⟨2, 3⟩ |
| B | 1 | ⟨3, 4⟩ |
| C | 3 | ⟨1, 4⟩ ⟨3, 4⟩ ⟨4, 3⟩ |
| D | 3 | ⟨1, 4⟩ ⟨3, 4⟩ ⟨4, 3⟩ |
| E | 3 | ⟨1, 4⟩ ⟨2, 3⟩ ⟨3, 4⟩ |
| F | 2 | ⟨2, 3⟩ ⟨4, 3⟩ |

## Phần 6: Ví dụ 3 – Tái cấu trúc Global DHO-List (trên DB0)

TL = 4, f = 0.9. Tính lại DO cho từng nút và sắp xếp theo Support tăng dần.

### 6.1. Tính DO cho từng item

**Item A:**
Entry ⟨1, 4⟩: 𝑂 = 1/4 = 0.25, 𝑓^(4−1) = 0.9^3 = 0.729,𝐷𝑂 = 0.25 × 0.729 = 0.1823
Entry ⟨2, 3⟩: 𝑂 = 1/3 = 0.3333, 𝑓^(4−2) = 0.9^2 = 0.8100,𝐷𝑂 = 0.3333 × 0.8100 = 0.2700
=>DO(A) = 0.4523

**Item B:**
Entry ⟨3, 4⟩: 𝑂 = 1/4 = 0.2500, 𝑓^(4−3) = 0.9^1 = 0.9000,𝐷𝑂 = 0.2500 × 0.9000 = 0.2250
=>DO(B) = 0.2250

**Item C:**
Entry ⟨1, 4⟩: 𝑂 = 1/4 = 0.2500, 𝑓^(4−1) = 0.9^3 = 0.7290,𝐷𝑂 = 0.2500 × 0.7290 = 0.1823
Entry ⟨3, 4⟩: 𝑂 = 1/4 = 0.2500, 𝑓^(4−3) = 0.9^1 = 0.9000,𝐷𝑂 = 0.2500 × 0.9000 = 0.2250
Entry ⟨4, 3⟩: 𝑂 = 1/3 = 0.3333, 𝑓^(4−4) = 0.9^0 = 1.0000,𝐷𝑂 = 0.3333 × 1.0000 = 0.3333
=>DO(C) = 0.7406

**Item D:**
Entry ⟨1, 4⟩: 𝑂 = 1/4 = 0.2500, 𝑓^(4−1) = 0.9^3 = 0.7290,𝐷𝑂 = 0.2500 × 0.7290 = 0.1823
Entry ⟨3, 4⟩: 𝑂 = 1/4 = 0.2500, 𝑓^(4−3) = 0.9^1 = 0.9000,𝐷𝑂 = 0.2500 × 0.9000 = 0.2250
Entry ⟨4, 3⟩: 𝑂 = 1/3 = 0.3333, 𝑓^(4−4) = 0.9^0 = 1.0000,𝐷𝑂 = 0.3333 × 1.0000 = 0.3333
=>DO(D) = 0.7406

**Item E:**
Entry ⟨1, 4⟩: 𝑂 = 1/4 = 0.2500, 𝑓^(4−1) = 0.9^3 = 0.7290,𝐷𝑂 = 0.2500 × 0.7290 = 0.1823
Entry ⟨2, 3⟩: 𝑂 = 1/3 = 0.3333, 𝑓^(4−2) = 0.9^2 = 0.8100,𝐷𝑂 = 0.3333 × 0.8100 = 0.2700
Entry ⟨3, 4⟩: 𝑂 = 1/4 = 0.2500, 𝑓^(4−3) = 0.9^1 = 0.9000,𝐷𝑂 = 0.2500 × 0.9000 = 0.2250
=>DO(E) = 0.6773

**Item F:**
Entry ⟨2, 3⟩: 𝑂 = 1/3 = 0.3333, 𝑓^(4−2) = 0.9^2 = 0.8100,𝐷𝑂 = 0.3333 × 0.8100 = 0.2700
Entry ⟨4, 3⟩: 𝑂 = 1/3 = 0.3333, 𝑓^(4−4) = 0.9^0 = 1.0000,𝐷𝑂 = 0.3333 × 1.0000 = 0.3333
=>DO(F) = 0.6033

### 6.2. Sắp xếp theo Support tăng dần

| Thứ tự | Item | Support | DO |
|---|---|---|---|
| 1 | B | 1 | 0.2250 |
| 2 | A | 2 | 0.4523 |
| 3 | F | 2 | 0.6033 |
| 4 | C | 3 | 0.7406 |
| 5 | D | 3 | 0.7406 |
| 6 | E | 3 | 0.6773 |

Thứ tự xử lý: B ≺ A ≺ F ≺ C ≺ D ≺ E

## Phần 7: Ví dụ 4 – Cập nhật DHO-List khi thêm DB1, DB2

### 7.1. Quét DB1 (T5, T6)

• T5 = {B, F}, |T5| = 2:
– Nút B tồn tại → thêm ⟨5, 2⟩. Sup(B)=2.
– Nút F tồn tại → thêm ⟨5, 2⟩. Sup(F)=3.
• T6 = {D, E, F}, |T6| = 3:
– Nút D: thêm ⟨6, 3⟩. Sup(D)=4.
– Nút E: thêm ⟨6, 3⟩. Sup(E)=4.
– Nút F: thêm ⟨6, 3⟩. Sup(F)=4.

### 7.2. Quét DB2 (T7, T8)

• T7 = {A, B, C, F}, |T7| = 4:
– Nút A: thêm ⟨7, 4⟩. Sup(A)=3.
– Nút B: thêm ⟨7, 4⟩. Sup(B)=3.
– Nút C: thêm ⟨7, 4⟩. Sup(C)=4.
– Nút F: thêm ⟨7, 4⟩. Sup(F)=5.
• T8 = {A, E, G}, |T8| = 3:
– Nút A: thêm ⟨8, 3⟩. Sup(A)=4.
– Nút E: thêm ⟨8, 3⟩. Sup(E)=5.
– Tạo nút G: DO=0, entries = {⟨8, 3⟩}. Sup(G)=1.

### 7.3. Global DHO-List sau khi quét DB0+DB1+DB2

| Item | Support | Entry Set (⟨TID, TLen⟩) |
|---|---|---|
| A | 4 | ⟨1, 4⟩ ⟨2, 3⟩ ⟨7, 4⟩ ⟨8, 3⟩ |
| B | 3 | ⟨3, 4⟩ ⟨5, 2⟩ ⟨7, 4⟩ |
| C | 4 | ⟨1, 4⟩ ⟨3, 4⟩ ⟨4, 3⟩ ⟨7, 4⟩ |
| D | 4 | ⟨1, 4⟩ ⟨3, 4⟩ ⟨4, 3⟩ ⟨6, 3⟩ |
| E | 5 | ⟨1, 4⟩ ⟨2, 3⟩ ⟨3, 4⟩ ⟨6, 3⟩ ⟨8, 3⟩ |
| F | 5 | ⟨2, 3⟩ ⟨4, 3⟩ ⟨5, 2⟩ ⟨6, 3⟩ ⟨7, 4⟩ |
| G | 1 | ⟨8, 3⟩ |

## Phần 8: Recontruction sau khi thêm DB1, DB2

### 8.1. Tính lại DO với TL=8, f=0.9

**Item A (Sup=4):**
- Entry ⟨1, 4⟩: (1/4) × 0.9^7 = 0.2500 × 0.4783 = 0.1196
- Entry ⟨2, 3⟩: (1/3) × 0.9^6 = 0.3333 × 0.5314 = 0.1771
- Entry ⟨7, 4⟩: (1/4) × 0.9^1 = 0.2500 × 0.9000 = 0.2250
- Entry ⟨8, 3⟩: (1/3) × 0.9^0 = 0.3333 × 1.0000 = 0.3333
=> DO(A) = 0.8551

**Item B (Sup=3):**
- Entry ⟨3, 4⟩: (1/4) × 0.9^5 = 0.2500 × 0.5905 = 0.1476
- Entry ⟨5, 2⟩: (1/2) × 0.9^3 = 0.5000 × 0.7290 = 0.3645
- Entry ⟨7, 4⟩: (1/4) × 0.9^1 = 0.2500 × 0.9000 = 0.2250
=>DO(B) = 0.7371

**Item C (Sup=4):**
- Entry ⟨1, 4⟩: (1/4) × 0.9^7 = 0.2500 × 0.4783 = 0.1196
- Entry ⟨3, 4⟩: (1/4) × 0.9^5 = 0.2500 × 0.5905 = 0.1476
- Entry ⟨4, 3⟩: (1/3) × 0.9^4 = 0.3333 × 0.6561 = 0.2187
- Entry ⟨7, 4⟩: (1/4) × 0.9^1 = 0.2500 × 0.9000 = 0.2250
=>DO(C) = 0.7109

**Item D (Sup=4):**
- Entry ⟨1, 4⟩: (1/4) × 0.9^7 = 0.2500 × 0.4783 = 0.1196
- Entry ⟨3, 4⟩: (1/4) × 0.9^5 = 0.2500 × 0.5905 = 0.1476
- Entry ⟨4, 3⟩: (1/3) × 0.9^4 = 0.3333 × 0.6561 = 0.2187
- Entry ⟨6, 3⟩: (1/3) × 0.9^2 = 0.3333 × 0.8100 = 0.2700
=>DO(D) = 0.7559

**Item E (Sup=5):**
- Entry ⟨1, 4⟩: (1/4) × 0.9^7 = 0.2500 × 0.4783 = 0.1196
- Entry ⟨2, 3⟩: (1/3) × 0.9^6 = 0.3333 × 0.5314 = 0.1771
- Entry ⟨3, 4⟩: (1/4) × 0.9^5 = 0.2500 × 0.5905 = 0.1476
- Entry ⟨6, 3⟩: (1/3) × 0.9^2 = 0.3333 × 0.8100 = 0.2700
- Entry ⟨8, 3⟩: (1/3) × 0.9^0 = 0.3333 × 1.0000 = 0.3333
=>DO(E) = 1.0477

**Item F (Sup=5):**
- Entry ⟨2, 3⟩: (1/3) × 0.9^6 = 0.3333 × 0.5314 = 0.1771
- Entry ⟨4, 3⟩: (1/3) × 0.9^4 = 0.3333 × 0.6561 = 0.2187
- Entry ⟨5, 2⟩: (1/2) × 0.9^3 = 0.5000 × 0.7290 = 0.3645
- Entry ⟨6, 3⟩: (1/3) × 0.9^2 = 0.3333 × 0.8100 = 0.2700
- Entry ⟨7, 4⟩: (1/4) × 0.9^1 = 0.2500 × 0.9000 = 0.2250
=>DO(F) = 1.2553

**Item G (Sup=1):**
- Entry ⟨8, 3⟩: (1/3) × 0.9^0 = 0.3333 × 1.0000 = 0.3333
=>DO(G) = 0.3333

### 8.2. Sắp xếp theo Support tăng dần

| Thứ tự | Item | Support | DO |
|---|---|---|---|
| 1 | G | 1 | 0.3333 |
| 2 | B | 3 | 0.7371 |
| 3 | A | 4 | 0.8551 |
| 4 | C | 4 | 0.7109 |
| 5 | D | 4 | 0.7559 |
| 6 | E | 5 | 1.0477 |
| 7 | F | 5 | 1.2553 |

Thứ tự xử lý mới: G ≺ B ≺ A ≺ C ≺ D ≺ E ≺ F

## Phần 9: Ví dụ 5 – Khai phá DFS toàn diện

Tham số: TL = 8, f = 0.9, ∂ = 15% → minSup = 8 × 0.15 = 1.2

Thứ tự xử lý: G ≺ B ≺ A ≺ C ≺ D ≺ E ≺ F

### 9.1. Bảng khai phá chi tiết từng bước DFS

Mỗi dòng thể hiện một mẫu ứng viên được duyệt, với các giá trị DO, DUBO và quyết định.

| STT | Mẫu X | Transactions | Sup | DO(X) | DUBO(X) | Là DOP? | Cắt tỉa? |
|---|---|---|---|---|---|---|---|
| 1 | G | T8 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 2 | B | T3, T5, T7 | 3 | 0.7371 | 1.8000 | - | Tiếp tục |
| 3 | BA | T7 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 4 | BC | T3, T7 | 2 | 0.7452 | 1.8000 | - | Tiếp tục |
| 5 | BCD | T3 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 6 | BCE | T3 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 7 | BCF | T7 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 8 | BD | T3 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 9 | BE | T3 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 10 | BF | T5, T7 | 2 | 1.1790 | 1.0935 | - | Null |
| 11 | A | T1, T2, T7, T8 | 4 | 0.8551 | 3.5000 | - | Tiếp tục |
| 12 | AC | T1, T7 | 2 | 0.6891 | 1.8000 | - | Tiếp tục |
| 13 | ACD | T1 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 14 | ACE | T1 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 15 | ACF | T7 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 16 | AD | T1 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 17 | AE | T1, T2, T8 | 3 | 1.2601 | 2.7500 | X | Tiếp tục |
| 18 | AEF | T2 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 19 | AF | T2, T7 | 2 | 0.8043 | 0.9300 | - | Pruning |
| 20 | C | T1, T3, T4, T7 | 4 | 0.7109 | 2.7000 | - | Tiếp tục |
| 21 | CD | T1, T3, T4 | 3 | 0.9718 | 1.6402 | - | Tiếp tục |
| 22 | CDE | T1, T3 | 2 | 0.8016 | 1.1810 | ✗ | Pruning |
| 23 | CDF | T4 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 24 | CE | T1, T3 | 2 | 0.5344 | 1.1810 | - | Pruning |
| 25 | CF | T4, T7 | 2 | 0.8874 | 1.1482 | - | Pruning |
| 26 | D | T1, T3, T4, T6 | 4 | 0.7559 | 2.8350 | - | Tiếp tục |
| 27 | DE | T1, T3, T6 | 3 | 1.0744 | 2.0250 | - | Tiếp tục |
| 28 | DEF | T6 | 1 | - | - | | < 𝑚𝑖𝑛𝑆𝑢𝑝 |
| 29 | DF | T4, T6 | 2 | 0.9774 | 1.6200 | - | Null |
| 30 | E | T1, T2, T3, T6, T8 | 5 | 1.0477 | 4.5000 | - | Tiếp tục |
| 31 | EF | T2, T6 | 2 | 0.8943 | 1.6200 | - | - |
| 32 | F | T2, T4, T5, T6, T7 | 5 | 1.2553 | 3.0375 | X | Null |

Note: Giá trị null tại Cắt tỉa là vì F là item cuối, nên việc tính mở rộng không có ý nghĩa

### 9.2. Chi tiết tính toán cho các mẫu quan trọng

**Mẫu AE (DOP)**

AE xuất hiện trong: T1={A, C, D, E} (|T|=4), T2={A, E, F} (|T|=3), T8={A, E, G} (|T|=3)

Tính DO(AE):
T1: (2/4) × 0.9^7 = 0.5000 × 0.4783 = 0.2391
T2: (2/3) × 0.9^6 = 0.6667 × 0.5314 = 0.3543
T8: (2/3) × 0.9^0 = 0.6667 × 1.0000 = 0.6667
▶ DO(AE) = 1.2601 ≥ 1.2 → ĐẠT

Tính DUBO(AE):
DUBO(AE, 1): lₖ=3, Tₖ=8, giá trị = 2.7500
DUBO(AE, 2): lₖ=4, Tₖ=1, giá trị = 0.4783
▶ DUBO(AE) = 2.7500 → Tiếp tục mở rộng (nhưng AEF, AEG... đều bị cắt)

**Mẫu F (DOP)**

F xuất hiện trong: T2={A, E, F} (|T|=3), T4={C, D, F} (|T|=3), T5={B, F} (|T|=2), T6={D, E, F} (|T|=3), T7={A, B, C, F} (|T|=4)

Tính DO(F):
T2: (1/3) × 0.9^6 = 0.3333 × 0.5314 = 0.1771
T4: (1/3) × 0.9^4 = 0.3333 × 0.6561 = 0.2187
T5: (1/2) × 0.9^3 = 0.5000 × 0.7290 = 0.3645
T6: (1/3) × 0.9^2 = 0.3333 × 0.8100 = 0.2700
T7: (1/4) × 0.9^1 = 0.2500 × 0.9000 = 0.2250
▶ DO(F) = 1.2553 ≥ 1.2 → ĐẠT

**Mẫu G (Bị cắt tỉa ngay)**

G chỉ xuất hiện trong T8={A,E,G} (|T|=3).
DO(G) = (1/3) × 0.9^0 = 0.3333
DUBO(G) = 1 × (3/3) × 0.9^0 = 1.0000
DUBO(G) = 1.0000 < minSup = 1.2 → Pruning

## Phần 10: Bảng tổng kết kết quả

### 10.1. Kết quả cuối cùng: Tập DOP

Với minSup = 1.2 (∂ = 15%), f = 0.9:

| STT | Mẫu DOP | DO(X) | Transactions | Sup |
|---|---|---|---|---|
| 17 | AE | 1.2601 | T1, T2, T8 | 3 |
| 32 | F | 1.2553 | T2, T4, T5, T6, T7 | 5 |

Tổng số mẫu được duyệt: 32
Tổng số mẫu bị cắt tỉa (pruned): 19
Tổng số DOP tìm được: 2
Danh sách DOP: AE, F

### 10.2. So sánh HOP truyền thống vs DOP

Trên cùng CSDL, nếu không dùng hệ số suy giảm (occupancy truyền thống, f=1.0), kết quả khác biệt đáng kể so với khi dùng damped window model.

Điều này chứng minh rằng mô hình suy giảm loại bỏ được các mẫu xuất hiện chủ yếu ở dữ liệu cũ và giữ lại các mẫu phù hợp với xu hướng gần đây.

| Mẫu | O(X) (f=1.0) | DO(X) (f=0.9) | Là HOP? | Là DOP? |
|---|---|---|---|---|
| AE | 0.0000 | 0.0000 | - | - |
| F | 1.7500 | 1.2553 | X | X |
| CD | 0.0000 | 0.0000 | - | - |
| DE | 0.0000 | 0.0000 | - | - |
| BF | 0.0000 | 0.0000 | - | - |
| E | 1.5000 | 1.0477 | X | - |

## Phần 11: Test Cases cho dự án

Các test cases dưới đây được thiết kế để kiểm thử tính đúng đắn của việc cài đặt thuật toán DOPM. Mỗi test case bao gồm dữ liệu đầu vào, tham số, và kết quả mong đợi.

### Test Case 1: Ví dụ trong bài báo (cơ bản)

Mục tiêu: Kiểm tra thuật toán với chính dữ liệu mẫu trong bài báo.
Tham số: f = 0.9, ∂ = 15%, minSup = 1.2
Dữ liệu: 8 giao dịch (DB0 + DB1 + DB2) như Table 1.
Kết quả mong đợi: 2 DOP → AE (DO=1.2601), F (DO=1.2553)

### Test Case 2: Ngưỡng cao hơn (∂ = 20%)

Tham số: f = 0.9, ∂ = 20%, minSup = 1.6
Kết quả mong đợi: 0 DOP → Không có

### Test Case 3: Ngưỡng thấp hơn (∂ = 10%)

Tham số: f = 0.9, ∂ = 10%, minSup = 0.8
Kết quả mong đợi: 15 DOP

| Mẫu | Giá trị |
|---|---|
| GAE | 1.0000 |
| BACF | 0.9000 |
| BF | 1.1790 |
| A | 0.8551 |
| AE | 1.2601 |
| AF | 0.8043 |
| CD | 0.9718 |
| CDE | 0.8016 |
| CF | 0.8874 |
| DE | 1.0744 |
| DEF | 0.8100 |
| DF | 0.9774 |
| E | 1.0477 |
| EF | 0.8943 |
| F | 1.2553 |

### Test Case 4: Hệ số suy giảm khác (f = 0.8)

Tham số: f = 0.8, ∂ = 15%, minSup = 1.2
Kết quả mong đợi: 0 DOP

### Test Case 5: Không suy giảm (f = 1.0, HOP truyền thống)

Tham số: f = 1.0 (không suy giảm), ∂ = 15%, minSup = 1.2
Kết quả mong đợi: 9 HOP (vì f=1.0 thì DO = O)
So sánh với DOP ở Test Case 1 để thấy sự khác biệt.

| Mẫu | Giá trị |
|---|---|
| BF | 1.5000 |
| AE | 1.8333 |
| CD | 1.6667 |
| CDE | 1.5000 |
| DE | 1.6667 |
| DF | 1.3333 |
| E | 1.5000 |
| EF | 1.3333 |
| F | 1.7500 |

### Test Case 6: Chỉ quét DB0 (4 giao dịch đầu)

Tham số: f = 0.9, ∂ = 25%, minSup = 1.0, TL = 4
Kết quả mong đợi: 3 DOP

| Mẫu | Giá trị |
|---|---|
| FCD | 1.0000 |
| CD | 1.4812 |
| CDE | 1.2218 |

### Test Case 7: Bộ dữ liệu tùy chỉnh (10 giao dịch)

Tham số: f = 0.9, ∂ = 15%, minSup = 1.5, TL = 10

| TID | Items | |T| |
|---|---|---|
| T1 | A, B, C | 3 |
| T2 | A, B | 2 |
| T3 | B, C, D | 3 |
| T4 | A, C | 2 |
| T5 | A, B, C, D | 4 |
| T6 | B, D | 2 |
| T7 | A, C | 2 |
| T8 | A, B, C | 3 |
| T9 | B, C, D | 3 |
| T10 | A, B | 2 |

Kết quả mong đợi: 9 DOP

| Mẫu | Giá trị |
|---|---|
| DCB | 1.8212 |
| DB | 1.8702 |
| A | 1.8922 |
| AC | 2.3540 |
| ACB | 1.6403 |
| AB | 2.5240 |
| C | 1.6364 |
| CB | 2.0124 |
| B | 2.0495 |

### Test Case 8: Trường hợp biên – Mỗi giao dịch 1 item

Mỗi giao dịch chỉ có 1 item → Occupancy luôn = 1.0 cho mỗi lần xuất hiện.
Tham số: f = 0.9, ∂ = 30%, minSup = 1.5, TL = 5
Kết quả mong đợi: 1 DOP

| Mẫu | Giá trị |
|---|---|
| A | 2.4661 |

Dữ liệu:

| TID | Items | |T| |
|---|---|---|
| T1 | A | 1 |
| T2 | B | 1 |
| T3 | A | 1 |
| T4 | C | 1 |
| T5 | A | 1 |
