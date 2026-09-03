package com.movie.notification_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Cần MySQL + RabbitMQ + cấu hình mail thật để khởi động context — chạy thủ công khi có hạ tầng.")
class NotificationServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
