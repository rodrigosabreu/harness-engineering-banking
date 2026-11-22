# ADR Template — Decisões Arquiteturais Hexagonal (Banking)

# docs/adr/YYYY-MM-DD-{dominio}-{decisao}.md

## Status
[Proposed | Accepted | Superseded]

## Contexto
- **Domínio**: [Crédito Consignado | Conciliação | Remuneração Variável | Shared]
- **Problema**: [Descreva o problema técnico/negócio]
- **Restrições**: 
  - Regulatórias: [Bacen Res. X, LGPD Art. Y, SOX, PCI-DSS]
  - Performance: [Latência P99 < Xms, Throughput > Y TPS]
  - Legado: [Integração com sistema X, versão Y]
  - Segurança: [Dados sensíveis, criptografia, LGPD]
  - Operacionais: [Deploy window, rollback time, observabilidade]

## Decisão
[Descreva a decisão técnica de forma clara e acionável]

**Exemplo**: "Usar Outbox Pattern com Kafka para garantir atomicidade entre persistência de averbação e publicação de evento `AverbacaoRegistrada`, com idempotency key = `averbacao_id`."

## Alternativas Consideradas

| Alternativa | Prós | Contras | Decisão |
|-------------|------|---------|---------|
| 1. Dual Write (DB + Kafka direto) | Simples | Risco inconsistência, sem atomicidade | ❌ Rejeitado |
| 2. Saga Coreografada | Descentralizado | Complexo compensação, debug difícil | ❌ Rejeitado |
| 3. Outbox + Kafka (Debezium) | Atomicidade, replay, auditoria | +5ms latência, infra Kafka | ✅ Escolhido |
| 4. Transactional Outbox (application-level) | Controle total | Mais código, manutenção | 🔄 Backup |

## Consequências

### Positivas
- [Ex: Atomicidade garantida entre DB e evento]
- [Ex: Auditoria completa via event store]
- [Ex: Replay para novos consumidores/projeções]

### Negativas
- [Ex: Latência adicional ~5ms na escrita]
- [Ex: Complexidade operacional (Kafka, Debezium, schema registry)]
- [Ex: Eventual consistency nas read models]

### Riscos Mitigados
- **Duplicação de eventos**: Handler idempotente obrigatório (chave `averbacao_id`)
- **Lag de replicação**: Read models eventually consistent (documentado no SLA)
- **Schema evolution**: Avro + Schema Registry (backward compatibility enforced)
- **Data loss**: Kafka replication factor 3 + min.insync.replicas=2

## Compliance (Obrigatório)

- [ ] **Bacen Res. 4.935** — Averbação rastreável, margem respeitada, taxa teto
- [ ] **LGPD Art. 7º/11º/16º** — Base legal, consentimento sensível, retenção ≤ 10 anos
- [ ] **SOX** — Trilha imutável, segregação de funções, aprovação documentada
- [ ] **PCI-DSS** — Zero PAN/CVV em logs/eventos, tokenização
- [ ] **CMN Res. 4.658** — Gestão de risco cibernético, continuidade

## AI-DLC Impact

### Eval Gates Afetados
- [ ] `idempotency_rate`: 1.0 (testes de caos + duplicação)
- [ ] `audit_completeness`: 1.0 (trilha completa command → event → projection)
- [ ] `latency_p95_ms`: < 100ms (escrita + outbox)
- [ ] `regulatory_compliance`: 1.0 (regras Bacen/LGPD/SOX)
- [ ] `chaos_resilience`: > 0.99 (Kafka down, DB slow, network partition)

### Pipeline Changes
- [ ] Contract test (Pact) para evento `AverbacaoRegistrada`
- [ ] Chaos test: Kafka broker down, DB latency spike, network partition
- [ ] Load test: 10k TPS averbação simultânea
- [ ] Rollback test: < 30s (feature flag kill switch)

### Observabilidade
- [ ] Trace completo: `CommandHandler` → `OutboxPersist` → `KafkaPublish` → `Consumer` → `Projection`
- [ ] Métricas: `outbox.latency.ms`, `kafka.publish.latency.ms`, `idempotency.duplicates`
- [ ] Alertas: `outbox.lag > 1000`, `kafka.publish.error.rate > 0.01%`

## Implementação (Referência)

```java
// Domain Service (puro)
@Service
public class AverbacaoService {
    
    private final AverbacaoRepository repository;
    private final OutboxRepository outbox;
    
    @Transactional
    public Averbacao registrar(AverbacaoCommand cmd) {
        // 1. Valida regras de negócio (margem, convênio, taxa)
        Averbacao averbacao = Averbacao.criar(cmd);
        
        // 2. Persiste entidade
        repository.save(averbacao);
        
        // 3. Persiste outbox (mesma transação)
        outbox.save(OutboxEvent.of(
            "AverbacaoRegistrada",
            averbacao.getId(),
            averbacao.toEventPayload()
        ));
        
        return averbacao;
    }
}

// Outbox Publisher (infraestrutura)
@Component
@RequiredArgsConstructor
public class OutboxPublisher {
    
    private final OutboxRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    
    @Scheduled(fixedDelay = 100)
    @Transactional
    public void publish() {
        outbox.findUnpublished(100).forEach(event -> {
            kafka.send(event.getTopic(), event.getKey(), event.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex == null) event.markPublished();
                });
        });
    }
}
```

## Aprovação

| Papel | Nome | Assinatura | Data |
|-------|------|------------|------|
| Tech Lead | | | |
| Arquiteto | | | |
| Compliance | | | |
| Product Owner | | | |

---

## Histórico de Versões

| Versão | Data | Autor | Mudança |
|--------|------|-------|---------|
| 1.0 | YYYY-MM-DD | [Autor] | Criação inicial |