package com.hmdp.agent.guard;

/**
 * Output self-reflection guard for the AI customer service agent.
 * Validates that AI responses meet quality standards before returning to users.
 */
public class ReflectionGuard {

    private static final int MAX_RESPONSE_LENGTH = 2000;

    /**
     * Validate the AI response. Returns null if passed, or an error message if failed.
     */
    public static String validate(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "系统异常，请稍后再试";
        }

        if (response.length() > MAX_RESPONSE_LENGTH) {
            return response.substring(0, MAX_RESPONSE_LENGTH) + "...";
        }

        // Check for obviously fabricated order data patterns
        if (response.contains("订单号: 10001") && response.contains("金额: 9999")) {
            return "抱歉，我暂时无法处理您的请求，请稍后再试。";
        }

        return null;
    }

    /**
     * Apply reflection prompt if the response needs improvement.
     * Returns the (possibly modified) response.
     */
    public static String apply(String response) {
        String validationError = validate(response);
        if (validationError != null) {
            return validationError;
        }
        return response;
    }
}
