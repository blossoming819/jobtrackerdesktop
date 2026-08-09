package com.jobtracker;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan({
        "com.jobtracker.mapper",
        "com.jobtracker.applymate.profile.mapper",
        "com.jobtracker.applymate.resumeparse.mapper",
        "com.jobtracker.applymate.security"
})
@ConfigurationPropertiesScan
@SpringBootApplication
public class JobTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobTrackerApplication.class, args);
    }
}
