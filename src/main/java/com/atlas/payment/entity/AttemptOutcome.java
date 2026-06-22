package com.atlas.payment.entity;

/**
 * Outcome of a single provider call attempt (services/payment/service.md §Provider Call).
 * Classifies the attempt so the retry policy can decide whether to retry and which terminal
 * Payment state to reach when attempts are exhausted.
 */
public enum AttemptOutcome {

    /** Provider approved (HTTP 200, SUCCESS). Resolves the payment to SUCCEEDED. */
    SUCCESS,

    /** Provider rejected on business grounds (HTTP 402/422, DECLINED). Never retried; FAILED. */
    DECLINED,

    /** Transient provider error (HTTP 503). Retried; if persistent past max attempts → FAILED. */
    TRANSIENT_ERROR,

    /** Provider did not respond before the per-call timeout. Retried; if persistent → TIMED_OUT. */
    TIMEOUT
}
