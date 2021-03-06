package ca.radiant3.redisq.producer;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.StringUtils;

import ca.radiant3.redisq.Message;
import ca.radiant3.redisq.MessageQueue;
import ca.radiant3.redisq.persistence.RedisOps;

public class MessageProducerImpl<T> implements MessageProducer<T> {

    private RedisOps redisOps;

    private MessageQueue queue;

    private long defaultTimeToLive = 1;
    private TimeUnit defaultTimeToLiveUnit = TimeUnit.MINUTES;
    private SubmissionStrategy submissionStrategy;

    @PostConstruct
    public void initialize() {
        if (submissionStrategy == null) {
            submissionStrategy = new MultiConsumerSubmissionStrategy(redisOps);
        }
    }

    @Override
    public void submit(T payload) {
        create(payload).submit();
    }

    @Override
    public MessageSender<T> create(T payload) {
        return new DefaultMessageSender(payload);
    }

    private void submit(Message<T> message) {
        submissionStrategy.submit(queue, message);
    }

    private void submit(Message<T> message, String consumer) {
        submissionStrategy.submit(queue, message, consumer);
    }

    public void setRedisOps(RedisOps redisOps) {
        this.redisOps = redisOps;
    }

    public void setSubmissionStrategy(SubmissionStrategy submissionStrategy) {
        this.submissionStrategy = submissionStrategy;
    }

    public void setDefaultTimeToLive(long defaultTimeToLive) {
        this.defaultTimeToLive = defaultTimeToLive;
    }

    public void setDefaultTimeToLiveUnit(TimeUnit defaultTimeToLiveUnit) {
        this.defaultTimeToLiveUnit = defaultTimeToLiveUnit;
    }

    public void setQueue(MessageQueue queue) {
        this.queue = queue;
    }

    private class DefaultMessageSender implements MessageSender<T> {
        private T payload;
        private long timeToLive;
        private TimeUnit timeToLiveUnit;
        private String targetConsumer;

        private DefaultMessageSender(T payload) {
            this.payload = payload;
            this.timeToLive = defaultTimeToLive;
            this.timeToLiveUnit = defaultTimeToLiveUnit;
        }

        @Override
        public MessageSender<T> withTimeToLive(long time, TimeUnit unit) {
            this.timeToLive = time;
            this.timeToLiveUnit = unit;
            return this;
        }

        @Override
        public MessageSender<T> withTargetConsumer(String consumerId) {
            this.targetConsumer = consumerId;
            return this;
        }

        @Override
        public void submit() {
            long ttlSeconds = timeToLiveUnit.toSeconds(timeToLive);

            Message<T> message = new Message<T>();
            message.setCreation(Calendar.getInstance());
            message.setPayload(payload);
            message.setTimeToLiveSeconds(ttlSeconds);

            redisOps.addMessage(queue.getQueueName(), message);

            if (StringUtils.isNotEmpty(targetConsumer)) {
                MessageProducerImpl.this.submit(message, targetConsumer);
            } else {
                MessageProducerImpl.this.submit(message);
            }
        }
    }
}
