# Base unificada de demanda de passageiros - VigiMetro

Neste projeto foram utilizados três arquivos CSV que, juntos, definem a demanda de passageiros, as estações da rede e as conexões entre as linhas.

## Fontes utilizadas

| Fonte | Formato de origem | Dados disponíveis | Ano de referência | Registros |
|---|---|---|---|---:|
| Metrô - Linhas 1, 2, 3 e 15 | CSV | Por estação | 2025 | 840 |
| CPTM - Linhas 7, 10, 11, 12 e 13 | PDF | Por linha | 2025 | 60 |
| Linha 4-Amarela | 11 PDFs mensais | Por estação | 2024/2025 | 144 |
| Linha 5-Lilás | 12 PDFs mensais | Por estação | 2024 | 216 |
| Linha 8-Diamante | PDFs mensais | Por estação | 2025/2026 | 288 |
| Linha 9-Esmeralda | 12 PDFs mensais | Por estação | 2025 | 264 |
| **Total** | | | | **1.812** |

## 1. `demanda_unificada.csv` - Demanda de passageiros

Contém os dados de movimentação de passageiros nas linhas do Metrô, CPTM e concessionárias privadas.

O arquivo contém 1.812 registros, com informações sobre linha, estação, mês, quantidade de passageiros, unidade de medida, tipo de fonte e arquivo de origem.

| Coluna | Descrição |
|---|---|
| `linha` | Nome da linha metroferroviária |
| `estacao` | Nome da estação ou `TOTAL` quando os dados são por linha |
| `mes` | Nome do mês, de janeiro a dezembro, sem ano |
| `passageiros` | Quantidade de passageiros informada pela fonte |
| `unidade` | `milhares de passageiros` ou `passageiros (absoluto)` |
| `fonte_tipo` | `metro_estatal`, `cptm` ou `privada` |
| `dados_por_estacao` | `Sim` ou `Não`, conforme o detalhamento da fonte |
| `ano_original_arquivo` | Ano do arquivo de origem, quando informado |
| `arquivo_origem` | Nome do arquivo original |

Os dados são utilizados para calcular os pesos de demanda das estações, que influenciam os cálculos de exposição e disseminação epidemiológica.

Para as linhas da CPTM sem dados individuais por estação, a demanda total da linha é distribuída igualmente entre as estações cadastradas na topologia.

**Leitura no código:** `Main.java` chama `MalhaMetroferroviaria.carregar(...)`, que executa `lerCsv(...)` em `MalhaMetroferroviaria.java`. O método `rede(...)` usa a demanda para calcular os pesos das estações.

## 2. `topologia_extra.csv` - Estrutura das linhas da CPTM

Define as estações e sua ordem nas linhas da CPTM que possuem apenas dados agregados de passageiros.

| Linha | Estações |
|---|---:|
| Linha 7-Rubi | 17 |
| Linha 10-Turquesa | 15 |
| Linha 11-Coral | 17 |
| Linha 12-Safira | 13 |
| Linha 13-Jade | 3 |
| **Total** | **65** |

Possui duas colunas: `linha` e `estacao`.

O arquivo permite representar individualmente as estações dessas linhas e estabelecer as ligações entre estações consecutivas.

**Leitura no código:** `MalhaMetroferroviaria.carregar(...)` chama `montarNos(...)`, que utiliza `lerTopologia(...)`. Em seguida, `montarArestas(...)` conecta as estações consecutivas.

## 3. `conexoes.csv` - Baldeações entre linhas

Contém 41 conexões explícitas entre estações de diferentes linhas.

| Coluna | Descrição |
|---|---|
| `linhaA` | Linha de origem |
| `estacaoA` | Estação de origem |
| `linhaB` | Linha de destino |
| `estacaoB` | Estação de destino |

O arquivo permite representar as transferências entre linhas, como Sé (Linhas 1 e 3) e Consolação–Paulista (Linhas 2 e 4). Estações com o mesmo nome não são conectadas automaticamente.

Essas conexões são utilizadas pelos algoritmos BFS, Dijkstra e pelo modelo de disseminação para representar a circulação entre as linhas.

**Leitura no código:** `MalhaMetroferroviaria.carregar(...)` chama `montarArestas(...)`, que lê as conexões. A rede resultante é utilizada por `menorNumeroEstacoes(...)` (BFS), `menorExposicao(...)` (Dijkstra) e `simular(...)` (disseminação), em `MalhaMetroferroviaria.java`.

## 4. Linha 8-Diamante - Janeiro a abril

Os dados de janeiro, fevereiro, março e abril foram substituídos integralmente pelos arquivos de 2026, incluindo todas as estações e o total da linha. Os dados de maio a dezembro continuam sendo de 2025.

Os meses da base reúnem dados de anos diferentes e não formam uma série histórica anual uniforme.

## Resumo dos arquivos

| Arquivo | Função | Registros |
|---|---|---:|
| `demanda_unificada.csv` | Demanda de passageiros | 1.812 |
| `topologia_extra.csv` | Estações e sequência das linhas CPTM | 65 |
| `conexoes.csv` | Baldeações entre linhas | 41 |

No fim, os três arquivos permitem construir a rede metroferroviária utilizada pelo projeto, composta por **203 nós, 231 arestas e 41 baldeações**, sem necessidade de banco de dados. São carregados no início da execução do `Main.java`.
