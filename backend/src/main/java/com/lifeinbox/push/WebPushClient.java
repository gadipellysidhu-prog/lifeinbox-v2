package com.lifeinbox.push;

import com.lifeinbox.entity.PushSubscription;

public interface WebPushClient {

    /**
     * Dispatches an encrypted Web Push notification payload to the given subscription.
     *
     * @param subscription the target push subscription containing endpoint and keys
     * @param payloadJson  the JSON payload to encrypt and deliver
     * @return the HTTP status code returned by the push service (e.g. 201 for success, 404/410 for expired)
     * @throws Exception if an unexpected transmission or cryptographic failure occurs
     */
    int send(PushSubscription subscription, String payloadJson) throws Exception;
}
