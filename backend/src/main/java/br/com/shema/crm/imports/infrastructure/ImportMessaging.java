package br.com.shema.crm.imports.infrastructure;

import br.com.shema.crm.imports.ImportRequested;
import br.com.shema.crm.shared.messaging.Topology;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exchange das importações e a fila (com DLQ) do processador. */
@Configuration(proxyBeanMethods = false)
class ImportMessaging {

    @Bean
    Declarables importQueue() {
        return Topology.queue(ImportRequested.QUEUE, Topology.exchange(ImportRequested.EXCHANGE), ImportRequested.TYPE);
    }
}
