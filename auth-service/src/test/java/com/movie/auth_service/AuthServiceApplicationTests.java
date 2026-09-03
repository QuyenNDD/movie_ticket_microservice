package com.movie.auth_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Cần MySQL + biến môi trường DB/JWT thật để khởi động context — chạy thủ công khi có hạ tầng.")
class AuthServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
