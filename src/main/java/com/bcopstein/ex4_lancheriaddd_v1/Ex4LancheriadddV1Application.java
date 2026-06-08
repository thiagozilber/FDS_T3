package com.bcopstein.ex4_lancheriaddd_v1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.bcopstein")
public class Ex4LancheriadddV1Application {

	public static void main(String[] args) {
		SpringApplication.run(Ex4LancheriadddV1Application.class, args);
	}

}
