# DHOPM – Kế hoạch Phiên bản 4: Extreme (mức ý tưởng)

> **Trạng thái:** tài liệu **mức ý tưởng** — vạch hướng, ghi nhận mục tiêu và các hướng khảo sát, **chưa chi tiết**. Việc chi tiết hoá (thiết kế, milestone, nghiệm thu) sẽ thực hiện **sau khi V2 và V3 hoàn tất** như đã chốt.

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-PLAN-004 |
| **Version** | 0.2 (Idea only) |
| **Bí danh** | **V4 — Extreme** |
| **Module** | `implementation/dhopm-v4-extreme` (tạo khi bắt đầu G4) |
| **Phụ thuộc** | `00-OVERALL-PLAN.md` (canonical C1–C12); **V2, V3 hoàn tất trước khi triển khai** |
| **Source** | Paper gốc `docs/root/1-s2_0-….md`; ví dụ chạy tay `docs/root/Nhom01_VDChayTay.md` |
| **Định vị threading** | Level 3 (pipeline data-oriented) |

### Revision History

| Phiên bản | Mô tả |
|---|---|
| 0.1 | Lập plan V3-Extreme (ý tưởng) |
| **0.2** | Đổi thành **V4**, mốc so sánh **V3**, bổ sung góc nhìn cửa sổ minOcc (pipeline 3 pha tương thích với evict), threading **Level 3** |

---

## 1. Vị trí & Mục tiêu ý tưởng

Phiên bản "tối ưu cực đoan": **loại bỏ dần khái niệm OOP**, chỉ tập trung vào hiệu năng (tốc độ + bộ nhớ), vẫn trong **Java 25**, vẫn **Thread + worker**, vẫn **trùng kết quả** V1/V2/V3/V4 theo canonical. **Không áp dụng design pattern** (kể cả GoF) vì đây là phiên bản cực đoan — mục tiêu hiệu năng, không mục tiêu cấu trúc pattern.

Các mục tiêu định hướng:
1. Tối đa **cache locality** và **bandwidth bộ nhớ** (dữ liệu contiguous, vòng lặp đơn giản).
2. Loại bỏ overhead của object/boxing/interface/abstract/reflection trong **đường nóng** (hot path).
3. Tối ưu song song sâu: pipeline giữa các giai đoạn, worker chuyên trách.
4. Chỉ giữ lại những gì sinh lợi **đo được** (benchmark chứng minh > V3).

## 2. Các hướng ý tưởng (khảo sát khi triển khai)

### 2.1 Hướng Data-Oriented Design (SoA & bộ nhớ)
- Toàn bộ global DHO-List chuyển sang **cấu trúc mảng phẳng**:
  - `int[][]` hoặc mảng gộp cho `txSlot`, `len` theo từng item; `double[]` cho `doValue`; `int[]` cho support/count.
- Không `class Node`/`interface`/`abstract` cho dữ liệu; chỉ **record/struct-thuần** hoặc mảng.
- Đi qua các mảng **tuần tự/liên khối** để tận dụng prefetch; hạn chế con trỏ nhảy.
- **Cửa sổ minOcc thuận lợi ở đây:** vì `W` hữu hạn ⇒ `WindowBuffer` là mảng **kích thước cố định**, không cấp phát lại ⇒ `head` chỉ là số nguyên; toàn bộ cửa sổ nằm trong L2/L3.

### 2.2 Hướng Procedural & Cache
- Logic thuật toán đặt trong các **method static thuần** (không giữ state trên instance).
- Tối thiểu tạo object trong vòng nóng: recycle buffer, tránh `ArrayList` ở các điểm lặp triệu lần.
- Chuyên hoá số học: `double` các đại lượng; precompute decay **theo tuổi trong cửa sổ** (đã làm ở V3) và **loại bỏ thừa phép tính lặp** (cửa sổ, tích luỹ).

### 2.3 Hướng Song song tối đa (Level 3)
- **Pipeline 3 giai đoạn chạy xen kẽ** (construction của batch *k+1* song song với mining batch *k*) **nếu** đảm bảo semantic one-scan và kết quả.
  - ⚠️ **Ràng buộc thêm do minOcc:** evict **phải đơn luồng và xảy ra trước mọi pha song song** (C10). Với pipeline xen kẽ, cần chứng minh rằng mọi giai đoạn đọc DHO-List đều thấy **cùng một ảnh chụp cửa sổ** ⇒ nếu không chứng minh được ⇒ **giữ pipeline tuần tự**, chỉ song song hoá bên trong từng pha.
- Worker chuyên trách: reader/parser, reconstruction, mining; giao tiếp qua buffer không lock (hoặc lock tối thiểu).
- Sink kết quả lock-free; vẫn đảm bảo determinism C5/C6.

### 2.4 Hướng I/O & JVM
- Đọc file **memory-mapped** (`FileChannel.map`) cho dataset lớn; parse tối giểu, không tạo chuỗi tạm.
- Chỉnh GC (G1/ZGC/khởi động flags) và JIT (kích thước tối đa inline, monomorphic) — **đo**, không áp đặt.
- Giảm object lưu giữ kết quả: trả về cấu trúc tối giản khi caller không cần full transactions.

### 2.5 Hướng "không làm gì" (đo trước, giữ sau)
- Bỏ hẳn các tính năng chỉ phục vụ **quan sát**: nếu đã có kết quả đo ở V2/V3 thì giữ thống kê evict/memory ở V4 chỉ khi nó **nằm ngoài hot path**.
- Nếu `minOcc = 0` (chế độ bám sát paper) không phải mục tiêu hiệu năng chính của V4 → cho phép **từ chối** `minOcc = 0` trong V4, ghi rõ trong tài liệu (và bảo đảm lệnh so sánh dùng `minOcc > 0`).

## 3. Ràng buộc & Tiêu chí quyết định hướng

Khi bắt đầu giai đoạn V4 (sau khi V2/V3 xong) phải:
- **So sánh có chứng cứ:** mỗi hướng phải đo được lợi ích ≥ ngưỡng rõ ràng so với V3; ngừng hướng nào không đạt.
- **Trùng kết quả:** minOcc = 0 ⇒ ≡ V1; minOcc > 0 ⇒ ≡ V2/V3 (double đầy đủ).
- **Đơn giản để review:** mất mát về "dễ đọc" phải được bù bằng tốc độ/bộ nhớ **đo được**, không phải niềm tin.

## 4. Công việc hiện tại (cho tới khi V3 xong)

- [ ] Khoá danh sách này: ghi nhận ý tưởng như trên; **không triển khai**.
- [ ] Khi G4 bắt đầu: viết **plan + tài liệu giai đoạn** (thiết kế, milestone, nghiệm thu) dựa trên benchmark V3; sản xuất **bộ tài liệu riêng của project `dhopm-v4-extreme`** (README, design, test, report) khi kết thúc.
- [ ] Bổ sung bất kỳ ý tưởng mới phát hiện khi làm V2/V3 vào mục 2.

## 5. Tiêu chí "Done" (tạm, sẽ cập nhật khi chi tiết hoá)

- TC/chạy đúng + trùng V3; benchmark vượt trội V3 (định lượng tại thời điểm triển khai); vẫn Thread + worker; vẫn Java 25; không dùng pattern.

---

*Kết thúc plan V4 (ý tưởng). Tài liệu sẽ được chi tiết hoá sau khi V2, V3 hoàn tất.*