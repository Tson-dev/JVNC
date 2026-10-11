lệnh config

cụ thể là configuration interface, một giao diện nhỏ trong CLI chuyên về cài đặt config.

các lệnh gồm:
- `get <key>`
- `set <key> <value>`
- `list`
- `cancel` / `exit`

3 cấp giá trị (độ ưu tiên tăng dần — `D49`):
```
default (built-in)  <  file JSON (thư mục cấu hình người dùng)  <  CLI args
```
- file JSON: `%APPDATA%\dhopm\config.json` (Windows) / `~/.config/dhopm/config.json` (macOS/Linux);
- CLI args luôn thắng file; file luôn thắng default.

các key khởi điểm:
- `isLog` (bool, mặc định `false`): có ghi file kết quả mining hay không (`D48`).
- `export.dir` (string, mặc định `/mine`): thư mục ghi file kết quả mặc định.
- `workers` (int, mặc định = số CPU): số worker.
- `detail` (int, mặc định `1`): mức `--detail` mặc định cho `stream --step`.

> Đặc tả đầy đủ (lệnh, ví dụ, mã lỗi `CONFIG_KEY_UNKNOWN`/`CONFIG_INVALID_VALUE`): `docs/plans/08-FE-CONTRACT.md` §9.
