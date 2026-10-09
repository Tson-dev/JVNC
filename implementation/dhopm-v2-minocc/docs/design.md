# Thiết kế V2 MinOcc/Window (implementation/dhopm-v2-minocc)

> **Trạng thái: early-access.** Đặc tả (`docs/plans/02-MINOCC-WINDOW-VERSION-PLAN.md`,
> `00-OVERALL-PLAN.md` §2.3/2.7) còn vài mâu thuẫn; tài liệu này + báo cáo
> `../../docs/reports/G2-V2-MINOCC-BAOCAO.md` **chốt ngữ nghĩa đã triển khai**.
> Tuân các quyết định D38/D39/D40/D41 (sổ `docs/DECISIONS.md`).

## Đường ống (GĐ0 → GĐ1 → GĐ2 → GĐ3)

| Giai đoạn | Lớp | Xử lý theo | Ghi chú |
|---|---|---|---|
| GĐ0 Window | `WindowBuffer` + `Handle` | 1 luồng trong `loadBatch` | circular O(1); evict `m−W` trước write `m`; bỏ qua write txs thỏa `batchMax − tid ≥ W` |
| GĐ1 Construction | `DHOListBuilder` | 1 luồng | `add` (ghi entry) / `addNoWindow` (đếm, không ghi); entry nối TID tăng (INV-B) |
| GĐ2 Reconstruction | `Reconstructor` | song song theo nút | `discardDeadPrefix()` trước, tích lũy **entry sống** tuần tự trên 1 nút (C5) → stable sort (C3) |
| GĐ3 Mining | `Miner` + `ConditionalListBuilder` | song song theo root | C2 bỏ nút bằng `support()=size−head`; bound `min(DUBO, Z(X))`; C4 cộng DOP với ε; `windowTruncated` flag |

## Cửa sổ minOcc và minSup 2 pha

```
W(f,minOcc)  = ⌈ln(minOcc(1−f)) / ln f⌉         minOcc=0 hoặc f=1 ⇒ W=∞ (paper mode, cửa sổ tắt là V1)
N_eff   = min(TL, W)
minSup  = ∂ × N_eff                   pha 1: TL < W ⇒ ∂×TL (≡ paper) ; pha 2: TL ≥ W ⇒ ∂×W (đóng băng)
Z(f,TL) = (1 − f^TL) / (1 − f)        trần DO (mọi X: DO(X) ≤ Z)
```

- **INV-G**: các transaction ngoài cửa sổ có tổng đóng góp `< minOcc` ⇒ `|DO_win − DO_full| ≤ minOcc`.
- **Short-circuit O(1)**: nếu `minSup > Z + ε` ⇒ tập DOP chắc chắn rỗng, trả ∅ không mine.
- **UB' = min(DUBO, Z(X))** chỉ khi cửa sổ **hữu hạn**; tại minOcc=0 giữ nguyên cận DUBO của V1 (INV-I).
- Entry sống (D39): `ref1 == null` ⇒ live; ngược lại sống **chỉ khi** `ref1.tx != null && ref1.tid == entry.tid`.
  Buộc lưu `tid` trong Entry để chống lỗi âm thầm khi `WindowBuffer` tái dùng slot vòng tròn.

## Ánh xạ GoF

- **Proxy 2 tầng**: `Handle` = `(TxSlot tx, int tid)` — node giữ `Handle` gián tiếp, window giữ slot thật;
  evict chỉ xóa `tx` của slot (O(1)), không duyệt danh sách nút.
- **Strategy**: `MetricCalculator` (giống V1) + `ZCalculator` tách công thức — V2 thêm trần Z mà không đụng pipeline.
- **Builder**: `DHOListBuilder`/`ConditionalListBuilder`.
- **Facade**: `MiningEngineV2` ẩn pipeline sau `Engine + PhaseAwareEngine + ProgressAwareEngine + WindowAwareEngine`.
- **Observer**: `WindowAwareEngine.windowInfo()`/`warnings()` (common) — CLI in dòng window mà không vào nội bộ.

## Quyết định lập trình chính (ánh xạ G2-D* / D*)

- **G2-D11 `WindowMath` + `ParameterValidator` ở `dhopm-common`** — V1 tái dùng validator; không đổi hành vi V1.
- **G2-D12 tên tham số**: `minOcc` = cửa sổ (V2+ default `1e-6`); `epsilon` = sai số so sánh (`1e-9`).
- **G2-D13 Entry 3 trường**: `record Entry(Handle ref1, int len, int tid)` — trường giữa là **độ dài |T| cached**
  (đặc tả gọi nó là `ref2` và đồng thời dùng làm độ dài; code đặt tên `len` để rõ); `tid` bắt buộc (D39).
  Node không lưu `ref2` riêng — `DHONode` chỉ giữ `ref1` + con (khác mô tả §5.2.1 "entry giữ 2 ref");
  `len`/`tid` là bản sao nội trang, tránh 3 lần deref khi đọc ngoài cửa sổ.
- **G2-D14 hai công thức maxPartial**: validator dùng **chính xác** `Z(f,TL)/N_eff`; lệnh `window` (không có TL)
  chỉ in **xấp xỉ** `1/((1−f)·W)` với hậu tố `…Asymptotic`, `…Exact = null` (không lẫn, D38).
- **G2-D15 windowOverride**: chưa mở ngoài nghiên cứu; nếu mở, khác `W(f,minOcc)` ⇒ phải cảnh báo `research-mode` (D40).
- **D41: minOcc in mọi output** — header CLI và dòng benchmark đều in minOcc.
- `dhONode.support()` = `size() − head()` (đếm entry sống từ đầu danh sách), không cần duyệt đuôi chết.

## Bất biến lập trình

- **INV-A**: TID tăng nghiêm ngặt toàn stream; vi phạm → `IllegalArgumentException` ở `loadBatch` (V2 thêm giảm TID khi đọc tuần tự).fail-fast tại `ParameterValidator`.
- **INV-B**: entry mỗi nút nối theo TID tăng.
- **INV-C**: `len`/`|T|` giữ theo entry (sau khi Transaction loại trùng + sắp item).
- **INV-D**: không sắp list điều kiện; giao giữ thứ tự TID.
- **INV-E**: kết quả độc lập workers ∈ {1,2,4,CPU} — mining 1 task/root qua `WorkerPool.invokeAll`, gộp theo thứ tự task; nút tích lũy DO trên 1 luồng (C5).
- **INV-F**: `minSup = ∂ × N_eff` tính ở thời điểm `mineNow` (2 pha).
- **INV-I**: `minOcc = 0` ⇒ V2 bit-for-bit ≡ V1 (TC1–TC8). Khi minOcc=0 cửa sổ **không kích hoạt**: không evict, không short-circuit, cận DUBO V1.
- **INV-G**: sai số cửa sổ so với full-recompute ≤ minOcc (TC11/TC18; minOcc=0 ⇒ lệch 0).

## Điểm cần chú ý khi đọc code

- `WindowBuffer` circular `tid mod W`; evict trả `boolean` (có evict thật) để tính `evicted` trong windowInfo.
- GĐ1 bỏ qua ghi txs đã nằm ngoài cửa sổ ngay lúc quét — dataset rất lớn vẫn giữ O(live) thay vì O(TL).
- `Reconstructor` snapshot list theo thứ tự tạo rồi stable-sort theo support.
- `Miner` giao 2 nút two-pointer trên entry TID-tăng (INV-B); partner `support < minSup` bỏ trước khi giao.
- `warnings()` từ `MiningEngineV2` được CLI in khi có (ví dụ window inactive, limit bỏ qua).