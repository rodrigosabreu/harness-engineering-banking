# Banking OTEL Tracer — Observabilidade Padrão Itaú
# templates/BankingTracer.java

package br.com.itau.banking.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Tracer padronizado para domínios bancários (Itaú).
 * 
 * Convenções de nomenclatura:
 * - Span name: {operation_type}.{domain}.{operation}
 *   Ex: command.consignado.registrarAverbacao
 *       query.concilicacao.buscarLancamentos
 *       event.pix.liquidacaoRecebida
 *       saga.remuneracao.calcularFolha
 * 
 * Atributos obrigatórios (banking.*):
 * - banking.domain: consignado | conciliacao | remuneracao | pix | shared
 * - banking.operation_type: command | query | event | saga | batch
 * - banking.operation: nome da operação (ex: registrarAverbacao)
 * - banking.aggregate_id: ID do aggregate root (ex: averbacao-123)
 * - banking.correlation_id: ID de correlação end-to-end
 * - banking.causation_id: ID do comando que causou este evento
 * - banking.success: true/false
 * - banking.error_type: classe da exceção (se erro)
 * - banking.sla_compliant: true/false (para operações com SLA)
 * - banking.latency_ms: latência em ms
 * - banking.regulatory_check: true/false (se passou validação regulatória)
 * - banking.feature_flag: valor da feature flag ativa
 * - banking.version: versão do deploy
 * 
 * Uso:
 * 
 * @Autowired
 * private BankingTracer tracer;
 * 
 * public Averbacao registrar(AverbacaoCommand cmd) {
 *     return tracer.traceCommand("consignado", "registrarAverbacao", cmd.getAverbacaoId(), () -> {
 *         // lógica do comando
 *         return averbacaoService.registrar(cmd);
 *     });
 * }
 */
@Component
public class BankingTracer {
    
    private static final String BANKING_PREFIX = "banking.";
    
    // Attribute Keys (constantes para performance)
    private static final AttributeKey<String> DOMAIN = AttributeKey.stringKey(BANKING_PREFIX + "domain");
    private static final AttributeKey<String> OPERATION_TYPE = AttributeKey.stringKey(BANKING_PREFIX + "operation_type");
    private static final AttributeKey<String> OPERATION = AttributeKey.stringKey(BANKING_PREFIX + "operation");
    private static final AttributeKey<String> AGGREGATE_ID = AttributeKey.stringKey(BANKING_PREFIX + "aggregate_id");
    private static final AttributeKey<String> CORRELATION_ID = AttributeKey.stringKey(BANKING_PREFIX + "correlation_id");
    private static final AttributeKey<String> CAUSATION_ID = AttributeKey.stringKey(BANKING_PREFIX + "causation_id");
    private static final AttributeKey<Boolean> SUCCESS = AttributeKey.booleanKey(BANKING_PREFIX + "success");
    private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey(BANKING_PREFIX + "error_type");
    private static final AttributeKey<Boolean> SLA_COMPLIANT = AttributeKey.booleanKey(BANKING_PREFIX + "sla_compliant");
    private static final AttributeKey<Long> LATENCY_MS = AttributeKey.longKey(BANKING_PREFIX + "latency_ms");
    private static final AttributeKey<Boolean> REGULATORY_CHECK = AttributeKey.booleanKey(BANKING_PREFIX + "regulatory_check");
    private static final AttributeKey<String> FEATURE_FLAG = AttributeKey.stringKey(BANKING_PREFIX + "feature_flag");
    private static final AttributeKey<String> VERSION = AttributeKey.stringKey(BANKING_PREFIX + "version");
    private static final AttributeKey<String> TENANT = AttributeKey.stringKey(BANKING_PREFIX + "tenant"); // convênio/produto
    
    private final Tracer tracer;
    private final String serviceVersion;
    
    public BankingTracer() {
        this.tracer = GlobalOpenTelemetry.getTracer("br.com.itau.banking", "1.0.0");
        this.serviceVersion = System.getProperty("app.version", "unknown");
    }
    
    // =============================================================================
    // COMMAND TRACING (Write Operations)
    // =============================================================================
    
    /**
     * Traça execução de um Command (escrita).
     * 
     * @param domain Domínio bancário (consignado, conciliacao, remuneracao, pix)
     * @param operation Nome da operação (ex: registrarAverbacao)
     * @param aggregateId ID do aggregate root
     * @param fn Lógica do command
     * @return Resultado da execução
     */
    public <T> T traceCommand(String domain, String operation, String aggregateId, Supplier<T> fn) {
        return traceCommand(domain, operation, aggregateId, null, null, fn);
    }
    
    /**
     * Traça execução de um Command com correlation/causation IDs.
     */
    public <T> T traceCommand(String domain, String operation, String aggregateId,
                              String correlationId, String causationId, Supplier<T> fn) {
        return traceInternal(
            "command",
            domain,
            operation,
            aggregateId,
            correlationId,
            causationId,
            null,
            fn
        );
    }
    
    // =============================================================================
    // QUERY TRACING (Read Operations)
    // =============================================================================
    
    /**
     * Traça execução de uma Query (leitura).
     */
    public <T> T traceQuery(String domain, String operation, String aggregateId, Supplier<T> fn) {
        return traceInternal(
            "query",
            domain,
            operation,
            aggregateId,
            null,
            null,
            null,
            fn
        );
    }
    
    // =============================================================================
    // EVENT TRACING (Domain Events)
    // =============================================================================
    
    /**
     * Traça publicação/processamento de Domain Event.
     */
    public <T> T traceEvent(String domain, String eventType, String aggregateId,
                            String correlationId, String causationId, Supplier<T> fn) {
        return traceInternal(
            "event",
            domain,
            eventType,
            aggregateId,
            correlationId,
            causationId,
            null,
            fn
        );
    }
    
    // =============================================================================
    // SAGA TRACING (Distributed Transactions)
    // =============================================================================
    
    /**
     * Traça passo de uma Saga (transação distribuída com compensação).
     */
    public <T> T traceSagaStep(String domain, String stepName, String sagaId,
                               String correlationId, Supplier<T> fn) {
        return traceInternal(
            "saga",
            domain,
            stepName,
            sagaId,
            correlationId,
            null,
            Attributes.of(AttributeKey.stringKey(BANKING_PREFIX + "saga_id"), sagaId),
            fn
        );
    }
    
    // =============================================================================
    // BATCH/JOB TRACING
    // =============================================================================
    
    /**
     * Traça execução de job/batch (ex: conciliação noturna, cálculo folha).
     */
    public <T> T traceBatch(String domain, String jobName, Supplier<T> fn) {
        return traceInternal(
            "batch",
            domain,
            jobName,
            null,
            null,
            null,
            null,
            fn
        );
    }
    
    // =============================================================================
    // LIQUIDATION/SETTLEMENT TRACING (PIX, TED, Conciliação)
    // =============================================================================
    
    /**
     * Traça liquidação PIX/TED/DOC com validação de SLA.
     * 
     * @param liquidationId ID da liquidação (ex: pix-123)
     * @param status Status: RECEIVED, PROCESSING, SETTLED, FAILED, REJECTED
     * @param latency Latência da liquidação
     * @param slaThresholdMs Threshold de SLA em ms (ex: 10000 para PIX)
     */
    public void traceLiquidation(String liquidationId, String status, Duration latency, long slaThresholdMs) {
        Span span = tracer.spanBuilder("liquidity." + status.toLowerCase())
            .setAttribute(DOMAIN, "pix")
            .setAttribute(OPERATION_TYPE, "liquidation")
            .setAttribute(OPERATION, "traceLiquidation")
            .setAttribute(AGGREGATE_ID, liquidationId)
            .setAttribute(SUCCESS, "SETTLED".equals(status))
            .setAttribute(LATENCY_MS, latency.toMillis())
            .setAttribute(SLA_COMPLIANT, latency.toMillis() <= slaThresholdMs)
            .setAttribute(AttributeKey.stringKey(BANKING_PREFIX + "sla_threshold_ms"), slaThresholdMs)
            .setAttribute(AttributeKey.stringKey(BANKING_PREFIX + "liquidation_status"), status)
            .setAttribute(VERSION, serviceVersion)
            .setSpanKind(SpanKind.INTERNAL)
            .startScopedSpan(span -> {
                if (!"SETTLED".equals(status)) {
                    span.setAttribute(ERROR_TYPE, status);
                    span.recordException(new IllegalStateException("Liquidação não liquidada: " + status));
                }
            });
    }
    
    // =============================================================================
    // AUDIT TRAIL TRACING (Immutable Trail)
    // =============================================================================
    
    /**
     * Registra evento na trilha de auditoria imutável (SOX, Bacen, LGPD).
     */
    public void traceAudit(String aggregateId, String eventType, Object payload) {
        String payloadHash = hash(payload);
        
        Span span = tracer.spanBuilder("audit." + eventType)
            .setAttribute(DOMAIN, inferDomainFromEvent(eventType))
            .setAttribute(OPERATION_TYPE, "audit")
            .setAttribute(OPERATION, "traceAudit")
            .setAttribute(AGGREGATE_ID, aggregateId)
            .setAttribute(AttributeKey.stringKey(BANKING_PREFIX + "event_type"), eventType)
            .setAttribute(AttributeKey.stringKey(BANKING_PREFIX + "payload_hash"), payloadHash)
            .setAttribute(AttributeKey.booleanKey(BANKING_PREFIX + "immutable"), true)
            .setAttribute(AttributeKey.stringKey(BANKING_PREFIX + "compliance_frameworks"), "SOX,BACEN,LGPD")
            .setAttribute(VERSION, serviceVersion)
            .setSpanKind(SpanKind.INTERNAL)
            .startScopedSpan(span -> {});
    }
    
    // =============================================================================
    // REGULATORY CHECK TRACING
    // =============================================================================
    
    /**
     * Traça validação regulatória (OPA/Policy check).
     */
    public <T> T traceRegulatoryCheck(String domain, String policyName, Supplier<T> fn) {
        return traceInternal(
            "compliance",
            domain,
            "policy_check_" + policyName,
            null,
            null,
            null,
            Attributes.of(
                AttributeKey.stringKey(BANKING_PREFIX + "policy_name"), policyName,
                REGULATORY_CHECK, true
            ),
            fn
        );
    }
    
    // =============================================================================
    // FEATURE FLAG TRACING
    // =============================================================================
    
    /**
     * Traça execução sob feature flag específica.
     */
    public <T> T traceWithFeatureFlag(String domain, String operation, String aggregateId,
                                       String flagKey, String flagValue, Supplier<T> fn) {
        return traceInternal(
            "command",
            domain,
            operation,
            aggregateId,
            null,
            null,
            Attributes.of(FEATURE_FLAG, flagKey + "=" + flagValue),
            fn
        );
    }
    
    // =============================================================================
    // INTERNAL IMPLEMENTATION
    // =============================================================================
    
    private <T> T traceInternal(String operationType, String domain, String operation,
                                 String aggregateId, String correlationId, String causationId,
                                 Attributes extraAttributes, Supplier<T> fn) {
        
        String spanName = operationType + "." + domain + "." + operation;
        
        Span span = tracer.spanBuilder(spanName)
            .setAttribute(DOMAIN, domain)
            .setAttribute(OPERATION_TYPE, operationType)
            .setAttribute(OPERATION, operation)
            .setAttribute(VERSION, serviceVersion)
            .setSpanKind(SpanKind.INTERNAL);
        
        if (aggregateId != null) {
            span.setAttribute(AGGREGATE_ID, aggregateId);
        }
        if (correlationId != null) {
            span.setAttribute(CORRELATION_ID, correlationId);
        }
        if (causationId != null) {
            span.setAttribute(CAUSATION_ID, causationId);
        }
        if (extraAttributes != null) {
            span.setAllAttributes(extraAttributes);
        }
        
        long startTime = System.currentTimeMillis();
        
        try (Scope scope = span.makeCurrent()) {
            T result = fn.get();
            
            long latencyMs = System.currentTimeMillis() - startTime;
            span.setAttribute(SUCCESS, true);
            span.setAttribute(LATENCY_MS, latencyMs);
            
            return result;
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            span.setAttribute(SUCCESS, false);
            span.setAttribute(LATENCY_MS, latencyMs);
            span.setAttribute(ERROR_TYPE, e.getClass().getSimpleName());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
    
    // =============================================================================
    // HELPER METHODS
    // =============================================================================
    
    private String inferDomainFromEvent(String eventType) {
        String lower = eventType.toLowerCase();
        if (lower.contains("averbacao") || lower.contains("consignado") || lower.contains("margem")) {
            return "consignado";
        }
        if (lower.contains("concilia") || lower.contains("match") || lower.contains("lancamento")) {
            return "concilicacao";
        }
        if (lower.contains("remuneracao") || lower.contains("folha") || lower.contains("variavel")) {
            return "remuneracao";
        }
        if (lower.contains("pix") || lower.contains("ted") || lower.contains("doc") || lower.contains("liquid")) {
            return "pix";
        }
        return "shared";
    }
    
    private String hash(Object payload) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
            byte[] hash = md.digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString().substring(0, 16); // Primeiros 16 chars
        } catch (Exception e) {
            return "hash_error";
        }
    }
}