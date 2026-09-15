package study.cartsync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record SyncCartItemsRequest(@NotNull List<@NotNull @Valid Item> items) {

    public record Item(@NotNull Long productId, @NotNull @Positive Integer quantity) {
    }
}
