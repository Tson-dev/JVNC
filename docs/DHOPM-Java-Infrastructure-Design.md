# DHOPM/DOPM – Java Infrastructure Design (Draft)

> Tài liệu này là bản **draft thiết kế triển khai Java (OOP)** cho hệ thống khai phá DHOPM/DOPM. Nó **dịch** bản thiết kế ngôn ngữ-trung-lập trong `DHOPM-Infrastructure-OOP-Design.md` thành các **class/interface/record Java cụ thể**, kèm chữ ký phương thức, cấu trúc dữ liệu, và mã khung (skeleton) cho các thủ tục lõi.
>
> **Cảnh báo draft:** Tài liệu này dựa trên `DHOPM-Domain-Specification.md` v1.0 (Draft) và `DHOPM-Infrastructure-OOP-Design.md` v0.1 (Draft) – **cả hai đều chưa kiểm chứng**. Phần Paper gốc (`1-s2_0-S095219762600792X-main.md`) và ví dụ chạy tay (`Nhom01_VDChayTay.md`) được dùng để **củng cố / đối chiếu**, không phải nguồn quyết định. Các điểm mâu thuẫn phát hiện được liệt kê ở **mục 11 – cần bạn review trước khi chốt**.

---

## Document Header

| Mục | Giá trị |
|---|---|
| **Document Name** | DHOPM / DOPM – Java Infrastructure Design |
| **Document ID** | DHOPM-JAVA-001 |
| **Version** | 0.1 (Draft – chờ review & tinh chỉnh) |
| **Status** | Draft – chờ đối chiếu với 2 tài liệu nguồn |
| **Source Documents** | [1] `DHOPM-Domain-Specification.md` (đặc tả miền – draft)<br/>[2] `DHOPM-Infrastructure-OOP-Design.md` (thiết kế OOP trung lập – draft)<br/>[3] `1-s2_0-S095219762600792X-main.md` (paper gốc – raw text, dùng đối chiếu)<br/>[4] `Nhom01_VDChayTay.md` (ví dụ chạy tay – raw text, dùng đối chiếu) |

### Revision History

| Phiên bản | Mô tả |
|---|---|
| 0.1 | Draft đầu tiên – dịch thiết kế OOP trung lập sang Java, đề xuất cấu trúc package & chữ ký lớp, kèm các quyết định/vấn đề mở. |

---

# 1. Scope & Mục tiêu

## 1.1 Mục tiêu

- Cung cấp **bản thiết kế Java cụ thể** (class/interface/record, package, chữ ký phương thức) để cài đặt thuật toán DHOPM theo hướng đối tượng, truy vết được về [1] và [2].
- Đưa ra **mã khung cho 4 thủ tục lõi** (Reconstruction, Build/Intersection, DUBO, Mine) – đây là phần dễ sai nhất, cần được review sớm.
- Nêu rõ **quyết định triển khai** và **vấn đề mở** cần bạn xác nhận trước khi code thật.

## 1.2 Phạm vi quyết định của tài liệu này

**Quyết định:**

- Ngôn ngữ nền tảng: **Java 17 LTS** (dùng `record`, `sealed` không bắt buộc, `Optional`).
- Package structure + tên class + chữ ký phương thức.
- Cấu trúc dữ liệu nội bộ (ArrayList/LinkedHashMap/…).
- Chính sách so sánh số thực (`double` + epsilon).
- Cách tổ chức luồng `loadBatch` / `mineNow`.

**Chưa quyết định (để dành cho bạn / tài liệu sau):**

- Framework: DI (Spring/Kodein/…), logging (SLF4J), config, ORM – tài liệu này **giữ vanilla JDK**, chỉ nêu chỗ chèn về sau (xem mục 12).
- Build tool (Maven/Gradle), module JPMS.
- Vòng đời nguồn dữ liệu chi tiết (file format chuẩn) – chỉ nêu format giả định.
- Tối ưu hoá hiệu năng / bộ nhớ mức tinh chỉnh.

## 1.3 Nguyên tắc thiết kế (kế thừa từ [2])

- **P1/OOP:** Mỗi thành phần trung lập ở [2] → đúng 1 class Java; trách nhiệm minh bạch.
- **P2 (one-scan):** `TransactionSource` chỉ trả mỗi giao dịch đúng một lần; `DHOListBuilder` chỉ ghi append.
- **P3 (immutability):** `Item`, `Occurrence`, `Transaction`, `ResultPattern`, `AlgorithmConfig` là `record` bất biến.
- **P4 (tách lớp):** `io` (đọc) ≠ `model` (state) ≠ `algo` (nghiệp vụ) ≠ `engine` (điều phối) ≠ `math` (hàm thuần).
- **P5 (một chiều phụ thuộc):** `algo` phụ thuộc `model`+`math`; `engine` phụ thuộc tất cả; không chiều ngược. (Áp dụng mềm – draft.)

---

# 2. Quyết định nền tảng

| Hạng mục | Quyết định (draft) | Ghi chú |
|---|---|---|
| Ngôn ngữ / LTS | Java 17 | `record`, `Optional`, `List.copyOf`, pattern matching không bắt buộc |
| Kiểu số | `double` cho mọi đại lượng DO/DUBO/occupancy/decay | `Math.pow`; so sánh dùng epsilon |
| So sánh ngưỡng | `Numeric.ge(value, threshold)` mặc định ε = 1e-9 | Lý do: giá trị trong [3]/[4] làm tròn 4 chữ số; không nên so sánh `>=` thô |
| Bất biến | Value object dùng `record` | Transaction/Occurrence/Item/ResultPattern/AlgorithmConfig |
| Struct + index | `ArrayList<DHONode>` (thứ tự) + `LinkedHashMap<Item,DHONode>` (tra cứu) | `createOrGetNode` O(1); sort stable bảo toàn thứ tự tạo cho support bằng nhau |
| TID / độ dài | `int` (TID), `int` (transaction length) | Stream thực tế có thể vượt int → ghi chú ở mục 12 |
| Phụ thuộc ngoài | Không (vanilla JDK) | Logging/DI/DB bàn ở tài liệu sau |
| Test | JUnit 5 (đề xuất, tuỳ chọn) | Đối chiếu TC1–TC8 của [4] |

---

# 3. Cấu trúc Package (đề xuất – placeholder root `dhopm`)

```
dhopm
├── model        Item, Occurrence, Transaction, DHONode, DHOList,
│                ConditionalDHOList, ResultPattern, AlgorithmConfig, StreamStatus
├── io           TransactionSource, ListTransactionSource, TextTransactionSource
├── math         MetricCalculator, DUBOCalculator, Numeric, LengthGroup
├── algo         DHOListBuilder, Reconstructor, ConditionalListBuilder, Miner
└── engine       MiningEngine
```

> Root package `dhopm` là placeholder – báo danh sách dự án thật (vd `vn.edu.xxx.dhopm`) sẽ đổi sau.

```
engine.MiningEngine
   │ loadBatch(...)          algo.DHOListBuilder.scanAndAppend → model.DHOList
   │ mineNow()               algo.Reconstructor.reconstruct → sort(model.DHOList)
   │                          → algo.Miner.mine → math.DUBOCalculator + algo.ConditionalListBuilder
   │                          → Set<model.ResultPattern>
   │ đọc giao dịch           io.TransactionSource → model.Transaction
```

---

# 4. Bảng Mapping: thành phần trung lập [2] → Java

| Thành phần [2] | Java | Ghi chú |
|---|---|---|
| `Parameter` | `AlgorithmConfig` (record, giữ `∂`, `f`) + `StreamStatus` (mutable: counters) | **Lệch khỏi [2]** – xem Q3 mục 11 |
| `Transaction` | `record Transaction(int tid, List<Item> items)` | `length()` suy từ items phân biệt |
| `Occurrence` | `record Occurrence(int tid, int transactionLength)` | cặp `<TID, TLen>` |
| `DHONode` | `final class DHONode` | doValue mutable; occurrences append-only |
| `DHOList` | `class DHOList implements Iterable<DHONode>` | `ConditionalDHOList extends DHOList` (marker) |
| `ConditionalDHOList` | `ConditionalDHOList extends DHOList` | không thêm hành vi (xem Q8) |
| `TransactionSource` | `interface TransactionSource` | thêm `ListTransactionSource`/`TextTransactionSource` |
| `DHOListBuilder` | `final class DHOListBuilder` | `scanAndAppend(...)` → `BatchScanStats` |
| `Reconstructor` | `final class Reconstructor` | tính DO + sort support tăng dần |
| `MetricCalculator` | `final class MetricCalculator` (stateless) | F1–F6 |
| `DUBOCalculator` | `final class DUBOCalculator` | F9; dùng `LengthGroup` |
| `ConditionalListBuilder` | `final class ConditionalListBuilder` | two-pointer intersection |
| `Miner` | `final class Miner` | DFS pattern-growth |
| `ResultPattern` | `record ResultPattern(...)` | items + doValue + occurrences |
| `MiningEngine` | `final class MiningEngine` | orchestration |

---

# 5. Thiết kế chi tiết từng lớp (Java)

> Quy ước: chỉ ra package; `//` trong code là ghi chú bổ sung. Không bàn logging/DI (để tài liệu sau).

---

## 5.1 `dhopm.model.Item`

```java
package dhopm.model;

/** Một item – "đơn vị" tối giản trong giao dịch (mục 2.1 [1]). */
public record Item(String name) {
    public Item {
        if (name == null) throw new NullPointerException("item name");
        if (name.isBlank()) throw new IllegalArgumentException("item name must not be blank");
        name = name.trim();               // canonical form
    }
    @Override public String toString() { return name; }
}
```

- Trách nhiệm: định danh duy nhất trong phạm vi dữ liệu. (Q7: có thể thay bằng `String` nếu muốn đơn giản.)

---

## 5.2 `dhopm.model.Occurrence`

```java
package dhopm.model;

/** Một lần xuất hiện của item/mẫu trong một giao dịch: (TID, |T|). Mục 3.3 [2], DR-06/08. */
public record Occurrence(int tid, int transactionLength) {
    public Occurrence {
        if (tid < 0) throw new IllegalArgumentException("tid must be >= 0");
        if (transactionLength <= 0) throw new IllegalArgumentException("transactionLength must be > 0");
    }
}
```

- Bất biến: `transactionLength > 0` (chống chia 0 ở occupancy). Giao dịch rỗng bị loại/từ chối ở tầng nguồn.

---

## 5.3 `dhopm.model.Transaction`

```java
package dhopm.model;

/** Một giao dịch: TID + tập item phân biệt. Mục 3.2 [2], DR-01..03. */
public record Transaction(int tid, List<Item> items) {
    public Transaction {
        if (tid < 0) throw new IllegalArgumentException("tid must be >= 0");
        LinkedHashSet<Item> distinct = new LinkedHashSet<>(items == null ? List.of() : items);
        if (distinct.isEmpty())
            throw new IllegalArgumentException("transaction must contain at least one item (len=0 -> divide-by-zero)");
        items = List.copyOf(distinct);      // normalise: distinct, giữ thứ tự xuất hiện đầu tiên (R3 mục 5.1 [1])
    }

    /** Số item phân biệt = |T|. */
    public int length() { return items.size(); }

    /** Giao dịch có chứa item i? (DR-01 mức transaction). */
    public boolean contains(Item item) { return items.contains(item); }
}
```

- **Chính sách giao dịch rỗng:** từ chối tại constructor (xem Q6 – có thể đổi thành bỏ qua ở `TextTransactionSource`).

---

## 5.4 `dhopm.model.AlgorithmConfig` + `dhopm.model.StreamStatus`

> **Lệch khỏi [2]:** [2] gộp `∂, f, totalTransactionCount, latestTid` trong `Parameter` (được mô tả là immutable – mâu thuẫn vì counters thay đổi). Draft này **tách**:
> - `AlgorithmConfig` (immutable): chỉ `∂` và `f`.
> - `StreamStatus` (mutable): tổng số giao dịch, `latestTid`, `minSup` cache (nếu muốn).

```java
package dhopm.model;

/** Cấu hình người dùng (không đổi trong phiên) – mục 4.4 [1], DR-14. */
public record AlgorithmConfig(double delta, double f) {
    public AlgorithmConfig {
        if (delta < 0.0 || delta > 1.0) throw new IllegalArgumentException("delta must be in [0,1]");
        if (f <= 0.0 || f >= 1.0)        throw new IllegalArgumentException("f must be in (0,1)");
    }

    /** F8: minSup = ∂ × |DB|. */
    public double computeMinSup(int totalTransactionCount) {
        return delta * totalTransactionCount;
    }
}
```

```java
package dhopm.model;

/** Trạng thái tiến trình của stream – DR-04, DR-05. Mutable, thuộc sở hữu của engine. */
public final class StreamStatus {
    private int totalTransactionCount;
    private int latestTid = -1;          // -1 = chưa có giao dịch nào

    public void update(int scannedCount, int lastTid) {
        if (lastTid <= latestTid)
            throw new IllegalStateException("TID must be strictly increasing across batches: got " + lastTid + " after " + latestTid);
        this.totalTransactionCount += scannedCount;
        this.latestTid = lastTid;
    }

    public int totalTransactionCount() { return totalTransactionCount; }
    public int latestTid()              { return latestTid; }
    public boolean hasData()            { return latestTid >= 0; }
}
```

---

## 5.5 `dhopm.model.DHONode`

```java
package dhopm.model;

import java.util.*;

/** Hồ sơ một item/mẫu trong DHO-List. Mục 3.4 [2], DR-06/07/12. */
public final class DHONode {
    private final Item item;
    private final List<Occurrence> occurrences = new ArrayList<>();   // append-only, TID tăng dần
    private double doValue;                                           // chỉ có nghĩa tại đúng TL/f đã tính (INV-4 [2])

    public DHONode(Item item) { this.item = Objects.requireNonNull(item); }

    public Item item()                       { return item; }
    public List<Occurrence> occurrences()    { return Collections.unmodifiableList(occurrences); }
    public Occurrence occurrenceAt(int i)    { return occurrences.get(i); }
    public int size()                        { return occurrences.size(); }
    public boolean isEmpty()                 { return occurrences.isEmpty(); }

    /** Support = số lần xuất hiện đã ghi (tương đương cột support, DR-07). */
    public int support()                     { return occurrences.size(); }

    public double doValue()                  { return doValue; }
    void resetDO()                           { doValue = 0.0; }        // R4 mục 5.5 [1]
    void accumulateDO(double contribution)   { doValue += contribution; }

    /** Bổ sung đúng 1 lần xuất hiện; người gọi phải đảm bảo TID tăng dần & item phân biệt. */
    void addOccurrence(Occurrence occ)       { occurrences.add(occ); }

    /** R2 mục 5.9 [1]: Sup(X) ≥ minSup? */
    public boolean supports(double minSup)   { return support() >= minSup; }
}
```

- **Lệch nhẹ so với [2]:** `accumulateDO(Occurrence, dF)` → `accumulateDO(double contribution)`; phần `(1/len)×dF` do caller tính (nằm ở Reconstructor/builder) – node giữ "ngu" như [2] yêu cầu.
- `addOccurrence/resetDO/accumulateDO` package-private: chỉ `algo` gọi, tránh thay đổi tuỳ tiện ngoài package.

---

## 5.6 `dhopm.model.DHOList` + `ConditionalDHOList`

```java
package dhopm.model;

import java.util.*;

/** Danh sách toàn cục các node – DR-04..13 mức list. Mục 3.5 [2]. */
public class DHOList implements Iterable<DHONode> {
    private final LinkedHashMap<Item, DHONode> index = new LinkedHashMap<>();   // tra cứu / thu hồi thứ tự tạo
    private final List<DHONode> order = new ArrayList<>();                      // dùng để sort & duyệt

    public Optional<DHONode> findNode(Item item) { return Optional.ofNullable(index.get(item)); }

    /** R2/R3 mục 5.2 [1]: có thì trả về, chưa có thì tạo node mới (doValue=0, rỗng) và thêm vào list. */
    public DHONode createOrGetNode(Item item) {
        var existing = index.get(item);
        if (existing != null) return existing;
        DHONode node = new DHONode(item);
        index.put(item, node);
        order.add(node);
        return node;
    }

    /** Thêm node có sẵn (conditional list builder dùng). Thời điểm insert = thứ tự kết hợp. */
    public void add(DHONode node) { order.add(node); }

    /** DR-13 – sort support tăng dần; stable -> support bằng nhau giữ thứ tự tạo (xem Q4). */
    public void sortBySupportAscending() {
        order.sort(Comparator.comparingInt(DHONode::support));
    }

    public int size()            { return order.size(); }
    public boolean isEmpty()     { return order.isEmpty(); }
    public DHONode nodeAt(int i) { return order.get(i); }
    public List<DHONode> asList(){ return List.copyOf(order); }

    @Override public Iterator<DHONode> iterator() { return Collections.unmodifiableList(order).iterator(); }
}
```

```java
package dhopm.model;

/** DHO-List con của một nhánh khai phá (mẫu cùng tiền tố). Mục 3.6 [2], DR-11.
 *  KHÔNG được sort lại sau khi dựng (giữ thứ tự kết hợp theo list cha). */
public final class ConditionalDHOList extends DHOList {}
```

- Quan trọng: **conditional list không sort** (đúng paper [3] – chỉ list toàn cục sort ở Reconstruction).
- Ghi chú hiệu năng (chưa áp dụng): có thể tách `index` riêng để conditional list nhẹ hơn (chỉ cần `order`).

---

## 5.7 `dhopm.io.TransactionSource`

```java
package dhopm.io;

import dhopm.model.Transaction;
import java.io.*;
import java.util.Optional;

/** Đọc transaction tuần tự, mỗi giao dịch đúng một lần (P1 [2], DR-02/03). TID phải tăng dần. */
public interface TransactionSource extends AutoCloseable {
    /** Trả giao dịch kế tiếp theo TID tăng dần; rỗng khi hết nguồn. */
    Optional<Transaction> readNext() throws IOException;

    @Override default void close() throws IOException {}
}
```

- Khác [2]: bỏ phương thức `open(...)` tách rời – việc "mở" gói gọn trong nhà máy (factory) của từng impl; `readNext` dùng `Optional` thay vì nullable (Java-idiomatic).

```java
package dhopm.io;

/** Source trong bộ nhớ – phục vụ test/TC1..TC8 của [4]. */
public final class ListTransactionSource implements TransactionSource {
    private final Iterator<Transaction> it;
    private int lastTid = Integer.MIN_VALUE;

    public ListTransactionSource(List<Transaction> transactions) { this.it = transactions.iterator(); }

    @Override public Optional<Transaction> readNext() throws IOException {
        if (!it.hasNext()) return Optional.empty();
        Transaction t = it.next();
        if (t.tid() <= lastTid)
            throw new IllegalStateException("TID must be strictly increasing: got " + t.tid() + " after " + lastTid);
        lastTid = t.tid();
        return Optional.of(t);
    }
}
```

```java
package dhopm.io;

import dhopm.model.Item;
import dhopm.model.Transaction;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Format giả định (cần bạn chốt – xem mục 12):
 *  mỗi dòng:  <TID>  <item1> <item2> ...    ví dụ:  1  A  C  D  E
 *  (item phân tách bằng khoảng trắng/tab; dòng trống bỏ qua; dòng bắt đầu bằng '#': comment). */
public final class TextTransactionSource implements TransactionSource {
    private final BufferedReader reader;
    private int lastTid = Integer.MIN_VALUE;

    public TextTransactionSource(Path path) throws IOException {
        this.reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
    }

    @Override public Optional<Transaction> readNext() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("[\\s,]+");
            int tid = Integer.parseInt(parts[0]);
            if (tid <= lastTid)
                throw new IllegalStateException("TID must be strictly increasing: got " + tid + " after " + lastTid);
            lastTid = tid;
            List<Item> items = new ArrayList<>(parts.length - 1);
            for (int i = 1; i < parts.length; i++) items.add(new Item(parts[i]));
            return Optional.of(new Transaction(tid, items));   // Transaction tự normalize items phân biệt
        }
        return Optional.empty();
    }

    @Override public void close() throws IOException { reader.close(); }
}
```

---

## 5.8 `dhopm.algo.DHOListBuilder`

```java
package dhopm.algo;

import dhopm.model.*;
import java.util.*;

/** Giai đoạn Xây dựng/Cập nhật global list (capability 5.1–5.3 [1], mục 3.8 [2]).
 *  Không biết TL/minSup – chỉ ghi nhận dữ liệu thô. */
public final class DHOListBuilder {

    /** Kết quả một lần quét batch. */
    public record BatchScanStats(int scannedCount, int lastTid) {}

    /**
     * Quét transactions, tạo/update node, append occurrence, tăng support (implied).
     * Không đụng dữ liệu cũ (P3 [2]).
     */
    public BatchScanStats scanAndAppend(DHOList list, Iterable<Transaction> transactions) {
        int count = 0;
        int lastTid = Integer.MIN_VALUE;
        for (Transaction t : transactions) {
            if (t.tid() <= lastTid)
                throw new IllegalStateException("TID must be strictly increasing within batch: " + t.tid() + " after " + lastTid);
            for (Item item : t.items()) {
                DHONode node = list.createOrGetNode(item);
                node.addOccurrence(new Occurrence(t.tid(), t.length()));
            }
            lastTid = t.tid();
            count++;
        }
        return new BatchScanStats(count, lastTid);
    }
}
```

- Kiểm tra TID tăng dần **trong batch** (tăng dần toàn bộ stream do `StreamStatus.update` đảm nhận).

---

## 5.9 `dhopm.algo.Reconstructor`

```java
package dhopm.algo;

import dhopm.model.*;
import dhopm.math.MetricCalculator;

/** Giai đoạn Tái cấu trúc: tính lại DO (TL, f) + sort support tăng dần. Capability 5.5 [1], mục 3.9 [2]. */
public final class Reconstructor {

    public void reconstruct(DHOList list, int tl, double f) {
        for (DHONode node : list) {
            node.resetDO();                                   // R4 mục 5.5 [1]: tính lại từ 0
            for (Occurrence occ : node.occurrences()) {
                double dF = MetricCalculator.decayFactor(f, tl, occ.tid());
                node.accumulateDO(MetricCalculator.dampedOccupancy(1, occ.transactionLength(), dF));
            }
        }
        list.sortBySupportAscending();                        // DR-13; quyết định thứ tự duyệt của Miner
    }
}
```

- **Không được:** sort/gán giá trị cũ cho DO – phải reset từ 0 mỗi lần `TL` đổi (INV-4 [2]).

---

## 5.10 `dhopm.math.MetricCalculator`

```java
package dhopm.math;

/** Các phép đo thuần tuý (pure) – F1..F6 [1], mục 3.10 [2]. Stateless, không lưu trạng thái. */
public final class MetricCalculator {
    public static final double DEFAULT_EPSILON = 1e-9;

    /** F4: dF(Td) = f^(TL - Td). */
    public static double decayFactor(double f, int tl, int tid) {
        return Math.pow(f, (double) (tl - tid));
    }

    /** F1: O(X, Td) = |X| / |Td|. */
    public static double occupancy(int patternLength, int transactionLength) {
        return (double) patternLength / transactionLength;
    }

    /** F5: DO(X, Td) = O(X, Td) × dF. */
    public static double dampedOccupancy(int patternLength, int transactionLength, double dF) {
        return occupancy(patternLength, transactionLength) * dF;
    }

    private MetricCalculator() {}
}
```

---

## 5.11 `dhopm.math.DUBOCalculator` (+ `LengthGroup`)

```java
package dhopm.math;

/** Nhóm giao dịch cùng độ dài: length, count(nk), lastTid(Tk) – DR-09/DR-10, F9 [1]. */
public record LengthGroup(int length, int count, int lastTid) {}
```

```java
package dhopm.math;

import dhopm.model.*;
import java.util.*;

/** Tính cận trên DUBO(X) – F9 [1], mục 3.11 [2], Sub-procedure 3 [3].
 *
 *  DUBO(X, k) = Σ_{i=k..u} ni × (lk/li) × f^(TL - Tk);   DUBO(X) = max_k DUBO(X, k)
 *
 *  LƯU Ý: hệ số f^(TL - Tk) dùng Tk của NHÓM k (đồng nhất cho cả tổng) – theo Definition 6
 *  và pseudocode của [3] / [4]. (Xem Q1 mục 11.) */
public final class DUBOCalculator {

    private static final class MutableGroup {
        int count;
        int lastTid = Integer.MIN_VALUE;
    }

    public double compute(DHONode node, int tl, double f) {
        if (node.isEmpty()) return 0.0;

        // 1) Gom theo transactionLength (DR-09): group(length, count, lastTid) (DR-10)
        TreeMap<Integer, MutableGroup> byLen = new TreeMap<>();                     // key = length, tự sắp tăng
        for (Occurrence occ : node.occurrences()) {
            MutableGroup g = byLen.computeIfAbsent(occ.transactionLength(), k -> new MutableGroup());
            g.count++;
            if (occ.tid() > g.lastTid) g.lastTid = occ.tid();
        }

        // 2) L = {l1..lu} sắp tăng
        List<LengthGroup> groups = new ArrayList<>(byLen.size());
        byLen.forEach((len, g) -> groups.add(new LengthGroup(len, g.count, g.lastTid)));

        // 3) DUBO = max_k [ f^(TL - Tk) × Σ_{i>=k} ni × (lk/li) ]
        double dubo = 0.0;
        for (int k = 0; k < groups.size(); k++) {
            LengthGroup gk = groups.get(k);
            double dF = MetricCalculator.decayFactor(f, tl, gk.lastTid());
            double sum = 0.0;
            for (int i = k; i < groups.size(); i++) {
                LengthGroup gi = groups.get(i);
                sum += (double) gi.count() * ((double) gk.length() / gi.length());
            }
            dubo = Math.max(dubo, sum * dF);
        }
        return dubo;
    }
}
```

**Kiểm chứng cụ thể (đối chiếu [3]/[4]):**

| Pattern | DUBO | Tính tay |
|---|---|---|
| E (TL=8) | 4.5 | g(3,3,T8), g(4,2,T3): k=1 → [3×1 + 2×3/4]×0.9^0 = 4.5 |
| B | 1.8 | g(2,1,T5), g(4,2,T7): k=2 → 2×(4/4)×0.9^1 = 1.8 |
| CD | 1.6402 | g(3,1,T4), g(4,2,T3): k=1 → [1 + 2×3/4]×0.9^4 = 1.640 | *(xem Q1 – paper [3] prose ghi 1.542, coi là sai)* |
| CDE | 1.181 | g(4,2,T3): 2×1×0.9^5 = 1.181 |
| AF | 0.93 | g(3,1,T2), g(4,1,T7): k=1 → [1 + 3/4]×0.9^6 = 0.930 |
| F | 3.0375 | g(2,1,T5), g(3,3,T6), g(4,1,T7): k=2 → [3×1 + 1×3/4]×0.9^2 = 3.0375 |

---

## 5.12 `dhopm.algo.ConditionalListBuilder`

```java
package dhopm.algo;

import dhopm.model.*;
import dhopm.math.MetricCalculator;

/** Xây mẫu mở rộng + ConditionalDHOList mức kế tiếp (capability 5.6 [1], mục 3.12 [2]).
 *  Giao 2 tập occurrence bằng two-pointer (entries đã sắp TID tăng dần). */
public final class ConditionalListBuilder {

    /**
     * Kết hợp node left (mẫu = prefix ∪ {right?=left.item}) với node right (right.item)
     * → mẫu mới = prefix ∪ {left.item} ∪ {right.item}, độ dài = prefixLength + 2.
     *
     * @param prefixLength = |prefix| của lời gọi Mine đang chạy (paper: |pref| trong Mine line 20).
     * @return node mới (item = right.item) đã được thêm vào nextLevelList; null nếu không có TID chung.
     */
    public DHONode build(ConditionalDHOList nextLevelList, DHONode left, DHONode right,
                         int prefixLength, int tl, double f) {
        int patternLength = prefixLength + 2;          // = |X ∪ {j}|
        DHONode merged = new DHONode(right.item());

        int i = 0, j = 0;
        while (i < left.size() && j < right.size()) {
            Occurrence li = left.occurrenceAt(i);
            Occurrence rj = right.occurrenceAt(j);
            if (li.tid() < rj.tid())      { i++; }
            else if (rj.tid() < li.tid()) { j++; }
            else {
                // TID chung -> entry mới (TLen lấy từ phía left; cùng giao dịch nên TLen như nhau)
                Occurrence common = new Occurrence(li.tid(), li.transactionLength());
                merged.addOccurrence(common);
                double dF = MetricCalculator.decayFactor(f, tl, common.tid());
                merged.accumulateDO(MetricCalculator.dampedOccupancy(patternLength, common.transactionLength(), dF));
                i++;
                j++;
            }
        }

        if (merged.isEmpty()) return null;             // không có TID chung -> không thêm node (ví dụ CDF)
        nextLevelList.add(merged);
        return merged;
    }
}
```

- Công thức `(prefixLength + 2)/TLen × dF` **khớp chính xác** pseudocode Mine line 20 của [3].
- `prefixLength` truyền vào = `prefix.size()` của lời gọi `Miner.mine` hiện tại (xem 5.13).

---

## 5.13 `dhopm.algo.Miner`

```java
package dhopm.algo;

import dhopm.model.*;
import dhopm.math.*;

/** Khai phá DFS pattern-growth (capability 5.7/5.9/5.10/5.11 [1], mục 3.13 [2], Sub-procedure 2 [3]).
 *  Cấu hình 1 lần: minSup, tl, f. */
public final class Miner {

    private final double minSup;
    private final int tl;
    private final double f;
    private final DUBOCalculator dubo = new DUBOCalculator();
    private final ConditionalListBuilder builder = new ConditionalListBuilder();

    public Miner(double minSup, int tl, double f) {
        this.minSup = minSup;
        this.tl = tl;
        this.f = f;
    }

    public Set<ResultPattern> mine(DHOList currentList, List<Item> prefix) {
        Set<ResultPattern> results = new LinkedHashSet<>();
        for (int i = 0; i < currentList.size(); i++) {
            DHONode node = currentList.nodeAt(i);

            // R2 mục 5.9 [1] / Mine line 03 [3]: Sup < minSup -> BỎ QUA cả node (không đánh giá, không mở rộng).
            // (Xem Q2 mục 11 – có cách đọc khác cho rằng chỉ chặn mở rộng.)
            if (!node.supports(minSup)) continue;

            // Đánh giá DOP: DO(X) >= minSup (R1 mục 5.7 [1]).
            if (Numeric.ge(node.doValue(), minSup)) {
                results.add(ResultPattern.of(prefix, node));
            }

            // Cận trên DUBO: >= minSup thì mở rộng; ngược lại cắt nhánh (R1 mục 5.9 [1]).
            double duboValue = dubo.compute(node, tl, f);
            if (!Numeric.ge(duboValue, minSup)) continue;

            // Xây conditional list cho mức kế tiếp: kết hợp node i với node j đứng sau i.
            ConditionalDHOList nextLevel = new ConditionalDHOList();
            for (int j = i + 1; j < currentList.size(); j++) {
                builder.build(nextLevel, node, currentList.nodeAt(j), prefix.size(), tl, f);
                // build trả null khi không có TID chung -> node không được thêm
            }
            if (!nextLevel.isEmpty()) {
                List<Item> newPrefix = new ArrayList<>(prefix);
                newPrefix.add(node.item());                        // X = prefix ∪ {i}
                results.addAll(mine(nextLevel, newPrefix));        // đệ quy với tiền tố mới
            }
        }
        return results;
    }
}
```

- **Invariant:** mỗi mẫu xuất hiện đúng một lần (chỉ kết hợp với node **đứng sau** – không lặp); cắt DUBO không làm mất mẫu hợp lệ (Lemma 2 [3]).
- **Thứ tự kết quả:** phụ thuộc thứ tự duyệt → cần thống nhất tie-break (Q4).

---

## 5.14 `dhopm.model.ResultPattern`

```java
package dhopm.model;

import java.util.*;

/** Một kết quả DOP. Bất biến. Mục 3.14 [2]. */
public record ResultPattern(List<Item> items, double doValue, List<Occurrence> occurrences) {

    public ResultPattern {
        items = List.copyOf(items);
        occurrences = List.copyOf(occurrences);
    }

    public static ResultPattern of(List<Item> prefix, DHONode node) {
        List<Item> items = new ArrayList<>(prefix);
        items.add(node.item());
        return new ResultPattern(items, node.doValue(), node.occurrences());
    }

    /** Chuỗi hiển thị, vd "AE" / "A,E". */
    public String render() {
        StringBuilder sb = new StringBuilder();
        for (Item i : items) sb.append(i.name());
        return sb.toString();
    }

    @Override public String toString() {
        return render() + "(DO=" + Numeric.round4(doValue) + ")";
    }
}
```

- `occurrences` lưu các giao dịch chứa mẫu – phục vụ báo cáo và đối chiếu test của [4] (cột "Transactions").

---

## 5.15 `dhopm.math.Numeric`

```java
package dhopm.math;

/** Tiện ích so sánh/định dạng số thực – tránh sai sót so sánh trực tiếp >= . */
public final class Numeric {
    public static final double DEFAULT_EPSILON = 1e-9;

    public static boolean ge(double value, double threshold)         { return ge(value, threshold, DEFAULT_EPSILON); }
    public static boolean ge(double value, double threshold, double eps) {
        return value >= threshold - eps;
    }

    /** Làm tròn 4 chữ số – CHỈ cho hiển thị/kiểm thử, không dùng trong tính toán. */
    public static double round4(double v) { return Math.round(v * 1e4) / 1e4; }

    private Numeric() {}
}
```

---

## 5.16 `dhopm.engine.MiningEngine`

```java
package dhopm.engine;

import dhopm.algo.*;
import dhopm.io.TransactionSource;
import dhopm.model.*;

/** Điều phối vòng đời: nạp DB → update → reconstruct → mine → trả DOP. Mục 3.15 [2], Main-Procedure [3]. */
public final class MiningEngine {

    private final AlgorithmConfig config;
    private final StreamStatus status = new StreamStatus();
    private final DHOList globalList = new DHOList();       // giữ xuyên suốt các đợt (P3 [2])

    private final DHOListBuilder builder = new DHOListBuilder();
    private final Reconstructor reconstructor = new Reconstructor();

    public MiningEngine(double delta, double f) {
        this.config = new AlgorithmConfig(delta, f);        // validate ∂, f ngay khi khởi tạo
    }

    public AlgorithmConfig config() { return config; }
    public DHOList globalList()     { return globalList; }

    /** Bước 1/2 workflow [1]: nạp một batch giao dịch mới (chỉ quét phần mới). */
    public void loadBatch(Iterable<Transaction> transactions) {
        if (!status.hasData() && status.latestTid() == -1) {
            // batch đầu tiên
        }
        DHOListBuilder.BatchScanStats stats = builder.scanAndAppend(globalList, transactions);
        status.update(stats.scannedCount(), stats.lastTid());
        // minSup được tính LAZY khi cần (mục 3.13 [2]: tránh trạng thái lỗi thời)
    }

    /** Nạp trực tiếp từ một TransactionSource cho đến khi hết. */
    public void loadBatch(TransactionSource source) throws Exception {
        var builder = new java.util.ArrayList<Transaction>();
        source.readNext().ifPresent(builder::add);              // placeholder – dòng này chỉ demo
        while (true) {
            var t = source.readNext();
            if (t.isEmpty()) break;
            builder.add(t.get());
        }
        loadBatch(builder);
    }

    /** F8: minSup hiện tại (tính mới mỗi lần gọi). */
    public double minSup() { return config.computeMinSup(status.totalTransactionCount()); }

    /** Bước 3 workflow [1]: reconstruct rồi mine; trả tập DOP. */
    public Set<ResultPattern> mineNow() {
        if (!status.hasData()) return Set.of();
        reconstructor.reconstruct(globalList, status.latestTid(), config.f());
        Miner miner = new Miner(minSup(), status.latestTid(), config.f());
        return miner.mine(globalList, List.of());
    }
}
```

> **Ghi chú:** phương thức `loadBatch(TransactionSource)` hiện đọc hết batch vào List rồi gọi `loadBatch(Iterable)` – với dữ liệu lớn nên đổi thành stream/lazily (ghi chú mục 12). Đây là code skeleton, chưa phải final.

---

# 6. Luồng phối hợp (Sequence cấp cao)

## 6.1 Nạp dữ liệu (batch T1..T8, ví dụ [4]/[5])

```
client                  MiningEngine              DHOListBuilder                DHOList / DHONode
  │ loadBatch(T1..T8)        │                       │                              │
  │ ────────────────────────►│ scanAndAppend(list, …)│                              │
  │                          │ ─────────────────────►│  createOrGetNode(item) ─────►│ (tạo nếu chưa có)
  │                          │                       │  addOccurrence((tid,len)) ───►│ (append)
  │                          │◄──────────────────────│ BatchScanStats(8, 8)          │
  │                          │ status.update(8, 8)   │                              │
  │                          │ (latestTid=8, total=8)│                              │
```

## 6.2 Khai phá (TL=8, f=0.9, ∂=15% → minSup=1.2)

```
client                  MiningEngine              Reconstructor        Miner
  │ mineNow()               │                        │                  │
  │ ───────────────────────►│ reconstruct(list,8,0.9)│                  │
  │                         │ ──────────────────────►│  per node:       │
  │                         │                        │   resetDO();  accumulate (1/len)·f^(8-tid)
  │                         │                        │  sortBySupportAscending()
  │                         │◄───────────────────────│  (G,B,A,C,D,E,F)
  │                         │ mine(list,∅)           │                  │
  │                         │ ──────────────────────────────────────────►│ per node (support≥1.2)
  │                         │                        │                  │  DO≥minSup → DOP
  │                         │                        │                  │  DUBO≥minSup → build conditional
  │                         │                        │                  │  mine(ncl, prefix∪{i}) đệ quy
  │◄────────────────────────│ Set<ResultPattern>{AE,F}│                  │
```

---

# 7. Bất biến & trường hợp biên (Java-specific)

| # | Bất biến | Nơi bảo đảm |
|---|---|---|
| J1 | TID tăng dần nghiêm ngặt trong batch & giữa các batch | `DHOListBuilder.scanAndAppend` + `StreamStatus.update` |
| J2 | `Occurrence.transactionLength > 0` (không chia 0) | `record Occurrence` validator |
| J3 | Item trong `Transaction` phân biệt, `length()==items.size()` | `record Transaction` normalise |
| J4 | DO chỉ có nghĩa tại đúng (TL,f) đã tính; sau `loadBatch` phải `mineNow()` (tự reconstruct) | `MiningEngine` design; Q5 mục 11 |
| J5 | Conditional list không được sort lại | `ConditionalDHOList` + chỉ `Reconstructor` gọi `sortBySupportAscending` |
| J6 | Baseline: với data T1..T8 (f=0.9, TL=8, ∂=15%) → `{AE, F}` | Test TC1 [4] |
| J7 | `DO(Y) ≤ DUBO(X)` với mọi siêu mẫu Y của X | `DUBOCalculator` (kiểm chứng bằng property-test đề xuất, mục 10) |

| Tình huống | Xử lý đề xuất |
|---|---|
| Giao dịch rỗng / item rỗng | Từ chối tại `Transaction`/`Item` (chính sách có thể đổi → Q6) |
| TID không tăng dần | Ném `IllegalStateException` với thông điệp rõ ràng |
| `∂`/`f` ngoài khoảng | Ném `IllegalArgumentException` tại `AlgorithmConfig` constructor |
| Node không có TID chung khi mở rộng | `build` trả null; không thêm node (vd CDF) |
| DUBO < minSup ngay từ đầu | `Miner` cắt, không dựng conditional list (vd G, trong TC1) |
| Không có DOP nào | `Miner` trả `Set.of()` – hợp lệ, không phải lỗi (TC2, TC4) |
| DO ≈ minSup (sai số làm tròn) | `Numeric.ge` với ε = 1e-9; hiển thị dùng `round4` |
| Item cuối trong list | Nhánh `mine` sau cùng không có j → `nextLevel` rỗng → không đệ quy |

---

# 8. Truy vết Capability → Class (bổ sung bảng [2] mục 2.3 ở mức Java)

| Domain Capability [1] | Class Java | Phương thức chính |
|---|---|---|
| Record Transaction | `io.TransactionSource`, `model.Transaction` | `readNext`, ctor |
| Record Item Occurrence | `algo.DHOListBuilder`, `model.DHONode` | `scanAndAppend`, `addOccurrence` |
| Maintain Pattern Statistics | `model.DHONode`, `model.DHOList` | `support()`, `createOrGetNode` |
| Calculate Occupancy | `math.MetricCalculator` | `occupancy` |
| Calculate Damped Occupancy | `math.MetricCalculator` + `algo.Reconstructor` | `dampedOccupancy`, `reconstruct` |
| Generate Extended Pattern | `algo.ConditionalListBuilder` | `build` |
| Evaluate Pattern | `algo.Miner` | `mine` (nhánh DO) |
| Estimate Upper Bound | `math.DUBOCalculator` | `compute` |
| Prune Search Space | `algo.Miner` + `math.DUBOCalculator` | `mine` (nhánh DUBO) |
| Traverse Pattern Space | `algo.Miner` | `mine` (DFS) |
| Discover DOPs | `algo.Miner`, `model.ResultPattern`, `engine.MiningEngine` | `mine`, `mineNow` |

---

# 9. Kiểm thử đề xuất (đối chiếu [4] Phần 11)

| TC | Input / Tham số | Kỳ vọng [4] | Lớp test chủ yếu |
|---|---|---|---|
| TC1 | DB0+DB1+DB2, f=0.9, ∂=15% | `{AE=1.2601, F=1.2553}` | `MiningEngine` end-to-end |
| TC2 | ∂=20% → minSup=1.6 | `{}` (0 DOP) | `MiningEngine` |
| TC3 | ∂=10% → minSup=0.8 | 15 DOP (bảng [4]) | `MiningEngine` |
| TC4 | f=0.8, ∂=15% | `{}` (0 DOP) | `MiningEngine` |
| TC5 | f=1.0 (HOP) | 9 HOP (bảng [4]) | `MiningEngine` |
| TC6 | Chỉ DB0, ∂=25%, minSup=1.0, TL=4 | 3 DOP (FCD/CD/CDE) | `MiningEngine` |
| TC7 | Custom 10 TID, ∂=15% | 9 DOP (bảng [4]) | `MiningEngine` |
| TC8 | Mỗi giao dịch 1 item, ∂=30%, TL=5 | 1 DOP `A=2.4661` | `MiningEngine` |
| Unit – DO | node A (TL=8,f=0.9) | 0.8551 | `Reconstructor` |
| Unit – DUBO | node E / CD / AF | 4.5 / 1.6402 / 0.93 | `DUBOCalculator` |
| Unit – intersection | CD ∩ CE (TL=8) | CDE: entries (1,4),(3,4), DO=0.8016 | `ConditionalListBuilder` |
| Property | ∀ mẫu X: DO(Y) ≤ DUBO(X) với mọi Y ⊇ X (random data nhỏ) | luôn đúng | `DUBOCalculator` |

> Khuyến nghị: dùng **epsilon-based assert** (`Numeric.ge`) hoặc làm tròn 4 chữ số khi so sánh với bảng [4], vì kết quả thật có thể lệch chữ số thứ 5+ (xem Q5).

---

# 10. Mã khung kiểm thử acceptance (JUnit – đề xuất)

```java
@Test
void tC1_paper_example() {
    var src = new ListTransactionSource(List.of(
        new Transaction(1, List.of(item("A"), item("C"), item("D"), item("E"))),
        new Transaction(2, List.of(item("A"), item("E"), item("F"))),
        // ... T3..T8
    ));
    MiningEngine engine = new MiningEngine(0.15, 0.9);
    while (true) {
        var t = src.readNext(); if (t.isEmpty()) break; engine.loadBatch(List.of(t.get()));
    }
    Set<ResultPattern> dops = engine.mineNow();
    assertTrue(dops.stream().anyMatch(p -> p.render().equals("AE")  && Numeric.round4(p.doValue()) == 1.2601));
    assertTrue(dops.stream().anyMatch(p -> p.render().equals("F")   && Numeric.round4(p.doValue()) == 1.2553));
}
```

*(Đây chỉ minh hoạ cấu trúc test; bảng dữ liệu đầy đủ lấy từ [4].)*

---

# 11. Vấn đề mở & quyết định cần bạn review (quan trọng nhất)

Trong quá trình dịch sang Java, đối chiếu 4 tài liệu phát hiện các điểm chưa nhất quán **giữa các draft**. Đề nghị khoanh vùng và tinh chỉnh trước khi code:

| # | Vấn đề | Phát hiện | Đề xuất của draft này |
|---|---|---|---|
| **Q1** | **Cách tính DUBO nhóm cùng chiều dài** | `Definition 6 / Sub-procedure 3 [3]` và ví dụ chạy tay [4] (CD → **1.6402**) dùng cùng hệ số `f^(TL−Tk)` với `Tk` của nhóm k cho **cả tổng**. Nhưng ở prose **Example 6 [3]** ghi CD → **1.542** (gán decay theo từng nhóm i khác nhau) – **mâu thuẫn**. Infra [2] đồng thuận phương án 1.6402. | **Giữ Definition 6/pseudocode [3] + [4]` (1.6402)** như `DUBOCalculator` của draft. Cần bạn xác nhận. |
| **Q2** | **Ngữ nghĩa "Sup < minSup" trong Mine** | Paper [3] (Mine line 03) **bỏ qua cả node** (không đánh giá DOP lẫn không mở rộng). Domain [1] 5.9.R2 chỉ nói "không dùng làm tiền tố mở rộng" – có thể đọc là vẫn phải đánh giá DOP. Ví dụ G (Sup=1) trong [4] bị bỏ qua hoàn toàn. | Draft theo **paper [3]** (bỏ qua cả node). Xác nhận lại đọc hiểu của [1]. |
| **Q3** | **`Parameter` vừa immutable vừa chứa counter (mâu thuẫn ở [2])** | [2] mô tả `Parameter` immutable nhưng gồm `totalTransactionCount`, `latestTid` (thay đổi theo batch). | Draft tách `AlgorithmConfig` (immutable) + `StreamStatus` (mutable). Đổi tên không? |
| **Q4** | **Tie-break khi support bằng nhau** | Thứ tự nguồn: DB0 → `B ≺ A ≺ F ≺ C ≺ D ≺ E`; toàn bộ → `G ≺ B ≺ A ≺ C ≺ D ≺ E ≺ F`. Quy luật ngầm: **thứ tự node được tạo** (first-appearance). Không thấy ghi rõ trong [1]/[2]. | Draft dùng **stable sort – giữ thứ tự tạo**. Cần xác nhận để tái tạo bit-for-bit (TODO-4 [2]). |
| **Q5** | **Số thực & làm tròn** | Bảng [4] làm tròn 4 chữ số; tính thật cho lệch chữ số 5+. `>=` trực tiếp có thể lật ngược kết quả khi DO sát minSup. | So sánh ε=1e-9 (`Numeric.ge`); `round4` chỉ để hiển thị/test. Chốt ε? |
| **Q6** | **Giao dịch rỗng / item trùng / chính sách reject** | [2] 6.2 gợi ý "loại bỏ **hoặc** từ chối". `Transaction` hiện **từ chối** giao dịch rỗng. | Chốt một trong hai: reject (fail-fast) hay bỏ qua (tolerant). |
| **Q7** | **Item type: `record Item` hay `String`** | Domain [1] định nghĩa Item là khái niệm. | Draft dùng `record Item` (type-safe). Có thể đổi `String`. |
| **Q8** | **`ConditionalDHOList`** | [2] để ngỏ "kế thừa hoặc tái dùng". | Draft dùng subclass marker (dễ truy vết). Có thể bỏ nếu muốn gọn. |
| **Q9** | **`loadBatch(TransactionSource)`** | Format dữ liệu chưa thống nhất (xem mục 12 TODO). | Draft đưa `TextTransactionSource` giả định; cho ý kiến format thật. |
| **Q10** | **Kết quả kèm transactions hay chỉ items+DO** | [4] báo cáo cột Transactions; [2] giữ transactions trong ResultPattern. | Draft giữ `occurrences` (phục vụ test/báo cáo). Có bỏ để tiết kiệm bộ nhớ không? |

---

# 12. Công việc tiếp theo / TODO

- **TODO-1:** Review & chốt 10 quyết định ở mục 11 (đặc biệt **Q1, Q2, Q4** – ảnh hưởng trực tiếp tới tính đúng).
- **TODO-2:** Chốt định dạng dữ liệu đầu vào (text / DB / stream) → hoàn thiện `io` package + `TextTransactionSource`.
- **TODO-3:** Chốt build tool (Maven/Gradle), root package, logging (SLF4J), DI (nếu có), module JPMS.
- **TODO-4:** Viết test JUnit cho TC1–TC8 theo bảng mục 9 (có thể tách project con `dhopm-test`).
- **TODO-5:** Hiệu năng: đánh giá `double` vs `float`, cache `Math.pow`, cấu trúc entry (primitive arrays), bộ nhớ cho stream lớn (TID vượt `int`). Ghi nhận như tinh chỉnh, không đổi API bây giờ.
- **TODO-6:** Cập nhật đồng bộ khi [1]/[2] được sửa (đảm bảo truy vết Q1/Q2/…).

---

*Hết tài liệu (bản nháp – chờ bạn review và tinh chỉnh).*