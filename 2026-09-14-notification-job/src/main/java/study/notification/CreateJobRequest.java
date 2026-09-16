package study.notification;

import jakarta.validation.constraints.*;
import java.util.List;
public record CreateJobRequest(
    @NotEmpty List<@NotNull @Positive Long> memberIds,
    @NotBlank @Size(max = 2000) String message) {}
