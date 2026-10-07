# DHOPM — Spec (góc nhìn của tác giả)

> ⚠️ **Tài liệu này là bản nháp** ("có thể có mâu thuẫn, sai sót" — theo lời tác giả). Nó ghi lại **ý định và góc nhìn**, **không phải đặc tả chuẩn**.
>
> | Cần gì? | Đọc file nào |
> |---|---|
> | **Đặc tả chuẩn (bắt buộc tuân theo)** | `../plans/00-OVERALL-PLAN.md` §2 — canonical C1–C12, INV-A–J |
> | Quyết định kiến trúc | `../DECISIONS.md` |
> | Ý tưởng gốc về thuật toán | `../root/1-s2_0-S095219762600792X-main.md`, `../root/Nhom01_VDChayTay.md` |

---

## 1. Vấn đề muốn giải

Khai thác frequent pattern kinh điển giả định dữ liệu **tĩnh**. Nhưng nhiều ứng dụng thực tế có dữ liệu **dòng chảy**: giao dịch đến liên tục, và một mẫu "phổ biến hồi tháng trước" **không còn phổ biến** hôm nay. Các thuật toán cũ:

- **tính lại toàn bộ** ⇒ tốn kém theo độ dài stream;
- **hoặc cắt bỏ cửa sổ tùy ý** ⇒ sai số không kiểm soát được.

## 2. Góc nhìn của tác giả

> *"Thay vì cắt cửa sổ bằng một quy tắc tuỳ ý (ví dụ "chỉ giữ 1000 giao dịch gần nhất"), hãy **để người dùng đặt sai số ε**, rồi **suy ra** cửa sổ cần thiết."*

Đây là điểm mấu chốt: **sai số là đầu vào, cửa sổ là kết quả** — ngược với cách cắt cửa sổ truyền thống.

Ba tầng suy nghĩ:

1. **Suy giảm theo tuổi.** Giao dịch càng xa thì giá trị càng nhỏ, theo `f^(TL − tid)`. Hệ số `f` là **một tham số đơn lẻ** cho toàn bộ thuật toán (canonical C1).
2. **Sai số tích luỹ.** Giao dịch bị loại khỏi cửa sổ vẫn đóng góp `f^k`. Tổng phần bị loại là `Σ_{k≥W} f^k = f^W/(1−f)`. Yêu cầu `< ε` cho ra **công thức đóng** cho `W`.
3. **Ngưỡng phải đo trên cùng đơn vị.** Sau khi cắt cửa sổ, ngưỡng `∂` phải nhân với **số transaction còn lại trong cửa sổ**, không phải tổng số transaction từng thấy — nếu không, ngưỡng tăng vô hạn trong khi dữ liệu không tăng ⇒ thuật toán "chết dần".

## 3. Những gì tác giả muốn phát triển

| # | Mong muốn | Trạng thái |
|---|---|---|
| 1 | Cài đặt ý tưởng ε thành **một phiên bản thuật toán thật** | 🕔 → G2 (`plans/02`) |
| 2 | **Đo** xem cửa sổ ε giúp/mất bao nhiêu (thời gian, bộ nhớ, độ lệch) | 🕔 → báo cáo G2 |
| 3 | **Kiểm tra lại** các ngưỡng `∂` của paper dưới cách hiểu mới | 🕔 — 3/6 ngưỡng rơi vào vùng bất khả thi (`plans/00` §6.4) |
| 4 | Tách **ngữ nghĩa** (V2) khỏi **tối ưu hoá** (V3/V4) để so sánh cho sạch | ✅ đã chốt (D13) |
| 5 | Khám phá các mở rộng tiếp (HUIM, baseline khác, v.v.) | 🕓 đang cân nhắc (`DECISIONS.md` §5) |

## 4. Vì sao 4 phiên bản thay vì 3

Nếu gộp "cửa sổ ε" và "tối ưu hoá" vào cùng một phiên bản thì khi so sánh ta **không biết** phần nào của khác biệt đến từ ngữ nghĩa và phần nào đến từ kỹ thuật. Tách ra:

- **V1** là mốc đối chiếu (`ε = 0` ⇒ ≡ paper, bit-for-bit);
- **V2** chỉ thêm cửa sổ ⇒ mọi khác biệt là **ý nghĩa**;
- **V3/V4** giữ nguyên ngữ nghĩa, chỉ tối ưu ⇒ mọi khác biệt là **kỹ thuật**.

## 5. Cách kiểm chứng ý tưởng

Ba tầng bằng chứng, từ rẻ đến đắt:

| Tầng | Cách kiểm | Bắt được lỗi gì |
|---|---|---|
| **1. Công thức** | `WindowMathTest` đối chiếu bảng tra cứu `W` | Sai số làm tròn, off-by-one |
| **2. Bất biến** | `ε = 0` ⇒ bit-for-bit ≡ V1; `\|DO_win − DO_full\| ≤ ε` | Lỗi logic evict, đọc nhầm handle |
| **3. Thực đo** | Đo độ lệch trên dataset thật + ablation V1 ↔ V2 | Sai số tích luỹ, hiệu năng thực |

Tầng 2 là **bắt buộc** — nó là test hồi quy giữ các phiên bản với nhau.

## 6. Rủi ro tác giả đã thấy

| Rủi ro | Giảm thiểu |
|---|---|
| Cửa sổ quá lớn vì `f` quá gần 1 | Bảng tra cứu cho thấy `f=0.99, ε=1e-6` ⇒ `W ≈ 1833`; người dùng cần biết trước |
| Chọn `ε` quá lớn ⇒ cửa sổ rỗng | Validator **từ chối cấu hình** (không phải cảnh báo) khi `ε ≥ 1/(1−f)` |
| Cửa sổ quá nhỏ ⇒ mất ngữ nghĩa HOP | Bắt buộc đo độ lệch, không chỉ suy luận |
| Hiệu năng xoá entry từ DHO-List | Handle 2 tầng cho evict O(1) — không rà từng node |

---

*Bản nháp của tác giả. Khi hoàn thiện hoặc bỏ, cập nhật `../DECISIONS.md`.*