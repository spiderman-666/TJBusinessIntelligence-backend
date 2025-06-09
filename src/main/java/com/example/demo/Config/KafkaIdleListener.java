// KafkaIdleListener.java
package com.example.demo.Config;

import org.springframework.context.event.EventListener;
import org.springframework.kafka.event.ListenerContainerIdleEvent;
import org.springframework.stereotype.Component;

@Component
public class KafkaIdleListener {

    @EventListener
    public void handleIdleEvent(ListenerContainerIdleEvent event) {
        System.out.println("⚠️ Kafka 消费者空闲，无消息：" +
                "监听器ID=" + event.getListenerId() +
                ", topic=" + event.getTopicPartitions());
//        event.getContainer().pause();
//        System.out.println("⏸️ 已暂停 Kafka 消费者！");
    }
}
