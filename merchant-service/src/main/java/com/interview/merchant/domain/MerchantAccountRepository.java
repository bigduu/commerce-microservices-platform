package com.interview.merchant.domain;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface MerchantAccountRepository extends CrudRepository<MerchantAccount, String> {

    Optional<MerchantAccount> findById(String merchantId);
}
