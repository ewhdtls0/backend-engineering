package study.cartsync.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import study.cartsync.dto.*;
import study.cartsync.service.CartService;

@RestController
@RequestMapping("/api/carts")
public class CartController {
    private final CartService service;
    public CartController(CartService service) { this.service = service; }
    @PutMapping("/{cartId}/items")
    public CartResponse sync(@PathVariable Long cartId, @Valid @RequestBody SyncCartItemsRequest request) {
        return service.syncItems(cartId, request);
    }
}
