### Tài liệu mô tả hệ thống (bản draft)

``` text
lưu ý:
- Tài liệu này là viết tay, rất thô và chỉ nói trọng tâm vấn đề, chưa giải quyết những quan hệ xung quanh
- nhưng mã trong này không phải là định danh mà là đánh dấu để truy vấn lại dễ dàng hơn.
- Tài liệu này chỉ phục vụ cho kham khảo, nhằm để ai khác đọc có thể nắm bắt nhanh những quy luật, flow và rằng buộc có trong project.
```

## Chương 1. Mô tả

# 0. Mapping

- Phần này là phụ lục nói về ký hiệu, khái niệm khái quát.

|Tên|Mô tả|
|---|---|
|item|là đơn vị nhỏ, tối giản xuất hiện trong giao dịch|
|Transaction|là một tập hơp các item|

# 1. Item

|Mã|Mô tả|
|---|---|
|DES_ITEM_001|Item là một đơn vị tối giản xuất hiện trong Transaction|
|DES_ITEM_002|Bản thân item là một pattern chứa 1 prefix.|
|DES_ITEM_003|Item có tên định danh duy nhất|

Cấu trúc: chỉ một chuỗi string duy nhất

Ví dụ: "A", "B", "C" hoặc A, B, C cho paper

# 2. Transaction

|Mã|Mô tả|
|---|---|
|DES_TRAN_001|Transaction là tập hơp chứa các item|
|DES_TRAN_002|Transaction không liên quan đến các khai niệm như pattern, ...|
|DES_TRAN_003|Transaction nên là đơn giản như array, list hoặc string|

Cấu trức: có thể là list, array hoặc string

ví dụ: [A, B, C] hoặc {A, B, C} hoăc "A B C", "A|B|C","A,B,C",...

# 3. Pattern

|Mã|Mô tả|
|---|---|
|DES_PAT_001|Pattern là một tập hợp chứa các prefix|
|DES_PAT_002|Prefix là 