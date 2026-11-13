package com.innovify.skillswap.iam.application.fakes;

import com.innovify.skillswap.iam.application.internal.outboundservices.PushDeliveryResult;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushNotification;
import com.innovify.skillswap.iam.application.internal.outboundservices.PushNotificationSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records the push notifications instead of sending them; each test programs the answer of the provider. */
public class FakePushNotificationSender implements PushNotificationSender {

    public record SentPush(String deviceToken, PushNotification notification) {
    }

    private final List<SentPush> sent = new CopyOnWriteArrayList<>();
    private volatile PushDeliveryResult answer = PushDeliveryResult.SENT;

    public List<SentPush> sent() {
        return sent;
    }

    public void answer(PushDeliveryResult answer) {
        this.answer = answer;
    }

    public void clear() {
        sent.clear();
        answer = PushDeliveryResult.SENT;
    }

    @Override
    public PushDeliveryResult send(String deviceToken, PushNotification notification) {
        sent.add(new SentPush(deviceToken, notification));
        return answer;
    }
}
