---
name: shared-banking
description: "Utilitários compartilhados para domínios bancários — LGPD Anonymizer, Vault Config, Outbox Pattern, Saga Orchestrator, Validadores CPF/CNPJ, Juros, PIX."
version: "1.0.0"
category: banking-domain
requires: ["harness-engineering-banking"]
---

# Skill: Shared Banking Utilities

> **Utilitários transversais** para todos os domínios bancários (consignado, conciliação, remuneração, PIX, etc.).
> **Princípio**: DRY + Segurança + Conformidade + Observabilidade.

---

## 1. LGPD Anonymizer (Dados Sensíveis)

### 1.1 Anotação para Serialização Segura

```java
// shared/annotation/Anonymize.java
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@JsonSerialize(using = AnonymizeSerializer.class)
public @interface Anonymize {
    AnonymizeType type() default AnonymizeType.CPF_CNPJ;
    boolean preserveFormat() default true;  // Mantém máscara (xxx.xxx.xxx-xx)
    int preserveChars() default 4;          // Mantém últimos N chars
}

public enum AnonymizeType {
    CPF_CNPJ,           // 123.456.789-** ou 12.345.678/****-**
    NOME,               // João S*** (primeiro + última letra)
    EMAIL,              // jo***@exemplo.com
    TELEFONE,           // (11) 9****-1234
    CONTA_AGENCIA,      // 1234-** / 56789-*
    CARTAO,             // 1234 **** **** 5678
    PIX_KEY,            // Chave PIX anonimizada
    SALARIO,            // Faixa: "Até 5k", "5k-10k", "10k-20k", "Acima 20k"
    ENDERECO,           // Rua ****, 123 - Bairro - Cidade/UF
    GENERIC             // Primeiros 2 + últimos 2 chars
}
```

### 1.2 Serializer Customizado

```java
// shared/serializer/AnonymizeSerializer.java
public class AnonymizeSerializer extends JsonSerializer<Object> {
    
    private static final Map<AnonymizeType, Function<String, String>> ANONYMIZERS = Map.of(
        AnonymizeType.CPF_CNPJ, SharedBankingUtils::anonymizeCpfCnpj,
        AnonymizeType.NOME, SharedBankingUtils::anonymizeNome,
        AnonymizeType.EMAIL, SharedBankingUtils::anonymizeEmail,
        AnonymizeType.TELEFONE, SharedBankingUtils::anonymizeTelefone,
        AnonymizeType.CONTA_AGENCIA, SharedBankingUtils::anonymizeContaAgencia,
        AnonymizeType.CARTAO, SharedBankingUtils::anonymizeCartao,
        AnonymizeType.PIX_KEY, SharedBankingUtils::anonymizePixKey,
        AnonymizeType.SALARIO, SharedBankingUtils::anonymizeSalario,
        AnonymizeType.ENDERECO, SharedBankingUtils::anonymizeEndereco,
        AnonymizeType.GENERIC, SharedBankingUtils::anonymizeGeneric
    );
    
    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        
        Anonymize annotation = provider.getAnnotation(Anonymize.class);
        if (annotation == null) {
            gen.writeObject(value);
            return;
        }
        
        String str = value.toString();
        Function<String, String> anonymizer = ANONYMIZERS.getOrDefault(
            annotation.type(), SharedBankingUtils::anonymizeGeneric);
        
        String anonymized = annotation.preserveFormat() 
            ? anonymizer.apply(str) 
            : anonymizer.apply(str).replaceAll("[^a-zA-Z0-9]", "");
        
        gen.writeString(anonymized);
    }
}
```

### 1.3 Utilitários de Anonimização

```java
// shared/util/SharedBankingUtils.java
public final class SharedBankingUtils {
    
    private static final Pattern CPF_PATTERN = Pattern.compile("\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}");
    private static final Pattern CNPJ_PATTERN = Pattern.compile("\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^([^@]+)@(.+)$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\(?(\\d{2})\\)?[\\s-]?(\\d{4,5})[\\s-]?(\\d{4})$");
    private static final Pattern CARD_PATTERN = Pattern.compile("\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}");
    
    public static String anonymizeCpfCnpj(String input) {
        if (input == null) return null;
        String digits = input.replaceAll("\\D", "");
        if (digits.length() == 11) { // CPF
            return String.format("%s.***.%s-%s", 
                digits.substring(0, 3), digits.substring(6, 9), digits.substring(9));
        } else if (digits.length() == 14) { // CNPJ
            return String.format("%s.%s.%s/%s-%s",
                digits.substring(0, 2), digits.substring(2, 5), digits.substring(5, 8),
                digits.substring(8, 12), digits.substring(12));
        }
        return maskLast(digits, 4);
    }
    
    public static String anonymizeNome(String input) {
        if (input == null) return null;
        String[] parts = input.trim().split("\\s+");
        if (parts.length == 1) return parts[0].charAt(0) + "***";
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + "***";
    }
    
    public static String anonymizeEmail(String input) {
        if (input == null) return null;
        Matcher m = EMAIL_PATTERN.matcher(input);
        if (m.matches()) {
            String local = m.group(1);
            String domain = m.group(2);
            String anonLocal = local.length() <= 2 ? "**" : local.charAt(0) + "**" + local.charAt(local.length() - 1);
            return anonLocal + "@" + domain;
        }
        return "***@" + input.split("@")[1];
    }
    
    public static String anonymizeTelefone(String input) {
        if (input == null) return null;
        Matcher m = PHONE_PATTERN.matcher(input.replaceAll("\\D", ""));
        if (m.matches()) {
            return String.format("(%s) %s****-%s", m.group(1), m.group(2).substring(0, 1), m.group(3));
        }
        return maskLast(input.replaceAll("\\D", ""), 4);
    }
    
    public static String anonymizeContaAgencia(String input) {
        if (input == null) return null;
        // Formato: agencia/conta ou agencia conta
        String[] parts = input.split("[/\\s-]+");
        if (parts.length >= 2) {
            return String.format("%s-*/%s-*", 
                maskLast(parts[0], 1), maskLast(parts[1], 1));
        }
        return maskLast(input, 2);
    }
    
    public static String anonymizeCartao(String input) {
        if (input == null) return null;
        String digits = input.replaceAll("\\D", "");
        if (digits.length() >= 13) {
            return String.format("%s **** **** %s", 
                digits.substring(0, 4), digits.substring(digits.length() - 4));
        }
        return maskLast(digits, 4);
    }
    
    public static String anonymizePixKey(String input) {
        if (input == null) return null;
        // CPF, CNPJ, EMAIL, PHONE, EVP (aleatória)
        if (CPF_PATTERN.matcher(input).matches() || CNPJ_PATTERN.matcher(input).matches()) {
            return anonymizeCpfCnpj(input);
        } else if (EMAIL_PATTERN.matcher(input).matches()) {
            return anonymizeEmail(input);
        } else if (PHONE_PATTERN.matcher(input.replaceAll("\\D", "")).matches()) {
            return anonymizeTelefone(input);
        } else if (input.length() == 32) { // EVP (UUID-like)
            return input.substring(0, 8) + "****" + input.substring(28);
        }
        return maskLast(input, 4);
    }
    
    public static String anonymizeSalario(BigDecimal salario) {
        if (salario == null) return null;
        double val = salario.doubleValue();
        if (val <= 5000) return "Até 5k";
        if (val <= 10000) return "5k-10k";
        if (val <= 20000) return "10k-20k";
        if (val <= 50000) return "20k-50k";
        return "Acima 50k";
    }
    
    public static String anonymizeEndereco(String input) {
        if (input == null) return null;
        // Rua X, 123 - Bairro - Cidade/UF
        return input.replaceAll("\\d+", "***")
            .replaceAll("(?i)(rua|av|travessa|praça)\\s+[^,]+", "$1 ***");
    }
    
    public static String anonymizeGeneric(String input) {
        if (input == null || input.length() <= 4) return "****";
        return input.substring(0, 2) + "***" + input.substring(input.length() - 2);
    }
    
    private static String maskLast(String input, int preserve) {
        if (input == null || input.length() <= preserve) return "****";
        return input.substring(0, input.length() - preserve) + "*".repeat(input.length() - preserve);
    }
}
```

### 1.4 Uso em Domain Events (LGPD Compliant)

```java
// domain/event/ConciliacaoExecutada.java
@Value
public class ConciliacaoExecutada implements DomainEvent {
    String eventType = "ConciliacaoExecutada";
    ConciliacaoId aggregateId;
    TipoConcilicao tipo;
    StatusConciliacao status;
    
    @Anonymize(type = AnonymizeType.CPF_CNPJ)
    String cpfCnpjPagador;
    
    @Anonymize(type = AnonymizeType.CPF_CNPJ)
    String cpfCnpjRecebedor;
    
    @Anonymize(type = AnonymizeType.CONTA_AGENCIA)
    String contaOrigem;
    
    @Anonymize(type = AnonymizeType.CONTA_AGENCIA)
    String contaDestino;
    
    @Anonymize(type = AnonymizeType.PIX_KEY)
    String chavePix;
    
    BigDecimal valor;
    Instant timestamp = Instant.now();
    String correlationId;
    String causationId;
}
```

---

## 2. Vault Configuration (Zero Secrets no Código)

### 2.1 Configuration Properties

```java
// shared/config/VaultConfig.java
@Configuration
@ConfigurationProperties(prefix = "vault")
@Validated
public class VaultConfig {
    
    private String uri = "https://vault.itau.com.br";
    private String namespace = "banking";
    private String authPath = "kubernetes";
    private String role = "banking-agent";
    private int leaseTtlSeconds = 3600;
    private int renewThresholdSeconds = 300;
    private Map<String, SecretPath> secrets = new HashMap<>();
    
    @Getter @Setter
    public static class SecretPath {
        private String path;           // Ex: secret/banking/consignado/db
        private String key;            // Chave dentro do secret (ex: password)
        private Class<?> type;         // String.class, Integer.class, etc.
        private Duration cacheTtl;     // TTL do cache local
    }
    
    // Getters/Setters...
}
```

### 2.2 Vault Client (Spring Vault + Cache)

```java
// shared/vault/VaultSecretProvider.java
@Component
@RequiredArgsConstructor
public class VaultSecretProvider {
    
    private final VaultTemplate vaultTemplate;
    private final VaultConfig config;
    private final Cache<String, Object> cache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build();
    
    public <T> T getSecret(String secretName, Class<T> type) {
        return cache.get(secretName, key -> {
            VaultConfig.SecretPath secretPath = config.getSecrets().get(key);
            if (secretPath == null) {
                throw new IllegalArgumentException("Secret não configurado: " + key);
            }
            
            VaultResponse response = vaultTemplate.read(secretPath.getPath());
            if (response == null || response.getData() == null) {
                throw new IllegalStateException("Secret não encontrado no Vault: " + secretPath.getPath());
            }
            
            Object value = response.getData().get(secretPath.getKey());
            if (value == null) {
                throw new IllegalStateException("Chave não encontrada no secret: " + secretPath.getKey());
            }
            
            return convert(value, secretPath.getType());
        });
    }
    
    public void renewLeases() {
        // Renovação proativa de leases próximos do vencimento
        // Implementação depende da estratégia de auth (Kubernetes, AppRole, etc.)
    }
}
```

### 2.3 Uso em Configuration Classes

```java
// infrastructure/persistence/DataSourceConfig.java
@Configuration
@RequiredArgsConstructor
public class DataSourceConfig {
    
    private final VaultSecretProvider vault;
    
    @Bean
    @Primary
    public DataSource dataSource() {
        String url = vault.getSecret("database/url", String.class);
        String username = vault.getSecret("database/username", String.class);
        String password = vault.getSecret("database/password", String.class);
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setConnectionTestQuery("SELECT 1");
        
        return new HikariDataSource(config);
    }
}
```

---

## 3. Outbox Pattern (Garantia de Publicação)

### 3.1 Entidade Outbox

```java
// shared/outbox/OutboxEvent.java
@Entity
@Table(name = "outbox_event", indexes = {
    @Index(name = "idx_outbox_unpublished", columnList = "published, created_at"),
    @Index(name = "idx_outbox_aggregate", columnList = "aggregate_id")
})
public class OutboxEvent {
    
    @Id
    @GeneratedValue
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String aggregateId;
    
    @Column(nullable = false, length = 100)
    private String eventType;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON
    
    @Column(nullable = false, length = 100)
    private String topic;
    
    @Column(nullable = false, length = 100)
    private String key; // Partition key
    
    @Column(nullable = false)
    private Integer priority = 0; // Maior = mais urgente
    
    @Column(nullable = false)
    private Boolean published = false;
    
    @Column(nullable = false)
    private Integer retryCount = 0;
    
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    
    private Instant publishedAt;
    private String lastError;
    
    // Factory
    public static OutboxEvent fromDomainEvent(DomainEvent event, String topic) {
        OutboxEvent outbox = new OutboxEvent();
        outbox.aggregateId = event.getAggregateId().value();
        outbox.eventType = event.getEventType();
        outbox.payload = toJson(event);
        outbox.topic = topic;
        outbox.key = event.getAggregateId().value(); // Partition by aggregate
        outbox.priority = calculatePriority(event);
        return outbox;
    }
    
    private static int calculatePriority(DomainEvent event) {
        return switch (event.getEventType()) {
            case "ConciliacaoExecutada", "DivergenciaDetectada" -> 10; // Tempo real
            case "AverbacaoRegistrada", "RecursosLiberados" -> 8;
            case "CalculoGerado", "CalculoOficializado" -> 5;
            default -> 1;
        };
    }
}
```

### 3.2 Publisher com Retry + Dead Letter

```java
// shared/outbox/OutboxPublisher.java
@Component
@RequiredArgsConstructor
public class OutboxPublisher {
    
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BankingTracer tracer;
    private final MeterRegistry meterRegistry;
    
    @Scheduled(fixedDelay = 100) // 10x/seg
    @Transactional
    public void publish() {
        List<OutboxEvent> events = outboxRepository
            .findTop500ByPublishedFalseOrderByPriorityDescCreatedAtAsc();
        
        for (OutboxEvent event : events) {
            publishEvent(event);
        }
    }
    
    private void publishEvent(OutboxEvent event) {
        tracer.traceEvent("shared", "outbox_publish", event.getAggregateId(), () -> {
            try {
                kafkaTemplate.send(event.getTopic(), event.getKey(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            event.setPublished(true);
                            event.setPublishedAt(Instant.now());
                            event.setRetryCount(0);
                            event.setLastError(null);
                            meterRegistry.counter("outbox.published", "topic", event.getTopic()).increment();
                        } else {
                            handleFailure(event, ex);
                        }
                    });
            } catch (Exception ex) {
                handleFailure(event, ex);
            }
            return null;
        });
    }
    
    private void handleFailure(OutboxEvent event, Exception ex) {
        event.setRetryCount(event.getRetryCount() + 1);
        event.setLastError(ex.getMessage());
        
        if (event.getRetryCount() >= 10) {
            // Move para Dead Letter Queue
            moveToDeadLetter(event, ex);
            meterRegistry.counter("outbox.dead_letter", "topic", event.getTopic()).increment();
        } else {
            // Backoff exponencial
            event.setPublished(false); // Será retentado
            meterRegistry.counter("outbox.retry", "topic", event.getTopic(), "attempt", String.valueOf(event.getRetryCount())).increment();
        }
    }
    
    private void moveToDeadLetter(OutboxEvent event, Exception ex) {
        DeadLetterEvent dlq = DeadLetterEvent.builder()
            .originalEventId(event.getId())
            .topic(event.getTopic())
            .payload(event.getPayload())
            .error(ex.getMessage())
            .retryCount(event.getRetryCount())
            .failedAt(Instant.now())
            .build();
        
        deadLetterRepository.save(dlq);
        event.setPublished(true); // Remove da fila principal
        event.setPublishedAt(Instant.now());
        
        alertService.fire(Alert.builder()
            .title("Outbox Dead Letter")
            .description("Evento movido para DLQ após 10 tentativas: " + event.getEventType())
            .severity(AlertSeverity.CRITICAL)
            .build());
    }
}
```

---

## 4. Saga Orchestrator (Transações Distribuídas)

### 4.1 Definição de Saga

```java
// shared/saga/SagaDefinition.java
public interface SagaDefinition {
    String getSagaId();
    List<SagaStep> getSteps();
    
    @Value
    @Builder
    class SagaStep {
        String name;                    // Ex: "reservar_margem", "confirmar_averbacao"
        String compensationName;        // Ex: "liberar_margem", "cancelar_averbacao"
        Class<? extends SagaCommand> commandClass;
        Class<? extends SagaCommand> compensationClass;
        Duration timeout;               // Timeout do step
        boolean retryable;              // Se pode retry automático
        int maxRetries;                 // Max retries antes de compensar
    }
}
```

### 4.2 Exemplo: Saga Averbação Consignado

```java
// shared/saga/ConsignadoSaga.java
@Component
public class ConsignadoSaga implements SagaDefinition {
    
    @Override
    public String getSagaId() {
        return "consignado-averbacao";
    }
    
    @Override
    public List<SagaStep> getSteps() {
        return List.of(
            SagaStep.builder()
                .name("validar_margem")
                .compensationName("liberar_margem_reservada")
                .commandClass(ValidarMargemCommand.class)
                .compensationClass(LiberarMargemCommand.class)
                .timeout(Duration.ofSeconds(30))
                .retryable(true)
                .maxRetries(3)
                .build(),
            
            SagaStep.builder()
                .name("registrar_averbacao")
                .compensationName("cancelar_averbacao")
                .commandClass(RegistrarAverbacaoCommand.class)
                .compensationClass(CancelarAverbacaoCommand.class)
                .timeout(Duration.ofSeconds(60))
                .retryable(true)
                .maxRetries(2)
                .build(),
            
            SagaStep.builder()
                .name("confirmar_averbacao_convenio")
                .compensationName("reverter_confirmacao")
                .commandClass(ConfirmarAverbacaoCommand.class)
                .compensationClass(ReverterConfirmacaoCommand.class)
                .timeout(Duration.ofMinutes(5))
                .retryable(true)
                .maxRetries(5)
                .build(),
            
            SagaStep.builder()
                .name("liberar_recursos")
                .compensationName("estornar_recursos")
                .commandClass(LiberarRecursosCommand.class)
                .compensationClass(EstornarRecursosCommand.class)
                .timeout(Duration.ofSeconds(30))
                .retryable(true)
                .maxRetries(3)
                .build()
        );
    }
}
```

### 4.3 Saga Orchestrator Engine

```java
// shared/saga/SagaOrchestrator.java
@Component
@RequiredArgsConstructor
public class SagaOrchestrator {
    
    private final CommandBus commandBus;
    private final SagaStateRepository stateRepository;
    private final BankingTracer tracer;
    private final MeterRegistry meterRegistry;
    
    public <T> T execute(SagaDefinition definition, T initialCommand) {
        String sagaInstanceId = UUID.randomUUID().toString();
        
        return tracer.traceSaga(definition.getSagaId(), sagaInstanceId, () -> {
            SagaState state = SagaState.builder()
                .sagaId(definition.getSagaId())
                .instanceId(sagaInstanceId)
                .status(SagaStatus.STARTED)
                .currentStep(0)
                .payload(serialize(initialCommand))
                .createdAt(Instant.now())
                .build();
            
            stateRepository.save(state);
            
            try {
                for (int i = 0; i < definition.getSteps().size(); i++) {
                    SagaStep step = definition.getSteps().get(i);
                    state.setCurrentStep(i);
                    stateRepository.save(state);
                    
                    executeStep(step, state);
                }
                
                state.setStatus(SagaStatus.COMPLETED);
                state.setCompletedAt(Instant.now());
                stateRepository.save(state);
                
                meterRegistry.counter("saga.completed", "saga", definition.getSagaId()).increment();
                return deserialize(state.getPayload());
                
            } catch (Exception ex) {
                compensate(definition, state, ex);
                throw ex;
            }
        });
    }
    
    private void executeStep(SagaStep step, SagaState state) {
        tracer.traceSagaStep(step.getName(), state.getInstanceId(), () -> {
            int attempts = 0;
            while (true) {
                try {
                    Object command = deserialize(state.getPayload(), step.getCommandClass());
                    Object result = commandBus.send(command);
                    state.setPayload(serialize(result));
                    stateRepository.save(state);
                    meterRegistry.counter("saga.step.success", "step", step.getName()).increment();
                    return;
                } catch (Exception ex) {
                    attempts++;
                    if (!step.isRetryable() || attempts >= step.getMaxRetries()) {
                        throw new SagaStepFailedException(step.getName(), ex);
                    }
                    // Backoff exponencial
                    Thread.sleep(Duration.ofSeconds((long) Math.pow(2, attempts)).toMillis());
                }
            }
        });
    }
    
    private void compensate(SagaDefinition definition, SagaState state, Exception cause) {
        state.setStatus(SagaStatus.COMPENSATING);
        state.setError(cause.getMessage());
        stateRepository.save(state);
        
        // Executa compensações em ordem reversa
        for (int i = state.getCurrentStep(); i >= 0; i--) {
            SagaStep step = definition.getSteps().get(i);
            try {
                tracer.traceSagaCompensation(step.getCompensationName(), state.getInstanceId(), () -> {
                    Object compensationCommand = createCompensationCommand(step, state);
                    commandBus.send(compensationCommand);
                    meterRegistry.counter("saga.compensation.success", "step", step.getCompensationName()).increment();
                });
            } catch (Exception ex) {
                // Log erro mas continua compensando
                log.error("Falha na compensação {}: {}", step.getCompensationName(), ex.getMessage());
                meterRegistry.counter("saga.compensation.failed", "step", step.getCompensationName()).increment();
                
                // Alerta crítico
                alertService.fire(Alert.builder()
                    .title("Saga Compensation Failed")
                    .description("Saga: " + definition.getSagaId() + " | Step: " + step.getCompensationName())
                    .severity(AlertSeverity.CRITICAL)
                    .build());
            }
        }
        
        state.setStatus(SagaStatus.FAILED);
        state.setCompletedAt(Instant.now());
        stateRepository.save(state);
        meterRegistry.counter("saga.failed", "saga", definition.getSagaId()).increment();
    }
}
```

---

## 5. Validadores Comuns (CPF, CNPJ, PIX, Juros)

### 5.1 CPF/CNPJ Validator

```java
// shared/validator/DocumentValidator.java
public final class DocumentValidator {
    
    public static boolean isValidCpf(String cpf) {
        if (cpf == null) return false;
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11) return false;
        if (digits.chars().distinct().count() == 1) return false; // Todos iguais
        
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * (10 - i);
        }
        int dv1 = 11 - (sum % 11);
        if (dv1 >= 10) dv1 = 0;
        if (dv1 != Character.getNumericValue(digits.charAt(9))) return false;
        
        sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * (11 - i);
        }
        int dv2 = 11 - (sum % 11);
        if (dv2 >= 10) dv2 = 0;
        return dv2 == Character.getNumericValue(digits.charAt(10));
    }
    
    public static boolean isValidCnpj(String cnpj) {
        if (cnpj == null) return false;
        String digits = cnpj.replaceAll("\\D", "");
        if (digits.length() != 14) return false;
        if (digits.chars().distinct().count() == 1) return false;
        
        int[] weights1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] weights2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * weights1[i];
        }
        int dv1 = 11 - (sum % 11);
        if (dv1 >= 10) dv1 = 0;
        if (dv1 != Character.getNumericValue(digits.charAt(12))) return false;
        
        sum = 0;
        for (int i = 0; i < 13; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * weights2[i];
        }
        int dv2 = 11 - (sum % 11);
        if (dv2 >= 10) dv2 = 0;
        return dv2 == Character.getNumericValue(digits.charAt(13));
    }
    
    public static String formatCpf(String cpf) {
        String digits = cpf.replaceAll("\\D", "");
        return String.format("%s.%s.%s-%s",
            digits.substring(0, 3), digits.substring(3, 6),
            digits.substring(6, 9), digits.substring(9));
    }
    
    public static String formatCnpj(String cnpj) {
        String digits = cnpj.replaceAll("\\D", "");
        return String.format("%s.%s.%s/%s-%s",
            digits.substring(0, 2), digits.substring(2, 5),
            digits.substring(5, 8), digits.substring(8, 12),
            digits.substring(12));
    }
}
```

### 5.2 PIX Key Validator

```java
// shared/validator/PixKeyValidator.java
public final class PixKeyValidator {
    
    private static final Pattern CPF_PATTERN = Pattern.compile("^\\d{11}$");
    private static final Pattern CNPJ_PATTERN = Pattern.compile("^\\d{14}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@]+@[^@]+\\.[^@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?55\\d{10,11}$");
    private static final Pattern EVP_PATTERN = Pattern.compile("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$", Pattern.CASE_INSENSITIVE);
    
    public enum PixKeyType { CPF, CNPJ, EMAIL, PHONE, EVP }
    
    public static ValidationResult validate(String key) {
        if (key == null) return ValidationResult.invalid("Chave PIX não pode ser nula");
        
        String clean = key.trim();
        
        if (CPF_PATTERN.matcher(clean).matches()) {
            if (!DocumentValidator.isValidCpf(clean)) {
                return ValidationResult.invalid("CPF inválido");
            }
            return ValidationResult.valid(PixKeyType.CPF);
        }
        
        if (CNPJ_PATTERN.matcher(clean).matches()) {
            if (!DocumentValidator.isValidCnpj(clean)) {
                return ValidationResult.invalid("CNPJ inválido");
            }
            return ValidationResult.valid(PixKeyType.CNPJ);
        }
        
        if (EMAIL_PATTERN.matcher(clean).matches()) {
            return ValidationResult.valid(PixKeyType.EMAIL);
        }
        
        if (PHONE_PATTERN.matcher(clean).matches()) {
            return ValidationResult.valid(PixKeyType.PHONE);
        }
        
        if (EVP_PATTERN.matcher(clean).matches()) {
            return ValidationResult.valid(PixKeyType.EVP);
        }
        
        return ValidationResult.invalid("Formato de chave PIX inválido");
    }
    
    @Value
    @Builder
    public static class ValidationResult {
        boolean valid;
        PixKeyType type;
        String error;
        
        public static ValidationResult valid(PixKeyType type) {
            return ValidationResult.builder().valid(true).type(type).build();
        }
        public static ValidationResult invalid(String error) {
            return ValidationResult.builder().valid(false).error(error).build();
        }
    }
}
```

### 5.3 Calculadora de Juros (BigDecimal Precision)

```java
// shared/financial/InterestCalculator.java
public final class InterestCalculator {
    
    private static final int SCALE = 10; // Precisão interna
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    
    // Juros Simples: J = C * i * t
    public static BigDecimal jurosSimples(BigDecimal capital, BigDecimal taxa, int dias) {
        validatePositive(capital, "capital");
        validateNonNegative(taxa, "taxa");
        validatePositiveDays(dias);
        
        BigDecimal t = BigDecimal.valueOf(dias).divide(BigDecimal.valueOf(360), SCALE, RoundingMode.HALF_UP);
        return capital.multiply(taxa).multiply(t).setScale(2, RoundingMode.HALF_UP);
    }
    
    // Juros Compostos: M = C * (1 + i)^t
    public static BigDecimal jurosCompostos(BigDecimal capital, BigDecimal taxa, int periodos) {
        validatePositive(capital, "capital");
        validateNonNegative(taxa, "taxa");
        if (periodos <= 0) throw new IllegalArgumentException("Períodos deve ser > 0");
        
        BigDecimal umMaisI = BigDecimal.ONE.add(taxa);
        BigDecimal fator = umMaisI.pow(periodos);
        return capital.multiply(fator).setScale(2, RoundingMode.HALF_UP);
    }
    
    // TIR (Taxa Interna de Retorno) - Newton-Raphson
    public static BigDecimal tir(List<BigDecimal> fluxos, BigDecimal guess) {
        if (fluxos == null || fluxos.isEmpty()) throw new IllegalArgumentException("Fluxos vazios");
        
        BigDecimal x = guess != null ? guess : BigDecimal.valueOf(0.1);
        BigDecimal tolerance = BigDecimal.valueOf(1e-10);
        int maxIter = 1000;
        
        for (int i = 0; i < maxIter; i++) {
            BigDecimal fx = npv(fluxos, x);
            BigDecimal fpx = npvDerivative(fluxos, x);
            
            if (fpx.compareTo(BigDecimal.ZERO) == 0) break;
            
            BigDecimal xNew = x.subtract(fx.divide(fpx, SCALE, RoundingMode.HALF_UP));
            
            if (xNew.subtract(x).abs().compareTo(tolerance) < 0) {
                return xNew.setScale(6, RoundingMode.HALF_UP);
            }
            x = xNew;
        }
        return x.setScale(6, RoundingMode.HALF_UP);
    }
    
    private static BigDecimal npv(List<BigDecimal> fluxos, BigDecimal rate) {
        BigDecimal npv = BigDecimal.ZERO;
        BigDecimal onePlusRate = BigDecimal.ONE.add(rate);
        for (int i = 0; i < fluxos.size(); i++) {
            npv = npv.add(fluxos.get(i).divide(BigDecimal.ONE.add(rate).pow(i), SCALE, RoundingMode.HALF_UP));
        }
        return npv;
    }
    
    private static BigDecimal npvDerivative(List<BigDecimal> fluxos, BigDecimal rate) {
        BigDecimal deriv = BigDecimal.ZERO;
        for (int i = 1; i < fluxos.size(); i++) {
            deriv = deriv.add(fluxos.get(i).multiply(BigDecimal.valueOf(i))
                .divide(BigDecimal.ONE.add(rate).pow(i + 1), SCALE, RoundingMode.HALF_UP));
        }
        return deriv.negate();
    }
    
    // VPL (Valor Presente Líquido)
    public static BigDecimal vpl(List<BigDecimal> fluxos, BigDecimal taxa) {
        return npv(fluxos, taxa);
    }
    
    // Price (Tabela Price - parcelas fixas)
    public static BigDecimal parcelaPrice(BigDecimal principal, BigDecimal taxa, int periodos) {
        validatePositive(principal, "principal");
        validateNonNegative(taxa, "taxa");
        if (periodos <= 0) throw new IllegalArgumentException("Períodos > 0");
        
        if (taxa.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(periodos), 2, RoundingMode.HALF_UP);
        }
        
        BigDecimal umMaisI = BigDecimal.ONE.add(taxa);
        BigDecimal fator = umMaisI.pow(periodos);
        BigDecimal numerador = principal.multiply(taxa).multiply(fator);
        BigDecimal denominador = fator.subtract(BigDecimal.ONE);
        
        return numerador.divide(denominador, 2, RoundingMode.HALF_UP);
    }
    
    // SAC (Sistema de Amortização Constante)
    public static List<BigDecimal> parcelasSac(BigDecimal principal, BigDecimal taxa, int periodos) {
        validatePositive(principal, "principal");
        validateNonNegative(taxa, "taxa");
        if (periodos <= 0) throw new IllegalArgumentException("Períodos > 0");
        
        BigDecimal amortizacao = principal.divide(BigDecimal.valueOf(periodos), 2, RoundingMode.HALF_UP);
        List<BigDecimal> parcelas = new ArrayList<>();
        BigDecimal saldoDevedor = principal;
        
        for (int i = 0; i < periodos; i++) {
            BigDecimal juros = saldoDevedor.multiply(taxa).setScale(2, RoundingMode.HALF_UP);
            BigDecimal parcela = amortizacao.add(juros);
            parcelas.add(parcela);
            saldoDevedor = saldoDevedor.subtract(amortizacao);
        }
        return parcelas;
    }
    
    private static void validatePositive(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(name + " deve ser > 0");
        }
    }
    private static void validateNonNegative(BigDecimal value, String name) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(name + " não pode ser negativo");
        }
    }
    private static void validatePositiveDays(int dias) {
        if (dias <= 0) throw new IllegalArgumentException("Dias deve ser > 0");
    }
}
```

---

## 6. Scripts Utilitários

### 6.1 Cost Check (Pipeline)

```python
# scripts/cost_check.py
#!/usr/bin/env python3
import json
import sys
import argparse
from pathlib import Path

def check_cost(threshold_usd: float, usage_file: str = "evals/usage.json") -> bool:
    try:
        with open(usage_file) as f:
            data = json.load(f)
        
        total_cost = data.get("total_cost_usd", 0)
        num_inferences = data.get("num_inferences", 1)
        cost_per_inference = total_cost / num_inferences
        
        print(f"Total cost: ${total_cost:.4f}")
        print(f"Inferences: {num_inferences}")
        print(f"Cost per inference: ${cost_per_inference:.4f}")
        print(f"Threshold: ${threshold_usd:.4f}")
        
        if cost_per_inference > threshold_usd:
            print(f"❌ FAIL: Cost per inference (${cost_per_inference:.4f}) exceeds threshold (${threshold_usd:.4f})")
            return False
        else:
            print(f"✅ PASS: Cost per inference within threshold")
            return True
            
    except FileNotFoundError:
        print(f"⚠️  Usage file not found: {usage_file}")
        return True
    except Exception as e:
        print(f"❌ ERROR: {e}")
        return False

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI-DLC Cost Check")
    parser.add_argument("--threshold", type=float, default=0.30, help="Max cost per inference (USD)")
    parser.add_argument("--usage-file", type=str, default="evals/usage.json", help="Usage data file")
    args = parser.parse_args()
    
    success = check_cost(args.threshold, args.usage_file)
    sys.exit(0 if success else 1)
```

### 6.2 Drift Detection

```python
# scripts/drift_detection.py
#!/usr/bin/env python3
import numpy as np
import argparse
import json
import sys
from pathlib import Path
from scipy.spatial.distance import cosine

def detect_drift(baseline_file: str, current_file: str, threshold: float = 0.05) -> dict:
    try:
        baseline = np.load(baseline_file)
        current = np.load(current_file)
        
        # Mean embedding shift
        baseline_mean = np.mean(baseline, axis=0)
        current_mean = np.mean(current, axis=0)
        
        shift = cosine(baseline_mean, current_mean)
        
        # Distribution shift (KS test approximation)
        from scipy import stats
        ks_stat, p_value = stats.ks_2samp(
            np.linalg.norm(baseline, axis=1),
            np.linalg.norm(current, axis=1)
        )
        
        result = {
            "embedding_shift": float(shift),
            "ks_statistic": float(ks_stat),
            "p_value": float(p_value),
            "drift_detected": shift > threshold or p_value < 0.05,
            "threshold": threshold,
            "baseline_samples": len(baseline),
            "current_samples": len(current)
        }
        
        return result
        
    except Exception as e:
        return {"error": str(e), "drift_detected": True}

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI-DLC Drift Detection")
    parser.add_argument("--baseline", required=True, help="Baseline embeddings .npy file")
    parser.add_argument("--current", required=True, help="Current embeddings .npy file")
    parser.add_argument("--threshold", type=float, default=0.05, help="Drift threshold (cosine)")
    parser.add_argument("--output", required=True, help="Output JSON file")
    args = parser.parse_args()
    
    result = detect_drift(args.baseline, args.current, args.threshold)
    
    with open(args.output, "w") as f:
        json.dump(result, f, indent=2)
    
    if result.get("drift_detected"):
        print(f"⚠️ DRIFT DETECTED: shift={result.get('embedding_shift', 0):.4f}")
        sys.exit(1)
    else:
        print(f"✅ No drift detected: shift={result.get('embedding_shift', 0):.4f}")
        sys.exit(0)
```

### 6.3 Retrain Trigger

```python
# scripts/retrain_trigger.py
#!/usr/bin/env python3
import json
import argparse
import sys
from pathlib import Path

def should_retrain(feedback_file: str, drift_file: str, cost_file: str,
                   threshold_feedback_neg: float = 0.20,
                   threshold_drift: float = 0.05,
                   threshold_cost: float = 0.35) -> dict:
    
    reasons = []
    
    # Feedback negativo
    try:
        with open(feedback_file) as f:
            feedback = json.load(f)
        total = feedback.get("total", 0)
        negative = feedback.get("negative", 0)
        if total > 0:
            neg_rate = negative / total
            if neg_rate > threshold_feedback_neg:
                reasons.append(f"Feedback negativo: {neg_rate:.1%} > {threshold_feedback_neg:.1%}")
    except:
        pass
    
    # Drift
    try:
        with open(drift_file) as f:
            drift = json.load(f)
        if drift.get("drift_detected"):
            reasons.append(f"Drift detectado: shift={drift.get('embedding_shift', 0):.4f}")
    except:
        pass
    
    # Custo
    try:
        with open(cost_file) as f:
            cost = json.load(f)
        cost_per_inf = cost.get("cost_per_inference_usd", 0)
        if cost_per_inf > threshold_cost:
            reasons.append(f"Custo por inferência: ${cost_per_inf:.4f} > ${threshold_cost:.4f}")
    except:
        pass
    
    should = len(reasons) > 0
    return {
        "should_retrain": should,
        "reasons": reasons,
        "timestamp": "2024-01-15T10:30:00Z"
    }

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="AI-DLC Retrain Trigger")
    parser.add_argument("--feedback-report", required=True)
    parser.add_argument("--drift-report", required=True)
    parser.add_argument("--cost-report", required=True)
    parser.add_argument("--threshold-feedback-negative", type=float, default=0.20)
    parser.add_argument("--threshold-drift", type=float, default=0.05)
    parser.add_argument("--threshold-cost", type=float, default=0.35)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    
    result = should_retrain(
        args.feedback_report, args.drift_report, args.cost_report,
        args.threshold_feedback_negative, args.threshold_drift, args.threshold_cost
    )
    
    with open(args.output, "w") as f:
        json.dump(result, f, indent=2)
    
    if result["should_retrain"]:
        print(f"🔄 RETRAIN TRIGGERED: {', '.join(result['reasons'])}")
        sys.exit(0)  # Exit 0 = trigger retrain pipeline
    else:
        print("✅ No retrain needed")
        sys.exit(0)
```

---

## 7. Checklist de Uso (Shared Banking)

```markdown
# Checklist Shared Banking Utilities
- [ ] LGPD Anonymizer: @Anonymize em todos os Domain Events
- [ ] Vault: Zero secrets no código, todos via VaultSecretProvider
- [ ] Outbox Pattern: Todos eventos publicados via OutboxPublisher
- [ ] Saga Orchestrator: Transações distribuídas com compensação
- [ ] DocumentValidator: CPF/CNPJ validados em entrada
- [ ] PixKeyValidator: Chaves PIX validadas em entrada
- [ ] InterestCalculator: BigDecimal precision (SCALE=10, HALF_UP)
- [ ] Scripts: cost_check, drift_detection, retrain_trigger no CI
- [ ] Testes: Unit tests para todos utilitários (coverage > 90%)
- [ ] Documentação: Exemplos de uso em cada utilitário
```

---

*Skill versionada — atualize com nova utilidade, mudança regulatória, ou lição de produção.*