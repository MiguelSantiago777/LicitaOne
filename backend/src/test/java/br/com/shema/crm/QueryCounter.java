package br.com.shema.crm;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Conta os comandos SQL da thread atual (JPA e JdbcTemplate). Com o MockMvc, a requisição roda na thread do teste:
 * {@code start()} antes, {@code stop()} depois dá o número de consultas daquela requisição, sem o ruído dos
 * consumidores de fila. É assim que um N+1 vira teste (Fase 6).
 */
public final class QueryCounter {

    private static final Set<String> STATEMENTS = Set.of("prepareStatement", "createStatement", "prepareCall");

    private static final ThreadLocal<int[]> COUNT = new ThreadLocal<>();

    private QueryCounter() {}

    public static void start() {
        COUNT.set(new int[1]);
    }

    public static int stop() {
        int[] count = COUNT.get();
        COUNT.remove();
        return count == null ? 0 : count[0];
    }

    private static void increment() {
        int[] count = COUNT.get();
        if (count != null) {
            count[0]++;
        }
    }

    /** Embrulha o DataSource para contar os comandos de cada conexão. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        static BeanPostProcessor countingDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource dataSource)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            QueryCounter.class.getClassLoader(),
                            new Class<?>[] {DataSource.class},
                            forward(
                                    dataSource,
                                    (target, result, method) -> result instanceof Connection connection
                                            ? Proxy.newProxyInstance(
                                                    QueryCounter.class.getClassLoader(),
                                                    new Class<?>[] {Connection.class},
                                                    forward(connection, (c, r, m) -> {
                                                        if (STATEMENTS.contains(m)) {
                                                            increment();
                                                        }
                                                        return r;
                                                    }))
                                            : result));
                }
            };
        }

        private interface AfterCall {
            Object apply(Object target, Object result, String method);
        }

        private static InvocationHandler forward(Object target, AfterCall after) {
            return (proxy, method, args) -> {
                if (method.getName().equals("unwrap") || method.getName().equals("isWrapperFor")) {
                    return method.invoke(target, args);
                }
                try {
                    return after.apply(target, method.invoke(target, args), method.getName());
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            };
        }
    }
}
