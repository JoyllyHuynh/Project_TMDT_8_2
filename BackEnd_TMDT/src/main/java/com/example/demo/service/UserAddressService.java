
package com.example.demo.service;

import com.example.demo.dto.address.AddressRequest;
import com.example.demo.dto.address.AddressResponse;
import com.example.demo.model.User;
import com.example.demo.model.UserAddress;
import com.example.demo.repository.UserAddressRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAddressService {

    private final UserAddressRepository addressRepository;
    private final UserRepository userRepository;

    // Lấy danh sách địa chỉ của người dùng
    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses(Long userId) {
        return addressRepository
                .findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    // Thêm địa chỉ mới
    @Transactional
    public AddressResponse createAddress(Long userId, AddressRequest request) {
        User user = getUser(userId);

        UserAddress address = new UserAddress();
        address.setUser(user);

        applyRequest(address, request);

        boolean hasAddress = addressRepository.existsByUserId(userId);
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault()) || !hasAddress;

        if (makeDefault) {
            clearDefaultAddress(userId);
        }

        address.setIsDefault(makeDefault);

        return AddressResponse.from(addressRepository.save(address));
    }

    // Cập nhật địa chỉ
    @Transactional
    public AddressResponse updateAddress(
            Long userId,
            Long addressId,
            AddressRequest request
    ) {
        UserAddress address = getAddress(addressId, userId);

        applyRequest(address, request);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearDefaultAddress(userId);
            address.setIsDefault(true);
        }

        return AddressResponse.from(addressRepository.save(address));
    }

    // Xóa địa chỉ
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress address = getAddress(addressId, userId);
        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());

        addressRepository.delete(address);

        // Nếu xóa địa chỉ mặc định, chọn địa chỉ khác làm mặc định
        if (wasDefault) {
            List<UserAddress> remaining =
                    addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);

            if (!remaining.isEmpty()) {
                UserAddress newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                addressRepository.save(newDefault);
            }
        }
    }

    // Chọn địa chỉ mặc định
    @Transactional
    public AddressResponse setDefaultAddress(Long userId, Long addressId) {
        UserAddress address = getAddress(addressId, userId);

        clearDefaultAddress(userId);

        address.setIsDefault(true);

        return AddressResponse.from(addressRepository.save(address));
    }

    // Tìm người dùng
    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy tài khoản."
                ));
    }

    // Chỉ lấy địa chỉ thuộc tài khoản đang đăng nhập
    private UserAddress getAddress(Long addressId, Long userId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy địa chỉ giao hàng."
                ));
    }

    // Bỏ trạng thái mặc định của các địa chỉ khác
    private void clearDefaultAddress(Long userId) {
        addressRepository.findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(address -> {
                    address.setIsDefault(false);
                    addressRepository.save(address);
                });
    }

    // Gán dữ liệu từ Request vào Entity
    private void applyRequest(UserAddress address, AddressRequest request) {
        address.setRecipientName(request.getRecipientName().trim());
        address.setPhone(request.getPhone().trim());
        address.setProvince(request.getProvince().trim());
        address.setDistrict(request.getDistrict().trim());
        address.setWard(request.getWard().trim());
        address.setAddressDetail(request.getAddressDetail().trim());
    }
}
