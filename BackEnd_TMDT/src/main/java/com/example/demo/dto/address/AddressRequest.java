
package com.example.demo.dto.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddressRequest {

    @NotBlank(message = "Vui lòng nhập tên người nhận.")
    @Size(max = 100, message = "Tên người nhận tối đa 100 ký tự.")
    private String recipientName;

    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự.")
    private String phone;

    @NotBlank(message = "Vui lòng chọn tỉnh/thành phố.")
    @Size(max = 100)
    private String province;

    @NotBlank(message = "Vui lòng chọn quận/huyện.")
    @Size(max = 100)
    private String district;

    @NotBlank(message = "Vui lòng chọn phường/xã.")
    @Size(max = 100)
    private String ward;

    @NotBlank(message = "Vui lòng nhập địa chỉ cụ thể.")
    @Size(max = 255, message = "Địa chỉ cụ thể tối đa 255 ký tự.")
    private String addressDetail;

    private Boolean isDefault;
}
