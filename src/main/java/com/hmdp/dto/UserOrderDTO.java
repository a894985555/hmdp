package com.hmdp.dto;

import lombok.Data;

@Data
public class UserOrderDTO {
    private Long id;
    private String nickName;
    private String icon;
    private Long voucherId;
    private Long orderId;
}
