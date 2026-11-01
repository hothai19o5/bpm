# BPM — môi trường Docker dev/test

Cấu hình dùng Java 25, Spring Boot 4.1.2, Flowable 8.0.0 theo `pom.xml` và PostgreSQL 18.6. Backend, build và test chạy trong container; máy host cần Docker Desktop ở chế độ Linux containers và Docker Compose v2 có hỗ trợ `up --wait`.

**Trạng thái:** Đã chuẩn bị cấu hình; người dùng xác nhận đã chạy xong Docker. Chưa có xác nhận riêng về kết quả test stack và kiểm tra persistence, nên chưa đánh dấu toàn bộ P1.2 hoàn thành. Các lần build/test tiếp theo tiếp tục do người dùng chạy.

## 1. Chuẩn bị lần đầu

Trong PowerShell tại thư mục repository:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Chỉnh `.env` theo máy của bạn; giá trị mật khẩu trong file mẫu chỉ phục vụ dev. `.env` được Git và Docker build context bỏ qua. Các tham số:

| Biến | Giá trị mẫu | Ý nghĩa |
| --- | --- | --- |
| `APP_PORT` | `8080` | Cổng backend trên máy host. |
| `POSTGRES_PORT` | `5432` | Cổng PostgreSQL trên máy host, phục vụ IDE/công cụ DB. |
| `POSTGRES_DB` | `bpm` | Database dev. |
| `POSTGRES_USER` | `bpm` | Tài khoản DB dev. |
| `POSTGRES_PASSWORD` | Giá trị mẫu trong `.env.example` | Mật khẩu DB dev; không để trống. |

Các cổng được bind vào `127.0.0.1`. Nếu cổng đang được dùng, đổi `APP_PORT`/`POSTGRES_PORT` trong `.env`. Backend luôn kết nối nội bộ tới service `postgres:5432`; đổi cổng host không đổi địa chỉ kết nối giữa container.

## 2. Build và chạy dev

```powershell
docker compose up --build -d --wait --wait-timeout 180
docker compose ps
```

Lệnh đầu build image backend, chờ PostgreSQL healthy rồi chạy ứng dụng với profile `dev`; cuối cùng chờ health check của backend. Image build dùng `-Dmaven.test.skip=true`, không chạy hoặc compile test. Bộ kiểm thử có lệnh riêng ở mục 4.

Với cổng mặc định, kiểm tra:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Kết quả mong đợi là `status: UP`. Nếu đổi `APP_PORT`, dùng cổng đã cấu hình. Health endpoint kiểm tra trạng thái ứng dụng và DB; chưa phải bằng chứng nghiệp vụ/transaction/concurrency đã đúng. Chưa có màn hình hoặc API nghiệp vụ; truy cập `/` có thể yêu cầu đăng nhập theo Spring Security mặc định.

Xem log:

```powershell
docker compose logs -f backend postgres
```

Sau khi sửa source, chạy lại `docker compose up --build -d --wait --wait-timeout 180`. Source được đóng vào image, chưa cấu hình hot reload/bind mount source.

## 3. Dừng và giữ dữ liệu dev

```powershell
docker compose stop
docker compose up -d --wait --wait-timeout 180
```

Hoặc xóa container/network và dựng lại, vẫn giữ named volume:

```powershell
docker compose down
docker compose up -d --wait --wait-timeout 180
```

Với project mặc định `bpm-dev`, dữ liệu nằm trong volume `bpm-dev_postgres-data`, mount tại `/var/lib/postgresql` phù hợp image PostgreSQL 18. Dữ liệu ở container/volume của cấu hình Compose cũ không tự chuyển sang volume mới. Các lệnh trong tài liệu này chưa được chạy tự động.

`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` được PostgreSQL áp dụng khi khởi tạo volume trống. Đổi các biến đó trong `.env` không tự sửa tài khoản/database đã tồn tại trong volume; cần điều chỉnh DB hiện có hoặc reset dev có chủ đích.

**Chỉ khi muốn xóa toàn bộ database dev để tạo lại từ đầu:**

```powershell
docker compose -p bpm-dev -f compose.yaml down --volumes
docker compose -p bpm-dev -f compose.yaml up --build -d --wait --wait-timeout 180
```

Đây là thao tác xóa dữ liệu dev, tách riêng với lệnh dừng thông thường. Nếu chủ động dùng project name khác, thay `bpm-dev` đúng project đó.

## 4. Chạy test cách ly trong Docker

`compose.test.yaml` là file độc lập; không ghép bằng `-f compose.yaml -f compose.test.yaml`.

```powershell
docker compose -p bpm-test -f compose.test.yaml up --build --abort-on-container-exit --exit-code-from tests
$testExitCode = $LASTEXITCODE
```

Test runner dùng JDK 25 và Maven Wrapper của repository, chạy `clean test`. PostgreSQL test có database/user `bpm_test`, không mở cổng host và không mount volume dev. Dữ liệu test nằm trên tmpfs, mất khi container PostgreSQL test dừng. Dev có thể tiếp tục chạy song song.

Mã thoát từ lệnh Compose là kết quả của service `tests`; `0` khi thành công. Nếu dùng trong script CI/PowerShell, lưu `$LASTEXITCODE` ngay sau lệnh `up` như trên và trả lại `$testExitCode` sau các bước thu thập báo cáo/dọn dẹp.

Thu thập báo cáo **trước khi xóa container test**:

```powershell
New-Item -ItemType Directory -Force target | Out-Null
docker compose -p bpm-test -f compose.test.yaml cp tests:/workspace/target/surefire-reports ./target/test-results
docker compose -p bpm-test -f compose.test.yaml down
```

Nếu Maven lỗi trước khi chạy Surefire, có thể chưa có thư mục báo cáo; xem output hoặc `docker compose -p bpm-test -f compose.test.yaml logs tests` để biết lỗi. Volume `bpm-test_maven-cache` chỉ giữ dependency Maven, không chứa DB; có thể giữ cho lần chạy sau.

Smoke test hiện tại yêu cầu profile `test`, xác nhận PostgreSQL/database `bpm_test` và truy vấn được bảng Flowable. Chưa có các bài kiểm thử business flow, rollback hoặc concurrency của P1.5–P1.6.

Nếu chạy test từ IDE thay vì Docker runner, cần tự cung cấp `TEST_DATABASE_URL`, `TEST_DATABASE_USERNAME`, `TEST_DATABASE_PASSWORD` trỏ tới DB PostgreSQL test riêng tên `bpm_test`. Profile test không có fallback về DB dev.

## 5. Cấu hình ứng dụng và schema

| File | Chức năng |
| --- | --- |
| `application.yaml` | Cấu hình chung, tắt auto-update schema ngoài profile được chọn, graceful shutdown, chỉ expose health endpoint. |
| `application-dev.yaml` | Datasource từ các biến `DB_*`; JPA `ddl-auto=update`, Flowable `database-schema-update=true`. |
| `src/test/resources/application-test.yaml` | Datasource riêng từ `TEST_DATABASE_*`; JPA `create-drop`, Flowable tạo/cập nhật schema trong DB tạm. Không đóng profile test vào JAR runtime. |

Không thêm Flyway/Liquibase hoặc migration script trong giai đoạn dev. Tự cập nhật schema dev hỗ trợ vòng lặp phát triển; đổi tên/xóa trường hoặc thay đổi dữ liệu vẫn có thể cần reset dev hoặc xử lý dữ liệu có chủ đích. Chính sách nâng cấp schema cho dữ liệu thật hoàn thiện trước pilot/production.

Compose trực tiếp điều phối container. Đã bỏ dependency `spring-boot-docker-compose` để ứng dụng không tự dựng stack khác; bỏ H2/H2 console để dev/test dùng PostgreSQL. Giữ nguyên phiên bản Java/Spring Boot/Flowable. Thêm Actuator cho health check và starter test cho bộ kiểm thử.

Storage S3-compatible sẽ được bổ sung trước chức năng đính kèm; cấu hình hiện tại gồm backend và PostgreSQL.

## 6. Checklist xác nhận sau khi bạn chạy

- Compose dev build/khởi động thành công, backend và PostgreSQL healthy.
- Health endpoint trả `UP` và log có Flowable khởi tạo thành công.
- Dữ liệu DB/engine vẫn còn khi `down` rồi `up` không kèm `--volumes`.
- Test stack chạy thành công trong DB `bpm_test`; dữ liệu dev không thay đổi.
- Báo cáo Surefire được lấy ra trước khi xóa container test.

Người dùng đã xác nhận chạy xong Docker; kết quả test stack và kiểm tra persistence sẽ được ghi nhận riêng tại [P1.2 trong kế hoạch](docs/Enterprise-BPM-Implementation-Plan.md). Bước tiếp theo là P1.3 — cấu trúc module và hợp đồng API nền.

Tham chiếu: [Docker Compose health/dependency](https://docs.docker.com/compose/how-tos/startup-order/), [PostgreSQL official image — volume PostgreSQL 18](https://hub.docker.com/_/postgres), [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html), [Flowable schema configuration](https://www.flowable.com/open-source/docs/bpmn/ch05a-Spring-Boot).
