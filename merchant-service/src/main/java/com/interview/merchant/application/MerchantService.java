package com.interview.merchant.application;

import com.interview.common.exception.AggregateNotFoundException;
import com.interview.merchant.domain.MerchantAccount;
import com.interview.merchant.domain.MerchantAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class MerchantService {

    private final MerchantAccountRepository merchantAccountRepository;

    public MerchantService(MerchantAccountRepository merchantAccountRepository) {
        this.merchantAccountRepository = merchantAccountRepository;
    }

    public MerchantAccount createMerchant(String merchantId, String name) {
        MerchantAccount account = new MerchantAccount(merchantId, name);
        return merchantAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public MerchantAccount getMerchant(String merchantId) {
        return merchantAccountRepository.findById(merchantId)
                .orElseThrow(() -> new AggregateNotFoundException(merchantId));
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(String merchantId) {
        MerchantAccount account = getMerchant(merchantId);
        return account.getBalance();
    }

    public void creditMerchant(String merchantId, BigDecimal amount) {
        MerchantAccount account = getMerchant(merchantId);
        account.credit(amount);
        merchantAccountRepository.save(account);
    }

    public void debitMerchant(String merchantId, BigDecimal amount) {
        MerchantAccount account = getMerchant(merchantId);
        account.debit(amount);
        merchantAccountRepository.save(account);
    }
}
