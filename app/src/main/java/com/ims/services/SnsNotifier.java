package com.ims.services;

import com.ims.config.ImsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/** SNS notifier for prod. */
@Component
@ConditionalOnProperty(name = "ims.aws.sns.enabled", havingValue = "true", matchIfMissing = true)
public class SnsNotifier implements Notifier {

    private final SnsClient sns;
    private final String topicArn;

    public SnsNotifier(SnsClient sns, ImsProperties props) {
        this.sns = sns;
        this.topicArn = props.getAws().getSns().getTopicArn();
    }

    @Override
    public void notifyLowStock(String subject, String message) {
        sns.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .subject(subject)
                .message(message)
                .build());
    }
}
