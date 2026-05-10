package com.interview.order.application;

import java.math.BigDecimal;

public interface MerchantProductClient {

    BigDecimal getUnitPrice(String merchantId, String sku);
}
