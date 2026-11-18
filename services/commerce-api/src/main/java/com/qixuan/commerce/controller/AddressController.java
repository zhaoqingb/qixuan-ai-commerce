package com.qixuan.commerce.controller;

import com.qixuan.commerce.dto.AddressCreateRequest;
import com.qixuan.commerce.entity.UserAddress;
import com.qixuan.commerce.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址。
 *
 * 所有接口的 userId 都来自 token（@RequestAttribute），请求体里不接受用户 id ——
 * 否则改个数字就能操作别人的地址。
 */
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<UserAddress> list(@RequestAttribute("userId") Long userId) {
        return addressService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserAddress create(@RequestAttribute("userId") Long userId,
            @Valid @RequestBody AddressCreateRequest request) {
        return addressService.create(userId, request);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute("userId") Long userId, @PathVariable Long addressId) {
        addressService.delete(userId, addressId);
    }

    @PostMapping("/{addressId}/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setDefault(@RequestAttribute("userId") Long userId, @PathVariable Long addressId) {
        addressService.setDefault(userId, addressId);
    }
}
