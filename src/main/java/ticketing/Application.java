package ticketing;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;

@EnableScheduling
@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@PostConstruct
	void init() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}
}
