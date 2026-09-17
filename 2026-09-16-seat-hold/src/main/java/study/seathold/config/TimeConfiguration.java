package study.seathold.config;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.*;
@Configuration
public class TimeConfiguration {
    @Bean
    public Clock clock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
}

