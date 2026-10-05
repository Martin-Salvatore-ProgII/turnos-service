package com.example.turnos;

import org.springframework.boot.SpringApplication;

public class TestTurnosApplication {

	public static void main(String[] args) {
		SpringApplication.from(TurnosApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
