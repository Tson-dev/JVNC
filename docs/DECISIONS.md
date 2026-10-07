# DHOPM — Sổ quyết định (Decision Log / ADR)

| Mục | Giá trị |
|---|---|
| **Document ID** | DHOPM-DEC-000 |
| **Version** | 1.1 |
| **Status** | ✅ Nguồn chân lý cho mọi quyết định đã chốt |
| **Phạm vi** | Toàn bộ dự án DHOPM/DOPM (thuật toán, kiến trúc, tài liệu, quy trình) |
| **Quy tắc** | Plan/SRS **không tự quyết lại** — chỉ trỏ về `D<n>` tương ứng. Quyết định mới **phải** được ghi ở đây trước khi áp dụng rộng. |

---

## 0. Cách dùng

| Trạng thái | Nghĩa |
|---|---|
| ✅ **Đã chốt** | Đang áp dụng. Thay đổi ⇒ thêm `D<n>` mới ghi "thay thế D<n> cũ", không sửa lịch sử. |
| 🕓 **Đang cân nhắc** | Đã bàn, chưa chốt. Tài liệu khác ghi "theo D<n> (đang cân nhắc)". |
| ⛔ **Bác bỏ** | Đã xét và loại. Giữ lại để không đề xuất lại. |

---

## 1. Quyết định về công cụ & kiến trúc

| ID | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| **D1** | Build tool = **Maven (đa module)** | Chuẩn, hỗ trợ multi-module + Java 25 LTS | ✅ |
| **D2** | Dự án code nằm ở **`implementation/`** trong repo `D:\JVNC\JVNC` | Tách tài liệu (`docs/`) khỏi code | ✅ |
| **D3** | `Default Project/` và `draft/` cũ | Đã lưu trữ branch `archive/legacy-draft`, xoá khỏi main; **không tái dùng** | ✅ |
| **D4** | Thread pool mặc định = `Runtime.availableProcessors()` | Tự động theo phần cứng; override qua CLI/config | ✅ |
| **D5** | Input: **FIMI** (mặc định) + **text** (TC) + **ZIP**; `utility-FIMI` **planned** (D42) | Đủ cho test, benchmark, và mở khoá dataset của paper (`accidents.dat`) | ✅ |
| **D14** | **Nơi chốt quyết định = `docs/DECISIONS.md`** (tài liệu này) | Tránh mâu thuẫn giữa các plan | ✅ |

---

## 2. Quyết định về thuật toán (canonical)

| ID | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| **D6** | Công cụ ↔ thuật toán: **chỉ qua API ổn định trong `dhopm-common`**. CLI là client của API. | Không để công cụ chạm nội bộ engine; thuật toán đổi cấu trúc không phá công cụ | ✅ |
| **D7** | Frontend ↔ Backend: frontend qua **1 Manager CLI/API duy nhất**; hợp đồng = **đặc tả giao thức**, không share class Java | Repo tách, nền tảng khác nhau (Windows/macOS), frontend không rebuild khi backend đổi | ✅ |
| **D8** | Quản lý tài nguyên = **`workers` + `limit` + `parts` ngay trong Manager**, **không** lớp native riêng | Java không có API quota CPU/RAM portable giữa Windows/macOS; nhu cầu thực tế đã đủ. Xem §6 (bản `ResourceManager → WindowsModule/MacModule` **đã bác bỏ**) | ✅ |
| **D16** | **Tên định danh quyết định cho canonical: C1–C12** (mở rộng từ C1–C6 của V1) | C1–C6 là quyết định của paper; C7–C12 là quyết định mới về ε | ✅ |
| **D17** | **Bất biến toàn cục: INV-A…INV-J** | Có thứ tự rõ ràng, test được; `INV-I`/`INV-G` là hai bất biến then chốt của hướng ε | ✅ |

---

## 3. Quyết định về hướng ε (thay đổi nền móng — 2026-10)

Nguồn ý tưởng: **`docs/Draft Idea.txt`** (tác giả). Chuẩn hoá tại `plans/00-OVERALL-PLAN.md` §2.

| ID | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| **D9** | **ε = sai số hệ thống do người dùng đặt, đóng vai trò ngưỡng DƯỚI của DO.** Đổi tên tham số `epsilon` cũ (so sánh 1e-9) → **`ε_cmp` / `epsilonCmp`**. | Tránh trùng tên với ε mới; tách rõ "tham số ngữ nghĩa" với "sai số kỹ thuật" | ✅ |
| **D10** | **Công thức cửa sổ:** `W = ⌈ln(ε(1−f))/ln f⌉` cho `0<f<1`, `0<ε<1/(1−f)`. `ε=0` hoặc `f=1` ⇒ `W = ∞`. `ε ≥ 1/(1−f)` ⇒ **lỗi cấu hình**. | Đảm bảo tổng sai số do các transaction bị bỏ ≤ ε: `Σ_{k≥W} f^k = f^W/(1−f) ≤ ε`. Công thức đóng, tra bằng được | ✅ |
| **D11** | **`minSup = ∂ × N_eff`, `N_eff = min(TL, W)` — hai giai đoạn.** Pha 1 (`TL < W`): `∂×TL` (≡ paper). Pha 2 (`TL ≥ W`): `∂×W` (đóng băng). | Ngưỡng phải **đo trên cùng đơn vị với dữ liệu đang dùng**. Nếu giữ `∂×TL` sau khi cắt cửa sổ ⇒ ngưỡng tăng vô hạn trong khi dữ liệu chỉ còn `W` tx ⇒ số pattern tuyến tính → 0 ⇒ thuật toán "chết dần" | ✅ |
| **D12** | **Cơ chế evict: handle hai tầng `ref1 → ref2`.** Evict = clear payload `ref2` (**O(1)**); node phát hiện entry chết trong lúc duyệt; entry chết luôn là **tiền tố** ⇒ compaction bằng chỉ số `head`, **O(1) amortized**. Evict chạy **đơn luồng trước mọi pha song song**. | Rà từng node để xoá entry là **O(\|I\|)** mỗi lần evict — không dùng được cho stream. Hai tầng **bắt buộc**: không được dereference tham chiếu đã vô hiệu (INV-H) | ✅ |
| **D13** | **Số phiên bản = 4.** V1 Standard (oracle, ε=0) · V2 Epsilon/Window · V3 Optimized · V4 Extreme. Ưu tiên **V1 → V2 → V3 → V4**. | Tác giả chốt. V2 là **mở rộng ngữ nghĩa** (không phải chỉ tối ưu) nên tách khỏi V3/V4 để so sánh "cửa sổ" tách bạch khỏi "tối ưu" | ✅ |
| **D18** | **Tương đương paper:** `ε = 0` (hoặc `W ≥ TL`) ⇒ **mọi phiên bản phải bit-for-bit ≡ V1** (INV-I). TC1–TC8 luôn chạy với `ε = 0`. | Bảo toàn mốc đã kiểm chứng; biến ε thành **công tắc bật/tắt** thay vì thay đổi thuật toán | ✅ |
| **D19** | **Trần DO & bound chặt hơn:** (a) nếu `minSup > Z(f,TL)+ε_cmp` ⇒ **short-circuit O(1)** + cảnh báo kèm số liệu; (b) prune bằng `UB'(X) = min(DUBO(X), Z(X))`, `Z(X) = Σ_{t∈T_sống(X)} f^(TL−t)`. | Vì `\|X\| ≤ \|T_t\|` nên `DO(X) ≤ Z(f,TL)` — trần vật lý. Đây là limitation #1 & #2 mà paper tự nêu. `min(DUBO,Z) ≤ DUBO` ⇒ **chỉ prune thêm, không bao giờ prune sai** | ✅ |
| **D20** | **Danh tính kết quả** = bộ tham số **`(∂, f, ε, TL)`** (C12). So sánh kết quả giữa các phiên bản/phiên chạy **bắt buộc** dùng cùng bộ này. | Vì `N_eff` phụ thuộc `ε`, thay `ε` **có thể đổi tập DOP** ⇒ ε là tham số **ngữ nghĩa**, không chỉ tham số hiệu năng | ✅ |
| **D21** | **Quy ước ký hiệu:** kích thước cửa sổ gọi là **`W`** (không phải `Z`); khối lượng suy giảm gọi là **`Z(f,TL)`**; DB hiệu dụng gọi là **`N_eff`**. | `Draft Idea.txt` dùng `Z` cho window và `winSup` cho DB hiệu dụng; khi thêm `Z(f,TL)` (trần DO) sẽ trùng tên | ✅ |
| **D22** | **`W(f,ε)` và validator đặt ở `dhopm-common`** (`dhopm.common.window`), dùng chung cho cả 4 version. | Là **định nghĩa dùng chung**, không phải tối ưu của version nào. Nhờ vậy V1 dùng được để in cảnh báo mà không đổi hành vi | ✅ |
| **D23** | **Bộ test ngữ nghĩa cửa sổ: TC9–TC18 + bảng trường hợp biên E1–E10.** | Bắt buộc có test cho mọi trường hợp biên mà người dùng có thể chọn sai (E7: ε quá lớn ⇒ W=0; E5: ∂=0.15 không khả thi với N_eff lớn; E2: f=1, ∂=1 suy biến) | ✅ |
| **D38** | **Miền `∂` khả thi có hai công thức, phải phân biệt:**<br>• **chính xác (hữu hạn):** `∂ ≤ Z(f,TL) / N_eff` — **validator dùng cái này**<br>• **xấp xỉ (TL→∞):** `∂ ≲ 1/((1−f)·W)` — chỉ để ước lượng | Ở `TL` ngắn hai giá trị lệch **một bậc độ lớn** (`f=0.9, TL=4`: xấp xỉ 6.54 %, chính xác 85.98 %). Nếu validator dùng nhầm xấp xỉ ⇒ chặn oan các tham số hợp lệ | ✅ |
| **D39** | **Chống lỗi âm thầm do tái sử dụng slot vòng tròn:** `Entry` **lưu kèm `tid`**; coi entry là sống **chỉ khi** `handle != null && handle.tid == entry.tid` | `WindowBuffer` dùng `tid mod W` ⇒ tái dùng **cùng object Handle**. Node giữ `ref1` tới slot cũ sẽ thấy `handle.tx` **của transaction mới** ⇒ `tid`/DO/occupancy sai mà **không crash**. Là điều kiện cần cho INV-H | ✅ |
| **D40** | **`windowOverride` là công cụ nghiên cứu, không phải tính năng.** Khác `W(f,ε)` ⇒ phải cảnh báo + ghi nhãn `research-mode` vào output/benchmark. Nếu vượt lớn hơn thì vô hại, cho phép im lặng | Ghi đè `W` nhỏ hơn làm **hỏng INV-G**: sai số vượt ε mà hệ thống im lặng. Kết quả cũng **không được so sánh** với lần chạy `ε` thuần | ✅ |
| **D41** | **Chính sách `ε` có hai tầng:** thư viện có mặc định (V1 = `0`, V2+ = `1e-6`); **giao thức Manager bắt buộc khai báo tường minh**. Ở mọi output/benchmark phải **in `ε`** | Mặc định thư viện tiện cho test; nhưng `ε` thay đổi **tập DOP** (D20) ⇒ giao thức không được im lặng. Không mâu thuẫn với D28 — khác tầng | ✅ |
| **D42** | **`utility-FIMI` = planned, chưa triển khai.** Không có reader, không có cờ `--format utility`, **không có dữ liệu** trong repo | Ghi "đã hỗ trợ" sẽ khiến người đọc tưởng chạy được. Nhóm utility là **hướng mở rộng** (D36), không phải phạm vi G1–G6 | ✅ |
| **D43** | **`--dataset` bắt buộc có extension;** nhận `.dat`/`.txt`/`.text`/`.csv`/`.tsv` (text thuần) và `.zip`; **từ chối `.rar`/`.7z`**; bỏ trống cũng từ chối | Extension quyết định **cách mở byte** — thiếu nó thì không đoán được. ZIP là định dạng JDK đọc native (`ZipInputStream`) và mọi nền tảng đều có công cụ; `.rar`/`.7z` cần dependency thứ ba vô ích. So khớp **không phân biệt hoa/thường** | ✅ |
| **D44** | **Mỗi dataset có 1 bản `.zip` trong `dataset/zip/`** (11 file, gồm `default.dat`). ZIP phải chứa **đúng 1 entry** để "1 đường dẫn = 1 dataset" không mơ hồ; reader **giải nén trực tiếp**, không ghi tạm ra đĩa | Dataset lớn tới 45 MB, repo không nên chứa 2 bản; nén tốt nhất đạt 4–6 %. Ràng buộc "đúng 1 entry" chặn nhầm lẫn khi bó nhiều dataset vào một zip | ✅ |

---

## 4. Quyết định về tài liệu & quy trình

| ID | Quyết định | Lý do | Trạng thái |
|---|---|---|---|
| **D24** | **Thang tài liệu** (mỗi tài liệu chỉ 1 vai trò):<br>`DECISIONS.md` (quyết định) → `plans/` (chiến lược) → `srs/` (yêu cầu) → `phases/` (kế hoạch thực thi) → `module/docs/` (as-built) → `reports/` (kết quả) | Tài liệu cũ trộn vai trò (plan tự quyết, spec trùng SRS) → mâu thuẫn | ✅ |
| **D25** | **Mỗi tài liệu có dòng trạng thái** (`Draft` / `Doing` / `Done`) ngay trong header | `P1-G1.md` toàn `[ ]` trong khi báo cáo nói đã xong → lệch thực tế | ✅ |
| **D26** | **Mỗi giai đoạn (G0–G7) có plan riêng** trong `docs/phases/` trước khi code | Plan chi tiết sinh ra lúc làm, không cố định trước | ✅ |
| **D27** | **Bộ lệnh CLI chuẩn** (D6): `mine` · `detail` · `stream` · `golden` · `inspect` · `window` · `validate` · `sweep`. Mọi version phải chạy được **cùng bộ lệnh** | So sánh chéo phiên bản phải công bằng | ✅ |
| **D28** | **Mỗi request/CLI phải khai báo `(∂, f, ε)`**; thiếu ⇒ lỗi, không tự đoán | ε là tham số ngữ nghĩa (D20) | ✅ |
| **D29** | **`ε_cmp` (sai số so sánh) mặc định `1e-9`**, chỉ làm tròn 4 chữ số khi hiển thị/test | Tách khỏi ε; giữ nguyên hành vi V1 | ✅ |
| **D30** | **`ε ≥ 1/(1−f)` là lỗi cấu hình**, không phải cảnh báo | `W = 0` ⇒ cửa sổ rỗng ⇒ kết quả vô nghĩa (mọi pattern đều đạt hoặc không pattern nào) | ✅ |
| **D31** | **`∂ = 0` cho phép nhưng phải cảnh báo** (`minSup = 0` ⇒ mọi pattern là DOP ⇒ output bùng nổ) | Không ép cấu hình, nhưng phải cảnh báo | ✅ |

---

## 5. Quyết định còn đang bàn (chưa chốt)

| ID | Câu hỏi | Phương án đang nghiêng về | Ảnh hưởng |
|---|---|---|---|
| **D32** | Baseline **HOMI-D / HEP-D** có làm không, và đặt ở đâu? | Nếu làm ⇒ **module riêng** `dhopm-baseline` (khác *thuật toán*, không phải khác *phiên bản*), **sau G2** | H4; giá trị khoa học nhưng không chặn V2 |
| **D33** | Ngưỡng chuẩn hoá `nDO = DO/Z(f,TL)` có mở không? | **Chỉ mở nếu sau G2 thấy `∂` vẫn khó chọn**; luôn giữ chế độ RAW (`∂×N_eff`) làm mặc định | Thêm tham số; có thể không cần |
| **D34** | Frontend (`javanc`) có chạy trên **máy khác** không? | Nếu không ⇒ **JSONL qua stdio**, bỏ TCP/daemon | D7, D8 |
| **D35** | `docs/Document/**` là thiết kế CLI cũ hay ý muốn mới? | Phải **chọn 1**; cái kia thành "UX sketch cho app" hoặc bỏ | H7 |
| **D36** | Có làm **utility-based (HUIM)** không? | Nếu có ⇒ **tách project ngay**, không trộn | Phạm vi lớn nhất còn lại |
| **D37** | Có đưa kết quả ra ngoài (báo cáo/hội thảo) không? | Nên — đó là lý do để làm R5/R6 | Mức đầu tư |

---

## 6. Quyết định đã bác bỏ

| Nội dung | Lý do bác bỏ |
|---|---|
| **SPI / plugin discovery / `PluginRegistry`** | Có 4 engine biết trước tên; hardcode list factory rõ hơn. YAGNI |
| **`ResourceManager` → native Windows/macOS** đầy đủ | Java không có API quota CPU portable; `workers` + `--limit` đã đủ |
| **Recovery / `JobLedger` / journal** | Sau cửa sổ ε, một lần `mine` là vài giây ⇒ vấn đề giả |
| **Daemon TCP / remote LAN** | Chưa có nhu cầu; stdio đủ |
| **FE10 "bật/tắt C1–C12 lúc chạy"** như tính năng chính | Phạm vi rất lớn, dễ tạo kết quả phi chuẩn gây nhầm lẫn. Giữ ở dạng bảng tham số nâng cao |
| **Đổi tên `Z` → giữ nguyên như `Draft Idea.txt`** | Sẽ trùng với `Z(f,TL)` (trần DO). Xem D21 |
| **Chạy 10 dataset nặng trong `mvn test`** | Tách sang profile `-Pheavy` |

---

## Phụ lục — Ánh xạ quyết định → tài liệu

| Quyết định | Tài liệu chi tiết |
|---|---|
| D9–D12, D18–D23, D38–D41 | `plans/00-OVERALL-PLAN.md` §2.3–2.7, §6 ; `plans/02-EPSILON-WINDOW-VERSION-PLAN.md` |
| D13 | `plans/00` §1.1, §8 |
| D6, D27, D28, D41 | `plans/00` §2.1, §4.2 ; `plans/05-BACKEND-CLIAPI-PLAN.md` §7 |
| D7, D34, D38 | `plans/00` §4.3, §2.5 ; `plans/05` §4, §7, §9 |
| D24–D26, D35 | `plans/00` §4.1 |
| D29–D31 | `plans/02` §5.1, §5.4, §6.3 |

---

*Đây là tài liệu **chốt quyết định**. Mọi thay đổi quyết định phải thêm `D<n>` mới thay vì sửa đè lịch sử.*