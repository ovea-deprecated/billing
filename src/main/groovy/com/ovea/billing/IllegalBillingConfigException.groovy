package com.ovea.billing

/**
 * @author Mathieu Carbou (mathieu.carbou@gmail.com)
 * @date 2012-11-13
 */
class IllegalBillingConfigException extends RuntimeException {
    IllegalBillingConfigException(String message) {
        super(message)
    }
}
