package br.com.shema.crm.extraction.infrastructure;

import br.com.shema.crm.extraction.ExtractionRequested;
import br.com.shema.crm.shared.messaging.Topology;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exchange da leitura com IA e a fila (com DLQ) do leitor. */
@Configuration(proxyBeanMethods = false)
class ExtractionMessaging {

    @Bean
    Declarables extractionQueue() {
        return Topology.queue(
                ExtractionRequested.QUEUE, Topology.exchange(ExtractionRequested.EXCHANGE), ExtractionRequested.TYPE);
    }
}
