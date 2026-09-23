# Relatório — Base Unificada de Demanda de Passageiros (Metrô SP)

## 1. Fontes processadas

| Fonte | Formato original | Granularidade | Ano(s) na origem | Registros gerados |
|---|---|---|---|---|
| Metrô (Linhas 1, 2, 3, 15) | CSV (na verdade não é .xlsx) | Por estação | 2025 | 840 |
| CPTM (Linhas 07, 10, 11, 12, 13) | PDF, 1 tabela | **Por linha (sem estação)** | 2025 | 60 |
| Linha 4‑Amarela | 11 PDFs mensais | Por estação | 2025 | 144 |
| Linha 5‑Lilás | 12 PDFs mensais | Por estação | 2024 | 216 |
| Linha 8‑Diamante | 12 PDFs mensais | Por estação | 2025 | 288 |
| Linha 9‑Esmeralda | 12 PDFs mensais | Por estação | 2025 | 264 |
| **Total** | | | | **1.812 linhas** |

## 2. Estrutura do CSV final (`demanda_unificada.csv`)

| Coluna | Descrição |
|---|---|
| `linha` | Nome da linha (ex.: "Linha 1-Azul", "Linha 07") |
| `estacao` | Nome da estação, ou `TOTAL` quando a fonte não abre por estação |
| `mes` | Nome do mês (Janeiro–Dezembro), **sem ano** — conforme solicitado |
| `passageiros` | Valor numérico de demanda |
| `unidade` | `milhares de passageiros` (Metrô e privadas) ou `passageiros (absoluto)` (CPTM) |
| `fonte_tipo` | `metro_estatal`, `cptm` ou `privada` |
| `dados_por_estacao` | **`Sim`** ou **`Não`** — sinalização formal de granularidade (ver seção 3) |
| `ano_original_arquivo` | Ano do arquivo de origem, mantido só como referência (não usado na chave dos dados) |
| `arquivo_origem` | Nome do arquivo original (nomes corrigidos, ver seção 4) |


## 3. Linha 8-Diamante (Jan/Fev/Mar/Abr)

Foi identificado que a estação **Ambuitá**, na Linha 8-Diamante, estava com demanda **zerada** em Janeiro, Fevereiro e Março (provável interdição/reforma no período de origem dos dados). A pedido do usuário, os 4 meses inteiros (Janeiro, Fevereiro, Março e Abril) da Linha 8-Diamante — todas as 23 estações + TOTAL, não só Ambuitá — foram **substituídos** pelos dados de 2026 enviados posteriormente (arquivos `Demanda_de_passageiros_por_estação_-_Linha_8_-_{mês}.pdf`).

Consequências para a base:
- `ano_original_arquivo` desses 4 meses agora é `2026` (era 2025 antes da troca).
- `arquivo_origem` desses 4 meses aponta para os novos PDFs.
- Os demais 8 meses da Linha 8-Diamante (Maio–Dezembro) continuam vindo dos arquivos 2025 originais — ou seja, essa linha da base agora mistura 2026 (Jan–Abr) com 2025 (Mai–Dez), o que é consistente com a decisão geral do nosso projeto de fazer conclusões preliminares.

## 7. Arquivos 
- `demanda_unificada.csv` — base final
