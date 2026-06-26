package com.example.pogun.service.payment;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.user.CreditLedgerItemResponse;
import com.example.pogun.dto.user.CreditLedgerListResponse;
import com.example.pogun.dto.user.UserCreditBalanceResponse;
import com.example.pogun.entity.payment.CreditLedgerEntry;
import com.example.pogun.entity.payment.Purchase;
import com.example.pogun.entity.payment.UserCreditBalance;
import com.example.pogun.entity.payment.enums.CreditLedgerType;
import com.example.pogun.entity.payment.enums.PurchaseStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.repository.payment.CreditLedgerRepository;
import com.example.pogun.repository.payment.PurchaseRepository;
import com.example.pogun.repository.payment.UserCreditBalanceRepository;
import com.example.pogun.service.user.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditService {

    private final UserCreditBalanceRepository userCreditBalanceRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final PurchaseRepository purchaseRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public UserCreditBalanceResponse getCurrentBalance() {
        User user = currentUserService.getCurrentUser();
        UserCreditBalance balance = userCreditBalanceRepository.findByUserId(user.getId())
                .orElseGet(() -> UserCreditBalance.builder().user(user).balance(0).updatedAt(Instant.now()).build());
        return new UserCreditBalanceResponse(balance.getBalance(), balance.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public CreditLedgerListResponse getCurrentUserCreditLogs() {
        User user = currentUserService.getCurrentUser();
        List<CreditLedgerItemResponse> items = creditLedgerRepository.findTop50ByUserOrderByCreatedAtDesc(user).stream()
                .map(entry -> new CreditLedgerItemResponse(
                        entry.getId(),
                        entry.getType().name(),
                        entry.getDelta(),
                        entry.getBalanceBefore(),
                        entry.getBalanceAfter(),
                        entry.getReferenceType(),
                        entry.getReferenceId(),
                        entry.getDescription(),
                        entry.getCreatedAt()
                ))
                .toList();
        return new CreditLedgerListResponse(items.size(), items);
    }

    @Transactional
    public int addCreditsForPurchase(User user, Purchase purchase, int credits) {
        UserCreditBalance balance = lockOrCreateBalance(user);
        int before = safeBalance(balance);
        int after = before + credits;
        balance.setBalance(after);
        userCreditBalanceRepository.save(balance);
        creditLedgerRepository.save(CreditLedgerEntry.builder()
                .user(user)
                .type(CreditLedgerType.CHARGE)
                .delta(credits)
                .balanceBefore(before)
                .balanceAfter(after)
                .referenceType("PURCHASE")
                .referenceId(purchase.getId().toString())
                .description(purchase.getProductId() + " 결제 적립")
                .build());
        return after;
    }

    @Transactional
    public int useCreditsForAi(AiCreditPolicy policy, String requestType, String referenceId, String content) {
        validateRequestCharacters(policy, content);
        User user = currentUserService.getCurrentUser();
        UserCreditBalance balance = lockOrCreateBalance(user);
        int before = safeBalance(balance);
        if (before < policy.getCost()) {
            throw ApiException.conflict("INSUFFICIENT_CREDITS", "크레딧이 부족합니다.");
        }
        int after = before - policy.getCost();
        balance.setBalance(after);
        userCreditBalanceRepository.save(balance);
        creditLedgerRepository.save(CreditLedgerEntry.builder()
                .user(user)
                .type(CreditLedgerType.USE)
                .delta(-policy.getCost())
                .balanceBefore(before)
                .balanceAfter(after)
                .referenceType("AI_REQUEST")
                .referenceId(referenceId)
                .description(requestType + " AI 요청 차감")
                .build());
        return after;
    }

    @Transactional
    public ManualAnalysisCreditUsage useMissingPetAnalysisCredit(String noticeId, String description) {
        User user = currentUserService.getCurrentUser();
        Purchase purchase = findEligiblePurchase(user, description);
        PaymentProductCatalog catalog = PaymentProductCatalog.requireSupported(purchase.getProductId());
        validateDescriptionLength(catalog, description);

        UserCreditBalance balance = lockOrCreateBalance(user);
        int before = safeBalance(balance);
        if (before < 1) {
            throw ApiException.conflict("INSUFFICIENT_CREDITS", "크레딧이 부족합니다.");
        }

        purchase.setConsumedCredits(safeConsumedCredits(purchase) + 1);
        purchaseRepository.save(purchase);

        int after = before - 1;
        balance.setBalance(after);
        userCreditBalanceRepository.save(balance);
        creditLedgerRepository.save(CreditLedgerEntry.builder()
                .user(user)
                .type(CreditLedgerType.USE)
                .delta(-1)
                .balanceBefore(before)
                .balanceAfter(after)
                .referenceType("MISSING_PET_ANALYSIS")
                .referenceId(noticeId)
                .description(purchase.getProductId() + " 분석 요청 차감")
                .build());

        return new ManualAnalysisCreditUsage(after, purchase.getId().toString(), purchase.getProductId(), catalog.getMaxPromptLength());
    }

    @Transactional
    public int refundAiCredits(String referenceId, int amount, String description) {
        User user = currentUserService.getCurrentUser();
        UserCreditBalance balance = lockOrCreateBalance(user);
        int before = safeBalance(balance);
        int after = before + amount;
        balance.setBalance(after);
        userCreditBalanceRepository.save(balance);
        creditLedgerRepository.save(CreditLedgerEntry.builder()
                .user(user)
                .type(CreditLedgerType.REFUND)
                .delta(amount)
                .balanceBefore(before)
                .balanceAfter(after)
                .referenceType("AI_REQUEST")
                .referenceId(referenceId)
                .description(description)
                .build());
        return after;
    }

    @Transactional
    public int revokeUnusedCreditsForPurchase(User user, Purchase purchase, String description) {
        int unusedCredits = Math.max(0, safeCreditedCredits(purchase) - safeConsumedCredits(purchase));
        if (unusedCredits == 0) {
            return 0;
        }

        UserCreditBalance balance = lockOrCreateBalance(user);
        int before = safeBalance(balance);
        int revokedCredits = Math.min(unusedCredits, before);
        if (revokedCredits == 0) {
            return 0;
        }

        int after = before - revokedCredits;
        balance.setBalance(after);
        userCreditBalanceRepository.save(balance);
        creditLedgerRepository.save(CreditLedgerEntry.builder()
                .user(user)
                .type(CreditLedgerType.ADJUST)
                .delta(-revokedCredits)
                .balanceBefore(before)
                .balanceAfter(after)
                .referenceType("PURCHASE")
                .referenceId(purchase.getId().toString())
                .description(description)
                .build());
        return revokedCredits;
    }

    public void validateRequestCharacters(AiCreditPolicy policy, String content) {
        int length = content == null ? 0 : content.length();
        if (length > policy.getMaxCharacters()) {
            throw ApiException.badRequest("AI_REQUEST_TOO_LONG", "AI 요청은 최대 " + policy.getMaxCharacters() + "자까지 허용됩니다.");
        }
    }

    public void validateDescriptionLength(PaymentProductCatalog catalog, String description) {
        int length = description == null ? 0 : description.length();
        if (length > catalog.getMaxPromptLength()) {
            throw ApiException.badRequest("AI_DESCRIPTION_TOO_LONG", "이 상품으로는 공고 설명을 최대 " + catalog.getMaxPromptLength() + "자까지 분석할 수 있습니다.");
        }
    }

    private UserCreditBalance lockOrCreateBalance(User user) {
        return userCreditBalanceRepository.findByUserIdForUpdate(user.getId())
                .orElseGet(() -> {
                    UserCreditBalance created = userCreditBalanceRepository.save(UserCreditBalance.builder()
                            .user(user)
                            .balance(0)
                            .build());
                    return userCreditBalanceRepository.findByUserIdForUpdate(user.getId()).orElse(created);
                });
    }

    private int safeBalance(UserCreditBalance balance) {
        return balance.getBalance() == null ? 0 : balance.getBalance();
    }

    private int safeConsumedCredits(Purchase purchase) {
        return purchase.getConsumedCredits() == null ? 0 : purchase.getConsumedCredits();
    }

    private int safeCreditedCredits(Purchase purchase) {
        return purchase.getCreditedCredits() == null ? 0 : purchase.getCreditedCredits();
    }

    private Purchase findEligiblePurchase(User user, String description) {
        int length = description == null ? 0 : description.length();
        return purchaseRepository.findByUserAndStatusForUpdate(user, PurchaseStatus.VERIFIED).stream()
                .filter(purchase -> safeConsumedCredits(purchase) < (purchase.getCreditedCredits() == null ? 0 : purchase.getCreditedCredits()))
                .filter(purchase -> {
                    PaymentProductCatalog catalog = PaymentProductCatalog.requireSupported(purchase.getProductId());
                    return length <= catalog.getMaxPromptLength();
                })
                .sorted((left, right) -> {
                    PaymentProductCatalog leftCatalog = PaymentProductCatalog.requireSupported(left.getProductId());
                    PaymentProductCatalog rightCatalog = PaymentProductCatalog.requireSupported(right.getProductId());
                    int maxLengthCompare = Integer.compare(leftCatalog.getMaxPromptLength(), rightCatalog.getMaxPromptLength());
                    if (maxLengthCompare != 0) {
                        return maxLengthCompare;
                    }
                    return left.getCreatedAt().compareTo(right.getCreatedAt());
                })
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("NO_ELIGIBLE_ANALYSIS_PACKAGE", "사용 가능한 분석 요청권이 없거나 공고 설명 길이에 맞는 상품이 없습니다."));
    }

    public record ManualAnalysisCreditUsage(
            int balanceAfter,
            String purchaseId,
            String productId,
            int appliedMaxDescriptionLength
    ) {
    }
}
