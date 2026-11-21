/*
Projeto da Disciplina de Teoria dos Grafos - Partes 2 e 3
Arquivo: Main.java
Conteudo: Aplicacao com menu de opcoes, leitura de arquivos,
          manipulação do CSV de demandas.

     
autor Pedro Henrique Carvalho Pereira - 10418861
autor Mateus Ribeiro Cerqueira - 10443901

Compilação e Execução:
javac *.java && java Main ./demanda_unificada.csv



Referencias:
- https://docs.oracle.com/javase/tutorial/essential/io/fileio.html: Base para gerenciar arquivos 
com a API NIO.2 do Java.

- https://docs.oracle.com/javase/tutorial/essential/io/pathOps.html: Uso da classe Path para resolver 
diretórios independentes de sistema operacional.

- https://www.baeldung.com/java-csv: Comparativo de abordagens para leitura de CSV puramente em Java 
sem bibliotecas externas.

- https://stackoverflow.com/questions/5375775/reading-a-csv-file-in-java: Discussão sobre como lidar 
com aspas e separadores complexos usando String.split(",").

- https://mkyong.com/java8/java-8-how-to-read-a-file-into-memory/: Aplicação de Files.readAllLines() 
para carregar dados em memória rapidamente.

- https://www.javatpoint.com/java-io-vs-nio: Entendimento das diferenças entre I/O bloqueante (java.io) 
e não-bloqueante (java.nio) para lidar com arquivos de log grandes.

- https://stackoverflow.com/questions/5868369/how-to-read-a-large-text-file-line-by-line-using-java: 
Consulta sobre otimização de memória usando BufferedReader caso o CSV seja muito grande.

- https://dzone.com/articles/reading-and-writing-files-in-java: Guia prático de tratamento de 
exceções (IOException) ao ler ou gravar os resultados finais.

*/

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

public class Main {

    static Scanner in = new Scanner(System.in, "UTF-8");
    static MalhaMetroferroviaria malha;
    static ModeloEpidemiologico modelo;
    static Path pastaBase = Paths.get("resultados");
    static Path arquivoDados;
    static MalhaMetroferroviaria.Config cfg = new MalhaMetroferroviaria.Config();
    static MalhaMetroferroviaria.Resultado ultimo;
    // parâmetros do modo percurso
    static double kInf = 1, tEst = 3, tBald = 5;

    static final String[] LIMITACOES = {
            "Simulação exploratória; NÃO é previsão epidemiológica.",
            "Sem matriz origem-destino: mobilidade é hipótese (contato proporcional à demanda das estações adjacentes).",
            "Topologia = demanda_unificada.csv + topologia_extra.csv; baldeações exclusivamente de conexoes.csv.",
            "Linhas sem dados por estação usam o total da linha dividido pelo nº de estações listadas na topologia.",
            "Meses de anos diferentes (2024/2025/2026) foram agrupados por mês, sem série anual homogênea.",
            "Cada estação é um grupo de N_i = w_i*popEscala pessoas simuladas (amostra), SEIR estocástico com durações exponenciais; a população de cada nó é fechada.",
            "SAR domiciliar não é aplicado ao metrô; R0 só calibra o beta da rede."};

    // ------------------------------------------------------------------ entrada
    
    static String ler(String prompt) {
        System.out.print(prompt);
        if (!in.hasNextLine()) { System.out.println(); System.exit(0); }
        return in.nextLine().trim();
    }

    static int lerInt(String prompt, int def, int min, int max) {
        while (true) {
            String s = ler(prompt + " [" + def + "]: ");
            if (s.isEmpty()) return def;
    
            try { int v = Integer.parseInt(s); if (v >= min && v <= max) return v; } catch (Exception e) { }
    
            System.out.println("  Valor inválido (" + min + " a " + max + ").");
        }
    }
    static int escolha(String prompt, int min, int max) {
        while (true) {
            String s = ler(prompt + " (" + min + "-" + max + "): ");
    
            try { int v = Integer.parseInt(s); if (v >= min && v <= max) return v; } catch (Exception e) { }
            System.out.println("  Opção inválida.");
        }
    }
    static double lerDouble(String prompt, double def, double min, double max) {
    
        while (true) {
            String s = ler(prompt + " [" + def + "]: ");
            if (s.isEmpty()) return def;
            try { double v = Double.parseDouble(s.replace(',', '.')); if (v >= min && v <= max) return v; } catch (Exception e) { }
    
            System.out.println("  Valor inválido (" + min + " a " + max + ").");
        }
    }
    static boolean sn(String prompt, boolean def) {
        String sufixo;
        if (def) {
            sufixo = " [S/n]: ";
        } else {
            sufixo = " [s/N]: ";
        }
        String resposta = ler(prompt + sufixo).toLowerCase(Locale.ROOT);
        if (resposta.isEmpty()) {
            return def;
        }
        return resposta.startsWith("s") || resposta.startsWith("y");
    }

    // ------------------------------------------------------------------ seleção linha -> estação
    static String escolherLinha(String titulo) {
    
    
        List<String> keys = new ArrayList<>(malha.linhas.keySet());
    
        System.out.println("\n" + titulo + " — escolha a linha:");
    
    
        for (int i = 0; i < keys.size(); i++) {
            List<Integer> ids = malha.linhas.get(keys.get(i));
    
            boolean agg = ids.size() == 1 && malha.nos.get(ids.get(0)).agregado;
            String observacao = "";
            if (agg) {
                observacao = " [dados agregados]";
            }
            System.out.printf("  %2d) %s (%d nó(s))%s%n", i + 1,
                    malha.nomeLinha.get(keys.get(i)), ids.size(), observacao);
    
        }
    
        return keys.get(escolha("Linha", 1, keys.size()) - 1);
    
    }
    static int escolherEstacao(String titulo) {
    
    
        String lk = escolherLinha(titulo);
    
        List<Integer> ids = malha.linhas.get(lk);
    
    
        System.out.println("\nEstações da " + malha.nomeLinha.get(lk) + ":");
    
        for (int i = 0; i < ids.size(); i++) System.out.printf("  %2d) %s%n", i + 1, malha.nos.get(ids.get(i)).estacao);
    
    
        return ids.get(escolha("Estação", 1, ids.size()) - 1);
    
    }
    static String listarSementes() {
    
        List<String> l = new ArrayList<>();
        for (int s : cfg.sementes) l.add(malha.nos.get(s).rotulo());
        return l.toString();
    }
    static void escolherSementes() {
    
        if (!cfg.sementes.isEmpty()) {
    
            System.out.println("Sementes atuais: " + listarSementes());
    
    
            int op = lerInt("1=manter, 2=limpar e escolher de novo, 3=adicionar mais", 1, 1, 3);
    
            if (op == 1) return;
            if (op == 2) cfg.sementes.clear();
        }
        do {
            int id = escolherEstacao("SEMENTE INFECTADA");
            if (cfg.sementes.contains(id)) System.out.println("  (já estava na lista)"); else cfg.sementes.add(id);
            System.out.println("Sementes: " + listarSementes());
        } while (sn("Adicionar mais uma semente?", false));
    }

    // ------------------------------------------------------------------ metadados / util
    
    static Map<String, Object> meta(String experimento, MalhaMetroferroviaria.Config c) {
    
    
        Map<String, Object> m = new LinkedHashMap<>();
    
        m.put("experimento", experimento);
    
        m.put("gerado_em", LocalDateTime.now().toString());
        m.put("arquivo_dados", arquivoDados.toString());
        m.put("mes", MalhaMetroferroviaria.MESES[c.mes]);
    
        Map<String, Object> ce = new LinkedHashMap<>();
        ce.put("nome", c.cenario.nome);
        ce.put("R0_ou_Re", c.cenario.r0);
        String tipoIndice = "R0";
        if (c.cenario.nome.contains("(Re)")) {
            tipoIndice = "Re";
        }
        ce.put("tipo_indice", tipoIndice);
        ce.put("intervalo_serial", c.cenario.intervaloSerial);
        ce.put("incubacao", c.cenario.incubacao); ce.put("latencia_derivada", c.cenario.latencia(modelo.duracaoInfecciosa)); ce.put("fonte", c.cenario.fonte);
    
    
        m.put("cenario_epidemiologico", ce);
        m.put("protecao", c.nomeProtecao + " (OR=" + c.oddsRatio + ")");
        String expressaoMobilidade = "C_ij = w_ij";
        if (c.mobilidade == 1) {
            expressaoMobilidade = "C_ij = w_i * T_ij (caminhada aleatória ponderada)";
        }
        m.put("mobilidade", expressaoMobilidade);
        m.put("isolamento_sintomaticos", c.isolamento);
        m.put("populacao_escala_no_maior", c.popEscala); m.put("infectados_iniciais_por_semente", c.infectadosIniciais);
    
        MalhaMetroferroviaria.Rede r = malha.rede(c.mes, c.mobilidade);
        Map<String, Object> rd = new LinkedHashMap<>();
        rd.put("nos", malha.nos.size());
        rd.put("arestas", malha.numArestas());
        rd.put("baldeacoes_explicitas", malha.numBaldeacoes());
    
    
        rd.put("rho_C", r.rho); rd.put("beta_calibrado", malha.beta(c.cenario, c.mes, c.mobilidade, modelo));
    
        m.put("rede_do_mes", rd);
    
    
        m.put("sementes", c.sementes.stream().map(i -> malha.nos.get(i).rotulo()).collect(java.util.stream.Collectors.toList()));
        m.put("dias", c.dias); m.put("execucoes_monte_carlo", c.execucoes); m.put("semente_aleatoria", c.rngSeed);

        m.put("parametros_modelo", modelo.paraMapa());
    
        m.put("avisos_dados", malha.avisos);
    
        m.put("limitacoes", Arrays.asList(LIMITACOES));
        return m;
    }

    static Map<String, Object> resumoMap(MalhaMetroferroviaria.Resultado r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("beta", r.beta); m.put("R_rede_alvo", r.rNet);
    
        m.put("taxa_ataque_media", r.ataqueMedia); m.put("taxa_ataque_p5", r.ataqueP5); m.put("taxa_ataque_p95", r.ataqueP95); m.put("populacao_simulada", r.popTotal);
    
    
        m.put("alcance_final_media_nos", r.alcFinalMedia); m.put("alcance_final_mediana", r.alcFinalMediana);
    
    
        m.put("alcance_final_p5", r.alcFinalP5); m.put("alcance_final_p95", r.alcFinalP95); m.put("alcance_final_dp", r.alcFinalDp);
        m.put("pico_infecciosos_medio", r.picoIMedio); m.put("dia_do_pico_medio", r.diaPicoMedio);
        return m;
    }

    static void imprimirResumo(MalhaMetroferroviaria.Resultado r) {
        int n = malha.nos.size();
        System.out.printf(Locale.US, "  beta=%.4f | alcance final: média %.1f de %d nós (%.1f%%), mediana %.1f, P5-P95 [%.1f ; %.1f]%n",
                r.beta, r.alcFinalMedia, n, 100 * r.alcFinalMedia / n, r.alcFinalMediana, r.alcFinalP5, r.alcFinalP95);
    
    
        System.out.printf(Locale.US, "  taxa de ataque: média %.3f%% (P5-P95 %.3f%% – %.3f%%) | pico: %.1f pessoas infecciosas (dia médio %.1f)%n", 100 * r.ataqueMedia, 100 * r.ataqueP5, 100 * r.ataqueP95, r.picoIMedio, r.diaPicoMedio);
    }

    static void cabecalho() {
        MalhaMetroferroviaria.Rede r = malha.rede(cfg.mes, cfg.mobilidade);
        System.out.println("\n================ SIMULADOR COVID-19 · MALHA METROFERROVIÁRIA SP ================");
    
        System.out.println(" Mês: " + MalhaMetroferroviaria.MESES[cfg.mes] + " | Cenário: " + cfg.cenario);
        String mobilidade = "C=w_ij";
        if (cfg.mobilidade == 1) {
            mobilidade = "C=w_i*T_ij";
        }
        System.out.println(" Proteção: " + cfg.nomeProtecao + " (OR=" + cfg.oddsRatio
                + ") | Mobilidade: " + mobilidade
                + " | Isolamento sintomáticos: " + (int) (cfg.isolamento * 100) + "%");
        String sementes = "(nenhuma)";
        if (!cfg.sementes.isEmpty()) {
            sementes = listarSementes();
        }
        System.out.println(" Sementes: " + sementes + " | Dias: " + cfg.dias + " | Execuções: " + cfg.execucoes
    
                + " | rho(C)=" + String.format(Locale.US, "%.3f", r.rho));
    
        System.out.println("-------------------------------------------------------------------------------");
    }


    // ------------------------------------------------------------------ configurações
    static void definirMes() {
        for (int i = 0; i < 12; i++) System.out.printf("  %2d) %s%n", i + 1, MalhaMetroferroviaria.MESES[i]);
        cfg.mes = escolha("Mês", 1, 12) - 1;

    }

    static void definirCenario() {
        List<ModeloEpidemiologico.Cenario> cs = modelo.cenarios;

        for (int i = 0; i < cs.size(); i++) System.out.printf("  %d) %s%n     fonte: %s%n", i + 1, cs.get(i), cs.get(i).fonte);
        System.out.printf("  %d) Personalizado%n", cs.size() + 1);
        int e = escolha("Cenário", 1, cs.size() + 1);
        if (e <= cs.size()) { cfg.cenario = cs.get(e - 1); return; }


        double r0 = lerDouble("R0 alvo", 2.66, 0.1, 20);

        double is = lerDouble("Intervalo serial (dias)", 5.2, 1, 15);

        double ic = lerDouble("Incubação (dias)", 5.2, 1, 20);
        ModeloEpidemiologico.Cenario c = new ModeloEpidemiologico.Cenario("Personalizado R0=" + r0, r0, is, ic, "definido pelo usuário");
        cs.add(c);
        cfg.cenario = c;

    }

    static void definirProtecao() {
        List<ModeloEpidemiologico.Protecao> ps = modelo.protecoes;
        for (int i = 0; i < ps.size(); i++) System.out.printf("  %d) %s%n     fonte: %s%n", i + 1, ps.get(i), ps.get(i).fonte);
        System.out.printf("  %d) Personalizada (informar OR)%n", ps.size() + 1);
        int e = escolha("Proteção", 1, ps.size() + 1);
        ModeloEpidemiologico.Protecao p;

        if (e <= ps.size()) p = ps.get(e - 1);
        else { double or = lerDouble("Odds ratio (0<OR<=1 = proteção)", 0.5, 0.001, 5); p = new ModeloEpidemiologico.Protecao("Personalizada OR=" + or, or, "definido pelo usuário"); ps.add(p); }
        cfg.oddsRatio = p.oddsRatio; cfg.nomeProtecao = p.nome;
    }

    static void parametrosAvancados() {
        cfg.mobilidade = lerInt("Contato entre nós: 0 = C_ij=w_ij | 1 = C_ij=w_i·T_ij (caminhada aleatória)", cfg.mobilidade, 0, 1);

        cfg.isolamento = lerDouble("Isolamento de sintomáticos após a incubação (0 a 1)", cfg.isolamento, 0, 1);

        cfg.popEscala = lerInt("População simulada na estação de maior demanda (N_i = w_i × este valor)", cfg.popEscala, 50, 200000);
        cfg.infectadosIniciais = lerInt("Pessoas infecciosas iniciais em cada estação-semente", cfg.infectadosIniciais, 1, 100000);
        kInf = lerDouble("Percurso: nº de infectados presentes em cada estação (k)", kInf, 0.001, 1e4);
        tEst = lerDouble("Percurso: minutos de exposição por estação", tEst, 0.1, 120);
        tBald = lerDouble("Percurso: minutos extras em baldeação", tBald, 0, 60);
    }

    // ------------------------------------------------------------------ E1/E2: percurso
    static void simularPercurso() throws Exception {
        int o = escolherEstacao("ORIGEM"), d = escolherEstacao("DESTINO");

        List<Integer> c1 = malha.menorNumeroEstacoes(o, d);
        if (c1 == null) {
            System.out.println("Sem caminho entre as estações. Confira os arquivos de topologia.");
            return;
        }

        List<Integer> c2 = malha.menorExposicao(o, d, cfg.mes, cfg.mobilidade, tEst, tBald);
        Map<String, List<Integer>> caminhos = new LinkedHashMap<>();

        caminhos.put("Menos estações", c1);
        if (c2 != null) {
            caminhos.put("Menor exposição", c2);
            if (c2.equals(c1)) {
                System.out.println("(BFS e Dijkstra encontraram o mesmo trajeto.)");
            }
        }
        double beta = malha.beta(cfg.cenario, cfg.mes, cfg.mobilidade, modelo);

        Path dir = MalhaMetroferroviaria.Saida.novaPasta(pastaBase, "E1E2_percurso");

        List<String> csv = new ArrayList<>(List.of("caminho,protecao,ordem,linha,estacao,entrada_por_baldeacao,peso_demanda,lambda,p_local,prob_acumulada"));

        List<MalhaMetroferroviaria.Serie> series = new ArrayList<>();

        Map<String, Object> resumo = new LinkedHashMap<>();


        int cor = 0;

        for (Map.Entry<String, List<Integer>> ent : caminhos.entrySet()) {

            System.out.println("\n>> " + ent.getKey() + ": " + ent.getValue().size() + " estações");


            StringBuilder trajeto = new StringBuilder();
            for (int v : ent.getValue()) {
                if (trajeto.length() > 0) {
                    trajeto.append(" -> ");
                }
                trajeto.append(malha.nos.get(v).rotulo());
            }

            System.out.println("   " + trajeto);
            for (ModeloEpidemiologico.Protecao pr : modelo.protecoes) {

                MalhaMetroferroviaria.AvalPercurso a = malha.avaliarPercurso(ent.getValue(), cfg.mes, cfg.mobilidade, beta, kInf, tEst, tBald, pr.oddsRatio);
                System.out.printf(Locale.US, "   %-28s P_total = %.4e%n", pr.nome, a.total);

                resumo.put(ent.getKey() + " | " + pr.nome, a.total);

                double[] x = new double[a.nos.size()];

                for (int i = 0; i < x.length; i++) {

                    x[i] = i + 1; MalhaMetroferroviaria.No no = malha.nos.get(a.nos.get(i));

                    csv.add(String.join(",", MalhaMetroferroviaria.Saida.q(ent.getKey()), MalhaMetroferroviaria.Saida.q(pr.nome), String.valueOf(i + 1),

                            MalhaMetroferroviaria.Saida.q(no.linha), MalhaMetroferroviaria.Saida.q(no.estacao), String.valueOf(a.baldeacao[i]),
                            MalhaMetroferroviaria.Saida.f(a.peso[i]), MalhaMetroferroviaria.Saida.f(a.lambda[i]),

                            MalhaMetroferroviaria.Saida.f(a.p[i]), MalhaMetroferroviaria.Saida.f(a.acum[i])));

                }

                series.add(new MalhaMetroferroviaria.Serie(ent.getKey() + " | " + pr.nome, x, a.acum, null, null,
                        MalhaMetroferroviaria.PALETA[cor++ % MalhaMetroferroviaria.PALETA.length]));
                if (pr.oddsRatio == 1.0) {
                    Integer[] idx = new Integer[a.nos.size()]; for (int i = 0; i < idx.length; i++) idx[i] = i;
                    Arrays.sort(idx, (p, q) -> Double.compare(a.p[q], a.p[p]));

                    System.out.print("   Estações críticas (maior p local): ");
                    for (int i = 0; i < Math.min(3, idx.length); i++) {
                        String separador = "; ";
                        if (i == Math.min(3, idx.length) - 1) {
                            separador = "\n";
                        }
                        System.out.printf(Locale.US, "%s (%.2e)%s",
                                malha.nos.get(a.nos.get(idx[i])).rotulo(), a.p[idx[i]], separador);
                    }
                }

            }

        }


        MalhaMetroferroviaria.Saida.escrever(dir.resolve("percurso_detalhado.csv"), csv);

        MalhaMetroferroviaria.graficoLinhas(dir.resolve("probabilidade_acumulada.png"),
                "Probabilidade acumulada de transmissão ao longo do percurso · "
                        + malha.nos.get(o).estacao + " → " + malha.nos.get(d).estacao
                        + " · " + MalhaMetroferroviaria.MESES[cfg.mes],
                "Estações percorridas [ordem no trajeto]",
                "Probabilidade acumulada de transmissão [0–1]", series,
                List.of(
                        "λ_i = β · w_i · k · t_i / 1440; p_i = aplicarOR(1 − exp(−λ_i), OR)",
                        "P_acum(m) = 1 − ∏(1 − p_i), de i = 1 até m.",
                        "Probabilidade condicional: presume k infectado(s) em cada estação do percurso."
                ));

        Map<String, Object> m = meta("E1/E2 percurso", cfg);
        m.put("origem", malha.nos.get(o).rotulo()); m.put("destino", malha.nos.get(d).rotulo());
        m.put("infectados_por_estacao_k", kInf); m.put("min_por_estacao", tEst); m.put("min_baldeacao", tBald);

        m.put("interpretacao", "P é a probabilidade de >=1 transmissão SE houver k infectado(s) em cada estação do trajeto; não é risco populacional.");

        m.put("P_total", resumo);

        MalhaMetroferroviaria.Saida.json(dir.resolve("metadados.json"), m);
        System.out.println("\nArquivos salvos em: " + dir);

    }


    // ------------------------------------------------------------------ E3/E6: disseminação
    static void simularDisseminacao() throws Exception {


        escolherSementes();
        if (cfg.sementes.isEmpty()) { System.out.println("Nenhuma semente."); return; }
        cfg.dias = lerInt("Nº de dias de simulação", cfg.dias, 1, 365);
        cfg.execucoes = lerInt("Nº de execuções Monte Carlo", cfg.execucoes, 1, 20000);
        cfg.rngSeed = (long) lerDouble("Semente aleatória (reprodutibilidade)", cfg.rngSeed, 0, 1e12);
        System.out.println("Simulando...");
        MalhaMetroferroviaria.Resultado r = malha.simular(cfg, modelo);


        r.rotulo = MalhaMetroferroviaria.MESES[cfg.mes];

        ultimo = r;

        imprimirResumo(r);

        Path dir = MalhaMetroferroviaria.Saida.novaPasta(pastaBase, "E3E6_disseminacao");

        List<String> c = new ArrayList<>(List.of("dia,S_media,E_media,I_media,I_p5,I_p95,R_media,alcance_media,alcance_p5,alcance_p95"));

        for (int d = 0; d <= r.dias; d++)

            c.add(d + "," + String.join(",", MalhaMetroferroviaria.Saida.f(r.mS[d]), MalhaMetroferroviaria.Saida.f(r.mE[d]), MalhaMetroferroviaria.Saida.f(r.mI[d]),
                    MalhaMetroferroviaria.Saida.f(r.p5I[d]), MalhaMetroferroviaria.Saida.f(r.p95I[d]), MalhaMetroferroviaria.Saida.f(r.mR[d]),
                    MalhaMetroferroviaria.Saida.f(r.mAlc[d]), MalhaMetroferroviaria.Saida.f(r.p5A[d]), MalhaMetroferroviaria.Saida.f(r.p95A[d])));

        MalhaMetroferroviaria.Saida.escrever(dir.resolve("curvas_temporais.csv"), c);
        List<String> e = new ArrayList<>(List.of("id,linha,estacao,semente,peso_demanda,grau,betweenness,prob_alcance,dia_chegada_medio"));
        double[] gr = malha.grau(), bt = malha.betweenness(); MalhaMetroferroviaria.Rede rd = malha.rede(cfg.mes, cfg.mobilidade);
        for (MalhaMetroferroviaria.No n : malha.nos)
            e.add(n.id + "," + MalhaMetroferroviaria.Saida.q(n.linha) + "," + MalhaMetroferroviaria.Saida.q(n.estacao) + "," + cfg.sementes.contains(n.id) + ","
                    + MalhaMetroferroviaria.Saida.f(rd.w[n.id]) + "," + (int) gr[n.id] + "," + MalhaMetroferroviaria.Saida.f(bt[n.id]) + ","
                    + MalhaMetroferroviaria.Saida.f(r.probAlcance[n.id]) + "," + MalhaMetroferroviaria.Saida.f(r.chegadaMedia[n.id]));

        MalhaMetroferroviaria.Saida.escrever(dir.resolve("estacoes_resultado.csv"), e);

        double[] x = MalhaMetroferroviaria.seq(r.dias + 1);
        MalhaMetroferroviaria.graficoLinhas(dir.resolve("curva_infecciosos.png"),
                "Pessoas infecciosas ao longo do tempo · " + MalhaMetroferroviaria.MESES[cfg.mes]
                        + " · " + listarSementes(),
                "Dia da simulação [dias]", "Pessoas infecciosas [indivíduos simulados]",
                List.of(new MalhaMetroferroviaria.Serie("Infecciosos (I)", x, r.mI, r.p5I, r.p95I,
                        MalhaMetroferroviaria.PALETA[1])),
                List.of("Faixa sombreada = percentis P5–P95 entre execuções Monte Carlo.",
                        "I(t) = número médio de indivíduos infecciosos na população simulada."));
        // O alcance representa estações; os compartimentos SEIR representam pessoas.
        // Em figuras separadas, cada eixo vertical conserva sua própria unidade.
        MalhaMetroferroviaria.graficoLinhas(dir.resolve("curva_alcance.png"),
                "Alcance acumulado da disseminação na rede · " + MalhaMetroferroviaria.MESES[cfg.mes]
                        + " · " + listarSementes(),
                "Dia da simulação [dias]", "Estações alcançadas [nós da rede]",
                List.of(new MalhaMetroferroviaria.Serie("Alcance médio (P5–P95)",
                        x, r.mAlc, r.p5A, r.p95A, MalhaMetroferroviaria.PALETA[0])),
                List.of("Faixa sombreada = percentis P5–P95 entre execuções Monte Carlo.",
                        "Alcance = número acumulado de estações atingidas pela disseminação."));

        MalhaMetroferroviaria.graficoLinhas(dir.resolve("curva_compartimentos.png"),
                "Evolução dos compartimentos SEIR · " + MalhaMetroferroviaria.MESES[cfg.mes]
                        + " · " + listarSementes(),
                "Dia da simulação [dias]", "Pessoas simuladas [indivíduos]",
                List.of(new MalhaMetroferroviaria.Serie("Expostos (E)", x, r.mE, null, null,
                                MalhaMetroferroviaria.PALETA[3]),
                        new MalhaMetroferroviaria.Serie("Infecciosos (I)", x, r.mI, null, null,
                                MalhaMetroferroviaria.PALETA[1]),
                        new MalhaMetroferroviaria.Serie("Removidos (R)", x, r.mR, null, null,
                                MalhaMetroferroviaria.PALETA[2])),
                List.of("SEIR: suscetíveis → expostos → infecciosos → removidos.",
                        "Cada curva representa a média sobre as execuções Monte Carlo."));
        malha.mapa(dir.resolve("mapa_prob_alcance.png"),
                "Probabilidade de a estação ser alcançada pela disseminação · " + MalhaMetroferroviaria.MESES[cfg.mes],
                r.probAlcance, 1.0, "Probabilidade de alcance [0–1]", cfg.sementes, cfg.mes,
                List.of("P(alcance) = execuções com a estação atingida / total de execuções.",
                        "Bolhas maiores indicam maior demanda; borda espessa = semente inicial."));

        Map<String, Object> m = meta("E3/E6 disseminação", cfg); m.put("resumo", resumoMap(r));
        MalhaMetroferroviaria.Saida.json(dir.resolve("metadados.json"), m);
        System.out.println("Arquivos salvos em: " + dir);

    }

    // ------------------------------------------------------------------ E2/E4/E5: comparações
    static void exigirSementes() {
        if (cfg.sementes.isEmpty()) { System.out.println("Defina as sementes e parâmetros de tempo:"); escolherSementes(); }

        cfg.dias = lerInt("Nº de dias de simulação", cfg.dias, 1, 365);
        cfg.execucoes = lerInt("Nº de execuções Monte Carlo por cenário", cfg.execucoes, 1, 20000);

    }

    static void comparar(String prefixo, String titulo, List<String> rot, List<MalhaMetroferroviaria.Config> cfgs) throws Exception {
        List<MalhaMetroferroviaria.Resultado> rs = new ArrayList<>();
        Path dir = MalhaMetroferroviaria.Saida.novaPasta(pastaBase, prefixo);

        List<String> csv = new ArrayList<>(List.of("cenario,beta,taxa_ataque_media,taxa_ataque_p5,taxa_ataque_p95,alcance_final_media,alcance_final_mediana,alcance_final_p5,alcance_final_p95,alcance_final_dp,pico_infecciosos_medio,dia_pico_medio"));

        List<MalhaMetroferroviaria.Serie> sA = new ArrayList<>(), sI = new ArrayList<>();
        double[] fin = new double[rot.size()];
        for (int i = 0; i < rot.size(); i++) {
            System.out.println("Simulando: " + rot.get(i));

            MalhaMetroferroviaria.Resultado r = malha.simular(cfgs.get(i), modelo); r.rotulo = rot.get(i); rs.add(r); fin[i] = 100 * r.ataqueMedia;

            imprimirResumo(r);

            csv.add(MalhaMetroferroviaria.Saida.q(rot.get(i)) + "," + String.join(",", MalhaMetroferroviaria.Saida.f(r.beta), MalhaMetroferroviaria.Saida.f(r.ataqueMedia), MalhaMetroferroviaria.Saida.f(r.ataqueP5), MalhaMetroferroviaria.Saida.f(r.ataqueP95), MalhaMetroferroviaria.Saida.f(r.alcFinalMedia),
                    MalhaMetroferroviaria.Saida.f(r.alcFinalMediana), MalhaMetroferroviaria.Saida.f(r.alcFinalP5), MalhaMetroferroviaria.Saida.f(r.alcFinalP95),

                    MalhaMetroferroviaria.Saida.f(r.alcFinalDp), MalhaMetroferroviaria.Saida.f(r.picoIMedio), MalhaMetroferroviaria.Saida.f(r.diaPicoMedio)));
            double[] x = MalhaMetroferroviaria.seq(r.dias + 1);
            java.awt.Color cor = MalhaMetroferroviaria.PALETA[i % MalhaMetroferroviaria.PALETA.length];

            sA.add(new MalhaMetroferroviaria.Serie(rot.get(i), x, r.mAlc, null, null, cor));

            sI.add(new MalhaMetroferroviaria.Serie(rot.get(i), x, r.mI, null, null, cor));
        }
        MalhaMetroferroviaria.Saida.escrever(dir.resolve("resumo_comparacao.csv"), csv);
        List<String> notasComparacao = List.of(
                "Comparação dos cenários com os mesmos nós-semente: " + listarSementes(),
                "Médias entre execuções Monte Carlo. Horizonte: " + cfgs.get(0).dias + " dias.");
        MalhaMetroferroviaria.graficoLinhas(dir.resolve("alcance_medio.png"),
                titulo + " · estações alcançadas ao longo do tempo", "Dia da simulação [dias]",
                "Estações alcançadas [nós da rede]", sA, notasComparacao);

        MalhaMetroferroviaria.graficoLinhas(dir.resolve("infecciosos_medio.png"),
                titulo + " · pessoas infecciosas ao longo do tempo", "Dia da simulação [dias]",
                "Pessoas infecciosas [indivíduos simulados]", sI, notasComparacao);

        MalhaMetroferroviaria.graficoBarras(dir.resolve("alcance_final.png"),
                titulo + " · taxa de ataque média", "% da população simulada infectada", rot, fin,
                List.of("Taxa de ataque = (total de pessoas infectadas / população simulada) × 100.",
                        "Sementes fixas: " + listarSementes()));

        Map<String, Object> m = meta(prefixo, cfg); m.put("cenarios_comparados", rot);
        Map<String, Object> res = new LinkedHashMap<>(); for (MalhaMetroferroviaria.Resultado r : rs) res.put(r.rotulo, resumoMap(r));
        m.put("resumos", res);
        MalhaMetroferroviaria.Saida.json(dir.resolve("metadados.json"), m);
        System.out.println("Arquivos salvos em: " + dir);
    }

    static void compararMeses() throws Exception {

        exigirSementes();
        List<String> rot = new ArrayList<>(); List<MalhaMetroferroviaria.Config> cs = new ArrayList<>();
        for (int m = 0; m < 12; m++) { MalhaMetroferroviaria.Config c = cfg.copia(); c.mes = m; cs.add(c); rot.add(MalhaMetroferroviaria.MESES[m]); }
        comparar("E4_comparacao_mensal", "Comparação mensal", rot, cs);
    }
    static void compararCenarios() throws Exception {

        exigirSementes();
        List<String> rot = new ArrayList<>(); List<MalhaMetroferroviaria.Config> cs = new ArrayList<>();
        for (ModeloEpidemiologico.Cenario s : modelo.cenarios) { MalhaMetroferroviaria.Config c = cfg.copia(); c.cenario = s; cs.add(c); rot.add(s.toString()); }

        comparar("E5_transmissibilidade", "Sensibilidade à transmissibilidade", rot, cs);
    }

    static void compararProtecao() throws Exception {
        exigirSementes();
        List<String> rot = new ArrayList<>(); List<MalhaMetroferroviaria.Config> cs = new ArrayList<>();
        for (ModeloEpidemiologico.Protecao p : modelo.protecoes) { MalhaMetroferroviaria.Config c = cfg.copia(); c.oddsRatio = p.oddsRatio; c.nomeProtecao = p.nome; cs.add(c); rot.add(p.nome + " OR=" + p.oddsRatio); }
        comparar("E2_protecao_disseminacao", "Efeito da proteção", rot, cs);
    }


    // ------------------------------------------------------------------ ranking e mapa
    static void ranking() throws Exception {
        int dias = lerInt("Dias por simulação", 60, 1, 365), ex = lerInt("Execuções por estação (semente única)", 30, 1, 2000);

        MalhaMetroferroviaria.Config c = cfg.copia(); c.dias = dias;

        System.out.println("Simulando " + malha.nos.size() + " estações como semente única...");
        double[] imp = malha.impactoPorSemente(c, modelo, ex);

        double[] gr = malha.grau(), bt = malha.betweenness(); MalhaMetroferroviaria.Rede rd = malha.rede(cfg.mes, cfg.mobilidade);
        Integer[] o = new Integer[malha.nos.size()]; for (int i = 0; i < o.length; i++) o[i] = i;
        Arrays.sort(o, (a, b) -> Double.compare(imp[b], imp[a]));
        int[] rkD = rank(rd.w), rkB = rank(bt), rkI = rank(imp);

        Path dir = MalhaMetroferroviaria.Saida.novaPasta(pastaBase, "ranking_estacoes");
        List<String> csv = new ArrayList<>(List.of("posicao_impacto,id,linha,estacao,demanda_mes,peso,grau,betweenness,impacto_alcance_final_medio,rank_demanda,rank_betweenness,rank_impacto"));

        List<String> rot = new ArrayList<>(); List<Double> vals = new ArrayList<>();

        for (int p = 0; p < o.length; p++) {

            MalhaMetroferroviaria.No n = malha.nos.get(o[p]);

            csv.add((p + 1) + "," + n.id + "," + MalhaMetroferroviaria.Saida.q(n.linha) + "," + MalhaMetroferroviaria.Saida.q(n.estacao) + ","
                    + MalhaMetroferroviaria.Saida.f(n.demanda[cfg.mes]) + "," + MalhaMetroferroviaria.Saida.f(rd.w[n.id]) + "," + (int) gr[n.id] + ","


                    + MalhaMetroferroviaria.Saida.f(bt[n.id]) + "," + MalhaMetroferroviaria.Saida.f(imp[n.id]) + "," + rkD[n.id] + "," + rkB[n.id] + "," + rkI[n.id]);
            if (p < 20) { rot.add(n.rotulo()); vals.add(imp[n.id]); }

        }
        MalhaMetroferroviaria.Saida.escrever(dir.resolve("ranking.csv"), csv);

        MalhaMetroferroviaria.graficoBarras(dir.resolve("top20_impacto.png"),
                "Top 20 estações por impacto quando usadas como semente única · "
                        + MalhaMetroferroviaria.MESES[cfg.mes],
                "Alcance final médio [estações alcançadas]",
                rot, vals.stream().mapToDouble(Double::doubleValue).toArray(),
                List.of("Cada barra apresenta o alcance final médio usando uma única estação como semente.",
                        "Impacto = número médio de estações alcançadas ao final da simulação."));

        double mx = Arrays.stream(imp).max().orElse(1);
        malha.mapa(dir.resolve("mapa_impacto.png"),
                "Impacto de cada estação quando usada como semente única · " + MalhaMetroferroviaria.MESES[cfg.mes],
                imp, mx, "Alcance médio [estações]", null, cfg.mes,
                List.of("Cada nó é avaliado separadamente como semente única.",
                        "Cor = impacto simulado; tamanho do nó = demanda mensal."));

        Map<String, Object> m = meta("ranking", c); m.put("execucoes_por_estacao", ex);
        MalhaMetroferroviaria.Saida.json(dir.resolve("metadados.json"), m);

        System.out.println("\nTop 15 por impacto simulado:");
        for (int p = 0; p < Math.min(15, o.length); p++)

            System.out.printf(Locale.US, "  %2d. %-45s impacto=%.1f | rank demanda=%d | rank betweenness=%d%n", p + 1, malha.nos.get(o[p]).rotulo(), imp[o[p]], rkD[o[p]], rkB[o[p]]);

        System.out.println("Arquivos salvos em: " + dir);

    }

    static int[] rank(double[] v) {

        Integer[] o = new Integer[v.length]; for (int i = 0; i < o.length; i++) o[i] = i;
        Arrays.sort(o, (a, b) -> Double.compare(v[b], v[a]));

        int[] r = new int[v.length]; for (int i = 0; i < o.length; i++) r[o[i]] = i + 1;
        return r;

    }


    static void mapaDemanda() throws Exception {

        Path dir = MalhaMetroferroviaria.Saida.novaPasta(pastaBase, "mapa_demanda");
        double[] w = malha.rede(cfg.mes, 0).w;

        malha.mapa(dir.resolve("mapa_demanda.png"),
                "Demanda normalizada de passageiros por estação · " + MalhaMetroferroviaria.MESES[cfg.mes],
                w, 1.0, "Demanda normalizada [D_i / D_max]", null, cfg.mes,
                List.of("Cor = demanda da estação dividida pela maior demanda do mês.",
                        "Bolhas maiores indicam estações de maior demanda mensal."));
        MalhaMetroferroviaria.Saida.json(dir.resolve("metadados.json"), meta("mapa_demanda", cfg));

        System.out.println("Arquivos salvos em: " + dir);
    }


    static void resumoMalha() {

        System.out.println("\nNós: " + malha.nos.size() + " | Arestas: " + malha.numArestas()
                + " | Baldeações: " + malha.numBaldeacoes() + " | Linhas: " + malha.linhas.size());
        for (Map.Entry<String, List<Integer>> e : malha.linhas.entrySet())

            System.out.printf("  %-24s %2d nó(s)%n", malha.nomeLinha.get(e.getKey()), e.getValue().size());
        System.out.println("\nAvisos da carga (" + malha.avisos.size() + "):");

        for (String a : malha.avisos) System.out.println("  - " + a);
    }


    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {

        System.setProperty("java.awt.headless", "true");

        System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        String caminhoDados = "demanda_unificada.csv";
        if (args.length > 0) {
            caminhoDados = args[0];
        }
        arquivoDados = Paths.get(caminhoDados);
        while (!Files.exists(arquivoDados)) {

            System.out.println("CSV não encontrado: " + arquivoDados.toAbsolutePath());
            arquivoDados = Paths.get(ler("Informe o caminho do demanda_unificada.csv: "));
        }

        Path pai = arquivoDados.toAbsolutePath().getParent();

        System.out.println("1/3 Carregando nós e arestas da malha...");
        malha = MalhaMetroferroviaria.carregar(arquivoDados, pai.resolve("topologia_extra.csv"), pai.resolve("conexoes.csv"));

        System.out.println("    " + malha.nos.size() + " nós, " + malha.numArestas()
                + " arestas (" + malha.numBaldeacoes() + " baldeações), "
                + malha.linhas.size() + " linhas.");
        System.out.println("2/3 Carregando modelo epidemiológico (" + "COVID-19" + ")...");

        modelo = new ModeloEpidemiologico();
        cfg.cenario = modelo.cenarios.get(1);

        Files.createDirectories(pastaBase);

        malha.exportarTopologia(pastaBase.resolve("carga_dados"));

        Files.write(pastaBase.resolve("carga_dados").resolve("avisos_carga.txt"), malha.avisos, java.nio.charset.StandardCharsets.UTF_8);

        System.out.println("    Topologia e avisos exportados em " + pastaBase.resolve("carga_dados"));

        System.out.println("    " + malha.avisos.size() + " aviso(s) de carga (opção 12 para ver).");

        System.out.println("3/3 Menu.");

        while (true) {

            cabecalho();

            System.out.println(" 1) Definir mês                       7) Comparar meses (E4)");
            System.out.println(" 2) Cenário de transmissibilidade     8) Comparar cenários de R0 (E5)");

            System.out.println(" 3) Cenário de proteção               9) Comparar proteção na disseminação (E2)");
            System.out.println(" 4) Parâmetros avançados             10) Ranking de estações");

            System.out.println(" 5) Simular percurso (E1/E2)         11) Mapa da rede (demanda)");
            System.out.println(" 6) Simular disseminação (E3/E6)     12) Resumo da malha e avisos");

            System.out.println(" 0) Sair");

            int op = escolha("Opção", 0, 12);

            try {
                switch (op) {

                    case 1: definirMes(); break;
                    case 2: definirCenario(); break;

                    case 3: definirProtecao(); break;
                    case 4: parametrosAvancados(); break;
                    case 5: simularPercurso(); break;
                    case 6: simularDisseminacao(); break;
                    case 7: compararMeses(); break;
                    case 8: compararCenarios(); break;
                    case 9: compararProtecao(); break;

                    case 10: ranking(); break;
                    case 11: mapaDemanda(); break;

                    case 12: resumoMalha(); break;
                    default: System.out.println("Até logo. Resultados em: " + pastaBase.toAbsolutePath()); return;
                }
            } catch (Exception e) {
                System.out.println("Erro: " + e);
                e.printStackTrace();
            }

        }
    }

}