Bạn đang tham gia dự án xây dựng tài liệu kỹ thuật cho thuật toán DHOPM/DOPM dựa trên các tài liệu nguồn của project.

# Bối cảnh

Project hiện có:

1. Bài báo khoa học mô tả thuật toán DHOPM/DOPM.
2. Tài liệu nội bộ phân tích thuật toán và ví dụ chạy tay.

Mục tiêu KHÔNG phải là chuyển PDF sang Markdown theo kiểu copy nội dung hoặc giữ nguyên cấu trúc paper.

Mục tiêu là tạo ra một tài liệu kỹ thuật (Technical Specification) đóng vai trò là nguồn tham chiếu chính (source of truth) cho việc hiểu và triển khai thuật toán trong tương lai.

---

# Nguyên tắc quan trọng

## 1. Domain-first

Tài liệu chỉ mô tả:

* Domain concepts
* Domain rules
* Logical data requirements
* Domain capabilities
* Workflow của thuật toán

Không mô tả implementation.

---

## 2. Không đi vào Infrastructure

Tài liệu không được quyết định:

* Class
* Interface
* Struct
* Record
* Object
* HashMap
* Tree
* Linked List
* Package
* Module
* Database

Ví dụ:

Sai:

* DHO-List gồm Node và Entry

Đúng:

* DHO-List là cấu trúc logic dùng để lưu và truy xuất thông tin phục vụ mining

---

## 3. Không phụ thuộc ngôn ngữ lập trình

Tài liệu phải độc lập với:

* Java
* C#
* Python
* Go

Sau này có thể tạo tài liệu triển khai riêng dựa trên tài liệu này.

Ví dụ:

DHOPM Technical Specification

↓

DHOPM Java Design

↓

DHOPM Java Implementation

---

## 4. Không viết theo hướng học thuật

Không tập trung vào:

* Literature review
* Motivation
* Related work
* Chứng minh định lý
* Phân tích học thuật

Chỉ giữ những gì phục vụ việc hiểu và triển khai thuật toán.

---

## 5. Không mô tả pseudocode chi tiết ở giai đoạn đầu

Ưu tiên:

* thuật ngữ
* quy tắc
* capability
* workflow
* data requirement

trước khi nói đến implementation.

---

# Ranh giới tài liệu

Tài liệu phải có phần giải thích rõ:

"Tài liệu mô tả cần biết gì và cần làm gì để giải bài toán DHOPM, không mô tả phải lưu như thế nào hoặc cài đặt như thế nào."

---

# Cấu trúc tài liệu mong muốn

## Document Header

* Document Name
* Document ID
* Version
* Status
* Source Documents
* Revision History

---

## Document Scope

* Purpose
* Scope

---

## Architectural Boundary

Bao gồm:

### Purpose of This Document

### What This Document Defines

* Domain Concepts
* Domain Rules
* Logical Data Requirements
* Domain Capabilities

### What This Document Does Not Define

* Programming Language
* Architecture
* Data Structure Implementation
* API
* Database
* Class Design

### Interpretation Guideline

Mọi thành phần phải được hiểu là logical concepts chứ không phải implementation concepts.

---

# 1. Overview

## 1.1 Introduction

* Thuật toán là gì
* Mục tiêu
* Kết quả đầu ra

Không nói về hạn chế của thuật toán khác.

---

## 1.2 Workflow

Workflow tổng thể của thuật toán.

---

## 1.3 Objectives

---

# 2. Domain Model

Bao gồm các khái niệm:

* Transaction
* Pattern
* Transaction Database
* Incremental Database
* DHO-List
* DOP

Mô tả ở mức khái niệm.

Không mô tả class hoặc cấu trúc dữ liệu cụ thể.

---

# 3. Mathematical Model

Bao gồm:

* Support
* Occupancy
* UBO
* DO
* DUBO

Mỗi mục gồm:

* Description
* Formula
* Purpose
* Example (nếu cần)

---

# 4. Input Data

Bao gồm:

* Transaction Format
* Database Format
* Incremental Database Format
* Algorithm Parameters

---

# 5. Domain Capabilities

Mô tả các khả năng của thuật toán.

Ví dụ:

* Record Transaction
* Record Item Occurrence
* Maintain Pattern Statistics
* Calculate Occupancy
* Calculate Damped Occupancy
* Generate Extended Pattern
* Evaluate Pattern
* Estimate Upper Bound
* Prune Search Space
* Traverse Pattern Space
* Discover DOPs

Mỗi capability phải có:

### Purpose

### Processing Rules

Các Rule được đánh số:

R1
R2
R3
...

Rule phải ở mức domain.

Không được chứa implementation details.

---

# 6. Logical Data Requirements

Ví dụ:

DR-01
Hệ thống phải xác định được transaction chứa item hoặc pattern.

DR-02
Hệ thống phải xác định được độ dài transaction.

DR-03
Hệ thống phải xác định được transaction mới nhất.

...

Đây là các yêu cầu đối với infrastructure nhưng không áp đặt infrastructure phải được thiết kế như thế nào.

---

# 7. Algorithm Workflow

Luồng thực thi end-to-end.

---

# 8. Walkthrough Example

Dựa trên ví dụ chạy tay trong tài liệu nguồn.

Bao gồm:

* Dataset
* Các bước xử lý
* Kết quả trung gian
* Kết quả cuối cùng

---

# 9. Formula Reference

Tổng hợp công thức.

---

# 10. Glossary

* Symbol Reference
* Parameter Reference
* Terminology Reference

---

# Yêu cầu đầu ra

Khi viết nội dung:

* Bám sát 2 tài liệu nguồn.
* Không tự thêm khái niệm ngoài phạm vi project.
* Không đưa ra kiến trúc phần mềm mới.
* Không đề xuất framework.
* Không đề xuất mô hình dữ liệu triển khai.
* Không chuyển sang Java Design.
* Không chuyển sang Implementation Design.

Mọi nội dung phải giữ ở mức Domain Specification và Technical Specification.
