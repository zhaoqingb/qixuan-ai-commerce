package com.qixuan.commerce.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qixuan.commerce.dto.AddressCreateRequest;
import com.qixuan.commerce.entity.UserAddress;
import com.qixuan.commerce.mapper.UserAddressMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 收货地址管理。
 *
 * 核心不变式：**同一个用户最多只有一条 is_default = 1 的记录**。
 * 结算页靠这条记录做默认选中，一旦出现两条，用户下单选中的地址就变得不确定。
 *
 * 这个"同组唯一"没法用数据库唯一索引表达（MySQL 不支持部分索引），
 * 只能靠事务内「先清零、再置一」保证。
 */
@Service
public class AddressService {

    /** 单用户地址上限：防止被脚本刷出几万条 */
    private static final int MAX_ADDRESS_COUNT = 20;

    private static final int DEFAULT_YES = 1;
    private static final int DEFAULT_NO = 0;

    private final UserAddressMapper addressMapper;

    public AddressService(UserAddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    /** 默认地址排最前，其余按新建时间倒序 */
    public List<UserAddress> list(Long userId) {
        return addressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAddress create(Long userId, AddressCreateRequest req) {
        Long count = addressMapper.selectCount(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId));
        if (count >= MAX_ADDRESS_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "收货地址最多 " + MAX_ADDRESS_COUNT + " 条，请先删除不用的");
        }

        // 第一条地址无论前端传什么，都必须是默认 —— 否则结算页选不到地址，用户下不了单
        boolean makeDefault = req.isDefault() || count == 0;
        if (makeDefault) {
            clearDefault(userId);
        }

        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setReceiverName(req.receiverName().trim());
        address.setReceiverPhone(req.receiverPhone().trim());
        address.setProvince(req.province().trim());
        address.setCity(req.city().trim());
        address.setDistrict(req.district().trim());
        address.setDetailAddress(req.detailAddress().trim());
        address.setIsDefault(makeDefault ? DEFAULT_YES : DEFAULT_NO);
        addressMapper.insert(address);
        return address;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long addressId) {
        UserAddress address = requireOwned(userId, addressId);
        addressMapper.deleteById(addressId);

        // 删的是默认地址：把剩下最新的一条补成默认，否则结算页会没有默认可选中
        if (Integer.valueOf(DEFAULT_YES).equals(address.getIsDefault())) {
            UserAddress next = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                    .eq(UserAddress::getUserId, userId)
                    .orderByDesc(UserAddress::getId)
                    .last("LIMIT 1"));
            if (next != null) {
                setDefault(userId, next.getId());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long addressId) {
        requireOwned(userId, addressId);
        clearDefault(userId);

        UserAddress patch = new UserAddress();
        patch.setId(addressId);
        patch.setIsDefault(DEFAULT_YES);
        addressMapper.updateById(patch);
    }

    /** 归属校验：查不到就 404，不区分"不存在"和"不是你的" */
    private UserAddress requireOwned(Long userId, Long addressId) {
        UserAddress address = addressMapper.selectOne(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId)
                .eq(UserAddress::getUserId, userId));
        if (address == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "收货地址不存在");
        }
        return address;
    }

    private void clearDefault(Long userId) {
        UserAddress patch = new UserAddress();
        patch.setIsDefault(DEFAULT_NO);
        addressMapper.update(patch, new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, DEFAULT_YES));
    }
}
