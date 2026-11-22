# Banking Compliance Gates — OPA/Rego Policies
# policies/banking/compliance-gates.rego

package banking.compliance

# =============================================================================
# LGPD (Lei 13.709/2018) — Proteção de Dados Pessoais
# =============================================================================

# Art. 7º: Base legal obrigatória para tratamento de dados pessoais
deny[msg] {
    input.processes_personal_data == true
    not input.has_lgpd_basis
    msg := "LGPD Art. 7º: Base legal obrigatória para tratamento de dados pessoais. Bases válidas: consentimento, contrato, obrigação legal, interesse legítimo, etc."
}

# Art. 11º: Dados sensíveis requerem consentimento específico e destacado
deny[msg] {
    input.contains_sensitive_data == true
    not input.has_explicit_consent
    msg := "LGPD Art. 11º: Consentimento específico e destacado obrigatório para dados sensíveis (origem racial, opinião política, saúde, vida sexual, biométricos, genéticos)."
}

# Art. 16º: Retenção máxima e finalidade
deny[msg] {
    input.data_retention_days > 3650
    not input.has_legal_retention_requirement
    msg := "LGPD Art. 16º: Retenção máxima 10 anos (3650 dias) sem exigência legal específica. Defina política de retenção por tipo de dado."
}

# Art. 18º: Direitos do titular (acesso, correção, eliminação, portabilidade)
deny[msg] {
    input.processes_personal_data == true
    not input.supports_data_subject_rights
    msg := "LGPD Art. 18º: Sistema deve suportar direitos do titular (acesso, retificação, eliminação, anonimização, portabilidade, informação sobre compartilhamento)."
}

# =============================================================================
# BACEN — Banco Central do Brasil
# =============================================================================

# Res. 4.935/2021 — Crédito Consignado
deny[msg] {
    input.domain == "consignado"
    input.margem_consignavel > 0.35
    not input.is_servidor_publico
    msg := "Bacen Res. 4.935 Art. 3º: Margem consignável CLT máxima 35% (servidor público 45%, INSS 30%)."
}

deny[msg] {
    input.domain == "consignado"
    input.taxa_efetiva > input.taxa_teto_vigente
    msg := sprintf("Bacen Res. 4.935 Art. 5º: Taxa efetiva (%.4f) excede teto vigente (%.4f).", [input.taxa_efetiva, input.taxa_teto_vigente])
}

deny[msg] {
    input.domain == "consignado"
    input.averbacao.registrada == false
    input.liberacao_recursos == true
    msg := "Bacen Res. 4.935 Art. 7º: Liberação de recursos só após averbação registrada no convênio."
}

deny[msg] {
    input.domain == "consignado"
    input.portabilidade.solicitada == true
    input.portabilidade.prazo_dias > 5
    msg := "Bacen Res. 4.935 Art. 12º: Portabilidade deve ser concluída em até 5 dias úteis."
}

# Cir. 3.978/2020 — Conciliação Financeira
deny[msg] {
    input.domain == "concilicacao"
    input.sla_minutes > 5
    msg := "Bacen Cir. 3.978: Conciliação deve ser D+0 (SLA < 5 minutos após liquidação)."
}

deny[msg] {
    input.domain == "concilicacao"
    input.match_rate < 0.999
    msg := "Bacen Cir. 3.978: Taxa de match automático deve ser ≥ 99.9%. Exceções requerem investigação manual documentada."
}

# Res. 1/2020 — PIX
deny[msg] {
    input.domain == "pix"
    input.settlement_time_seconds > 10
    msg := "Bacen Res. 1: Liquidação PIX deve ocorrer em < 10 segundos (disponibilidade 99.9%)."
}

deny[msg] {
    input.domain == "pix"
    input.disponibilidade_percent < 99.9
    msg := "Bacen Res. 1: Disponibilidade PIX deve ser ≥ 99.9% (excluindo janelas de manutenção programada)."
}

# =============================================================================
# SOX / Auditoria (Lei 11.941/2009 + CVM)
# =============================================================================

deny[msg] {
    input.domain == "remuneracao_variavel"
    not input.has_immutable_audit_trail
    msg := "SOX: Trilha de auditoria imutável obrigatória para cálculo de remuneração variável. Use Event Sourcing + hash encadeado."
}

deny[msg] {
    input.domain == "remuneracao_variavel"
    input.changes_calculation_rules == true
    not input.has_approval_chain
    msg := "SOX: Alteração de regras de cálculo requer cadeia de aprovação documentada (Gestor → Comitê → RH → Financeiro → Auditoria)."
}

deny[msg] {
    input.changes_financial_rules == true
    not input.has_segregation_of_duties
    msg := "SOX: Separação de funções obrigatória — quem define regra não aprova, quem aprova não executa, quem executa não audita."
}

deny[msg] {
    input.audit_retention_years < 7
    msg := "SOX/CVM: Retenção de registros de auditoria financeira mínima 7 anos."
}

# =============================================================================
# PCI-DSS — Dados de Cartão de Pagamento
# =============================================================================

deny[msg] {
    input.exposes_pan_in_logs == true
    msg := "PCI-DSS Req. 3.2: PAN (número do cartão) NUNCA em logs, traces, métricas, eventos. Use tokenização."
}

deny[msg] {
    input.exposes_cvv_in_any_form == true
    msg := "PCI-DSS Req. 3.2.1: CVV/CVC NUNCA armazenado, logado, transmitido sem criptografia ponta-a-ponta."
}

deny[msg] {
    input.stores_sensitive_auth_data == true
    msg := "PCI-DSS Req. 3.2: Dados sensíveis de autenticação (track data, PIN, CVV) NUNCA armazenados pós-autorização."
}

# =============================================================================
# Segurança Geral & Vault
# =============================================================================

deny[msg] {
    input.uses_hardcoded_secrets == true
    msg := "Vault obrigatório: Zero secrets (senhas, tokens, chaves, certificados) no código, config, variáveis de ambiente, Dockerfiles."
}

deny[msg] {
    input.uses_plaintext_credentials == true
    msg := "Credenciais em texto plano proibidas. Use Vault dynamic secrets, IAM roles, certificados mTLS."
}

deny[msg] {
    input.exposes_pii_in_logs == true
    msg := "PCI-DSS/LGPD: PII (CPF, CNPJ, nome, email, telefone, endereço, conta, agência) NUNCA em logs estruturados ou não estruturados."
}

deny[msg] {
    input.disables_tls_verification == true
    msg := "TLS verification obrigatório em todas as conexões (banco, Kafka, APIs externas, internos)."
}

# =============================================================================
# CMN Res. 4.658 — Cibernética & Continuidade
# =============================================================================

deny[msg] {
    input.rpo_minutes > 60
    msg := "CMN 4.658: RPO (Recovery Point Objective) máximo 60 minutos para sistemas críticos (PIX, conciliação, crédito)."
}

deny[msg] {
    input.rto_minutes > 240
    msg := "CMN 4.658: RTO (Recovery Time Objective) máximo 4 horas para sistemas críticos."
}

deny[msg] {
    input.lacks_disaster_recovery_test == true
    msg := "CMN 4.658: Teste de DR semestral obrigatório com relatório para diretoria."
}

# =============================================================================
# Helpers — Funções de Apoio
# =============================================================================

# Verifica se string contém CPF/CNPJ (regex simplificado)
contains_cpf_cnpj(str) {
    regex.match(`\d{3}\.?\d{3}\.?\d{3}-?\d{2}`, str)
    or
    regex.match(`\d{2}\.?\d{3}\.?\d{3}/?\d{4}-?\d{2}`, str)
}

# Verifica se string contém email
contains_email(str) {
    regex.match(`[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}`, str)
}

# Verifica se string contém número de cartão (Luhn simplificado)
contains_pan(str) {
    regex.match(`\b\d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{4}\b`, str)
}

# =============================================================================
# Testes das Políticas (para validação local)
# =============================================================================

# test/compliance_gates_test.rego
# package banking.compliance
# 
# test_lgpd_basis_required {
#     input := {"processes_personal_data": true, "has_lgpd_basis": false}
#     deny[msg] := "LGPD Art. 7º..."
# }
# 
# test_bacen_margem_clt {
#     input := {"domain": "consignado", "margem_consignavel": 0.40, "is_servidor_publico": false}
#     deny[msg] := "Bacen Res. 4.935..."
# }
# 
# test_pci_pan_in_logs {
#     input := {"exposes_pan_in_logs": true}
#     deny[msg] := "PCI-DSS Req. 3.2..."
# }