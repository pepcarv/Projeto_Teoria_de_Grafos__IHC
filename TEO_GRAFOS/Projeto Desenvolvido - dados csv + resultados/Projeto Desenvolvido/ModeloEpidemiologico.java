/*
Referencias:
- https://docs.oracle.com/javase/tutorial/collections/intro/index.html: Organização 
dos nós e adjacências usando Java Collections.

- https://www.baeldung.com/java-collections: Análise da complexidade de tempo para 
escolher entre ArrayList e LinkedList para a fila do modelo.

- https://stackoverflow.com/questions/2889777/difference-between-hashmap-linkedhashmap-and-treemap: 
Justificativa para o uso do LinkedHashMap para iterar os estágios da infecção na ordem exata de inserção.

- https://www.geeksforgeeks.org/graph-and-its-representations/: Adaptação da estrutura de Grafo (Lista 
de Adjacências) para mapear a rede metroferroviária.

- https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html: Uso de String.format e Locale.US ]
para padronizar o separador decimal das taxas de infecção.

- https://pt.wikipedia.org/wiki/Modelo_epidemiol%C3%B3gico: Consulta da teoria matemática dos modelos 
compartimentais (SIR/SEIR) para programar as transições de estado dos indivíduos (Suscetível, Infectado, Recuperado).

- https://stackoverflow.com/questions/5262308/how-do-implement-a-breadth-first-traversal: Estrutura lógica 
de busca em largura (BFS) para simular a propagação da doença pela malha a partir de um nó inicial.

- https://dzone.com/articles/java-8-streams-and-performance: Uso de Streams API e filter/map para buscar 
rapidamente estações específicas ou somar os totais de casos nos cenários.
*/

import java.util.*;

/*
 Parâmetros epidemiológicos da COVID-19 (valores extraídos dos dois PDFs do projeto).
 Nenhum valor daqui é tratado como probabilidade local de infecção: R0 só calibra o beta da rede
 (ver MalhaMetroferroviaria.beta) e as medidas de proteção entram por transformação de odds.
 */
public class ModeloEpidemiologico {

    /* Cenário de transmissibilidade. */
    public static class Cenario {
        public final String nome, fonte;
        public final double r0;              // R0 (ou Re, quando indicado) alvo para calibrar a rede
        public final double intervaloSerial; // dias
        public final double incubacao;       // dias

        public Cenario(String nome, double r0, double intervaloSerial, double incubacao, String fonte) {
            this.nome = nome; this.r0 = r0; this.intervaloSerial = intervaloSerial;
            this.incubacao = incubacao; this.fonte = fonte;
        }

        /* Período latente (E -> I): intervalo serial ~ latência + metade do período infeccioso. Hipótese do modelo. */
        public double latencia(double duracaoInfecciosa) {
            return Math.max(1.0, intervaloSerial - duracaoInfecciosa / 2.0);
        }

        @Override public String toString() {
            String indice = "R0";
            if (nome.contains("(Re)")) {
                indice = "Re";
            }
            return String.format(Locale.US, "%s (%s=%.2f, int. serial=%.1f d, incubação=%.2f d)",
                    nome, indice, r0, intervaloSerial, incubacao);
        }
    }

    /* Cenário de proteção, expresso como odds ratio. */
    public static class Protecao {
        public final String nome, fonte;
        public final double oddsRatio;
        public Protecao(String nome, double oddsRatio, String fonte) {
            this.nome = nome; this.oddsRatio = oddsRatio; this.fonte = fonte;
        }
        @Override public String toString() { return String.format(Locale.US, "%s (OR=%.2f)", nome, oddsRatio); }
    }

    public final String doenca = "COVID-19";
    public final double duracaoInfecciosa = 6.3;   // dias (Rahmani et al., 2022)
    public final double fracAssintomatica = 0.351; // Sah et al., 2021
    public final double sarDomiciliarRef = 0.166;  // Madewell et al., 2020 (só referência de ordem de grandeza)
    public final double ifrRef = 0.0068;           // Meyerowitz-Katz & Merone, 2020 (extensão opcional, não usado)

    public final List<Cenario> cenarios = new ArrayList<>();
    public final List<Protecao> protecoes = new ArrayList<>();

    public ModeloEpidemiologico() {
        cenarios.add(new Cenario("Ancestral (R0 baixo)", 2.2, 5.2, 5.2, "R0: Li et al. 2020; int. serial: Alene 2021; incub.: Li 2020"));
        cenarios.add(new Cenario("Agrupado estudos iniciais", 2.66, 5.2, 6.5, "R0: Dhungel 2022; int. serial e incub.: Alene 2021"));
        cenarios.add(new Cenario("Delta", 5.08, 3.9, 5.2, "R0: Liu & Rocklöv 2021; int. serial: Madewell 2023; incub. ancestral (sem valor Delta nos PDFs)"));
        cenarios.add(new Cenario("Ômicron (Re)", 4.20, 3.2, 3.49, "Re: Liu 2022 (Re, não R0); int. serial: Madewell 2023; incub. BA.1: Xu 2023"));

        protecoes.add(new Protecao("Sem proteção", 1.0, "referência"));
        protecoes.add(new Protecao("Proteção intermediária", 0.38, "Li et al. 2021 (OR 0,38; IC 0,21-0,69)"));
        protecoes.add(new Protecao("Proteção elevada", 0.15, "Chu et al. 2020 (aOR 0,15; IC 0,07-0,34)"));
    }

    /* Probabilidade protegida: odds = p/(1-p); odds_protegidas = odds*OR;
       p_protegida = odds_protegidas/(1+odds_protegidas). */
    public static double aplicarOR(double p, double or) {
       
       
        if (p <= 0) return 0;
        if (p >= 1) return 1;
        double odds = p / (1 - p) * or;
        return odds / (1 + odds);
    }

    public Map<String, Object> paraMapa() {
        Map<String, Object> m = new LinkedHashMap<>();

       // Parâmetros constantes usados para descrever a simulação.
        m.put("doenca", doenca);
        m.put("duracao_infecciosa_dias", duracaoInfecciosa);
        m.put("fracao_assintomatica", fracAssintomatica);
        m.put("sar_domiciliar_referencia", sarDomiciliarRef);
        List<Object> cs = new ArrayList<>();
        
        
        for (Cenario c : cenarios) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("nome", c.nome); x.put("r0", c.r0); x.put("intervalo_serial", c.intervaloSerial);
            x.put("incubacao", c.incubacao); x.put("latencia_derivada", c.latencia(duracaoInfecciosa)); x.put("fonte", c.fonte);
            cs.add(x);
        }
        m.put("cenarios", cs);
        
        List<Object> ps = new ArrayList<>();
        
        
        //
        for (Protecao p : protecoes) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("nome", p.nome); x.put("odds_ratio", p.oddsRatio); x.put("fonte", p.fonte);
            ps.add(x);
        }
        m.put("protecoes", ps);
        return m;
    }
}
