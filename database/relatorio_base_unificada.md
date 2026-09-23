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

## 3. Sinalização de granularidade — ponto crítico

A CPTM só publica demanda **agregada por linha**, sem abertura por estação. Para não misturar isso com dados de estação sem aviso, cada linha do CSV carrega a coluna `dados_por_estacao`:

- `Sim` → valor é de uma estação específica (Metrô e linhas privadas)
- `Não` → valor é o total da linha inteira; nesses casos `estacao = "TOTAL"` (todas as 5 linhas de CPTM: 07, 10, 11, 12, 13)

**Recomendação de uso**: se o seu projeto for fazer qualquer análise por estação (mapas, ranking de estações, etc.), filtre `dados_por_estacao == "Sim"` antes. Se for análise por linha/mês (ex.: total transportado), pode usar tudo, mas tenha em mente que os valores de CPTM já vêm em unidade diferente (absoluto, não em milhares) — a coluna `unidade` deixa isso explícito linha a linha.

## 4. Padronização de nomes de arquivo

Os nomes de arquivo vieram corrompidos no zip original (ex.: `Esta#U00e7#U00e3o` no lugar de `Estação`). Foram corrigidos internamente antes da consolidação; a coluna `arquivo_origem` no CSV final já reflete o nome correto.

## 5. Decisões de modelagem e observações de qualidade dos dados

1. **Ano descartado por decisão do projeto.** Como a Linha 5‑Lilás só tem 2024 e as demais 2025, os dados foram unificados por mês, ignorando o ano — conforme combinado. Isso significa que "Janeiro" da Linha 5 é 2024 e "Janeiro" das outras linhas é 2025; não há sobreposição de ano na base atual, mas se novos arquivos de anos diferentes forem adicionados no futuro, o modelo atual vai **somar/misturar meses de anos diferentes na mesma linha** — sinalizo isso para você decidir se quer adicionar uma dimensão de ano mais à frente.
2. **Mês extraído do conteúdo do PDF, não do nome do arquivo.** Em pelo menos 2 casos (Linha 5‑Lilás, arquivos "Outubro de 2024.pdf" e "Setembro de 2024.pdf") o nome do arquivo e o mês real dentro do documento estavam trocados. Usei o texto interno do PDF como fonte da verdade, então o CSV final está correto, mas o arquivo de origem citado pode não bater com o mês se você for conferir manualmente.
3. **Um arquivo da Linha 4‑Amarela ("Fevereiro de 2025.pdf") tem o texto interno dizendo "Fevereiro 2024"**, mesmo estando na leva de arquivos 2025. Isso não afeta o resultado (mês continua único), mas é uma inconsistência da fonte original que vale registrar caso você audite depois.
4. **Números decimais em vírgula (padrão BR)** foram convertidos para ponto.
5. **Marcadores de nota de rodapé** (¹²³⁴*) foram removidos dos nomes de estação.

## 6. Atualização — substituição Linha 8-Diamante (Jan/Fev/Mar/Abr)

Foi identificado que a estação **Ambuitá**, na Linha 8-Diamante, estava com demanda **zerada** em Janeiro, Fevereiro e Março (provável interdição/reforma no período de origem dos dados). A pedido do usuário, os 4 meses inteiros (Janeiro, Fevereiro, Março e Abril) da Linha 8-Diamante — todas as 23 estações + TOTAL, não só Ambuitá — foram **substituídos** pelos dados de 2026 enviados posteriormente (arquivos `Demanda_de_passageiros_por_estação_-_Linha_8_-_{mês}.pdf`).

Consequências para a base:
- `ano_original_arquivo` desses 4 meses agora é `2026` (era 2025 antes da troca).
- `arquivo_origem` desses 4 meses aponta para os novos PDFs.
- Os demais 8 meses da Linha 8-Diamante (Maio–Dezembro) continuam vindo dos arquivos 2025 originais — ou seja, essa linha da base agora mistura 2026 (Jan–Abr) com 2025 (Mai–Dez), o que é consistente com a decisão geral do projeto de ignorar o ano e tratar tudo por mês.
- Total de registros da base não mudou (1.812), pois foi uma substituição 1:1 (96 linhas antigas → 96 novas).

## 7. Arquivos entregues
- `demanda_unificada.csv` — base final
- Este relatório
