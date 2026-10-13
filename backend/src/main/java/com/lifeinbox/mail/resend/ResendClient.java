package com.lifeinbox.mail.resend;

public interface ResendClient {

    /**
     * Sends an email via the Resend API.
     *
     * @param apiUrl the Resend API endpoint (e.g. https://api.resend.com/emails)
     * @param apiKey the Resend API bearer token
     * @param request the email parameters
     * @return the Resend response containing the message ID
     * @throws Exception if sending fails or the API responds with an error
     */
    ResendEmailResponse sendEmail(String apiUrl, String apiKey, ResendEmailRequest request) throws Exception;
}
