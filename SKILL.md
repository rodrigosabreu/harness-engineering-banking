---
name: harness-engineering-banking
description: "Harness Engineering para banking de alta criticidade (Itaú) - contexto de negócio, compliance, arquitetura hexagonal, skills de domínio, pipelines regulatórios"
version: "1.0.0"
category: software-development
extends: ["agentic-dev-workflow"]
required_skills: ["harness-agent", "systematic-debugging", "test-driven-development", "architecture-diagram", "grounded-citations", "codebase-inspection"]
---

# Harness Engineering — Banking de Alta Criticidade

> **Objetivo**: Codificar a prática de engenharia com agentes para produtos financeiros regulados (crédito consignado, conciliação, remuneração variável) no maior banco da América Latina.
>
> **Princípio**: Agente não "chuta código" — executa com **contexto de negócio estruturado**, **validações regulatórias**, **arquitetura hexagonal** e **observabilidade completa**.

---

## Always-On Rules (Aplicam a TODAS as tasks banking)

1. **Carregar contexto obrigatório** antes de qualquer código: `skills/{dominio}/SKILL.md` + `references/banking-glossary.md` + `policies/{dominio}.rego`
2. **Arquitetura hexagonal obrigatória**: Domain puro (zero deps externas) → Application → Infrastructure → Interfaces → Shared
3. **Validação obrigatória antes de commit**: `make eval` (thresholds banking) + `opa eval policies/banking/compliance-gates.rego` (zero violações) + testes cálculo 100% accuracy + idempotência + Pact
3. **Zero PII em logs**, zero secrets no código (Vault), dados sensíveis → modelo local apenas, auditoria imutável (event sourcing)
4. **Pipeline regulatório**: Canary 5%→25%→50%→100% (segmento controlado), rollback <30s, feature flag por convênio/produto
5. **Observabilidade padronizada**: `BankingTracer.java` com atributos `banking.*` em todos spans
6. **Model routing**: Dados sensíveis (PI, financeiro) → APENAS modelo local (fallback: null)
7. **Métricas de sucesso**: Regulatory compliance 100%, Calculation accuracy 100%, PII leakage 0, Rollback <30s, SLA 100%

---

## Procedimento: Bootstrap Novo Projeto Banking

1. `git init` + `.gitignore` (binaries, secrets, local state)
2. Criar estrutura hexagonal: `domain/`, `application/`, `infrastructure/`, `interfaces/`, `shared/`, `docs/adr/`, `evals/`, `policies/`, `scripts/`
3. Copiar templates: `templates/hexagonal-adr.md` → `docs/adr/`, `templates/compliance-gates.rego` → `policies/banking/`, `templates/harness-banking-ci.yaml` → `.harness/ci/`, `templates/model-routing.yaml` → raiz, `templates/BankingTracer.java` → `shared/observability/`
4. Configurar skills de domínio necessárias (`credit-consignado`, `concilicacao`, `remuneracao-variavel`, `shared-banking`)
4. Configurar `evals/` + golden sets + thresholds banking (`references/banking-eval-thresholds.py`)
5. Configurar `scripts/` (`cost_check.py`, `drift_detection.py`, `retrain_trigger.py`) no CI
6. Pipeline Harness configurado + triggers (GitHub webhook PR)
6. Primeiro commit: `chore: bootstrap banking project with harness-engineering`

---

## Procedimento: Iniciar Task em Domínio Bancário

1. **Carregar contexto**: Ler `skills/{dominio}/SKILL.md` + `references/banking-glossary.md` + `policies/{dominio}.rego`
2. **Arquitetura**: Seguir estrutura hexagonal, usar `references/hexagonal-adr-guide.md` para decisões, domain puro = zero deps externas
3. **Desenvolver**: Command/Query handlers no Application, Domain Services para regras puras, Adapters em Infrastructure
4. **Validar (obrigatório antes de commit)**:
   - `make eval` passa (thresholds `references/banking-eval-thresholds.py`)
   - `opa eval policies/banking/compliance-gates.rego` → zero violações
   - Testes cálculo financeiro: 100% accuracy (BigDecimal, HALF_UP)
   - Testes idempotência: caos + duplicação (Kafka down, DB latency)
   - Contract tests (Pact) passam
5. **Segurança & Compliance**: Zero PII logs, zero secrets código (Vault), dados sensíveis → modelo local, auditoria imutável
6. **Observabilidade**: `BankingTracer.java` com atributos `banking.*` em todos spans

---

## Procedimento: Pipeline Harness (6 Stages)

1. **Code Quality & Security**: Compile, Test, SAST, SCA, Secrets, OPA Banking Compliance
2. **AI Evaluation**: Golden Set (RAGAS + Banking thresholds), Red Teaming (PromptFoo), Calc Accuracy, Idempotency+Chaos, Pact
3. **Build & Scan**: Docker Distroless, Container Scan (Trivy+Syft), SBOM (SPDX+CycloneDX), Cosign Sign
4. **Deploy Staging**: Canary 5%→25%→50%→100% (segmento controlado), Smoke+Regulatory validation, Auto-rollback se métrica violada
5. **Deploy Production**: Aprovação 2/2 (Tech Lead + Compliance), Blue-Green, Progressive 5%→25%→100%, Post-prod monitoring 30min
6. **Post-Deploy Feedback**: Métricas 24h, Drift Detection, Cost Analysis, Feedback Collection, Auto-Retrain Trigger

---

## Model Routing (Sempre Aplicar)

| Task Type | Models | Fallback | Constraints |
|-----------|--------|----------|-------------|
| Architecture Reasoning | GPT-4o, Claude-3.5-Sonnet | GPT-4-Turbo | Cite standards |
| Code Generation | Claude-3.5-Sonnet, GPT-4o | Kimi-K2 | Hexagonal, Java 21, Drools |
| Long Context | Kimi-K2, Claude-3.5-Sonnet | GPT-4o | 128k+ tokens |
| **Sensitive Data** | **Local Llama-3.1-70B, Mistral-Large** | **NONE** | Air-gapped, audit log |
| Compliance Validation | GPT-4o, Claude-3.5-Sonnet | GPT-4-Turbo | Cite regulation |
| Debugging/RCA | GPT-4o, Claude-3.5-Sonnet | Kimi-K2 | 4-phase method |

**Routing automático por keywords**: architecture/adr→architecture_reasoning, java/spring→code_generation, log/transcript/legacy→long_context, cpf/cnpj/account/pix/card/pii→sensitive_data_processing, compliance/bacen/lgpd/sox/pci→compliance_validation, debug/rca/failure→debugging_root_cause

---

## Referências (Consultar Quando Necessário)

- `references/banking-glossary.md` — Glossário mínimo obrigatório (termos negócio + técnicos + regulatórios)
- `references/hexagonal-adr-guide.md` — Guia completo ADR hexagonal + template + exemplos
- `references/compliance-gates-guide.md` — Políticas OPA completas (LGPD, Bacen, SOX, PCI-DSS, CMN 4.658) + testes
- `references/mcp-connectors-guide.md` — 18 conectores MCP padrão Itaú (Jira, Confluence, GitHub, Vault, DataCat, Datadog, ArgoCD, Kafka, etc.)
- `references/model-routing-guide.md` — Roteamento 7 task types + constraints + routing rules + global config
- `references/banking-eval-thresholds.py` — Thresholds banking (faithfulness 0.90, accuracy 1.0, compliance 1.0, etc.)
- `references/harness-pipeline-guide.md` — Pipeline 6 stages completo + scripts de validação
- `references/banking-tracer-guide.md` — OTEL tracer com atributos `banking.*` + exemplos de uso
- `references/model-routing.yaml` — Configuração completa de roteamento multi-modelo
- `references/banking-metrics.md` — Métricas de sucesso + alertas + dashboards
- `scripts/cost_check.py` — Verificação custo/inferência (<$0.30)
- `scripts/drift_detection.py` — Detecção drift embeddings (cosine + KS test)
- `scripts/retrain_trigger.py` — Trigger auto-retrain (feedback + drift + custo)

---

## Domain Skills (Carregar Conforme Necessário)

- `skill:credit-consignado` — Margem, convênio, averbação, taxa teto, portabilidade (Bacen Res. 4.935)
- `skill:concilicacao` — Match engine, PIX/TED/DOC/Cartão, SLA D+0 (Bacen Cir. 3.978, Res. 1)
- `skill:remuneracao-variavel` — Drools, SOX, Event Sourcing, hash chain, approval chain (CLT, SOX)
- `skill:shared-banking` — LGPD Anonymizer, Vault, Outbox, Saga, Validadores CPF/CNPJ/PIX, Juros

---

*Esta skill vive no seu Hermes. Atualize quando houver nova regulamentação (Bacen, CMN, LGPD, SOX), novo padrão arquitetural, ou lição de produção.*

---

## 2. Arquitetura Hexagonal (Padrão Obrigatório)

### 2.1 Estrutura de Projeto

```bash
# templates/hexagonal-structure/
projeto-bancario/
├── domain/                    # 🎯 Núcleo puro (zero dependências externas)
│   ├── model/                 # Entidades, Value Objects, Aggregates
│   ├── service/               # Domain Services (regras de negócio puras)
│   ├── event/                 # Domain Events
│   ├── exception/             # Domain Exceptions
│   └── repository/            # Repository Interfaces (Ports)
├── application/               # 🔄 Casos de uso (orquestração)
│   ├── command/               # Command Handlers (write)
│   ├── query/                 # Query Handlers (read)
│   ├── dto/                   # DTOs de entrada/saída
│   └── mapper/                # Domain ↔ DTO
├── infrastructure/            # 🔌 Adapters (implementações)
│   ├── persistence/           # JPA Repositories, Mongo, Redis
│   ├── messaging/             # Kafka Producers/Consumers, Outbox
│   ├── external/              # Clients: Bacen, Receita, Convênios
│   ├── security/              # Vault, KMS, LGPD Anonymizer
│   └── observability/         # Metrics, Traces, Logs estruturados
├── interfaces/                # 🌐 Entry points
│   ├── rest/                  # Controllers, DTOs, Validators
│   ├── grpc/                  # Protobuf services
│   ├── messaging/             # Event Listeners (Kafka)
│   └── scheduler/             # Jobs, Batch
└── shared/                    # 📦 Kernel compartilhado
    ├── kernel/                # Base classes, Result<T>, Validation
    ├── config/                # Configuration properties
    └── test/                  # Test fixtures, builders, containers
```

### 2.2 ADR Template para Decisões Arquiteturais

```markdown
# templates/hexagonal-adr.md
# docs/adr/YYYY-MM-DD-{dominio}-{decisao}.md

## Status: [Proposed | Accepted | Superseded]

## Contexto
- Domínio: [Crédito Consignado | Conciliação | Remuneração Variável]
- Problema: [Ex: Como garantir idempotência na averbação?]
- Restrições: [Regulatório, Performance, Legado, Segurança]

## Decisão
[Ex: Usar Outbox Pattern + Kafka com idempotency key = averbação_id]

## Alternativas Consideradas
1. **Dual Write** → Rejeitado: risco de inconsistência
2. **Saga Coreografada** → Rejeitado: complexidade de compensação
3. **Outbox + Kafka** → Escolhido: atomicidade + replay + auditoria

## Consequências
### Positivas
- Atomicidade garantida (DB + Evento)
- Auditoria completa (event store)
- Replay para novos consumidores

### Negativas
- Latência +5ms (persistência outbox)
- Complexidade de infra (Kafka, Debezium)

### Riscos
- Duplicação de eventos → Handler idempotente obrigatório
- Lag de replicação → Read models eventually consistent

## Compliance
- [ ] Bacen Res. 4.935 (averbação rastreável)
- [ ] LGPD Art. 7º (dados sensíveis em eventos)
- [ ] SOX (auditoria imutável)

## AI-DLC Impact
- Eval gates: idempotência, auditoria, latência P99 < 100ms
- Pipeline: Contract test (Pact) + Chaos (Kafka down) + Load (10k TPS)
- Observabilidade: Trace completo command → event → projection
```

---

## 3. Compliance & Validação (Policy-as-Code)

### 3.1 Regras Regulatórias (OPA/Rego)

```rego
# templates/compliance-gates.rego
package banking.compliance

# ========== LGPD ==========
deny[msg] {
    input.processes_personal_data
    not input.has_lgpd_basis
    msg := "LGPD Art. 7º: Base legal obrigatória para dados pessoais"
}

deny[msg] {
    input.contains_sensitive_data
    not input.has_explicit_consent
    msg := "LGPD Art. 11º: Consentimento explícito para dados sensíveis"
}

deny[msg] {
    input.data_retention_days > 3650
    msg := "LGPD Art. 16º: Retenção máxima 10 anos sem justificativa"
}

# ========== Bacen ==========
deny[msg] {
    input.domain == "consignado"
    input.margem_consignavel > 0.35
    not input.is_servidor_publico
    msg := "Bacen Res. 4.935: Margem CLT máx 35%"
}

deny[msg] {
    input.domain == "concilicacao"
    input.sla_minutes > 5
    msg := "Bacen Cir. 3.978: Conciliação D+0 (SLA < 5min)"
}

deny[msg] {
    input.domain == "pix"
    input.settlement_time_seconds > 10
    msg := "Bacen Res. 1: PIX liquidação < 10s"
}

# ========== SOX / Auditoria ==========
deny[msg] {
    input.domain == "remuneracao_variavel"
    not input.has_immutable_audit_trail
    msg := "SOX: Trilha de auditoria imutável obrigatória"
}

deny[msg] {
    input.changes_calculation_rules
    not input.has_approval_chain
    msg := "SOX: Alteração de regras requer cadeia de aprovação"
}

# ========== Segurança ==========
deny[msg] {
    input.exposes_pii_in_logs
    msg := "PCI-DSS/LGPD: PII não pode aparecer em logs"
}

deny[msg] {
    input.uses_hardcoded_secrets
    msg := "Vault obrigatório: zero secrets no código/config"
}
```

### 3.2 Eval Gates Específicos (Além dos Genéricos)

```python
# templates/banking-eval-thresholds.py
BANKING_EVAL_THRESHOLDS = {
    # Genéricos (herdados)
    "faithfulness": 0.90,           # Mais rigoroso: 0.85 → 0.90
    "answer_relevance": 0.85,
    "context_precision": 0.90,
    "hallucination_rate": 0.01,     # Quase zero tolerância
    "pii_leakage": 0.0,
    "cost_per_inference_usd": 0.30, # Otimizado para volume
    "latency_p95_ms": 5000,         # Crítico: < 5s
    
    # Banking específicos
    "regulatory_compliance": 1.0,   # 100% regras Bacen/LGPD/SOX
    "calculation_accuracy": 1.0,    # Cálculos financeiros exatos
    "idempotency_rate": 1.0,        # Zero duplicação
    "audit_completeness": 1.0,      # Trilha completa
    "rollback_time_seconds": 30,    # Rollback < 30s
    "chaos_resilience": 0.99,       # 99% sucesso sob falha
}
```

---

## 4. MCP Connectors (Integrações Padrão Itaú)

```yaml
# templates/mcp-connectors.yaml
mcp_connectors:
  # Gestão de Trabalho
  - name: jira-software
    type: atlassian-jira
    config:
      base_url: "https://itau.atlassian.net"
      auth: vault:kv/jira/api-token
      projects: ["CRED", "CONC", "REMU"]
    tools: [search_issues, create_issue, transition_issue, add_comment]
  
  # Documentação
  - name: confluence
    type: atlassian-confluence
    config:
      base_url: "https://itau.atlassian.net/wiki"
      auth: vault:kv/confluence/api-token
      spaces: ["ENG", "ARCH", "COMPLIANCE"]
    tools: [search_pages, get_page, create_page, update_page]
  
  # Código & Qualidade
  - name: github-enterprise
    type: github
    config:
      host: "github.itau.com.br"
      auth: vault:kv/github/pat
      orgs: ["itau-unibanco"]
    tools: [search_code, get_file, create_pr, review_pr, run_workflow]
  
  - name: sonarqube
    type: sonarqube
    config:
      base_url: "https://sonar.itau.com.br"
      auth: vault:kv/sonar/token
    tools: [get_project_quality_gate, get_issues, get_metrics]
  
  # Segurança & Secrets
  - name: hashicorp-vault
    type: vault
    config:
      address: "https://vault.itau.com.br"
      auth: kubernetes
      namespace: "banking"
    tools: [read_secret, write_secret, list_secrets, generate_token]
  
  # Dados & Catálogo
  - name: datacatalog
    type: custom
    config:
      base_url: "https://catalogo.itau.com.br/api"
      auth: vault:kv/datacatalog/token
    tools: [search_dataset, get_schema, get_lineage, get_owner]
  
  # Observabilidade
  - name: datadog
    type: datadog
    config:
      api_url: "https://api.datadoghq.com"
      auth: vault:kv/datadog/api-key
    tools: [query_metrics, get_traces, get_logs, create_monitor]
  
  # Infra & Deploy
  - name: argo-cd
    type: argocd
    config:
      server: "argocd.itau.com.br"
      auth: vault:kv/argocd/token
    tools: [get_application, sync_application, rollback_application]
```

---

## 5. Pipeline Harness (Regulatório)

```yaml
# templates/harness-banking-ci.yaml
pipeline:
  name: Banking AI-DLC CI
  identifier: banking_ai_dlc_ci
  projectIdentifier: YOUR_PROJECT
  orgIdentifier: default
  stages:
    - stage:
        name: Code Quality & Security
        identifier: code_quality
        type: CI
        spec:
          cloneCodebase: true
          platform: {os: Linux, arch: Amd64}
          runtime: {type: Cloud, spec: {}}
          execution:
            steps:
              - step:
                  name: Java Build & Test (Maven/Gradle)
                  identifier: java_build
                  type: Run
                  spec:
                    image: maven:3.9-eclipse-temurin-21
                    shell: Bash
                    command: |
                      ./mvnw verify -DskipITs=false -DtestIntelligence=true
              - step:
                  name: SAST (SonarQube + Checkmarx)
                  identifier: sast
                  type: SecurityScan
                  spec:
                    scanner: HarnessSAST
                    fail_on_severity: HIGH
              - step:
                  name: SCA (Dependências + Licenças)
                  identifier: sca
                  type: SecurityScan
                  spec:
                    scanner: HarnessSCA
                    fail_on_cvss: 7.0
              - step:
                  name: Secrets Scan
                  identifier: secrets
                  type: SecurityScan
                  spec:
                    scanner: HarnessSecrets
              - step:
                  name: Policy Check (OPA - Banking Compliance)
                  identifier: opa_banking
                  type: Run
                  spec:
                    image: openpolicyagent/opa:latest
                    shell: Bash
                    command: |
                      opa eval -i target/eval-results.json -d policies/banking/compliance-gates.rego "data.banking.compliance.deny" --format json > opa-results.json
                      if [ -s opa-results.json ] && [ "$(cat opa-results.json | jq '.result[0].expressions[0].value | length')" -gt 0 ]; then
                        echo "❌ POLICY VIOLATIONS:"
                        cat opa-results.json | jq -r '.result[0].expressions[0].value[]'
                        exit 1
                      fi
    - stage:
        name: AI Evaluation (Banking Gates)
        identifier: ai_eval
        type: CI
        spec:
          cloneCodebase: true
          platform: {os: Linux, arch: Amd64}
          runtime: {type: Cloud, spec: {}}
          execution:
            steps:
              - step:
                  name: Golden Set Eval (RAGAS + Banking)
                  identifier: golden_set
                  type: Run
                  spec:
                    image: python:3.11-slim
                    shell: Bash
                    command: |
                      pip install -r evals/requirements.txt
                      pytest evals/ -v --tb=short --banking-thresholds
              - step:
                  name: Red Teaming (PromptFoo - Injection, Jailbreak, PII)
                  identifier: red_teaming
                  type: Run
                  spec:
                    image: promptfoo/promptfoo:latest
                    shell: Bash
                    command: |
                      promptfoo eval -c evals/redteam-banking.yaml
              - step:
                  name: Calculation Accuracy (Testes Financeiros)
                  identifier: calc_accuracy
                  type: Run
                  spec:
                    image: maven:3.9-eclipse-temurin-21
                    shell: Bash
                    command: |
                      ./mvnw test -Dtest=*CalculationTest,*FinancialMathTest
              - step:
                  name: Idempotency & Chaos Test
                  identifier: chaos_test
                  type: Run
                  spec:
                    image: maven:3.9-eclipse-temurin-21
                    shell: Bash
                    command: |
                      ./mvnw test -Dtest=*IdempotencyTest,*ChaosTest
    - stage:
        name: Contract Testing (Pact)
        identifier: contract_test
        type: CI
        spec:
          cloneCodebase: true
          platform: {os: Linux, arch: Amd64}
          runtime: {type: Cloud, spec: {}}
          execution:
            steps:
              - step:
                  name: Consumer Contract Tests
                  identifier: consumer_pact
                  type: Run
                  spec:
                    image: maven:3.9-eclipse-temurin-21
                    shell: Bash
                    command: |
                      ./mvnw test -Dtest=*PactConsumerTest
              - step:
                  name: Provider Contract Verification
                  identifier: provider_pact
                  type: Run
                  spec:
                    image: maven:3.9-eclipse-temurin-21
                    shell: Bash
                    command: |
                      ./mvnw test -Dtest=*PactProviderTest
              - step:
                  name: Publish Pact Broker
                  identifier: publish_pact
                  type: Run
                  spec:
                    shell: Bash
                    command: |
                      ./mvnw pact:publish -Dpact.broker.url=$PACT_BROKER_URL
    - stage:
        name: Build & Security Scan
        identifier: build_scan
        type: CI
        spec:
          cloneCodebase: true
          platform: {os: Linux, arch: Amd64}
          runtime: {type: Cloud, spec: {}}
          execution:
            steps:
              - step:
                  name: Build Docker (Distroless)
                  identifier: build_docker
                  type: BuildAndPushDockerRegistry
                  spec:
                    connectorRef: ecr-banking
                    repo: 123456789.dkr.ecr.sa-east-1.amazonaws.com/banking/${PROJECT}
                    tags: ["<+pipeline.sequenceId>", "latest"]
                    dockerfile: Dockerfile.distroless
              - step:
                  name: Container Scan (Trivy + Syft)
                  identifier: container_scan
                  type: SecurityScan
                  spec:
                    scanner: HarnessContainerScan
                    fail_on_severity: HIGH
              - step:
                  name: Generate SBOM (SPDX)
                  identifier: sbom
                  type: Run
                  spec:
                    shell: Bash
                    command: |
                      syft 123456789.dkr.ecr.sa-east-1.amazonaws.com/banking/${PROJECT}:<+pipeline.sequenceId> -o spdx-json > sbom.spdx.json
    - stage:
        name: Deploy Staging (Canary Regulatório)
        identifier: deploy_staging
        type: Deployment
        spec:
          deploymentType: Kubernetes
          service: {name: ${PROJECT}, identifier: ${PROJECT}}
          environment:
            name: Staging
            identifier: staging
            spec: {type: PreProduction}
          execution:
            steps:
              - step:
                  name: Canary 5% (Segmento Controlado)
                  identifier: canary_5
                  type: K8sCanaryDeploy
                  spec:
                    replicas: "2"
                    trafficSplit: 5
                    featureFlags:
                      - "banking.canary.segment=internal"
              - step:
                  name: Smoke + Regulatory Validation
                  identifier: validate_canary
                  type: Run
                  spec:
                    shell: Bash
                    command: |
                      ./scripts/validate-canary.sh --environment staging --regulatory-check
              - step:
                  name: Promote 25% → 50% → 100%
                  identifier: promote
                  type: K8sCanaryPromote
                  spec:
                    steps: [25, 50, 100]
                    interval: 10m
              - step:
                  name: Rollback Automático se Métrica Violada
                  identifier: auto_rollback
                  type: Run
                  spec:
                    shell: Bash
                    command: |
                      ./scripts/check-metrics.sh --threshold error_rate<0.01,latency_p99<5000 || exit 1
```

---

## 6. Skills de Domínio (Estrutura)

```
skills/
├── credit-consignado/
│   ├── SKILL.md                    # Contexto: margem, convênio, averbação, taxa teto
│   ├── templates/
│   │   ├── averbacao-command.md    # Command handler padrão
│   │   ├── margem-calculator.md    # Cálculo de margem (testes exaustivos)
│   │   ├── convenio-config.yaml    # Configuração por convênio
│   │   └── bacen-report.md         # Relatório regulatório
│   ├── evals/
│   │   ├── golden-set-consignado.json
│   │   └── redteam-consignado.yaml
│   └── policies/
│       └── consignado.rego
├── conciliacao/
│   ├── SKILL.md                    # Contexto: PIX, TED, DOC, cartões, SLA
│   ├── templates/
│   │   ├── match-engine.md         # Algoritmo de match (fuzzy + exato)
│   │   ├── outbox-reconciliation.md
│   │   └── sla-monitor.md
│   ├── evals/
│   └── policies/
│       └── conciliacao.rego
├── remuneracao-variavel/
│   ├── SKILL.md                    # Contexto: regras complexas, auditoria, SOX
│   ├── templates/
│   │   ├── rule-engine.md          # Motor de regras (Drools/ Easy Rules)
│   │   ├── audit-trail.md          # Event sourcing imutável
│   │   └── approval-chain.md
│   ├── evals/
│   └── policies/
│       └── remuneracao.rego
└── shared-banking/
    ├── SKILL.md                    # Utilitários comuns
    ├── templates/
    │   ├── lgpd-anonymizer.md
    │   ├── vault-config.md
    │   ├── outbox-pattern.md
    │   └── saga-orchestrator.md
    └── scripts/
        ├── validate-cpf-cnpj.py
        ├── calculate-juros.py
        └── format-pix-payload.py
```

---

## 7. Model Routing (Multi-Model)

```yaml
# templates/model-routing.yaml
model_routing:
  # Reasoning complexo, arquitetura, ADRs
  - task_type: "architecture_reasoning"
    models: ["gpt-4o", "claude-3.5-sonnet"]
    fallback: "gpt-4-turbo"
    temperature: 0.1
  
  # Código Java/Spring, testes, refactoring
  - task_type: "code_generation"
    models: ["claude-3.5-sonnet", "gpt-4o"]
    fallback: "kimi-k2"
    temperature: 0.0
  
  # Contexto longo (logs, docs, transcrições > 100k tokens)
  - task_type: "long_context_analysis"
    models: ["kimi-k2", "claude-3.5-sonnet"]
    fallback: "gpt-4o"
    temperature: 0.1
  
  # Dados sensíveis (PI, financeiro) - APENAS modelo local
  - task_type: "sensitive_data_processing"
    models: ["local-llama-3.1-70b", "local-mistral-large"]
    fallback: null  # NÃO cai para cloud
    temperature: 0.0
    constraints:
      - "no_external_api_calls"
      - "audit_log_required"
  
  # Validação regulatória, compliance check
  - task_type: "compliance_validation"
    models: ["gpt-4o", "claude-3.5-sonnet"]
    fallback: "gpt-4-turbo"
    temperature: 0.0
    system_prompt: |
      Você é um validador de compliance bancário (Bacen, LGPD, SOX, PCI-DSS).
      Rejeite qualquer sugestão que viole regulamentação.
      Cite a norma específica (ex: "Bacen Res. 4.935 Art. 3º").
```

---

## 8. Observabilidade (OTEL + Banking)

```java
// templates/BankingTracer.java
@Component
public class BankingTracer {
    
    private final Tracer tracer = GlobalOpenTelemetry.getTracer("banking");
    
    public <T> T traceCommand(String domain, String command, Supplier<T> fn) {
        return tracer.spanBuilder("command." + domain + "." + command)
            .setAttribute("banking.domain", domain)
            .setAttribute("banking.operation_type", "command")
            .setSpanKind(SpanKind.INTERNAL)
            .startScopedSpan(span -> {
                try {
                    T result = fn.get();
                    span.setAttribute("banking.success", true);
                    return result;
                } catch (Exception e) {
                    span.setAttribute("banking.success", false);
                    span.setAttribute("banking.error_type", e.getClass().getSimpleName());
                    span.recordException(e);
                    throw e;
                }
            });
    }
    
    public void traceLiquidity(String pixId, String status, Duration latency) {
        tracer.spanBuilder("liquidity.pix")
            .setAttribute("banking.pix_id", pixId)
            .setAttribute("banking.status", status)
            .setAttribute("banking.latency_ms", latency.toMillis())
            .setAttribute("banking.sla_compliant", latency.toMillis() < 10000)
            .startScopedSpan(span -> {});
    }
    
    public void traceAudit(String aggregateId, String eventType, Object payload) {
        tracer.spanBuilder("audit." + eventType)
            .setAttribute("banking.aggregate_id", aggregateId)
            .setAttribute("banking.event_type", eventType)
            .setAttribute("banking.payload_hash", hash(payload))
            .setAttribute("banking.immutable", true)
            .startScopedSpan(span -> {});
    }
}
```

---

## 9. Checklist de Uso (Para o Agente)

```markdown
# Ao iniciar task em domínio bancário:

## 1. Carregar Contexto
- [ ] Ler `skills/{dominio}/SKILL.md`
- [ ] Carregar `templates/banking-glossary.md` no contexto
- [ ] Verificar `policies/{dominio}.rego` para regras

## 2. Arquitetura
- [ ] Seguir estrutura hexagonal (domain/application/infrastructure/interfaces)
- [ ] Usar `templates/hexagonal-adr.md` para decisões
- [ ] Domain puro = zero dependências externas

## 3. Validação (Obrigatório antes de commit)
- [ ] `make eval` passa (thresholds banking)
- [ ] `opa eval` policies/banking/compliance-gates.rego → zero violações
- [ ] Testes de cálculo financeiro: 100% accuracy
- [ ] Testes de idempotência: caos + duplicação
- [ ] Contract tests (Pact) passam

## 4. Segurança & Compliance
- [ ] Zero PII em logs (LGPD)
- [ ] Zero secrets no código (Vault)
- [ ] Dados sensíveis → modelo local apenas
- [ ] Auditoria imutável (event sourcing)

## 5. Pipeline
- [ ] Canary 5% → 25% → 50% → 100% (segmento controlado)
- [ ] Rollback automático < 30s
- [ ] Feature flag por convênio/produto

## 6. Observabilidade
- [ ] Trace completo command → event → projection
- [ ] Métricas: SLA, error rate, latency P99, throughput
- [ ] Alertas: SLA breach, regulatory violation, anomaly
```

---

## 10. Métricas de Sucesso (Banking)

| Métrica | Target | Criticidade |
|---------|--------|-------------|
| Regulatory compliance rate | 100% | Bloqueador |
| Calculation accuracy | 100% | Bloqueador |
| PII leakage | 0 | Bloqueador |
| Rollback time | < 30s | Crítico |
| SLA PIX/TED/Conciliação | 100% | Crítico |
| Audit trail completeness | 100% | Crítico |
| Lead time (issue → prod) | < 2h (hotfix) / < 1dia (feature) | Importante |
| Cost per inference | < $0.30 | Importante |
| Chaos resilience | > 99% | Importante |

---

*Esta skill vive no seu Hermes. Atualize quando houver nova regulamentação, padrão arquitetural ou lição de produção.*