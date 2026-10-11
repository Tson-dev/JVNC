lệnh Mine / Detail / Stream (đã chốt theo `D47` — bản nháp mới là chuẩn)

cấu trúc (Hybrid): `> <command> <dataset> <partial> <factor> <limit> --minOcc <x> [flags]`
ví dụ: `> mine "địa chỉ dataset local" 0.15 0.9 100 --minOcc 0.000001 --export "địa chỉ xuất"`

trong đó:

`<command>` là một trong các lệnh khai phá (mỗi mode là MỘT LỆNH riêng, không còn là positional `<mode>`):
- `mine`: mining bình thường, kết quả xuất ra gồm thông tin quan trọng **và danh sách DOPs**.
- `detail`: xuất chi tiết hơn bình thường (thêm thông tin cửa sổ/window + stats).
- `stream`: mỗi khi có một pattern đạt thì xuất kết quả ngay (cập nhật liên tục, real-time).
- `stream --step`: xuất nội dung tương ứng với **mỗi bước** trong thuật toán (thay cho lệnh `step` riêng).
- (chế độ `log` — ghi gần như toàn bộ, tốn tài nguyên nhất — **để sau**.)

`<dataset>`: một chuỗi string chỉ về một dataset tồn tại trên máy hiện tại.
`<partial>`: input thứ nhất của thuật toán (∂).
`<factor>`: ký hiệu là f, là input thứ hai.
`<limit>`: giới hạn số dòng đọc dataset (`0` = đọc hết).

flags:
- `--minOcc <x>`: **bắt buộc** ở mọi lệnh mining (D28/D41); thiếu ⇒ lỗi `MISSING_PARAMETER`.
- `--detail 0|1|2`: chỉ dùng cho `stream --step`:
  - `2` = chỉ khi pattern **đạt DOP**;
  - `1` = thêm **UBDO/DO khi điều kiện đúng**;
  - `0` = thêm **cả điều kiện sai** (log gần như toàn bộ).
- `--workers <n>` · `--format fimi|text|zip` · `--parts <n>`: tuỳ chọn.
- `--export [<exportLocation>]`: ghi đè cơ chế lưu (xem dưới).

Lưu kết quả (`D48`) — thay cho `--y`/`<export?>` cũ:
- **`isLog`** (config, xem `Config.md`) là **mặc định**: nếu bật ⇒ luôn ghi file kết quả.
- **`--export`** được dùng để **ghi đè**: có đường dẫn ⇒ ghi vào đó, không có ⇒ dùng thư mục mặc định.
  - nếu `<exportLocation>` không tồn tại → tạo file mặc định (phụ thuộc config);
  - nếu là folder (không có file cụ thể) → tự tạo file mới để lưu;
  - nếu cần cấp quyền (ví dụ cần admin để lưu tại `C:\Program Files`) → bỏ địa chỉ và dùng default;
  - nếu trống → dùng địa chỉ mặc định.
- Tên file: `mine_<yy-MM-dd>_<NNN>.txt` trong thư mục mặc định `/mine/` (ví dụ `mine_26-10-11_001.txt`);
  `<NNN>` là số 3 chữ số **tự tăng và reset về `001` mỗi ngày**.
- BE cấp `fileID = <yy-MM-dd>_<NNN>` trong response; FE tải lại bằng lệnh `fetch <fileID>` và **phải lưu bản sao cục bộ** khi `isLog`.

ràng buộc: `<exportLocation>` chỉ có nghĩa khi đi kèm `--export`; nếu không có `--export` mà vẫn truyền `<exportLocation>` ⇒ trả về lỗi (giữ tinh thần bản cũ).

> Ghi chú: bản gốc dùng `<mode>` như positional và cờ `--y`; theo `D35` (bản nháp mới là chuẩn) đã chuyển sang **mode = lệnh riêng** + **`--minOcc` bắt buộc** + **`--export`/`isLog`**. Đặc tả đầy đủ: `docs/plans/08-FE-CONTRACT.md` §6–§8.
