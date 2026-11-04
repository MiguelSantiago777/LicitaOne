package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.ExtractionRequested;
import br.com.shema.crm.extraction.OrderReadingRequested;
import br.com.shema.crm.shared.messaging.Topology;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exchange da leitura com IA e as filas (com DLQ) dos leitores de documento e de pedido. */
@Configuration(proxyBeanMethods = false)
class ExtractionMessaging {

    @Bean
    Declarables extractionQueue() {
        return Topology.queue(
                ExtractionRequested.QUEUE, Topology.exchange(ExtractionRequested.EXCHANGE), ExtractionRequested.TYPE);
    }

    @Bean
    Declarables orderReadingQueue() {
        return Topology.queue(
                OrderReadingRequested.QUEUE,
                Topology.exchange(ExtractionRequested.EXCHANGE),
                OrderReadingRequested.TYPE);
    }
}
