// src/main/java/com/flow/workflow/WorkflowApplication.java
package com.flow.workflow;

import com.flow.workflow.model.User;
import com.flow.workflow.model.Role;
import com.flow.workflow.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WorkflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(WorkflowApplication.class, args);
	}

	@Bean
	public CommandLineRunner createAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		return args -> {
			String adminUsername = "user";
			String adminPassword = "password";
			if (userRepository.findByUsername(adminUsername).isEmpty()) {
				User admin = User.builder()
						.username(adminUsername)
						.password(passwordEncoder.encode(adminPassword))
						.role(Role.ADMIN)
						.build();
				userRepository.save(admin);
				System.out.println("Created admin user: " + adminUsername);
			} else {
				// ensure role set if user already exists
				User existing = userRepository.findByUsername(adminUsername).get();
				if (existing.getRole() == null) {
					existing.setRole(Role.ADMIN);
					userRepository.save(existing);
					System.out.println("Updated admin role to ADMIN for: " + adminUsername);
				}
			}
		};
	}
}
