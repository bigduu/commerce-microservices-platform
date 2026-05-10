package com.interview.user.application;

import com.interview.common.domain.Money;
import com.interview.common.outbox.OutboxMessage;
import com.interview.common.outbox.OutboxRepository;
import com.interview.user.domain.UserAccount;
import com.interview.user.domain.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final OutboxRepository outboxRepository;

    public UserAccountService(UserAccountRepository userAccountRepository,
                              OutboxRepository outboxRepository) {
        this.userAccountRepository = userAccountRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public UserAccount createUser(String userId, String username) {
        if (userAccountRepository.existsById(userId)) {
            throw new IllegalArgumentException("User already exists: " + userId);
        }
        UserAccount account = UserAccount.create(userId, username);
        userAccountRepository.save(account);
        return account;
    }

    @Transactional
    public void topUp(String userId, BigDecimal amount) {
        UserAccount account = userAccountRepository.findById(userId);
        account.topUp(Money.of(amount));
        userAccountRepository.save(account);
    }

    @Transactional
    public void deductPayment(String userId, String orderId, BigDecimal amount) {
        UserAccount account = userAccountRepository.findById(userId);
        account.deductPayment(orderId, Money.of(amount));
        userAccountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public UserAccount getUser(String userId) {
        return userAccountRepository.findById(userId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(String userId) {
        UserAccount account = userAccountRepository.findById(userId);
        return account.getBalance().amount();
    }
}
