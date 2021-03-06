package ca.radiant3.redisq;

import java.util.Collection;

import javax.annotation.PostConstruct;

import ca.radiant3.redisq.consumer.MessageCallback;
import ca.radiant3.redisq.persistence.RedisOps;
import ca.radiant3.redisq.queuing.FIFOQueueDequeueStrategy;
import ca.radiant3.redisq.queuing.QueueDequeueStrategy;

public class RedisMessageQueue implements MessageQueue {

    private static final String DEFAULT_CONSUMER_ID = System.getenv("HOSTNAME");

    private RedisOps redisOps;

    private String queueName;

    private String defaultConsumerId = DEFAULT_CONSUMER_ID;

    private QueueDequeueStrategy queueDequeueStrategy;

    @PostConstruct
    public void initialize() {
        if (queueDequeueStrategy == null) {
            queueDequeueStrategy = new FIFOQueueDequeueStrategy(redisOps);
        }
    }

    @Override
    public String getQueueName() {
        return queueName;
    }

    @Override
    public Collection<String> getCurrentConsumerIds() {
        return redisOps.getRegisteredConsumers(queueName);
    }

    @Override
    public long getSize() {
        return getSizeForConsumer(getDefaultConsumerId());
    }

    @Override
    public long getSizeForConsumer(String consumerId) {
        Long size = redisOps.getQueueSizeForConsumer(queueName, consumerId);
        return (size == null) ? 0 : size;
    }

    @Override
    public void empty() {
        redisOps.emptyQueue(queueName);
    }

    @Override
    public String getDefaultConsumerId() {
        return defaultConsumerId;
    }

    @Override
    public void enqueue(Message<?> message, String... consumers) {
        redisOps.saveMessage(queueName, message);

        for (String consumer : consumers) {
            queueDequeueStrategy.enqueueMessage(queueName, consumer, message.getId());
        }
        // 移除过期消费者
        redisOps.removeExpireRegisteredConsumers(queueName);
    }

    @Override
    public void dequeue(String consumer, MessageCallback callback) {
        queueDequeueStrategy.dequeueNextMessage(queueName, consumer, callback);
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public void setRedisOps(RedisOps redisOps) {
        this.redisOps = redisOps;
    }

    public void setDefaultConsumerId(String defaultConsumerId) {
        this.defaultConsumerId = defaultConsumerId;
    }

    public void setQueueDequeueStrategy(QueueDequeueStrategy queueDequeueStrategy) {
        this.queueDequeueStrategy = queueDequeueStrategy;
    }
}
