# DHOPM – Kế hoạch Phiên bản 3: Extreme (mức ý tưởng)

> **Trạng thái:** tài liệu **mức ý tưởng** — vạch hướng, ghi nhận mục tiêu và các hướng khảo sát, **chưa chi tiết**. Việc chi tiết hoá (thiết kế, milestone, nghiệm thu) sẽ thực hiện **sau khi V1 và V2 hoàn tất** như đã chốt.

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-PLAN-003 |
| **Version** | 0.1 (Idea only) |
| **Phụ thuộc** | `00-OVERALL-PLAN.md` (canonical C1–C6); V1, V2 hoàn tất trước khi triển khai |
| **Source** | Paper gốc `docs/root/1-s2_0-….md`; ví dụ chạy tay `docs/root/Nhom01_VDChayTay.md` |

---

## 1. Vị trí & Mục tiêu ý tưởng

Phiên bản "tối ưu cực đoan": **loại bỏ dần khái niệm OOP**, chỉ tập trung vào hiệu năng (tốc độ + bộ nhớ), vẫn trong **Java 17**, vẫn **Thread + worker**, vẫn **trùng kết quả** V1/V2 theo canonical.

Các mục tiêu định hướng:
1. Tối đa **cache locality** và **bandwidth bộ nhớ** (dữ liệu contiguous, vòng lặp đơn giản).
2. Loại bỏ overhead của object/boxing/interface/abstract/reflection trong **đường nóng** (hot path).
3. Tối ưu song song sâu: pipeline giữa các giai đoạn, worker chuyên trách.
4. Chỉ giữ lại những gì sinh lợi **đo được** (benchmark chứng minh > V2).

## 2. Các hướng ý tưởng (khảo sát khi triển khai)

### 2.1 Hướng Data-Oriented Design (SoA & bộ nhớ)
- Toàn bộ global DHO-List chuyển sang **cấu trúc mảng phẳng**:
  - `int[][]` hoặc mảng gộp cho `tid`, `len` theo từng item; `double[]` cho `doValue`; `int[]` cho support/count.
- Không `class Node`/`interface`/`abstract` cho dữ liệu; chỉ **record/struct-thuần** hoặc mảng.
- Đi qua các mảng **tuần tự/liên khối** để tận dụng prefetch; hạn chế con trỏ nhảy.

### 2.2 Hướng Procedural & Cache
- Logic thuật toán đặt trong các **method static thuần** (không giữ state trên instance).
- Tối thiểu tạo object trong vòng nóng: recycle buffer, tránh `ArrayList` ở các điểm lặp triệu lần.
- Chuyên hoá số học: `double` các đại lượng; cân nhắc precompute decay theo TL (đã thử ở V2) và **loại bỏ thừa phép tính lặp** (cửa sổ, tích luỹ).

### 2.3 Hướng Song song tối đa
- Pipeline 3 giai đoạn chạy xen kẽ (construction của batch k+1 song song với mining batch k) **nếu** đảm bảo semantic one-scan và kết quả.
- Worker chuyên trách: reader/parser, reconstruction, mining; giao tiếp qua buffer không lock (hoặc lock tối thiểu).
- Sink kết quả lock-free; vẫn đảm bảo determinism C5/C6.

### 2.4 Hướng I/O & JVM
- Đọc file **memory-mapped** (`FileChannel.map`) cho dataset lớn; parse tối giản, không tạo chuỗi tạm.
- Chỉnh GC (G1/ZGC/khởi động flags) và JIT (Kích thước tối đa inline, monomorphic) — **đo**, không áp đặt.
- Giảm object lưu giữ kết quả: trả về cấu trúc tối giản khi caller không cần full transactions.

## 3. Ràng buộc & Tiêu chí quyết định hướng

Khi bắt đầu giai đoạn V3 (sau khi V1/V2 xong) phải:
- **So sánh có chứng cứ:** mỗi hướng phải đo được lợi ích ≥ ngưỡng rõ ràng so với V2; ngừng hướng nào không đạt.
- **Trùng kết quả:** TC1–TC8 + trùng V2 (double đầy đủ).
- **Đơn giản để review:** mất mát về "dễ đọc" phải được bù bằng tốc độ/bộ nhớ **đo được**, không phải niềm tin.

## 4. Công việc hiện tại (cho tới khi V2 xong)

- [ ] Khoá danh sách này: ghi nhận ý tưởng như trên; **không triển khai**.
- [ ] Khi G3 bắt đầu: viết **plan + tài liệu giai đoạn** (thiết kế, milestone, nghiệm thu) dựa trên benchmark V2; sản xuất **bộ tài liệu riêng của project `dhopm-v3-extreme`** (README, design, test, report) khi kết thúc.
- [ ] Bổ sung bất kỳ ý tưởng mới phát hiện khi làm V1/V2 vào mục 2.

## 5. Tiêu chí "Done" (tạm, sẽ cập nhật khi chi tiết hoá)

- TC/chạy đúng + trùng V2; benchmark vượt trội V2 (định lượng tại thời điểm triển khai); vẫn Thread + worker; vẫn Java 17.

---

*Kết thúc plan V3 (ý tưởng). Tài liệu sẽ được chi tiết hoá sau khi V1, V2 hoàn tất.*