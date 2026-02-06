package com.teamA.async.worker.exception;

//dlq 전략수정 : 비즈니스 규칙 위반을 NonRetryable로 명확히 하기 위한 공용 예외
public class BusinessRuleViolationException extends RuntimeException {
    private final String reasonCode;

    public BusinessRuleViolationException(String reasonCode, String message) {
        super(message);
        this.reasonCode = (reasonCode == null || reasonCode.isBlank())
                ? "BUSINESS_RULE_VIOLATION"
                : reasonCode;
    }

    public BusinessRuleViolationException(String reasonCode) {
        super(reasonCode);
        this.reasonCode = (reasonCode == null || reasonCode.isBlank())
                ? "BUSINESS_RULE_VIOLATION"
                : reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
