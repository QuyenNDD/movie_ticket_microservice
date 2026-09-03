package com.movie.api_gateway;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Cần JWT_SECRET và các biến môi trường service URL thật để khởi động context — chạy thủ công khi có hạ tầng.")
class ApiGatewayApplicationTests {

	@Test
	void contextLoads() {
	}

}
