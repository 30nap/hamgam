package ir.hamgam.landing.sms;

/** Sends the one-time code. Pick the implementation with {@code SMS_PROVIDER}. */
public interface SmsProvider {

    /**
     * @param phone normalized Iranian mobile number, {@code 09xxxxxxxxx}
     * @param code  the numeric one-time code
     * @throws SmsException if the provider did not accept the message
     */
    void sendOtp(String phone, String code) throws SmsException;
}
