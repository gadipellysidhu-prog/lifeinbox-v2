package com.lifeinbox.push;

import com.lifeinbox.config.LifeInboxProperties;
import com.lifeinbox.entity.PushSubscription;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Security;

@Component
public class DefaultWebPushClient implements WebPushClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultWebPushClient.class);

    private final LifeInboxProperties properties;

    public DefaultWebPushClient(LifeInboxProperties properties) {
        this.properties = properties;
    }

    @Override
    public int send(PushSubscription subscription, String payloadJson) throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        LifeInboxProperties.Push pushConfig = properties.getPush();
        if (!pushConfig.hasValidKeys()) {
            throw new IllegalStateException("VAPID keys are not configured or incomplete");
        }

        PushService pushService = new PushService(
                pushConfig.getVapidPublicKey(),
                pushConfig.getVapidPrivateKey(),
                pushConfig.getVapidSubject()
        );

        Notification notification = new Notification(
                subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuth(),
                payloadJson.getBytes(StandardCharsets.UTF_8)
        );

        HttpResponse response = pushService.send(notification);
        return response.getStatusLine().getStatusCode();
    }
}
