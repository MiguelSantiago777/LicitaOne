package br.com.shema.crm.contracts.infrastructure;

import br.com.shema.crm.contracts.ContractEvents;
import br.com.shema.crm.shared.messaging.Topology;
import org.springframework.amqp.core.Declarables;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exchange dos eventos de contrato. As filas são de quem consome (alertas e notificações, Fase 5). */
@Configuration(proxyBeanMethods = false)
class ContractMessaging {

    @Bean
    Declarables contractsExchange() {
        return new Declarables(Topology.exchange(ContractEvents.EXCHANGE));
    }
}
