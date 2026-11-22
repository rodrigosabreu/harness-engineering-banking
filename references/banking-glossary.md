# Banking Glossary — Termos Mínimos Obrigatórios no Contexto do Agente

## Termos de Negócio

### Crédito Consignado
- **Margem Consignável**: % da renda comprometível (35% CLT, 45% servidor público, 30% INSS)
- **Averbação**: Registro do desconto na folha de pagamento (convênio → instituição financeira)
- **Convênio**: Acordo entre instituição pagadora (órgão/empresa) e banco para consignação
- **Taxa Teto**: Taxa máxima permitida por regulamentação (Bacen Res. 4.935 Art. 5º)
- **Portabilidade**: Transferência de operação de crédito entre instituições (prazo: 5 dias úteis)
- **Refinanciamento**: Nova operação para quitar anterior com alongamento de prazo

### Conciliação Financeira
- **Liquidação**: Efetivação da transferência de recursos (SPI/STR/PIX)
- **Conciliação**: Match entre esperado vs realizado (extrato bancário vs lançamentos contábeis)
- **SLA Bacen**: PIX < 10s, TED < 30min, DOC D+1, Conciliação D+0
- **Chargeback**: Contestação de transação por portador (cartões)
- **Estorno**: Cancelamento de liquidação já efetivada
- **Arquivo de Retorno**: Layout padrão bancário (CNAB 240/400) com status das transações

### Remuneração Variável
- **Regra de Cálculo**: Fórmula paramétrica (metas, ponderações, tetos)
- **Período de Apuração**: Mensal, trimestral, semestral, anual
- **Cadeia de Aprovação**: Gestor → Comitê → RH → Financeiro
- **Auditoria Total**: Rastreabilidade completa input → processamento → output
- **Provisão Contábil**: Reconhecimento contábil mensal (competência)

### Comum
- **Idempotência**: Operação repetida = mesmo resultado (chave: idempotency_key)
- **Outbox Pattern**: Garante publicação de evento + persistência atômica
- **Saga**: Transação distribuída com compensação (rollback semântico)
- **Event Sourcing**: Estado = replay de eventos imutáveis
- **Feature Flag por Segmento**: Rollout por convênio/produto/canal/região

## Termos Técnicos & Regulatórios

### Regulamentação
| Sigla | Norma | Escopo |
|-------|-------|--------|
| **Bacen Res. 4.935** | Crédito consignado | Margem, taxa, averbação, portabilidade |
| **Bacen Cir. 3.978** | Conciliação | SLA D+0, regras de match |
| **Bacen Res. 1** | PIX | Liquidação < 10s, disponibilidade 99.9% |
| **LGPD (Lei 13.709)** | Dados pessoais | Base legal, consentimento, retenção, direitos |
| **PCI-DSS** | Cartões | Segurança de dados de pagamento |
| **SOX (Lei 11.941)** | Auditoria | Trilha imutável, segregação, aprovação |
| **CMN Res. 4.658** | Cybersecurity | Governança, gestão de risco, continuidade |

### Arquitetura
- **Hexagonal**: Domain (core) + Ports (interfaces) + Adapters (infra)
- **Ports**: Repository interfaces, External service interfaces, Event publishers
- **Adapters**: JPA/Kafka/REST clients, Vault, Metrics, LGPD Anonymizer
- **Domain Events**: Imutáveis, versionados, causação rastreável (correlation_id)
- **CQRS**: Command (write) / Query (read) separation
- **Saga Orchestrator**: Coordena transações distribuídas com compensação

### Observabilidade
- **Trace ID**: Correlação end-to-end (command → event → projection)
- **Span Attributes**: banking.domain, banking.operation_type, banking.sla_compliant
- **SLOs**: PIX P99 < 10s, Conciliação P99 < 5min, Disponibilidade 99.99%
- **Error Budget**: 0.01% para transações financeiras críticas