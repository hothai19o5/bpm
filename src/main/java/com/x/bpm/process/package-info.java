/**
 * Sở hữu danh mục quy trình, draft/version, công bố và mapping sang engine.
 *
 * <p>P1.3 có lát cắt đọc danh mục: api -> application -> domain policy + port;
 * adapter.persistence sẽ hiện thực port, config sẽ lắp ghép dependency.
 * Việc tìm kiếm danh mục ứng dụng không phải truy vấn danh sách Flowable.
 *
 * <p>P1.5 có port application.engine và adapter.flowable cho deploy/start/query/history.
 * Mọi kiểu org.flowable phải ở adapter;
 * không đưa chúng vào DTO công khai, policy hoặc use case.
 */
package com.x.bpm.process;
