package br.com.shema.crm.shared.messaging;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reprocessamento da DLQ (procedimento em docs/operacao/mensageria.md): mostra quantas mensagens estão paradas em
 * cada DLQ e devolve as mensagens à fila de origem, depois que a causa da falha foi corrigida.
 */
@RestController
@RequestMapping("/api/v1/admin/dead-letters")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "admin", description = "Operação")
class DeadLetterController {

    record DeadLetterQueueResponse(String queue, String deadLetterQueue, long messages) {}

    record ReplayResponse(String queue, int replayed) {}

    private final AmqpAdmin admin;
    private final RabbitTemplate rabbit;
    private final List<String> queues;

    DeadLetterController(AmqpAdmin admin, RabbitTemplate rabbit, List<Declarables> declarables) {
        this.admin = admin;
        this.rabbit = rabbit;
        this.queues = declarables.stream()
                .flatMap(d -> d.getDeclarablesByType(Queue.class).stream())
                .map(Queue::getName)
                .filter(name -> name.endsWith(Topology.DLQ_SUFFIX))
                .map(name -> name.substring(0, name.length() - Topology.DLQ_SUFFIX.length()))
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    @GetMapping
    @Operation(summary = "Filas com DLQ e quantas mensagens estão paradas", operationId = "deadLetterQueues")
    List<DeadLetterQueueResponse> list() {
        return queues.stream()
                .map(q -> new DeadLetterQueueResponse(q, q + Topology.DLQ_SUFFIX, count(q + Topology.DLQ_SUFFIX)))
                .toList();
    }

    @PostMapping("/{queue}/replay")
    @Operation(summary = "Devolve as mensagens da DLQ à fila de origem", operationId = "replayDeadLetters")
    ReplayResponse replay(@PathVariable String queue, @RequestParam(defaultValue = "100") int max) {
        if (!queues.contains(queue)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fila desconhecida: " + queue);
        }
        int replayed = 0;
        Message message;
        while (replayed < Math.clamp(max, 1, 1000) && (message = rabbit.receive(queue + Topology.DLQ_SUFFIX)) != null) {
            // O exchange padrão entrega direto na fila com o nome da routing key
            message.getMessageProperties().getHeaders().remove("x-death");
            rabbit.send("", queue, message);
            replayed++;
        }
        return new ReplayResponse(queue, replayed);
    }

    private long count(String queue) {
        Properties props = admin.getQueueProperties(queue);
        if (props == null) {
            return 0;
        }
        Object count = props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        return count instanceof Number n ? n.longValue() : 0;
    }
}
