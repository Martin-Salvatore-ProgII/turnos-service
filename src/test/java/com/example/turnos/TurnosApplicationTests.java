package com.example.turnos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class, TestCatedraConfiguration.class })
@SpringBootTest
class TurnosApplicationTests {

	@Test
	void contextLoads() {
	}

}
