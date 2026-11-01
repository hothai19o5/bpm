/**
 * Hạ tầng hợp đồng dùng chung cho P1.3.
 *
 * <p>application: kiểu dữ liệu Java thuần dùng ở ranh giới use case.
 * error: hợp đồng lỗi Java thuần dùng được từ policy và use case, không biết HTTP.
 * api: DTO và chuyển đổi lỗi HTTP. adapter.web: servlet filter.
 * config: lắp ghép Clock và correlation filter đã triển khai.
 *
 * <p>Không đưa entity nghiệp vụ, repository chung hoặc luật phê duyệt vào đây.
 * Xem docs/P1.3-Manual-Implementation-Guide.md trước khi triển khai.
 */
package com.x.bpm.shared;
