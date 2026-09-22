# DHOPM – Kế hoạch Phiên bản 1: Standard

> Phiên bản "làm đúng trước, làm nhanh sau": code **rõ ràng, dễ đọc, dễ bảo trì**, tuân 100% chuẩn hóa thuật toán ở plan tổng thể (mục 2), dùng **Thread + worker mức Level 1**, NFR tối thiểu.

## Document Header

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-PLAN-001 |
| **Version** | 1.0 (Draft) |
| **Phụ thuộc** | `00-OVERALL-PLAN.md` (chuẩn hóa thuật toán C1–C6, khung threading) |
| **Source** | `docs/root/1-s2_0-S095219762600792X-main.md`, `docs/root/Nhom01_VDChayTay.md` |

---

## 1. Mục tiêu

1. Cài đặt đúng DHOPM theo canonical spec → **qua TC1–TC8**.
2. Dùng **Thread + worker** (Level 1): Reconstruction song song theo node; Mining song song theo cây con gốc.
3. Đạt **tập DOP trùng golden** và làm **nền golden double đầy đủ** cho V2 so sánh sau này.
4. NFR tối thiểu: đúng, ổn định, tái lập (deterministic), thread-safe.

## 2. Phạm vi & Ngoài phạm vi

**Trong phạm vi:**
- Global DHO-List + Reconstruction + DUBO + Conditional list + Mine (DFS).
- Input: text (TID tường minh) cho test; FIMI reader cho benchmark sơ bộ (tái dùng `dhopm-common`).
- Level 1 threading.
- TC1–TC8 + benchmark cơ bản (runtime/memory sơ bộ).

**Ngoài phạm vi (để dành V2/V3):**
- Tối ưu cấu trúc dữ liệu (List vs Set, ID+name, primitive array).
- Construction song song, work-stealing, chia cây sâu.
- Bất kỳ mẹo tối ưu nào làm giảm độ rõ ràng.

## 3. NFR (tối thiểu)

| NFR | Yêu cầu |
|---|---|
| N1 Correctness | TC1–TC8 pass; đối chiếu với golden |
| N2 Determinism | Cùng input/tham số → cùng output (INV-E) |
| N3 Thread-safety | Không race condition; kết quả đúng khi song song |
| N4 Conf predict | Mỗi thành phần có test đơn vị cơ bản |
| N5 Runtime sơ bộ | Ghi được wall-clock 3 giai đoạn (không tối ưu) |

## 4. Định hướng thiết kế (V1 — "đúng & dễ hiểu")

> Thiết kế chi tiết class được quyết định trong giai đoạn triển khai; dưới đây là hướng dẫn (guideline). V1 dùng OOP hợp lý, trực tiếp khớp cấu trúc canonical.

- **Item**: `String` name (giữ đơn giản; V2 mới mã hoá số nguyên).
- **DHONode**: `item`, `ArrayList<Entry>` (Entry = `record(tid, len)`), `support()` = số entry, `doValue` (double).
- **DHOList**: `LinkedHashMap<Item, Node>` (thứ tự tạo + tra cứu), `ArrayList<Node>` để sort/duyệt. `sortBySupportAscending()` = stable sort theo `support()`.
- **Metrics**: các hàm thuần `decayFactor(f, tl, tid)`, `occupancy`, `dampedOccupancy`.
- **DUBO**: nhóm entry theo length dùng `TreeMap<Integer, int[2]>` (count, lastTid) hoặc nhóm tương đương; theo C1.
- **ConditionalListBuilder**: two-pointer trên 2 danh sách entry đã sắp TID.
- **Miner**: DFS đệ quy theo canonical; so sánh dùng ε (C4).
- **Engine** (`MiningEngine`): `loadBatch`, `mineNow` theo pipeline.

### Quy ước dùng chung bắt buộc từ canonical
- Entry append theo TID tăng dần (INV-B).
- DO/DUBO một node luôn tính tuần tự (C5).
- Conditional list không sort (INV-D).

## 5. Khung Threading Level 1 (bắt buộc)

```
Pool: ExecutorService, fixed, nThreads = Runtime.getRuntime().availableProcessors()

Reconstruction:
  - submit 1 Callable per node: recalcula DO(node) (reset DO về 0, cộng dồn tuần tự entries)
  - invokeAll, chờ hết → sort support (đơn luồng, stable) → xong

Mining:
  - Reconstruct xong, list được chia theo các prefix gốc (mỗi node đủ điều kiện mở rộng)
    → submit 1 task per cây con gốc (Mine(root_i, {i}))
  - Mỗi task chạy DFS nội bộ tuần tự (đệ quy trên chính task đó)
  - Kết quả các task gộp về tập kết quả chung (đồng bộ hóa tập trung sau khi join,
    HOẶC dùng ConcurrentLinkedHashSet tin cậy) — ưu tiên gộp sau join để giữ đơn giản

Construction: ĐƠN LUỒNG (không chia batch) — tránh race, giữ INV-B.
```

**Yêu cầu tối thiểu:**
- Pool đóng/giải phóng đúng (shutdown/await).
- Lỗi trong task → giữ/trả rõ ràng (Future.get ném ngoại lệ, không nuốt).
- Số task Reconstruction = số node; số task Mining = số cây con gốc khả thi.

## 6. Công việc & Milestone (V1)

**M1 – Setup hạ tầng** (dựa trên G0 tổng thể)
- [ ] Tạo `dhopm-v1-standard` module; import `dhopm-common` (io, config, TestKit).
- [ ] CLI chạy thử đơn giản (nạp text/FIMI, in DOP).

**M2 – Mô hình dữ liệu & Construction** (GĐ1)
- [ ] `Item`, `Entry`, `Transaction` (chuẩn hoá item phân biệt, loại giao dịch rỗng).
- [ ] `DHONode`, `DHOList` (createOrGetNode, append entry, stable sort).
- [ ] `DHOListBuilder`: scan batch (đơn luồng), theo thứ tự TID.
- [ ] `StreamStatus`: tổng transaction, TL; `minSup = ∂ × tổng`.

**M3 – Reconstruction & Metrics** (GĐ2)
- [ ] `MetricCalculator` (F1–F6), `Reconstructor` (reset DO, cộng dồn tuần tự).
- [ ] Threading Level 1 cho Reconstruction (mỗi node 1 task).
- [ ] Test đơn vị: DO(A)=0.8551, DO(F)=1.2553 (TL=8, f=0.9).

**M4 – Mining** (GĐ3)
- [ ] `DUBOCalculator` (C1) — test đơn vị: E=4.5, B=1.8, CD=1.6402, AF=0.93, CDE=1.181, F=3.0375.
- [ ] `ConditionalListBuilder` (two-pointer; C2, INV-D).
- [ ] `Miner` DFS (C3,C4); gộp kết quả deterministic.
- [ ] Threading Level 1 cho Mining (mỗi cây con 1 task).

**M5 – Nghiệm thu đúng đắn**
- [ ] GoldenRunner chạy TC1–TC8 → pass (so tolerance 1e-6 với bảng; ghi lại **actual double** làm golden cho V2).
- [ ] Determinism: chạy lại nhiều lần (đổi pool size 1/2/4/cpu) → cùng kết quả.

**M6 – Benchmark sơ bộ & bộ tài liệu project V1**
- [ ] Chạy 2 dataset đại diện từ `dataset/` – `mushroom.dat` (dense, ∂=6%) và `retail.dat` (sparse, ∂=0.1%), chia 5 phần incremental; ghi runtime 3 giai đoạn + peak memory.
- [ ] **Bộ tài liệu riêng của project `dhopm-v1-standard`** (đặt trong module): README (chạy/tham số), design tóm tắt, test plan & kết quả TC1–TC8, benchmark report sơ bộ.
- [ ] Đóng dấu: V1 là **golden reference** cho V2/V3.

## 7. Nghiệm thu & Tiêu chí "Done" (V1)

- [ ] TC1–TC8 pass.
- [ ] Determinism qua nhiều pool size.
- [ ] Threading Level 1 thực sự được dùng (không phải đơn luồng ẩn) — xác nhận qua log/số task.
- [ ] Không có race (chạy `-ea`, nhiều lần; hoặc sanity check đơn giản).
- [ ] Benchmark sơ bộ ghi được số liệu.
- [ ] Bộ tài liệu project V1 đầy đủ (M6).

## 8. Rủi ro (V1)

| Rủi ro | Xử lý |
|---|---|
| Kết quả lệch moốt chữ số thập phân so với bảng 4 chữ số | So golden với tolerance 1e-6; kiểm tra lại bằng công thức canonical (C1–C4) |
| Race khi gộp kết quả Mining | Gộp sau khi join (đơn giản nhất), GiSao đó mới cân nhắc concurrent set |
| Hiểu sai C2 (skip node khi sup<minSup) | Đối chiếu pseudocode Mine + hành vi G trong chạy tay |
| Recursion sâu (DFS) gây stack overflow ở dataset lớn | V1 ghi nhận; V2 xử lý (đổi ranh giới/stack task). Dataset benchmark nhỏ trước |

---

*Kết thúc plan V1. Đi tiếp `02-OPTIMIZED-VERSION-PLAN.md`.*