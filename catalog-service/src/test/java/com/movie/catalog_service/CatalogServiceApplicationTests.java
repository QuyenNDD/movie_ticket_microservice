package com.movie.catalog_service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Cần MySQL + biến môi trường DB/Cloudinary thật để khởi động context — chạy thủ công khi có hạ tầng.")
class CatalogServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
