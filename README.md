# Harness Engineering — Banking (Itaú)

> **Skill completa** para engenharia com agentes em banking de alta criticidade (Itaú Unibanco).
> Extende `agentic-dev-workflow` com contexto regulatório, arquitetura hexagonal, skills de domínio, pipelines Harness e utilitários compartilhados.

---

## 📁 Estrutura

```
harness-engineering-banking/
├── SKILL.md                                    # Documentação principal (esta skill)
├── templates/
│   ├── banking-glossary.md                     # Glossário mínimo obrigatório
│   ├── hexagonal-adr.md                        # Template ADR arquitetura hexagonal
│   ├── compliance-gates.rego                   # Políticas OPA (Bacen, LGPD, SOX, PCI-DSS)
│   ├── mcp-connectors.yaml                     # Conectores MCP padrão Itaú
│   ├── model-routing.yaml                      # Roteamento multi-modelo (GPT/Claude/Kimi/Local)
│   ├── BankingTracer.java                      # OTEL tracer padrão banking
│   ├── harness-banking-ci.yaml                 # Pipeline Harness completo (6 stages)
│   └── scripts/
│       ├── cost_check.py                       # Verificação custo/inferência
│       ├── drift_detection.py                  # Detecção drift embeddings
│       └── retrain_trigger.py                  # Trigger auto-retrain
├── skills/
│   ├── credit-consignado/                      # Skill domínio: Crédito Consignado
│   │   └── SKILL.md                            # Regras, modelos, testes, pipeline
│   ├── conciliacao/                            # Skill domínio: Conciliação Financeira
│   │   └── SKILL.md                            # Match engine, SLA, match engine
│   ├── remuneracao-variavel/                   # Skill domínio: Remuneração Variável
│   │   └── SKILL.md                            # Drools, SOX, Event Sourcing, hash chain
│   └── shared-banking/                         # Utilitários transversais
│       └── SKILL.md                            # LGPD, Vault, Outbox, Saga, Validadores, Juros
└── README.md                                   # Este arquivo
```

---

## 🎯 Domínios Cobertos

| Domínio | Skill | Regulatório | Complexidade |
|---------|-------|-------------|--------------|
| **Crédito Consignado** | `credit-consignado` | Bacen Res. 4.935, LGPD | Alta |
| **Conciliação Financeira** | `concilicacao` | Bacen Cir. 3.978, Res. 1 (PIX) | Muito Alta |
| **Remuneração Variável** | `remuneracao-variavel` | SOX, CLT, LGPD, Auditoria | Máxima |

---

## 🏗️ Arquitetura Base (Hexagonal)

```
domain/                    # 🎯 Núcleo puro (zero deps externas)
  ├── model/               # Entities, VOs, Aggregates, Domain Events
  ├── service/             # Domain Services (regras puras)
  ├── repository/          # Repository Interfaces (Ports)
  └── exception/
application/               # 🔄 Use Cases (Command/Query Handlers)
infrastructure/            # 🔌 Adapters (JPA, Kafka, Vault, External APIs)
interfaces/                # 🌐 Entry Points (REST, gRPC, Kafka, Scheduler)
shared/                    # 📦 Kernel compartilhado (utils, config, test)
```

---

## ⚙️ Pipeline Harness (6 Stages)

| Stage | Gates | Tempo |
|-------|-------|-------|
| **1. Code Quality & Security** | Compile, Test, SAST, SCA, Secrets, OPA Banking | ~5min |
| **2. AI Evaluation** | Golden Set (RAGAS), Red Teaming, Calc Accuracy, Idempotency, Pact | ~10min |
| **3. Build & Scan** | Docker Distroless, Container Scan, SBOM, Cosign Sign | ~5min |
| **4. Deploy Staging** | Canary 5% → 25% → 50% → 100% (segmento controlado) | ~15min |
| **5. Deploy Production** | Aprovação 2/2, Blue-Green, Progressive 5%→25%→100% | ~30min |
| **6. Post-Deploy** | Métricas 24h, Drift Detection, Cost Analysis, Feedback Loop, Auto-Retrain | Contínuo |

---

## 🤖 Model Routing (Multi-Model)

| Task Type | Models | Fallback | Constraints |
|-----------|--------|----------|-------------|
| Architecture Reasoning | GPT-4o, Claude-3.5-Sonnet | GPT-4-Turbo | Cite standards |
| Code Generation | Claude-3.5-Sonnet, GPT-4o | Kimi-K2 | Hexagonal, Java 21, Drools |
| Long Context | Kimi-K2, Claude-3.5-Sonnet | GPT-4o | 128k+ tokens |
| **Sensitive Data** | **Local Llama-3.1-70B, Mistral-Large** | **NONE** | Air-gapped, audit log |
| Compliance Validation | GPT-4o, Claude-3.5-Sonnet | GPT-4-Turbo | Cite regulation |
| Debugging/RCA | GPT-4o, Claude-3.5-Sonnet | Kimi-K2 | 4-phase method |

---

## 🔒 Compliance Gates (OPA/Rego)

| Regulamento | Políticas | Severidade |
|-------------|-----------|------------|
| **LGPD** | Base legal, consentimento sensível, retenção, direitos titular | CRITICAL |
| **Bacen** | Margem consignável, taxa teto, averbação, SLA PIX/TED/Cartão | CRITICAL |
| **SOX** | Trilha imutável, segregação, aprovação 3 níveis, retenção 7 anos | CRITICAL |
| **PCI-DSS** | Zero PAN/CVV em logs, tokenização, TLS obrigatório | CRITICAL |
| **CMN 4.658** | RPO ≤ 60min, RTO ≤ 4h, teste DR semestral | HIGH |

---

## 📊 Observabilidade (OTEL + Banking Attributes)

```java
// Atributos obrigatórios em todos spans
banking.domain = "consignado|concilicacao|remuneracao|pix|shared"
banking.operation_type = "command|query|event|saga|batch"
banking.aggregate_id = "uuid"
banking.correlation_id = "uuid"
banking.success = true/false
banking.sla_compliant = true/false
banking.latency_ms = long
banking.regulatory_check = true/false
banking.feature_flag = "key=value"
```

---

## 🧪 Eval Gates (Thresholds Banking)

| Métrica | Threshold | Bloqueador |
|---------|-----------|------------|
| Faithfulness | ≥ 0.90 | ✅ |
| Calculation Accuracy | 1.0 (100%) | ✅ |
| Regulatory Compliance | 1.0 (100%) | ✅ |
| Hallucination Rate | ≤ 0.01 | ✅ |
| PII Leakage | 0 | ✅ |
| Idempotency Rate | 1.0 | ✅ |
| Audit Trail Completeness | 1.0 | ✅ |
| Cost per Inference | ≤ $0.30 | ✅ |
| Latency P95 | ≤ 5s | ✅ |
| Chaos Resilience | ≥ 99% | ✅ |

---

## 🚀 Quick Start

```bash
# 1. Ver skill principal
skill_view(name='harness-engineering-banking')

# 2. Ver template específico
skill_view(name='harness-engineering-banking', file_path='templates/compliance-gates.rego')

# 3. Ver skill de domínio
skill_view(name='harness-engineering-banking', file_path='skills/credit-consignado/SKILL.md')

# 4. O agente vai:
#    - Criar estrutura hexagonal
#    - Aplicar templates (ADR, OPA, Pipeline, Model Routing)
#    - Configurar skills de domínio conforme necessidade
#    - Primeiro commit: "chore: bootstrap banking project with harness-engineering"
```

---

## 📋 Checklist de Projeto Novo

- [ ] `git init` + `.gitignore` (binaries, secrets, local state)
- [ ] Estrutura hexagonal (`domain/`, `application/`, `infrastructure/`, `interfaces/`, `shared/`)
- [ ] `docs/adr/` + template `hexagonal-adr.md`
- [ ] `policies/banking/compliance-gates.rego` (OPA)
- [ ] `.harness/ci/` + `harness-banking-ci.yaml`
- [ ] `model-routing.yaml` configurado
- [ ] `BankingTracer.java` integrado
- [ ] Skills de domínio necessárias (`credit-consignado`, `concilicacao`, `remuneracao-variavel`)
- [ ] `shared-banking` (LGPD, Vault, Outbox, Saga, Validadores, Juros)
- [ ] `evals/` + golden sets + thresholds banking
- [ ] `scripts/` (cost_check, drift_detection, retrain_trigger)
- [ ] Pipeline Harness configurado + triggers
- [ ] Primeiro commit + push + Harness project criado

---

## 📈 Métricas de Sucesso

| Métrica | Target | Crítico |
|---------|--------|---------|
| Regulatory Compliance | 100% | ✅ |
| Calculation Accuracy | 100% | ✅ |
| PII Leakage | 0 | ✅ |
| Rollback Time | < 30s | ✅ |
| SLA PIX/TED/Conciliação | 100% | ✅ |
| Lead Time (issue→prod) | < 2h (hotfix) / < 1dia (feature) | ⚠️ |
| Cost per Inference | < $0.30 | ⚠️ |
| Drift Detection | < 24h | ⚠️ |

---

*Skill versionada — atualize com nova regulamentação (Bacen, CMN, LGPD, SOX), nova tecnologia, ou lição de produção.*