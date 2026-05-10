package com.interview.user.domain;

import com.interview.common.domain.Money;
import com.interview.common.event.DomainEvent;
import com.interview.common.exception.InsufficientBalanceException;
import com.interview.user.domain.events.UserAccountEvents;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserAccountTest {

    @Test
    void createUserAccount_shouldProduceAccountCreatedEvent() {
        UserAccount account = UserAccount.create("user-1", "alice");

        assertEquals("user-1", account.getUserId());
        assertEquals("alice", account.getUsername());
        assertEquals(Money.zero(), account.getBalance());

        List<DomainEvent> events = account.getPendingEvents();
        assertEquals(1, events.size());
        UserAccountEvents.AccountCreated event = (UserAccountEvents.AccountCreated) events.get(0);
        assertEquals("user-1", event.getUserId());
        assertEquals("alice", event.getUsername());
    }

    @Test
    void topUp_shouldIncreaseBalance() {
        UserAccount account = UserAccount.create("user-1", "alice");
        account.clearPendingEvents();

        account.topUp(Money.of(100.00));

        assertEquals(Money.of(100.00), account.getBalance());
        List<DomainEvent> events = account.getPendingEvents();
        assertEquals(1, events.size());
        UserAccountEvents.AccountToppedUp event = (UserAccountEvents.AccountToppedUp) events.get(0);
        assertEquals("user-1", event.getUserId());
        assertEquals(0, new BigDecimal("100.00").compareTo(event.getAmount()));
    }

    @Test
    void topUp_withZeroAmount_shouldThrow() {
        UserAccount account = UserAccount.create("user-1", "alice");
        assertThrows(IllegalArgumentException.class, () -> account.topUp(Money.zero()));
    }

    @Test
    void deductPayment_shouldDecreaseBalance() {
        UserAccount account = UserAccount.create("user-1", "alice");
        account.topUp(Money.of(100.00));
        account.clearPendingEvents();

        account.deductPayment("order-1", Money.of(30.00));

        assertEquals(Money.of(70.00), account.getBalance());
        List<DomainEvent> events = account.getPendingEvents();
        assertEquals(1, events.size());
        UserAccountEvents.PaymentDeducted event = (UserAccountEvents.PaymentDeducted) events.get(0);
        assertEquals("user-1", event.getUserId());
        assertEquals("order-1", event.getOrderId());
        assertEquals(0, new BigDecimal("30.00").compareTo(event.getAmount()));
    }

    @Test
    void deductPayment_withInsufficientBalance_shouldThrow() {
        UserAccount account = UserAccount.create("user-1", "alice");
        account.topUp(Money.of(10.00));
        account.clearPendingEvents();

        assertThrows(InsufficientBalanceException.class,
                () -> account.deductPayment("order-1", Money.of(30.00)));
    }

    @Test
    void refundPayment_shouldIncreaseBalance() {
        UserAccount account = UserAccount.create("user-1", "alice");
        account.topUp(Money.of(100.00));
        account.deductPayment("order-1", Money.of(30.00));
        account.clearPendingEvents();

        account.refundPayment("order-1", Money.of(30.00));

        assertEquals(Money.of(100.00), account.getBalance());
        List<DomainEvent> events = account.getPendingEvents();
        assertEquals(1, events.size());
        UserAccountEvents.PaymentRefunded event = (UserAccountEvents.PaymentRefunded) events.get(0);
        assertEquals("user-1", event.getUserId());
        assertEquals("order-1", event.getOrderId());
        assertEquals(0, new BigDecimal("30.00").compareTo(event.getAmount()));
    }

    @Test
    void replay_shouldReconstructStateFromEvents() {
        UserAccount account = UserAccount.create("user-1", "alice");
        account.topUp(Money.of(100.00));
        account.deductPayment("order-1", Money.of(30.00));

        List<DomainEvent> events = account.getPendingEvents();

        UserAccount reconstructed = new UserAccount();
        reconstructed.loadFromEvents(events);

        assertEquals("user-1", reconstructed.getUserId());
        assertEquals("alice", reconstructed.getUsername());
        assertEquals(Money.of(70.00), reconstructed.getBalance());
    }

    @Test
    void replay_withRefundEvent_shouldReconstructCorrectly() {
        UserAccount account = UserAccount.create("user-1", "alice");
        account.topUp(Money.of(100.00));
        account.deductPayment("order-1", Money.of(30.00));
        account.refundPayment("order-1", Money.of(30.00));

        List<DomainEvent> events = account.getPendingEvents();

        UserAccount reconstructed = new UserAccount();
        reconstructed.loadFromEvents(events);

        assertEquals(Money.of(100.00), reconstructed.getBalance());
    }

    @Test
    void topUp_withNullAmount_shouldThrow() {
        UserAccount account = UserAccount.create("user-1", "alice");
        assertThrows(IllegalArgumentException.class, () -> account.topUp(null));
    }

    @Test
    void deductPayment_withNullAmount_shouldThrow() {
        UserAccount account = UserAccount.create("user-1", "alice");
        account.topUp(Money.of(100.00));
        assertThrows(IllegalArgumentException.class, () -> account.deductPayment("order-1", null));
    }

    @Test
    void deductPayment_withZeroAmount_shouldThrow() {
        UserAccount account = UserAccount.create("user-1", "alice");
        account.topUp(Money.of(100.00));
        assertThrows(IllegalArgumentException.class, () -> account.deductPayment("order-1", Money.zero()));
    }

    @Test
    void refundPayment_withNullAmount_shouldThrow() {
        UserAccount account = UserAccount.create("user-1", "alice");
        assertThrows(IllegalArgumentException.class, () -> account.refundPayment("order-1", null));
    }

    @Test
    void refundPayment_withZeroAmount_shouldThrow() {
        UserAccount account = UserAccount.create("user-1", "alice");
        assertThrows(IllegalArgumentException.class, () -> account.refundPayment("order-1", Money.zero()));
    }

    @Test
    void replay_withUnknownEvent_shouldThrow() {
        DomainEvent unknownEvent = new DomainEvent("user-1", "UserAccount") {};
        List<DomainEvent> events = List.of(unknownEvent);

        UserAccount account = new UserAccount();
        assertThrows(IllegalArgumentException.class, () -> account.loadFromEvents(events));
    }

    @Test
    void paymentRefundFailedEvent_shouldHaveAllFields() {
        UserAccountEvents.PaymentRefundFailed event = new UserAccountEvents.PaymentRefundFailed(
                "user-1", "order-1", new BigDecimal("50.00"));

        assertEquals("user-1", event.getUserId());
        assertEquals("order-1", event.getOrderId());
        assertEquals(0, new BigDecimal("50.00").compareTo(event.getAmount()));
        assertEquals("UserAccount", event.getAggregateType());
    }

    @Test
    void paymentDeductFailedEvent_shouldHaveAllFields() {
        UserAccountEvents.PaymentDeductFailed event = new UserAccountEvents.PaymentDeductFailed(
                "user-1", "order-1", new BigDecimal("100.00"));
        event.fail("INSUFFICIENT_BALANCE", "Not enough");

        assertEquals("user-1", event.getUserId());
        assertEquals("order-1", event.getOrderId());
        assertEquals(0, new BigDecimal("100.00").compareTo(event.getAmount()));
        assertEquals("UserAccount", event.getAggregateType());
        assertEquals("INSUFFICIENT_BALANCE", event.getFailureCode());
        assertEquals("Not enough", event.getFailureReason());
    }

    @Test
    void noArgConstructorsShouldBeInstantiable() throws Exception {
        assertNotNull(callProtectedNoArgCtor(UserAccountEvents.AccountCreated.class));
        assertNotNull(callProtectedNoArgCtor(UserAccountEvents.AccountToppedUp.class));
        assertNotNull(callProtectedNoArgCtor(UserAccountEvents.PaymentDeducted.class));
        assertNotNull(callProtectedNoArgCtor(UserAccountEvents.PaymentRefunded.class));
        assertNotNull(callProtectedNoArgCtor(UserAccountEvents.PaymentDeductFailed.class));
        assertNotNull(callProtectedNoArgCtor(UserAccountEvents.PaymentRefundFailed.class));
    }

    private <T> T callProtectedNoArgCtor(Class<T> clazz) throws Exception {
        var ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }
}
