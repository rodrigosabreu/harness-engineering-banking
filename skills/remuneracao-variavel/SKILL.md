---
name: remuneracao-variavel
description: "Skill para Remuneração Variável — regras complexas, motor de regras (Drools/Easy Rules), auditoria total (SOX), cadeia de aprovação, event sourcing imutável."
version: "1.0.0"
category: banking-domain
requires: ["harness-engineering-banking"]
---

# Skill: Remuneração Variável

> **Domínio**: Cálculo de remuneração variável (PLR, bônus, comissões, metas) com auditoria total.
> **Regulatório**: CLT, Convenções Coletivas, SOX (Lei 11.941/2009), LGPD, Auditoria Interna/Externa.
> **Criticidade**: Máxima — erro = passivo trabalhista + multa SOX + reputação.

---

## 1. Contexto de Negócio

### 1.1 Tipos de Remuneração Variável

| Tipo | Periodicidade | Base de Cálculo | Complexidade | Auditoria |
|------|---------------|-----------------|--------------|-----------|
| **PLR (Participação Lucros/Resultados)** | Semestral/Anual | Lucro líquido + metas | Alta (fórmulas complexas) | SOX + Sindicato |
| **Bônus Executivo** | Anual | KPIs individuais + corporativos | Muito Alta (multi-nível) | SOX + Comitê |
| **Comissão Vendas** | Mensal | Receita/margem por produto | Média (regras por canal) | SOX + Compliance |
| **Incentivo Curto Prazo (ICP)** | Trimestral | Metas táticas | Média | Gestor + RH |
| **Reconhecimento/Spot** | Eventual | Decisão gestor | Baixa | Gestor + RH |

### 1.2 Cadeia de Aprovação (SOX - Separação de Funções)

```mermaid
flowchart TD
    A[Input: Metas, Resultados, Regras] --> B[Motor de Regras\n(Drools/Easy Rules)]
    B --> C[Cálculo Bruto\npor Colaborador]
    C --> D[Validação Automática\nRegras + Limites]
    D --> E{Validação OK?}
    E -->|Não| F[Rejeita + Log Auditoria]
    E -->|Sim| G[Gera Proposta\nCálculo Detalhado]
    G --> H[Aprovação Nível 1\nGestor Direto]
    H --> I{Aprova?}
    I -->|Não| J[Retorna p/ Ajuste\nLog Motivo]
    I -->|Sim| K[Aprovação Nível 2\nComitê/Comissão PLR]
    K --> L{Aprova?}
    L -->|Não| M[Retorna p/ Ajuste\nLog Motivo]
    L -->|Sim| N[Aprovação Nível 3\nRH Corporativo + Financeiro]
    N --> O{Aprova?}
    O -->|Não| P[Retorna p/ Ajuste]
    O -->|Sim| Q[Gera Folha Oficial\nEvent Sourcing Imutável]
    Q --> R[Event Sourcing\nAudit Trail Imutável]
    R --> S[Pagamento via Folha]
    S --> T[Conciliação Bancária\nPagamento vs Cálculo]
```

### 1.3 Regras SOX (Não Negociáveis)

| Regra SOX | Implementação Técnica |
|-----------|----------------------|
| **Separação de Funções** | Quem define regra ≠ quem aprova ≠ quem executa ≠ quem audita |
| **Trilha Imutável** | Event Sourcing + Hash encadeado (Merkle tree) + Assinatura digital |
| **Cadeia de Aprovação** | Mínimo 3 níveis (Gestor → Comitê → RH/Financeiro) |
| **Rastreabilidade Total** | Input → Regra → Cálculo Intermediário → Resultado → Aprovação → Pagamento |
| **Retenção 7 anos** | Event store imutável + backup geográfico + hash verification |
| **Acesso Controlado** | RBAC + MFA + Auditoria de acesso (quem viu o quê, quando) |
| **Alteração de Regra** | Versionamento + Aprovação em cadeia + Effective date controlado |

---

## 2. Modelos de Domínio

### 2.1 Aggregate: CalculoRemuneracao

```java
// domain/model/CalculoRemuneracao.java
@Entity
public class CalculoRemuneracao {
    
    @Id
    private CalculoId id;                       // UUID v7
    
    private PeriodoApuracao periodo;            // Value Object (início/fim + tipo)
    
    @Enumerated(EnumType.STRING)
    private TipoRemuneracao tipo;               // PLR, BONUS, COMISSAO, ICP, SPOT
    
    @Embedded
    private ColaboradorId colaboradorId;        // Value Object (matrícula hash)
    
    private BigDecimal valorBruto;              // Resultado do motor de regras
    private BigDecimal valorLiquido;            // Após descontos (IR, INSS, etc.)
    
    @Enumerated(EnumType.STRING)
    private StatusCalculo status;               // RASCUNHO, VALIDADO, APROVADO_N1, APROVADO_N2, APROVADO_N3, OFICIAL, PAGO, CANCELADO
    
    private RuleExecutionTrace trace;           // Rastro completo da execução
    
    private List<Aprovacao> aprovacoes;         // Cadeia de aprovação
    
    private String hashAnterior;                // Hash do cálculo anterior (Merkle chain)
    private String hashAtual;                   // Hash deste cálculo (SHA-256)
    
    private Instant dataCalculo;                // Quando calculou
    private Instant dataOficializacao;          // Quando virou OFICIAL
    
    // Factory: Cálculo inicial (RASCUNHO)
    public static CalculoRemuneracao calcular(SolicitacaoCalculo cmd, RuleEngine engine) {
        CalculoRemuneracao c = new CalculoRemuneracao();
        c.id = CalculoId.novo();
        c.periodo = cmd.getPeriodo();
        c.tipo = cmd.getTipoRemuneracao();
        c.colaboradorId = cmd.getColaboradorId();
        
        // Executa motor de regras (auditado)
        RuleExecutionResult result = engine.executar(cmd.getParametros());
        
        // Validações automáticas
        validarResultado(result);
        
        c.valorBruto = result.getValorBruto();
        c.valorLiquido = result.getValorLiquido();
        c.trace = result.getTrace(); // Rastro completo: regra → input → output
        c.status = StatusCalculo.RASCUNHO;
        c.dataCalculo = Instant.now();
        c.hashAtual = calcularHash(c); // SHA-256 do cálculo completo
        
        // Domain Event
        c.registerEvent(new CalculoGerado(c.id, c.colaboradorId, c.tipo, c.periodo, c.valorBruto, c.trace));
        
        return c;
    }
    
    // Command: Aprovação (cadeia SOX)
    public void aprovar(Aprovacao aprovacao) {
        validarProximaAprovacao(aprovacao.getNivel());
        
        this.aprovacoes.add(aprovacao);
        
        if (aprovacao.getNivel() == 1) this.status = StatusCalculo.APROVADO_N1;
        else if (aprovacao.getNivel() == 2) this.status = StatusCalculo.APROVADO_N2;
        else if (aprovacao.getNivel() == 3) {
            this.status = StatusCalculo.OFICIAL;
            this.dataOficializacao = Instant.now();
            this.hashAnterior = this.hashAtual; // Hash anterior vira "pai"
            this.hashAtual = calcularHash(this); // Novo hash (Merkle chain)
        }
        
        this.registerEvent(new CalculoAprovado(this.id, aprovacao));
    }
    
    // Command: Oficializa (após N3) — gera evento imutável para pagamento
    public void oficializar() {
        if (this.status != StatusCalculo.OFICIAL) {
            throw new IllegalStateException("Só oficializa após aprovação N3");
        }
        this.registerEvent(new CalculoOficializado(this.id, this.valorLiquido, this.hashAtual));
    }
}
```

### 2.2 Value Objects

```java
// domain/model/valueobjects/

@Value
@Builder
public class PeriodoApuracao {
    LocalDate inicio;
    LocalDate fim;
    TipoPeriodo tipo;           // MENSAL, TRIMESTRAL, SEMESTRAL, ANUAL, EVENTUAL
    String descricao;           // "PLR 1º Semestre 2024"
}

@Value
public class ColaboradorId {
    String value; // Matrícula hash + salt
}

@Value
public class RuleExecutionTrace {
    List<RuleExecutionStep> steps;     // Cada regra executada
    Map<String, Object> inputs;        // Inputs do motor
    Map<String, Object> intermediates; // Valores intermediários
    BigDecimal valorFinalBruto;
    BigDecimal valorFinalLiquido;
    Duration tempoExecucaoMs;
    String engineVersao;               // Drools 8.x / Easy Rules 3.x
    String rulesetVersao;              // Versão do ruleset (Git commit)
}

@Value
@Builder
public class RuleExecutionStep {
    String ruleName;           // Nome da regra (ex: "PLR_META_LUCRO_10%")
    String ruleType;           // CONDITION, CALCULATION, VALIDATION, LIMIT
    Map<String, Object> input; // Inputs da regra
    Object output;             // Output da regra
    Duration durationMs;
    boolean fired;             // Se a regra disparou
    String salience;           // Prioridade (Drools)
}

@Value
public class Aprovacao {
    int nivel;                 // 1=Gestor, 2=Comitê, 3=RH/Financeiro
    String aprovadorId;        // User ID (hash)
    String aprovadorNome;      // Nome (para auditoria)
    Instant timestamp;
    String justificativa;      // Obrigatório se REJEITADO
    boolean aprovado;          // true/false
    String ipOrigem;           // Para auditoria
    String userAgent;          // Para auditoria
}
```

### 2.3 Domain Events (SOX Audit Trail)

```java
// domain/event/

@Value
public class CalculoGerado implements DomainEvent {
    String eventType = "CalculoGerado";
    CalculoId aggregateId;
    ColaboradorId colaboradorId;
    TipoRemuneracao tipo;
    PeriodoApuracao periodo;
    BigDecimal valorBruto;
    RuleExecutionTrace trace;      // Rastro completo (SOX)
    Instant timestamp = Instant.now();
    String correlationId;
    String causationId;
    String hashCalculo;            // SHA-256 do cálculo
}

@Value
public class CalculoAprovado implements DomainEvent {
    String eventType = "CalculoAprovado";
    CalculoId aggregateId;
    Aprovacao aprovacao;           // Nível, aprovador, justificativa, timestamp
    StatusCalculo novoStatus;
    Instant timestamp = Instant.now();
    String correlationId;
    String causationId;
}

@Value
public class CalculoOficializado implements DomainEvent {
    String eventType = "CalculoOficializado";
    CalculoId aggregateId;
    BigDecimal valorLiquido;       // Valor final para pagamento
    String hashCalculo;            // Hash do cálculo (Merkle chain)
    String hashAnterior;           // Hash do cálculo anterior (chain)
    Instant timestamp = Instant.now();
    String correlationId;
    String causationId;
}

@Value
public class CalculoCancelado implements DomainEvent {
    String eventType = "CalculoCancelado";
    CalculoId aggregateId;
    String motivo;                 // Ex: "Erro na regra X", "Desligamento colaborador"
    String canceladoPorId;
    Instant timestamp = Instant.now();
    String correlationId;
    String causationId;
}
```

---

## 3. Motor de Regras (Drools / Easy Rules)

### 3.1 Arquitetura do Motor

```java
// domain/service/RuleEngine.java
@Component
public class RuleEngine {
    
    private final KieContainer kieContainer;           // Drools
    private final RulesetRepository rulesetRepo;       // Versionamento Git
    private final BankingTracer tracer;
    
    public RuleExecutionResult executar(RuleEngineInput input) {
        return tracer.traceCommand("remuneracao", "executarRegras", input.getCalculoId(), () -> {
            // 1. Carrega ruleset versionado (Git commit hash)
            KieSession kieSession = kieContainer.newKieSession();
            
            // 2. Insere facts
            kieSession.insert(input.getParametros());      // Facts: metas, resultados, colaborador
            kieSession.insert(input.getColaborador());     // Fato: colaborador
            kieSession.insert(input.getRegrasConfig());    // Config: tetos, limites
            
            // 3. Agenda Event Listener (auditoria step-by-step)
            kieSession.addEventListener(new RuleExecutionListener());
            
            // 4. Dispara regras
            int fired = kieSession.fireAllRules();
            
            // 5. Extrai resultados + trace
            RuleExecutionResult result = extrairResultado(kieSession);
            result.setRulesFired(fired);
            result.setEngineVersao("Drools " + KieServices.Factory.get().getVersion());
            result.setRulesetVersao(rulesetRepo.getCurrentCommitHash());
            
            kieSession.dispose();
            return result;
        });
    }
}
```

### 3.2 Exemplo de Regras (DRL)

```drools
// rules/plr/PLR_Meta_Lucro.drl
package br.com.itau.remuneracao.rules.plr

import br.com.itau.remuneracao.domain.model.*;

rule "PLR_META_LUCRO_10%"
    salience 100
    when
        $param : ParametrosCalculo(periodo.tipo == TipoPeriodo.SEMESTRAL)
        $lucro : LucroLiquido(valor > 0) from collect(
            MetraResultado(tipo == MetricaTipo.LUCRO_LIQUIDO, periodo == $param.periodo)
        )
        $meta : Meta(tipo == MetaTipo.LUCRO_LIQUIDO, percentualAtingimento >= 1.0)
    then
        // Cálculo: 10% do lucro líquido distribuído proporcionalmente
        BigDecimal valorDistribuir = $lucro.getValor().multiply(BigDecimal.valueOf(0.10));
        
        // Distribuição proporcional ao salário base
        BigDecimal valorColaborador = valorDistribuir
            .multiply($colaborador.getSalarioBase())
            .divide($folhaTotal.getSalariosTotal(), 2, RoundingMode.HALF_UP);
        
        // Aplica teto (ex: 3 salários)
        BigDecimal teto = $colaborador.getSalarioBase().multiply(BigDecimal.valueOf(3));
        if (valorColaborador.compareTo(teto) > 0) {
            valorColaborador = teto;
        }
        
        // Registra passo no trace
        RuleExecutionStep step = new RuleExecutionStep(
            "PLR_META_LUCRO_10%", "CALCULATION",
            Map.of("lucroLiquido", $lucro.getValor(), "salarioBase", $colaborador.getSalarioBase()),
            valorColaborador, Duration.ZERO, true, "100"
        );
        $trace.addStep(step);
        
        // Adiciona ao resultado intermediário
        $intermediates.put("plr_meta_lucro", valorColaborador);
end

rule "PLR_TETO_3_SALARIOS"
    salience 90
    when
        $intermediates : Map()
        $plr : BigDecimal() from $intermediates.get("plr_meta_lucro")
        $colaborador : Colaborador()
        $teto : BigDecimal($colaborador.getSalarioBase().multiply(BigDecimal.valueOf(3)))
        eval($plr.compareTo($teto) > 0)
    then
        // Aplica teto
        $intermediates.put("plr_meta_lucro", $teto);
        
        RuleExecutionStep step = new RuleExecutionStep(
            "PLR_TETO_3_SALARIOS", "VALIDATION",
            Map.of("valorOriginal", $plr, "teto", $teto),
            $teto, Duration.ZERO, true, "90"
        );
        $trace.addStep(step);
end

rule "PLR_MINIMO_GARANTIDO"
    salience 80
    when
        $intermediates : Map()
        $plr : BigDecimal() from $intermediates.get("plr_meta_lucro")
        $colaborador : Colaborador(salarioBase > 0)
        $minimo : BigDecimal($colaborador.getSalarioBase().multiply(BigDecimal.valueOf(0.5))) // 50% salário
        eval($plr.compareTo($minimo) < 0)
    then
        // Garante mínimo 50% salário
        $intermediates.put("plr_meta_lucro", $minimo);
        
        RuleExecutionStep step = new RuleExecutionStep(
            "PLR_MINIMO_GARANTIDO", "VALIDATION",
            Map.of("valorOriginal", $plr, "minimo", $minimo),
            $minimo, Duration.ZERO, true, "80"
        );
        $trace.addStep(step);
end
```

---

## 4. Event Sourcing + Auditoria Imutável (SOX)

### 4.1 Event Store

```java
// infrastructure/persistence/EventStore.java
@Repository
public class EventStore {
    
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    
    @Transactional
    public void append(DomainEvent event) {
        String sql = """
            INSERT INTO event_store (event_id, aggregate_id, event_type, payload, 
                                     correlation_id, causation_id, timestamp, hash_chain)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        String payload = mapper.writeValueAsString(event);
        String hashChain = calcularHashChain(event.getAggregateId(), payload);
        
        jdbc.update(sql, 
            event.getEventId(), event.getAggregateId().value(), 
            event.getEventType(), payload,
            event.getCorrelationId(), event.getCausationId(),
            event.getTimestamp(), hashChain);
    }
    
    private String calcularHashChain(String aggregateId, String payload) {
        // Hash encadeado: SHA-256(previous_hash + payload + timestamp)
        String previousHash = jdbc.queryForObject(
            "SELECT hash_chain FROM event_store WHERE aggregate_id = ? ORDER BY timestamp DESC LIMIT 1",
            String.class, aggregateId);
        
        String input = (previousHash != null ? previousHash : "") + payload + Instant.now().toEpochMilli();
        return sha256(input);
    }
    
    // Verificação de integridade (auditoria)
    public VerificationResult verifyChain(String aggregateId) {
        List<EventRecord> events = jdbc.query(
            "SELECT * FROM event_store WHERE aggregate_id = ? ORDER BY timestamp",
            new EventRecordRowMapper(), aggregateId);
        
        String expectedHash = null;
        for (EventRecord event : events) {
            String calculated = sha256((expectedHash != null ? expectedHash : "") + event.getPayload() + event.getTimestamp());
            if (!calculated.equals(event.getHashChain())) {
                return VerificationResult.failed(event.getEventId(), "Hash chain broken");
            }
            expectedHash = event.getHashChain();
        }
        return VerificationResult.success();
    }
}
```

### 4.2 Auditoria SOX (Query API)

```java
// infrastructure/audit/SoxAuditService.java
@Service
@RequiredArgsConstructor
public class SoxAuditService {
    
    private final EventStore eventStore;
    private final CalculoRemuneracaoRepository calculoRepo;
    
    // Relatório SOX: Trilha completa de um cálculo
    public SoxAuditReport gerarRelatorio(CalculoId calculoId) {
        CalculoRemuneracao calculo = calculoRepo.findById(calculoId).orElseThrow();
        List<DomainEvent> events = eventStore.findByAggregateId(calculoId);
        VerificationResult integrity = eventStore.verifyChain(calculoId.value());
        
        return SoxAuditReport.builder()
            .calculoId(calculoId)
            .colaboradorId(calculo.getColaboradorId())
            .periodo(calculo.getPeriodo())
            .tipo(calculo.getTipo())
            .valorBruto(calculo.getValorBruto())
            .valorLiquido(calculo.getValorLiquido())
            .status(calculo.getStatus())
            .aprovacoes(calculo.getAprovacoes())
            .trace(calculo.getTrace())
            .eventos(events)
            .hashChain(calculo.getHashAtual())
            .integrityCheck(integrity)
            .build();
    }
    
    // Relatório de conformidade SOX (todos os cálculos do período)
    public SoxComplianceReport gerarRelatorioConformidade(PeriodoApuracao periodo) {
        List<CalculoRemuneracao> calculos = calculoRepo.findByPeriodo(periodo);
        
        long total = calculos.size();
        long oficializados = calculos.stream().filter(c -> c.getStatus() == OFICIAL).count();
        long comAprovacaoCompleta = calculos.stream()
            .filter(c -> c.getAprovacoes().size() >= 3).count();
        long comTraceCompleto = calculos.stream()
            .filter(c -> c.getTrace() != null && c.getTrace().getSteps().size() > 0).count();
        long integridadeOk = calculos.stream()
            .filter(c -> eventStore.verifyChain(c.getId().value()).isSuccess()).count();
        
        return SoxComplianceReport.builder()
            .periodo(periodo)
            .totalCalculos(total)
            .oficializados(oficializados)
            .taxaOficializacao((double) oficializados / total)
            .comAprovacaoCompleta(comAprovacaoCompleta)
            .taxaAprovacaoCompleta((double) comAprovacaoCompleta / total)
            .comTraceCompleto(comTraceCompleto)
            .taxaTraceCompleto((double) comTraceCompleto / total)
            .integridadeOk(integridadeOk)
            .taxaIntegridade((double) integridadeOk / total)
            .build();
    }
}
```

---

## 5. Testes (Eval Gates)

### 5.1 Golden Set (Cálculos)

```yaml
# evals/golden-set-remuneracao.yaml
test_cases:
  - id: "rem-001-plr-meta-lucro"
    description: "PLR - Meta lucro 10% + teto 3 salários + mínimo 50% salário"
    input:
      periodo: {inicio: "2024-01-01", fim: "2024-06-30", tipo: "SEMESTRAL"}
      tipo: "PLR"
      colaborador: {matricula: "EMP001", salarioBase: 10000.00, cargo: "GERENTE"}
      metricas: {lucroLiquido: 50000000.00, folhaTotal: 10000000.00}
      metas: {lucroLiquido: {atingimento: 1.2}}
    expected:
      valorBruto: 6000.00          # 10% lucro * proporção salarial
      valorLiquido: 6000.00        # Sem teto aplicado (3 salários = 30000 > 6000)
      regrasDisparadas: ["PLR_META_LUCRO_10%", "PLR_TETO_3_SALARIOS"]
  
  - id: "rem-002-plr-teto-3-salarios"
    description: "PLR - Teto 3 salários aplicado"
    input:
      periodo: {inicio: "2024-01-01", fim: "2024-06-30", tipo: "SEMESTRAL"}
      tipo: "PLR"
      colaborador: {matricula: "EMP002", salarioBase: 5000.00}
      metricas: {lucroLiquido: 100000000.00, folhaTotal: 5000000.00}
      metas: {lucroLiquido: {atingimento: 2.0}}
    expected:
      valorBruto: 15000.00         # Teto: 3 * 5000 = 15000
      valorLiquido: 15000.00
      regrasDisparadas: ["PLR_META_LUCRO_10%", "PLR_TETO_3_SALARIOS"]
  
  - id: "rem-003-plr-minimo-garantido"
    description: "PLR - Mínimo garantido 50% salário"
    input:
      periodo: {inicio: "2024-01-01", fim: "2024-06-30", tipo: "SEMESTRAL"}
      tipo: "PLR"
      colaborador: {matricula: "EMP003", salarioBase: 8000.00}
      metricas: {lucroLiquido: 1000000.00, folhaTotal: 10000000.00}
      metas: {lucroLiquido: {atingimento: 0.5}}
    expected:
      valorBruto: 4000.00          # Mínimo: 50% * 8000 = 4000
      valorLiquido: 4000.00
      regrasDisparadas: ["PLR_META_LUCRO_10%", "PLR_MINIMO_GARANTIDO"]

thresholds:
  calculation_accuracy: 1.0
  regulatory_compliance: 1.0
  audit_trail_completeness: 1.0
  approval_chain_completeness: 1.0
  hash_chain_integrity: 1.0
  hallucination_rate: 0.0
```

### 5.2 SOX Compliance Tests

```java
// test/compliance/SoxComplianceTest.java
@SpringBootTest
class SoxComplianceTest {
    
    @Autowired
    private SoxAuditService auditService;
    
    @Autowired
    private EventStore eventStore;
    
    @Test
    void deveManterTrilhaImutavelComHashChain() {
        // Given: Calcula PLR
        CalculoId id = calculoService.calcular(...);
        calculoService.aprovarN1(...);
        calculoService.aprovarN2(...);
        calculoService.aprovarN3(...);
        calculoService.oficializar(id);
        
        // When: Verifica integridade
        VerificationResult result = eventStore.verifyChain(id.value());
        
        // Then: Hash chain íntegro
        assertTrue(result.isSuccess());
    }
    
    @Test
    void deveTerCadeiaAprovacaoCompletaSOX() {
        // Given: Cálculo oficializado
        CalculoId id = ...;
        
        // When: Audita
        SoxAuditReport report = auditService.gerarRelatorio(id);
        
        // Then: 3 níveis de aprovação + trace completo + hash chain
        assertEquals(3, report.getAprovacoes().size());
        assertEquals(StatusCalculo.OFICIAL, report.getStatus());
        assertNotNull(report.getTrace());
        assertEquals(3, report.getAprovacoes().stream().map(Aprovacao::getNivel).distinct().count());
        assertTrue(report.getIntegrityCheck().isSuccess());
    }
    
    @Test
    void deveDetectarTentativaDeAlteracaoPosOficializacao() {
        // Given: Cálculo oficializado
        CalculoId id = ...;
        
        // When: Tenta alterar valor diretamente no DB (simula ataque)
        jdbc.update("UPDATE calculo_remuneracao SET valor_bruto = 99999 WHERE id = ?", id.value());
        
        // When: Verifica integridade
        VerificationResult result = eventStore.verifyChain(id.value());
        
        // Then: Detecta quebra de hash chain
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Hash chain broken"));
    }
}
```

---

## 6. Pipeline Específico (Harness)

```yaml
# .harness/ci/remuneracao-ci.yaml
stages:
  - stage: Remuneração Specific Tests
    identifier: remuneracao_tests
    spec:
      execution:
        steps:
          - step:
              name: Rule Engine Tests (Drools)
              identifier: drools_tests
              type: Run
              spec:
                command: ./mvnw test -Dtest=*RuleEngineTest,*PLRTest,*BonusTest
          - step:
              name: SOX Compliance Tests
              identifier: sox_tests
              type: Run
              spec:
                command: ./mvnw test -Dtest=*SoxComplianceTest,*AuditTrailTest
          - step:
              name: Hash Chain Integrity Tests
              identifier: hash_chain_tests
              type: Run
              spec:
                command: ./mvnw test -Dtest=*HashChainTest,*EventStoreTest
          - step:
              name: Approval Chain Tests (SOX)
              identifier: approval_chain_tests
              type: Run
              spec:
                command: ./mvnw test -Dtest=*ApprovalChainTest,*SoxApprovalTest
          - step:
              name: Event Sourcing Tests
              identifier: event_sourcing_tests
              type: Run
              spec:
                command: ./mvnw test -Dtest=*EventStoreTest,*EventSourcingTest
          - step:
              name: Calculation Accuracy (BigDecimal Precision)
              identifier: precision_tests
              type: Run
              spec:
                command: ./mvnw test -Dtest=*BigDecimalTest,*RoundingTest
```

---

## 7. Checklist de Deploy (Remuneração Variável)

```markdown
# Pré-deploy Remuneração Variável
- [ ] Golden set: 100% accuracy (BigDecimal, HALF_UP, scale correto)
- [ ] SOX Compliance: 100% (trilha imutável, hash chain, 3 aprovações)
- [ ] Motor de regras: Versionado (Git commit hash), testado isoladamente
- [ ] Cadeia de aprovação: N1→N2→N3 obrigatório para OFICIAL
- [ ] Event Sourcing: Hash chain íntegro (Merkle tree), verificação automática
- [ ] Auditoria: Relatório SOX gerável por cálculo + período
- [ ] Retenção: 7 anos (event store imutável + backup geo)
- [ ] RBAC + MFA: Acesso a cálculos/relatórios auditado
- [ ] Rollback: < 30s (feature flag kill switch por colaborador/período)
- [ ] Métricas: Taxa oficialização, integridade hash, tempo aprovação
- [ ] LGPD: Dados sensíveis (salário, CPF) anonimizados em logs/eventos
```

---

## 8. Métricas de Sucesso

| Métrica | Target | Alert |
|---------|--------|-------|
| Calculation Accuracy | 100% | < 100% |
| SOX Compliance Rate | 100% | < 100% |
| Hash Chain Integrity | 100% | < 100% |
| Approval Chain Completeness | 100% | < 100% |
| Audit Trail Completeness | 100% | < 100% |
| Time to Officialize | < 24h | > 48h |
| Rollback Time | < 30s | > 60s |
| PII Leakage | 0 | > 0 |

---

*Skill versionada — atualize com nova convenção coletiva, mudança SOX, nova regra de negócio, ou lição de produção.*