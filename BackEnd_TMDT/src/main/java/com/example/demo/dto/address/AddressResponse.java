
package com.example.demo.dto.address;

import com.example.demo.model.UserAddress;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class AddressResponse {

    private Long id;
    private String recipientName;
    private String phone;
    private String province;
    private String district;
    private String ward;
    private String addressDetail;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AddressResponse from(UserAddress address) {
        AddressResponse response = new AddressResponse();

        response.setId(address.getId());
        response.setRecipientName(address.getRecipientName());
        response.setPhone(address.getPhone());
        response.setProvince(address.getProvince());
        response.setDistrict(address.getDistrict());
        response.setWard(address.getWard());
        response.setAddressDetail(address.getAddressDetail());
        response.setIsDefault(address.getIsDefault());
        response.setCreatedAt(address.getCreatedAt());
        response.setUpdatedAt(address.getUpdatedAt());

        return response;
    }
}
