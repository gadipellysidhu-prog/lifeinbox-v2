package com.lifeinbox.mail;

import com.lifeinbox.dto.EmailMessage;

public interface EmailProvider {

    /**
     * Sends an email message.
     *
     * @param message the email message containing recipient, subject, HTML and plain-text body
     * @throws Exception if sending fails
     */
    void sendEmail(EmailMessage message) throws Exception;
}
