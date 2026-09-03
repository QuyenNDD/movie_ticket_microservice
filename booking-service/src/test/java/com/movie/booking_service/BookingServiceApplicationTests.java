package com.movie.booking_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Cần MySQL/Redis/RabbitMQ thật để khởi động context — chạy thủ công khi có hạ tầng."
        + " Logic nghiệp vụ được kiểm thử ở BookingServiceImplTest (unit test, không cần hạ tầng).")
class BookingServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
