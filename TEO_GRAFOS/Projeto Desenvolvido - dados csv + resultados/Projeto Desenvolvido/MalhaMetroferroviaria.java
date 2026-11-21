/*
Projeto da Disciplina de Teoria dos Grafos - Partes 2 e 3
Arquivo: MalhaMetroferroviaria.java
Conteudo: Constrói o grafo (𝑉, 𝐸, 𝑊𝑚) a partir dos dados 
          de demanda e executa as simulações e os gráficos
     
autor Pedro Henrique Carvalho Pereira - 10418861
autor Mateus Ribeiro Cerqueira - 10443901

Referencias:
- https://docs.oracle.com/javase/tutorial/2d/overview/rendering.html: Aplicação de Graphics2D,
 BasicStroke e dicas de renderização.

- https://docs.oracle.com/javase/tutorial/2d/overview/images.html: Fluxo para desenhar dados 
diretamente no buffer da BufferedImage.

- https://www.baeldung.com/java-graphics2d: Uso de cores RGB personalizadas e preenchimento de 
formas geométricas (Fill/Draw).

- https://stackoverflow.com/questions/29447994/how-do-i-draw-a-line-in-java: Lógica de mapeamento 
de coordenadas (X, Y) para desenhar as arestas e linhas do metrô.

- https://www.geeksforgeeks.org/image-processing-in-java-read-and-write/: Uso de ImageIO.write() 
para exportar o mapa gerado para PNG.

- https://stackoverflow.com/questions/31536952/how-to-fix-text-quality-in-java-2d: Configuração 
das RenderingHints (KEY_ANTIALIASING e KEY_TEXT_ANTIALIASING) para evitar serrilhado nas fontes 
e nos círculos das estações.

- https://docs.oracle.com/javase/tutorial/2d/advanced/transforming.html: Uso de AffineTransform 
para calcular a escala (zoom) e centralizar a malha de estações na tela.
- https://examples.javacodegeeks.com/desktop-java/awt/graphics2d/draw-a-gradient-in-java-2d/: Implementação 
de GradientPaint para melhorar o visual das barras nos gráficos ou mapas de calor de infecção.

*/


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import javax.imageio.ImageIO;

/*
 Grafo ponderado G_m = (V, E, W_m) da malha metroferroviária + todas as operações de simulação.
 Nós = estações; peso = demanda mensal normalizada; topologia fixa, pesos variam com o mês.
 */
public class MalhaMetroferroviaria {

    public static final String[] MESES = {"Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",

            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"};
    /* Nº de estações assumido para dividir o total de uma linha sem abertura por estação (nó único). */
    public static final int ESTACOES_PADRAO_AGREGADO = 15;

    // ---------------------------------------------------------------- estruturas
    public static class No {
        public int id;

        public String linhaChave, linha, estacao, chave;
        public boolean agregado;  // nó único representando uma linha inteira
        public boolean estimado;  // demanda = total da linha / nº de estações
        public double[] demanda = new double[12]; // passageiros absolutos por mês
        public double media;

        public String rotulo() { return linha + " · " + estacao; }

    }


    /* Rede de um mês: pesos w_i, vizinhos e coeficientes de contato c_ij, raio espectral. */
    public static class Rede {


        public double[] w; public int[][] viz; public double[][] c; public double rho;

        public double[][] cin; // cin[j][k] = coeficiente de contato do vizinho viz[j][k] para j
    }

    public static class Config {


        public int mes = 0;

        public ModeloEpidemiologico.Cenario cenario;
        public double oddsRatio = 1.0;
        public String nomeProtecao = "Sem proteção";

        public List<Integer> sementes = new ArrayList<>();
        public int dias = 60, execucoes = 200;
        public long rngSeed = 42;

        public int mobilidade = 0;      // 0: C_ij = w_ij ; 1: C_ij = w_i * T_ij (caminhada aleatória ponderada)

        public double isolamento = 0.0; // redução de contato de sintomáticos após o início dos sintomas
        public int infectadosIniciais = 5;  // pessoas infecciosas colocadas em cada estação-semente
        public int popEscala = 2000;        // pessoas simuladas na estação de maior demanda (N_i = w_i * popEscala)
        public Config copia() {
            Config c = new Config();
            c.mes = mes; c.cenario = cenario; c.oddsRatio = oddsRatio; c.nomeProtecao = nomeProtecao;
            c.sementes = new ArrayList<>(sementes); c.dias = dias; c.execucoes = execucoes;
            c.rngSeed = rngSeed; c.mobilidade = mobilidade; c.isolamento = isolamento; c.infectadosIniciais = infectadosIniciais; c.popEscala = popEscala;

            return c;
        }
    }

    public static class Resultado {

        public String rotulo; public int dias, execucoes; public double beta, rNet;

        public double[] mS, mE, mI, mR, mAlc, p5I, p95I, p5A, p95A;
        public double[] probAlcance, chegadaMedia;
        public double alcFinalMedia, alcFinalMediana, alcFinalP5, alcFinalP95, alcFinalDp, picoIMedio, diaPicoMedio;
        public double ataqueMedia, ataqueP5, ataqueP95, popTotal; // taxa de ataque = fração da população simulada que foi infectada
    }

    public static class AvalPercurso {
        public List<Integer> nos; public boolean[] baldeacao;

        public double[] peso, lambda, p, acum; public double total;
    }

    public static class Serie {
        public String nome; public double[] x, y, lo, hi; public Color cor;
        public Serie(String nome, double[] x, double[] y, double[] lo, double[] hi, Color cor) {
            this.nome = nome; this.x = x; this.y = y; this.lo = lo; this.hi = hi; this.cor = cor;
        }
    }


    // ---------------------------------------------------------------- estado do grafo
    public final List<No> nos = new ArrayList<>();

    public final List<List<int[]>> adj = new ArrayList<>(); // {vizinho, ehBaldeacao}
    public final LinkedHashMap<String, List<Integer>> linhas = new LinkedHashMap<>();
    public final Map<String, String> nomeLinha = new HashMap<>();
    public final List<String> avisos = new ArrayList<>();
    private final Set<String> arestasSet = new HashSet<>();
    private final Map<Integer, Rede> cacheRede = new HashMap<>();
    private double[][] pos;
    private double[] betCache;

    private final LinkedHashMap<String, LinkedHashMap<String, double[]>> demEst = new LinkedHashMap<>();

    private final Map<String, String> nomeEst = new HashMap<>();
    private final Map<String, double[]> totais = new HashMap<>();
    private final LinkedHashSet<String> ordemLinhas = new LinkedHashSet<>();
    private final Set<String> avisoNomes = new HashSet<>();

    // ---------------------------------------------------------------- utilidades de texto
    static String semAcento(String s) { return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", ""); }

    static String chaveTexto(String s) { return semAcento(s).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    static String chaveLinha(String s) {
        Matcher m = Pattern.compile("(\\d+)").matcher(s);

        if (m.find()) {
            return "L" + Integer.parseInt(m.group(1));
        }
        return chaveTexto(s);
    }

    public static int indiceMes(String s) {

        String k = chaveTexto(s);

        for (int i = 0; i < 12; i++) if (chaveTexto(MESES[i]).equals(k)) return i;
        return -1;

    }

    static double numero(String s) {
        s = s.trim().replace(" ", "");
        if (s.isEmpty()) return Double.NaN;
        if (s.contains(",") && s.contains(".")) s = s.replace(".", "").replace(",", ".");
        else if (s.contains(",")) s = s.replace(",", ".");

        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return Double.NaN; }
    }
    static char detectarSeparador(String cab) {
        int pv = 0, vg = 0, tb = 0;

        for (char c : cab.toCharArray()) { if (c == ';') pv++; else if (c == ',') vg++; else if (c == '\t') tb++; }
        if (pv >= vg && pv >= tb) {
            return ';';
        }
        if (tb > vg) {
            return '\t';
        }
        return ',';

    }

    static List<String> dividir(String linha, char sep) {

        List<String> r = new ArrayList<>(); StringBuilder sb = new StringBuilder(); boolean asp = false;
        for (char c : linha.toCharArray()) {
            if (c == '"') asp = !asp;

            else if (c == sep && !asp) { r.add(sb.toString().trim()); sb.setLength(0); }
            else sb.append(c);

        }

        r.add(sb.toString().trim());
        return r;

    }

    static double[] nan12() { double[] a = new double[12]; Arrays.fill(a, Double.NaN); return a; }

    static int col(Map<String, Integer> m, String nome) throws IOException {
        Integer i = m.get(nome);
        if (i == null) throw new IOException("Coluna obrigatória ausente no CSV: " + nome);
        return i;
    }

    // ---------------------------------------------------------------- carga dos dados
    /*
    @param csv   demanda_unificada.csv
    @param topo  (opcional) topologia_extra.csv: "linha;estacao" na ordem física; define a ordem das estações

                  e lista as estações das linhas só com TOTAL (CPTM)
    @param conex (opcional) conexoes.csv: "linhaA;estacaoA;linhaB;estacaoB" para baldeações extras
     */

    public static MalhaMetroferroviaria carregar(Path csv, Path topo, Path conex) throws IOException {
        if (topo == null || !Files.isRegularFile(topo)) {
            throw new IOException("Arquivo topologia_extra.csv obrigatório para individualizar linhas CPTM: " + topo);
        }
        MalhaMetroferroviaria g = new MalhaMetroferroviaria();


        g.lerCsv(csv);
        g.montarNos(topo);
        g.preencherLacunas();
        g.montarArestas(conex);
        return g;
    }

    private void lerCsv(Path csv) throws IOException {
        List<String> ls = Files.readAllLines(csv, StandardCharsets.UTF_8);

        if (ls.isEmpty()) throw new IOException("CSV vazio");
        String cab0 = ls.get(0).replace("\uFEFF", "");

        char sep = detectarSeparador(cab0);
        Map<String, Integer> col = new HashMap<>();

        List<String> cab = dividir(cab0, sep);

        for (int i = 0; i < cab.size(); i++) col.put(chaveTexto(cab.get(i)), i);

        int cL = col(col, "linha"), cE = col(col, "estacao"), cM = col(col, "mes"), cP = col(col, "passageiros");
        int cU = col.getOrDefault("unidade", -1);
        int max = Math.max(Math.max(cL, cE), Math.max(cM, cP));
        int ignoradas = 0, duplicadas = 0;
        for (int r = 1; r < ls.size(); r++) {
            if (ls.get(r).trim().isEmpty()) continue;
            List<String> f = dividir(ls.get(r), sep);

            if (f.size() <= max) { ignoradas++; continue; }


            int m = indiceMes(f.get(cM));
            double v = numero(f.get(cP));
            if (m < 0 || Double.isNaN(v)) { ignoradas++; continue; }

            if (cU >= 0 && f.size() > cU && chaveTexto(f.get(cU)).contains("milhar")) v *= 1000.0;
            String lk = chaveLinha(f.get(cL));
            nomeLinha.putIfAbsent(lk, f.get(cL));

            ordemLinhas.add(lk);
            String est = f.get(cE);

            String limpo = est.replaceAll("\\s+\\d+$", "").replaceAll("[*¹²³⁴]+$", "").trim();

            if (!limpo.equals(est) && !limpo.isEmpty()) { if (avisoNomes.add(est)) avisos.add("Nome de estação com marcador de rodapé ajustado: '" + est + "' -> '" + limpo + "'."); est = limpo; }
            if (chaveTexto(est).equals("total")) {

                totais.computeIfAbsent(lk, x -> nan12())[m] = v;
            } else {
                String ek = chaveTexto(est);
                double[] arr = demEst.computeIfAbsent(lk, x -> new LinkedHashMap<>()).computeIfAbsent(ek, x -> nan12());
                if (!Double.isNaN(arr[m])) duplicadas++;

                arr[m] = v;
                nomeEst.putIfAbsent(lk + "|" + ek, est);

            }
        }

        if (ignoradas > 0) avisos.add(ignoradas + " linha(s) do CSV ignorada(s) (mês/valor inválido ou colunas faltando).");


        if (duplicadas > 0) avisos.add(duplicadas + " registro(s) duplicado(s) (linha/estação/mês); prevaleceu o último.");
    }

    private Map<String, List<String>> lerTopologia(Path p) throws IOException {
        Map<String, List<String>> r = new LinkedHashMap<>();

        if (p == null || !Files.exists(p)) return r;
        List<String> ls = Files.readAllLines(p, StandardCharsets.UTF_8);
        if (ls.isEmpty()) return r;

        char sep = detectarSeparador(ls.get(0));

        for (int i = 1; i < ls.size(); i++) {
            List<String> f = dividir(ls.get(i).replace("\uFEFF", ""), sep);
            if (f.size() < 2 || f.get(1).isEmpty()) continue;

            r.computeIfAbsent(chaveLinha(f.get(0)), x -> new ArrayList<>()).add(f.get(1));

        }
        return r;
    }

    private No novoNo(String lk, String nome, String chave, boolean agregado) {
        No n = new No();

        n.id = nos.size(); n.linhaChave = lk; n.linha = nomeLinha.get(lk);

        n.estacao = nome; n.chave = chave; n.agregado = agregado;

        nos.add(n); adj.add(new ArrayList<>());

        return n;
    }

    private void montarNos(Path topo) throws IOException {
        Map<String, List<String>> topoL = lerTopologia(topo);
        for (String lk : ordemLinhas) {
            List<Integer> ids = new ArrayList<>();

            LinkedHashMap<String, double[]> est = demEst.get(lk);

            double[] tot = totais.get(lk);
            List<String> ordem = topoL.get(lk);
            String nl = nomeLinha.get(lk);
            if (est != null && !est.isEmpty()) {

                List<String> chaves = new ArrayList<>();

                if (ordem != null) {
                    for (String nome : ordem) {

                        String k = chaveTexto(nome);
                        if (est.containsKey(k)) { if (!chaves.contains(k)) chaves.add(k); }

                        else avisos.add("Topologia: '" + nome + "' (" + nl + ") não tem demanda no CSV; ignorada.");
                    }
                    for (String k : est.keySet())
                        if (!chaves.contains(k)) { chaves.add(k); avisos.add("Estação '" + nomeEst.get(lk + "|" + k) + "' (" + nl + ") ausente na topologia; anexada ao fim da linha."); }
                } else chaves.addAll(est.keySet()); // ordem do CSV assumida como ordem física
                for (String k : chaves) {

                    No no = novoNo(lk, nomeEst.get(lk + "|" + k), k, false);
                    System.arraycopy(est.get(k), 0, no.demanda, 0, 12);

                    ids.add(no.id);

                }

            } else if (tot != null) {

                if (ordem != null && !ordem.isEmpty()) {
                    int n = ordem.size();

                    for (String nome : ordem) {
                        No no = novoNo(lk, nome, chaveTexto(nome), false);
                        no.estimado = true;

                        for (int m = 0; m < 12; m++) no.demanda[m] = tot[m] / n;
                        ids.add(no.id);
                    }
                    avisos.add(nl + ": sem dados por estação; demanda de cada uma das " + n + " estações = total da linha / " + n + ".");
                } else {

                    No no = novoNo(lk, "(linha agregada)", "agregado", true);

                    for (int m = 0; m < 12; m++) no.demanda[m] = tot[m] / ESTACOES_PADRAO_AGREGADO;
                    ids.add(no.id);

                    avisos.add(nl + ": sem dados por estação e sem topologia. Nó único com demanda = total/" + ESTACOES_PADRAO_AGREGADO
                            + ". Ligue-o à malha via conexoes.csv e/ou detalhe as estações em topologia_extra.csv.");

                }
            } else avisos.add(nl + ": sem nenhum dado de demanda; linha ignorada.");

            if (!ids.isEmpty()) linhas.put(lk, ids);
        }
    }


    /* Meses ausentes ou <= 0 recebem a média dos demais meses do próprio nó (ou da linha). */
    private void preencherLacunas() {
        Map<String, double[]> acc = new HashMap<>();

        for (No n : nos) {

            double s = 0; int c = 0;
            for (double d : n.demanda) if (!Double.isNaN(d) && d > 0) { s += d; c++; }

            if (c > 0) {
                n.media = s / c;
            } else {
                n.media = Double.NaN;
            }
            if (c > 0) { double[] a = acc.computeIfAbsent(n.linhaChave, x -> new double[2]); a[0] += n.media; a[1]++; }
        }
        int cont = 0; List<String> det = new ArrayList<>();

        for (No n : nos) {
            for (int m = 0; m < 12; m++) {
                double d = n.demanda[m];
                if (Double.isNaN(d) || d <= 0) {
                    double v = n.media;
                    if (Double.isNaN(v)) {
                        double[] agregadoLinha = acc.get(n.linhaChave);
                        if (agregadoLinha != null) {
                            v = agregadoLinha[0] / agregadoLinha[1];
                        } else {
                            v = 0;
                        }
                    }
                    n.demanda[m] = v; cont++;
                    if (det.size() < 12) det.add(n.rotulo() + "/" + MESES[m]);
                }

            }

        }
        if (cont > 0) avisos.add(cont + " valor(es) de demanda ausente/zerado substituído(s) pela média do próprio nó. Ex.: " + det);

    }

    private boolean addAresta(int a, int b, boolean bald) {

        if (a == b) return false;

        String k = Math.min(a, b) + "-" + Math.max(a, b);


        if (!arestasSet.add(k)) return false;

        int indicadorBaldeacao = 0;
        if (bald) {
            indicadorBaldeacao = 1;
        }

        adj.get(a).add(new int[]{b, indicadorBaldeacao});
        adj.get(b).add(new int[]{a, indicadorBaldeacao});

        return true;

    }

    /*
     * Arestas de percurso: estações consecutivas da mesma linha.
     * Baldeações: somente as 4 colunas explícitas do conexoes.csv validado.
     * Estações homônimas NÃO devem ser unidas automaticamente: Lapa nas
     * Linhas 7 e 8, por exemplo, não possui conexão direta no conjunto de dados.
     */
    private void montarArestas(Path conex) throws IOException {
        if (conex == null || !Files.isRegularFile(conex)) {
            throw new IOException("Arquivo conexoes.csv obrigatório: " + conex);
        }

        for (List<Integer> ids : linhas.values()) {
            for (int i = 0; i + 1 < ids.size(); i++) {
                int origem = ids.get(i);
                int destino = ids.get(i + 1);
                addAresta(origem, destino, false);
            }
        }

        List<String> linhasCsv = Files.readAllLines(conex, StandardCharsets.UTF_8);
        if (linhasCsv.size() < 2) {
            throw new IOException("conexoes.csv não contém baldeações.");
        }

        String cabecalho = linhasCsv.get(0).replace("\uFEFF", "");
        char separador = detectarSeparador(cabecalho);
        List<String> colunas = dividir(cabecalho, separador);
        String[] esperadas = {"linhaa", "estacaoa", "linhab", "estacaob"};

        if (colunas.size() != 4) {
            throw new IOException("Cabeçalho inválido em conexoes.csv (esperadas quatro colunas).");
        }

        for (int i = 0; i < esperadas.length; i++) {
            if (!chaveTexto(colunas.get(i)).equals(esperadas[i])) {
                throw new IOException("Coluna inválida em conexoes.csv: " + colunas.get(i));
            }
        }

        int baldeacoes = 0;
        for (int linha = 1; linha < linhasCsv.size(); linha++) {
            String texto = linhasCsv.get(linha).replace("\uFEFF", "").trim();
            if (texto.isEmpty()) {
                continue;
            }

            List<String> campos = dividir(texto, separador);
            if (campos.size() != 4) {
                throw new IOException("conexoes.csv, linha " + (linha + 1) + ": quatro campos obrigatórios.");
            }

            int origem = acharNo(chaveLinha(campos.get(0)), chaveTexto(campos.get(1)));
            int destino = acharNo(chaveLinha(campos.get(2)), chaveTexto(campos.get(3)));

            if (origem < 0 || destino < 0) {
                throw new IOException("conexoes.csv, linha " + (linha + 1)
                        + ": estação não encontrada: " + texto);
            }

            if (!addAresta(origem, destino, true)) {
                throw new IOException("conexoes.csv, linha " + (linha + 1)
                        + ": conexão repetida ou inválida: " + texto);
            }
            baldeacoes++;
        }

        avisos.add("Baldeações explícitas carregadas: " + baldeacoes
                + ". Não foram inferidas conexões pelo nome da estação.");

        List<String> isolados = new ArrayList<>();
        for (No no : nos) {
            if (adj.get(no.id).isEmpty()) {
                isolados.add(no.rotulo());
            }
        }
        if (!isolados.isEmpty()) {
            avisos.add("Nós isolados: " + isolados);
        }
    }

    public int numBaldeacoes() {
        int quantidade = 0;
        for (int i = 0; i < adj.size(); i++) {
            for (int[] aresta : adj.get(i)) {
                if (aresta[0] > i && aresta[1] == 1) {
                    quantidade++;
                }
            }
        }
        return quantidade;
    }


    private int acharNo(String lk, String ek) {
        List<Integer> ids = linhas.get(lk);

        if (ids == null) return -1;


        if (ids.size() == 1 && nos.get(ids.get(0)).agregado) return ids.get(0);
        for (int id : ids) if (nos.get(id).chave.equals(ek)) return id;
        return -1;
    }

    public int numArestas() { int s = 0; for (List<int[]> l : adj) s += l.size(); return s / 2; }
    public boolean ehBaldeacao(int a, int b) { for (int[] e : adj.get(a)) if (e[0] == b) return e[1] == 1; return false; }

    // ---------------------------------------------------------------- rede do mês / calibração
    public Rede rede(int mes, int mobilidade) {
        return cacheRede.computeIfAbsent(mes * 2 + mobilidade, chv -> {
            int n = nos.size();

            Rede r = new Rede();
            r.w = new double[n]; r.viz = new int[n][]; r.c = new double[n][];
            double max = 0;
            for (No no : nos) max = Math.max(max, no.demanda[mes]);

            // Peso da estação no mês: w_i,m = D_i,m / max_j(D_j,m).
        for (No no : nos) {
            if (max > 0) {
                r.w[no.id] = no.demanda[mes] / max;
            } else {
                r.w[no.id] = 0;
            }
        }
            for (int i = 0; i < n; i++) {
                int d = adj.get(i).size();

                r.viz[i] = new int[d]; r.c[i] = new double[d];


                double soma = 0;
                for (int k = 0; k < d; k++) { int j = adj.get(i).get(k)[0]; r.viz[i][k] = j; soma += (r.w[i] + r.w[j]) / 2; }
                for (int k = 0; k < d; k++) {

                    int j = r.viz[i][k];
                    double wij = (r.w[i] + r.w[j]) / 2;                           // w_ij,m = média dos pesos

                    // Mobilidade 0: C_ij = (w_i + w_j) / 2.
                    // Mobilidade 1: T_ij = w_ij / soma_k(w_ik); C_ij = w_i * T_ij.
                    if (mobilidade == 0) {
                        r.c[i][k] = wij;
                    } else if (soma > 0) {
                        r.c[i][k] = r.w[i] * wij / soma;
                    } else {
                        r.c[i][k] = 0;
                    }
                }

            }

            r.cin = new double[n][];
            for (int j = 0; j < n; j++) {
                r.cin[j] = new double[r.viz[j].length];
                for (int k = 0; k < r.viz[j].length; k++) {
                    int i = r.viz[j][k];
                    for (int q = 0; q < r.viz[i].length; q++) if (r.viz[i][q] == j) { r.cin[j][k] = r.c[i][q]; break; }
                }
            }


            r.rho = raioEspectral(r);
            return r;
        });

    }


    /* rho(C) por iteração de potência em (C+I) (evita oscilação em grafos bipartidos): rho(C+I) = rho(C)+1. */
    private double raioEspectral(Rede r) {

        int n = r.w.length;
        double[] x = new double[n], y = new double[n];

        Arrays.fill(x, 1.0 / Math.sqrt(n));
        double lam = 0;
        for (int it = 0; it < 5000; it++) {
            double norm = 0;
            for (int i = 0; i < n; i++) {
                double s = x[i];
                for (int k = 0; k < r.viz[i].length; k++) s += r.c[i][k] * x[r.viz[i][k]];
                y[i] = s; norm += s * s;
            }

            norm = Math.sqrt(norm);
            for (int i = 0; i < n; i++) x[i] = y[i] / norm;
            if (Math.abs(norm - lam) < 1e-12) { lam = norm; break; }
            lam = norm;
        }
        return Math.max(lam - 1.0, 1e-9);
    }

    /* beta tal que rho(K) = beta * rho(C) * T_inf = R0 do cenário. */
    public double beta(ModeloEpidemiologico.Cenario c, int mes, int mob, ModeloEpidemiologico modelo) {
        // Transmissibilidade (beta) = R0 / (duracao_infecciosa * raio_espectral(C)).
        double duracao = modelo.duracaoInfecciosa;
        double raioEspectral = rede(mes, mob).rho;
        double beta = c.r0 / (duracao * raioEspectral);
        return beta;


    }

    // ---------------------------------------------------------------- MODO 1: percurso

    /*
     * BFS: menor número de arestas entre origem e destino.
     * Arestas de baldeação também contam como uma aresta de percurso.
     */
    public List<Integer> menorNumeroEstacoes(int origem, int destino) {
        int quantidade = nos.size();
        int[] anterior = new int[quantidade];
        Arrays.fill(anterior, -2);

        Deque<Integer> fila = new ArrayDeque<>();
        anterior[origem] = -1;
        fila.add(origem);

        while (!fila.isEmpty()) {
            int atual = fila.removeFirst();
            if (atual == destino) {
                break;
            }

            for (int[] aresta : adj.get(atual)) {
                int vizinho = aresta[0];
                if (anterior[vizinho] == -2) {
                    anterior[vizinho] = atual;
                    fila.addLast(vizinho);
                }
            }
        }

        return reconstruir(anterior, origem, destino);
    }

    /*
     * Dijkstra: minimiza a exposição ponderada do percurso.
     * Custo ao entrar no nó j = w_j * (t_est + t_bald se houver baldeação).
     * Não usa uma matriz origem-destino nem estima tempo real de viagem.
     */
    public List<Integer> menorExposicao(int origem, int destino, int mes, int mobilidade,
                                       double tEst, double tBald) {
        Rede rede = rede(mes, mobilidade);
        int quantidade = nos.size();

        double[] distancia = new double[quantidade];
        int[] anterior = new int[quantidade];
        boolean[] visitado = new boolean[quantidade];
        Arrays.fill(distancia, Double.POSITIVE_INFINITY);
        Arrays.fill(anterior, -2);

        distancia[origem] = 0;
        anterior[origem] = -1;

        for (int passo = 0; passo < quantidade; passo++) {
            int atual = -1;
            double menor = Double.POSITIVE_INFINITY;

            // Em empate, escolhe o menor ID para manter resultados reproduzíveis.
            for (int id = 0; id < quantidade; id++) {
                if (!visitado[id] && distancia[id] < menor) {
                    atual = id;
                    menor = distancia[id];
                }
            }

            if (atual == -1) {
                break;
            }
            visitado[atual] = true;

            for (int[] aresta : adj.get(atual)) {
                int vizinho = aresta[0];
                if (visitado[vizinho]) {
                    continue;
                }

                double tempo = tEst;
                if (aresta[1] == 1) {
                    tempo += tBald;
                }

                // Exposição ponderada: custo(atual, vizinho) = w_vizinho * tempo.
                double custo = rede.w[vizinho] * tempo;
                double alternativa = distancia[atual] + custo;

                if (alternativa < distancia[vizinho]) {
                    distancia[vizinho] = alternativa;
                    anterior[vizinho] = atual;
                }
            }
        }

        return reconstruir(anterior, origem, destino);
    }

    private List<Integer> reconstruir(int[] prev, int o, int d) {
        if (prev[d] == -2) return null;
        LinkedList<Integer> c = new LinkedList<>();
        for (int v = d; v != -1; v = prev[v]) c.addFirst(v);

        return c;

    }


    /*
     * lambda_i = beta * w_i * k * t_i (t em dias = min/1440; k = nº de infectados presentes na estação);
     * p_i = 1 - exp(-lambda_i); proteção via odds; P_total = 1 - prod(1 - p_i).

     * Resultado condicionado à presença de infectado(s) em cada estação do percurso.
     */
    public AvalPercurso avaliarPercurso(List<Integer> caminho, int mes, int mob, double beta, double k,
                                        double tEst, double tBald, double or) {
        Rede r = rede(mes, mob); int n = caminho.size();
        AvalPercurso a = new AvalPercurso();

        a.nos = caminho; a.baldeacao = new boolean[n]; a.peso = new double[n];
        a.lambda = new double[n]; a.p = new double[n]; a.acum = new double[n];


        double surv = 1;

        for (int i = 0; i < n; i++) {

            int v = caminho.get(i);
            a.baldeacao[i] = i > 0 && ehBaldeacao(caminho.get(i - 1), v);
            double t = tEst;
            if (a.baldeacao[i]) {
                t += tBald;
            }


            a.peso[i] = r.w[v];


            // Taxa de contágio (lambda_i) = beta * peso_i * k * tempo_em_dias.
            a.lambda[i] = beta * r.w[v] * k * (t / 1440.0);

            // Probabilidade local (p_i) = 1 - exp(-lambda_i), com proteção por OR.
            double p = 1 - Math.exp(-a.lambda[i]);
            a.p[i] = ModeloEpidemiologico.aplicarOR(p, or);
            surv *= (1 - a.p[i]);
            // Probabilidade acumulada = 1 - produto(1 - p_i).
            a.acum[i] = 1 - surv;
        }
        if (n > 0) {
            a.total = a.acum[n - 1];
        } else {
            a.total = 0;
        }
        return a;
    }

    // ---------------------------------------------------------------- MODO 2: disseminação SEIR estocástica na rede
    static int binom(int n, double p, Random rng) {


        if (n <= 0 || p <= 0) return 0;
        if (p >= 1) return n;


        double mean = n * p;
        if (n <= 40) { int c = 0; for (int i = 0; i < n; i++) if (rng.nextDouble() < p) c++; return c; }

        if (mean < 15) { double L = Math.exp(-mean), pp = 1; int k = 0; do { k++; pp *= rng.nextDouble(); } while (pp > L); return Math.min(k - 1, n); }
        int x = (int) Math.round(mean + Math.sqrt(mean * (1 - p)) * rng.nextGaussian());
        return Math.max(0, Math.min(n, x));

    }

    /*
     * SEIR estocástico por grupos: cada estação i tem N_i = w_i*popEscala pessoas (S,E,I,R).
     * Pressão sobre j: Lambda_j = beta * m * soma_i(C_ij * I_i); probabilidade por suscetível p_j = 1 - exp(-Lambda_j / N_j)
     * (equivale a 1 - prod(1 - p_ij)); proteção por odds; novos expostos ~ Binomial(S_j, p_j).
     * E->I com taxa 1/latência; I->R com taxa 1/T_inf. Como a matriz de próxima geração é beta*T_inf*C,
     * a calibração rho(K)=R0 permanece válida. 'm' reduz o contato dos sintomáticos (isolamento).
     */

    public Resultado simular(Config cfg, ModeloEpidemiologico modelo) {
        int n = nos.size(), D = cfg.dias, R = cfg.execucoes;
        Rede rd = rede(cfg.mes, cfg.mobilidade);
        double tInf = modelo.duracaoInfecciosa;
        // Transmissibilidade (beta) = R0 / (6,3 dias * rho(C)).
        double beta = cfg.cenario.r0 / (tInf * rd.rho);
        double lat = cfg.cenario.latencia(tInf);
        // Taxas de transição diárias: p(E -> I) = 1 - exp(-1/latencia).
        // Taxas de transição diárias: p(I -> R) = 1 - exp(-1/duracao_infecciosa).
        double pEI = 1 - Math.exp(-1.0 / lat);
        double pIR = 1 - Math.exp(-1.0 / tInf);
        // Fração do período infeccioso após início dos sintomas.
        double posSint = Math.max(0, 1 - Math.max(0, cfg.cenario.incubacao - lat) / tInf);
        // Fator de contato: m = 1 - (1 - frac_assintomatica) * posSint * isolamento.
        double m = 1 - (1 - modelo.fracAssintomatica) * posSint * cfg.isolamento;

        int[] N = new int[n]; long popTot = 0;
        for (int i = 0; i < n; i++) { N[i] = Math.max(1, (int) Math.round(rd.w[i] * cfg.popEscala)); }
        for (int s : cfg.sementes) N[s] = Math.max(N[s], cfg.infectadosIniciais);

        for (int i = 0; i < n; i++) popTot += N[i];

        double[][] S = new double[R][D + 1], E = new double[R][D + 1], I = new double[R][D + 1],

                Rm = new double[R][D + 1], A = new double[R][D + 1];

        int[] cont = new int[n]; double[] somaCh = new double[n];

        double[] pico = new double[R], diaPico = new double[R], ataque = new double[R];
        for (int run = 0; run < R; run++) {

            Random rng = new Random(cfg.rngSeed + 1000003L * run);

            int[] s = N.clone(), e = new int[n], in = new int[n], rr = new int[n], cheg = new int[n];

            Arrays.fill(cheg, -1);
            for (int sd : cfg.sementes) { in[sd] = cfg.infectadosIniciais; s[sd] = N[sd] - in[sd]; cheg[sd] = 0; }

            registrar(s, e, in, rr, cheg, 0, S[run], E[run], I[run], Rm[run], A[run]);

            for (int d = 1; d <= D; d++) {
                int[] ne = new int[n], ni = new int[n], nr = new int[n];

                for (int j = 0; j < n; j++) {

                    if (s[j] > 0) {
                        double pres = 0;
                        for (int k = 0; k < rd.viz[j].length; k++) {
                            int i = rd.viz[j][k];

                            if (in[i] > 0) pres += beta * rd.cin[j][k] * m * in[i];

                        }
                        if (pres > 0) {
                            // Pressão(j) = beta * m * soma_i(C_ij * I_i).
                            // Taxa por suscetível = Pressão(j) / N_j.
                            // Probabilidade de infecção = 1 - exp(-Pressão(j) / N_j).
                            double probabilidade = 1 - Math.exp(-pres / N[j]);
                            probabilidade = ModeloEpidemiologico.aplicarOR(probabilidade, cfg.oddsRatio);
                            ne[j] = binom(s[j], probabilidade, rng);
                        }

                    }
                    ni[j] = binom(e[j], pEI, rng);

                    nr[j] = binom(in[j], pIR, rng);
                }

                for (int j = 0; j < n; j++) {
                    // Modelo SEIR: S' = S - novos_E; E' = E + novos_E - novos_I;
                    // I' = I + novos_I - novos_R; R' = R + novos_R.
                    s[j] -= ne[j];
                    e[j] += ne[j] - ni[j];
                    in[j] += ni[j] - nr[j];
                    rr[j] += nr[j];
                    if (ne[j] > 0 && cheg[j] < 0) cheg[j] = d;

                }
                registrar(s, e, in, rr, cheg, d, S[run], E[run], I[run], Rm[run], A[run]);

            }
            double pk = -1; int dp = 0;

            for (int d = 0; d <= D; d++) if (I[run][d] > pk) { pk = I[run][d]; dp = d; }
            // Taxa de ataque = 1 - suscetíveis_finais / população_simulada_total.
            pico[run] = pk;
            diaPico[run] = dp;
            ataque[run] = 1.0 - S[run][D] / popTot;

            for (int j = 0; j < n; j++) if (cheg[j] >= 0) { cont[j]++; somaCh[j] += cheg[j]; }

        }
        Resultado r = new Resultado();

        r.dias = D; r.execucoes = R; r.beta = beta; r.rNet = beta * tInf * rd.rho; r.popTotal = popTot;
        r.mS = new double[D + 1]; r.mE = new double[D + 1]; r.mI = new double[D + 1]; r.mR = new double[D + 1];

        r.mAlc = new double[D + 1]; r.p5I = new double[D + 1]; r.p95I = new double[D + 1];

        r.p5A = new double[D + 1]; r.p95A = new double[D + 1];

        for (int d = 0; d <= D; d++) {

            double[] cs = coluna(S, d), ce = coluna(E, d), ci = coluna(I, d), cr = coluna(Rm, d), ca = coluna(A, d);
            r.mS[d] = media(cs); r.mE[d] = media(ce); r.mI[d] = media(ci); r.mR[d] = media(cr); r.mAlc[d] = media(ca);

            r.p5I[d] = pct(ci, 0.05); r.p95I[d] = pct(ci, 0.95); r.p5A[d] = pct(ca, 0.05); r.p95A[d] = pct(ca, 0.95);
        }
        double[] fin = coluna(A, D);
        r.alcFinalMedia = media(fin); r.alcFinalMediana = pct(fin, 0.5);
        r.alcFinalP5 = pct(fin, 0.05); r.alcFinalP95 = pct(fin, 0.95);
        double v = 0; for (double x : fin) v += (x - r.alcFinalMedia) * (x - r.alcFinalMedia);
        if (R > 1) {
            r.alcFinalDp = Math.sqrt(v / (R - 1));
        } else {
            r.alcFinalDp = 0;
        }

        r.picoIMedio = media(pico); r.diaPicoMedio = media(diaPico);

        r.ataqueMedia = media(ataque); r.ataqueP5 = pct(ataque, 0.05); r.ataqueP95 = pct(ataque, 0.95);

        r.probAlcance = new double[n]; r.chegadaMedia = new double[n];

        for (int j = 0; j < n; j++) {
            r.probAlcance[j] = cont[j] / (double) R;
            if (cont[j] > 0) {
                r.chegadaMedia[j] = somaCh[j] / cont[j];
            } else {
                r.chegadaMedia[j] = Double.NaN;
            }

        }

        return r;
    }


    private static void registrar(int[] s, int[] e, int[] i, int[] r, int[] cheg, int d,
                                  double[] S, double[] E, double[] I, double[] R, double[] A) {
        double a = 0, b = 0, c = 0, f = 0, g = 0;

        for (int j = 0; j < s.length; j++) { a += s[j]; b += e[j]; c += i[j]; f += r[j]; if (cheg[j] >= 0) g++; }
        S[d] = a; E[d] = b; I[d] = c; R[d] = f; A[d] = g;
    }
    static double[] coluna(double[][] m, int d) { double[] c = new double[m.length]; for (int i = 0; i < m.length; i++) c[i] = m[i][d]; return c; }
    static double media(double[] valores) {
        if (valores.length == 0) {
            return 0;
        }
        double soma = 0;
        for (double valor : valores) {
            soma += valor;
        }
        return soma / valores.length;
    }

    static double pct(double[] v, double p) {
        double[] a = v.clone(); Arrays.sort(a);

        double idx = p * (a.length - 1); int lo = (int) Math.floor(idx), hi = (int) Math.ceil(idx);
        return a[lo] + (a[hi] - a[lo]) * (idx - lo);
    }

    /* Impacto simulado de cada estação como única semente (alcance final médio, em nº de nós). */
    public double[] impactoPorSemente(Config base, ModeloEpidemiologico modelo, int execs) {
        double[] imp = new double[nos.size()];
        for (int i = 0; i < imp.length; i++) {
            Config c = base.copia(); c.sementes = new ArrayList<>(List.of(i)); c.execucoes = execs;

            imp[i] = simular(c, modelo).alcFinalMedia;

        }
        return imp;
    }

    // ---------------------------------------------------------------- centralidade
    public double[] grau() { double[] g = new double[nos.size()]; for (int i = 0; i < g.length; i++) g[i] = adj.get(i).size(); return g; }


    /* Centralidade de intermediação (Brandes, grafo não ponderado). */

    public double[] betweenness() {

        if (betCache != null) return betCache;
        int n = nos.size(); double[] cb = new double[n];
        for (int s = 0; s < n; s++) {
            Deque<Integer> stack = new ArrayDeque<>(); List<List<Integer>> P = new ArrayList<>();
            for (int i = 0; i < n; i++) P.add(new ArrayList<>());

            double[] sigma = new double[n]; int[] dist = new int[n]; Arrays.fill(dist, -1);

            sigma[s] = 1; dist[s] = 0;
            Queue<Integer> q = new ArrayDeque<>(); q.add(s);
            while (!q.isEmpty()) {
                int v = q.poll(); stack.push(v);
                for (int[] e : adj.get(v)) {

                    int w = e[0];

                    if (dist[w] < 0) { dist[w] = dist[v] + 1; q.add(w); }
                    if (dist[w] == dist[v] + 1) { sigma[w] += sigma[v]; P.get(w).add(v); }
                }

            }
            double[] delta = new double[n];


            while (!stack.isEmpty()) {
                int w = stack.pop();
                for (int v : P.get(w)) delta[v] += sigma[v] / sigma[w] * (1 + delta[w]);

                if (w != s) cb[w] += delta[w];
            }
        }
        for (int i = 0; i < n; i++) cb[i] /= 2.0;

        return betCache = cb;

    }


    // ---------------------------------------------------------------- exportação da topologia
    public void exportarTopologia(Path dir) throws IOException {

        Files.createDirectories(dir);

        List<String> a = new ArrayList<>();

        StringBuilder h = new StringBuilder("id,linha,estacao,agregado,estimado");

        for (String m : MESES) h.append(",demanda_").append(m);

        a.add(h.toString());

        for (No n : nos) {
            StringBuilder sb = new StringBuilder(n.id + "," + Saida.q(n.linha) + "," + Saida.q(n.estacao) + "," + n.agregado + "," + n.estimado);

            for (double d : n.demanda) sb.append(',').append(Saida.f(d));

            a.add(sb.toString());

        }

        Saida.escrever(dir.resolve("topologia_nos.csv"), a);

        List<String> b = new ArrayList<>(List.of("id_a,linha_a,estacao_a,id_b,linha_b,estacao_b,tipo"));
        for (int i = 0; i < nos.size(); i++)
            for (int[] e : adj.get(i))
                if (e[0] > i) b.add(i + "," + Saida.q(nos.get(i).linha) + "," + Saida.q(nos.get(i).estacao) + "," + e[0] + ","

                        + Saida.q(nos.get(e[0]).linha) + "," + Saida.q(nos.get(e[0]).estacao) + "," + tipoAresta(e[1]));

        Saida.escrever(dir.resolve("topologia_arestas.csv"), b);
    }

    private static String tipoAresta(int indicador) {
        if (indicador == 1) {
            return "baldeacao";
        }
        return "mesma_linha";
    }

    // ================================================================ SAÍDA (arquivos)
    public static class Saida {
        public static Path novaPasta(Path base, String prefixo) throws IOException {


            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path p = base.resolve(prefixo + "_" + ts);
            int i = 1; while (Files.exists(p)) p = base.resolve(prefixo + "_" + ts + "_" + (i++));

            Files.createDirectories(p);
            return p;
        }
        public static void escrever(Path arq, List<String> linhas) throws IOException {
            Files.write(arq, linhas, StandardCharsets.UTF_8);
        }
        public static String q(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }
        public static String f(double v) {
            if (Double.isNaN(v)) {
                return "";
            }
            return String.format(Locale.US, "%.6g", v);
        }
        public static void json(Path arq, Object o) throws IOException {
            StringBuilder sb = new StringBuilder(); js(sb, o, 0);
            Files.write(arq, sb.toString().getBytes(StandardCharsets.UTF_8));

        }

        private static String esc(String s) {

            return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t");
        }

        private static void js(StringBuilder sb, Object o, int ind) {
            String pad = "  ".repeat(ind + 1), end = "  ".repeat(ind);
            if (o == null) sb.append("null");
            else if (o instanceof Map) {

                Map<?, ?> m = (Map<?, ?>) o;

                if (m.isEmpty()) { sb.append("{}"); return; }
                sb.append("{\n"); int i = 0;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    sb.append(pad).append('"').append(esc(String.valueOf(e.getKey()))).append("\": ");
                    js(sb, e.getValue(), ind + 1);
                    if (++i < m.size()) sb.append(',');
                    sb.append('\n');
                }
                sb.append(end).append('}');
            } else if (o instanceof Collection) {
                Collection<?> c = (Collection<?>) o;

                if (c.isEmpty()) { sb.append("[]"); return; }
                sb.append("[\n"); int i = 0;
                for (Object x : c) { sb.append(pad); js(sb, x, ind + 1); if (++i < c.size()) sb.append(','); sb.append('\n'); }
                sb.append(end).append(']');
            } else if (o instanceof double[]) { List<Double> l = new ArrayList<>(); for (double d : (double[]) o) l.add(d); js(sb, l, ind); }
            else if (o instanceof int[]) { List<Integer> l = new ArrayList<>(); for (int d : (int[]) o) l.add(d); js(sb, l, ind); }
            else if (o instanceof Double) {
                double valor = (Double) o;
                if (Double.isNaN(valor) || Double.isInfinite(valor)) {
                    sb.append("null");
                } else {
                    sb.append(o.toString());
                }
            }
            else if (o instanceof Number || o instanceof Boolean) sb.append(o);
            else sb.append('"').append(esc(o.toString())).append('"');
        }
    }

    // ================================================================ GRÁFICOS (Java2D, sem dependências)
    public static final Color[] PALETA = {new Color(31, 119, 180), new Color(214, 39, 40), new Color(44, 160, 44),
            new Color(255, 127, 14), new Color(148, 103, 189), new Color(140, 86, 75), new Color(227, 119, 194),
            new Color(23, 190, 207), new Color(188, 189, 34), new Color(127, 127, 127), new Color(0, 0, 0), new Color(102, 194, 165)};

    // ----------------------------------------------------------------- apresentação das figuras
    private static final Color COR_TEXTO = new Color(35, 35, 35);
    private static final Color COR_GRADE = new Color(207, 207, 207);
    private static final Color COR_BORDA = Color.BLACK;
    private static final Font FONTE_TITULO = new Font("SansSerif", Font.BOLD, 37);
    private static final Font FONTE_SUBTITULO = new Font("SansSerif", Font.PLAIN, 29);
    private static final Font FONTE_EIXO = new Font("SansSerif", Font.BOLD, 34);
    private static final Font FONTE_NUMERO = new Font("SansSerif", Font.PLAIN, 29);
    private static final Font FONTE_LEGENDA = new Font("SansSerif", Font.PLAIN, 27);
    private static final Font FONTE_NOTA = new Font("SansSerif", Font.PLAIN, 22);

    // Cores usadas no exemplo aprovado (vermelho, azul, verde, laranja etc.).
    private static final Color[] CORES_CIENTIFICAS = {
            new Color(220, 20, 60), new Color(65, 105, 225),
            new Color(34, 139, 34), new Color(255, 140, 0),
            new Color(148, 0, 211), new Color(0, 139, 139),
            new Color(205, 92, 92), new Color(85, 107, 47),
            new Color(70, 130, 180), new Color(160, 82, 45),
            new Color(199, 21, 133), new Color(46, 139, 87)
    };

    private static double passo(double intervalo) {
        if (!Double.isFinite(intervalo) || intervalo <= 0) {
            return 1;
        }
        double ideal = intervalo / 6;
        double potencia = Math.pow(10, Math.floor(Math.log10(ideal)));
        double fracao = ideal / potencia;
        double fator = 1;
        if (fracao > 1 && fracao <= 2) {
            fator = 2;
        } else if (fracao > 2 && fracao <= 5) {
            fator = 5;
        } else if (fracao > 5) {
            fator = 10;
        }
        return fator * potencia;
    }

    private static String fmt(double valor, double intervalo) {
        if (Math.abs(valor) < 1e-15) {
            return "0";
        }
        if (Math.abs(valor) >= 1e7 || Math.abs(valor) < 1e-3) {
            return String.format(Locale.forLanguageTag("pt-BR"), "%.1e", valor);
        }
        int casas = 0;
        if (intervalo < 1) {
            casas = (int) Math.ceil(-Math.log10(intervalo));
        }
        casas = Math.max(0, Math.min(casas, 6));
        return String.format(Locale.forLanguageTag("pt-BR"), "%,." + casas + "f", valor);
    }

    private static Graphics2D nova(BufferedImage imagem) {
        Graphics2D g = imagem.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, imagem.getWidth(), imagem.getHeight());
        return g;
    }

    // Divide o título apenas para apresentá-lo; o nome original não é alterado.
    private static String[] dividirTitulo(String titulo) {
        int separador = titulo.indexOf(" · ");
        if (separador < 0) {
            return new String[]{titulo, ""};
        }
        String principal = titulo.substring(0, separador).trim();
        String complemento = titulo.substring(separador + 3).trim();
        return new String[]{principal, complemento};
    }

    private static String abreviar(Graphics2D g, String texto, int largura) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(texto) <= largura) {
            return texto;
        }
        int limite = texto.length();
        while (limite > 1 && fm.stringWidth(texto.substring(0, limite) + "…") > largura) {
            limite--;
        }
        return texto.substring(0, limite) + "…";
    }

    // Quebra rótulos longos do mapa em linhas, sem ocultá-los por reticências.
    private static List<String> quebrarRotulo(Graphics2D g, String texto, int largura) {
        List<String> partes = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String palavra : texto.split(" ")) {
            String candidato = atual.length() == 0 ? palavra : atual + " " + palavra;
            if (atual.length() > 0 && g.getFontMetrics().stringWidth(candidato) > largura) {
                partes.add(atual.toString());
                atual.setLength(0);
                atual.append(palavra);
            } else {
                atual.setLength(0);
                atual.append(candidato);
            }
        }
        if (atual.length() > 0) partes.add(atual.toString());
        return partes;
    }

    private static void centralizar(Graphics2D g, String texto, int x, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(texto, x - fm.stringWidth(texto) / 2, y);
    }

    private static void tituloFigura(Graphics2D g, String titulo, int largura) {
        String[] partes = dividirTitulo(titulo);
        g.setFont(FONTE_TITULO);
        g.setColor(COR_TEXTO);
        String principal = abreviar(g, partes[0], largura - 140);
        int centro = largura / 2;
        centralizar(g, principal, centro, 66);
        int comprimento = g.getFontMetrics().stringWidth(principal);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(centro - comprimento / 2, 73, centro + comprimento / 2, 73);
        if (!partes[1].isEmpty()) {
            g.setFont(FONTE_SUBTITULO);
            String complemento = abreviar(g, partes[1], largura - 140);
            centralizar(g, complemento, centro, 123);
        }
    }

    private static int caixaNotas(Graphics2D g, int x, int y, int largura, List<String> notas) {
        if (notas == null || notas.isEmpty()) {
            return y;
        }
        g.setFont(FONTE_NOTA);
        int linha = 30;
        int padding = 16;
        int altura = padding * 2 + notas.size() * linha;
        g.setColor(new Color(250, 250, 250, 235));
        g.fillRoundRect(x, y, largura, altura, 16, 16);
        g.setColor(new Color(120, 120, 120));
        g.setStroke(new BasicStroke(1.4f));
        g.drawRoundRect(x, y, largura, altura, 16, 16);
        g.setColor(COR_TEXTO);
        int py = y + padding + 22;
        for (String nota : notas) {
            g.drawString(abreviar(g, nota, largura - 2 * padding), x + padding, py);
            py += linha;
        }
        return y + altura;
    }

    private static BasicStroke tracoSerie(int indice, float espessura) {
        float[] padrao = null;
        if (indice % 4 == 1) {
            padrao = new float[]{16, 8};
        } else if (indice % 4 == 2) {
            padrao = new float[]{4, 8};
        } else if (indice % 4 == 3) {
            padrao = new float[]{15, 7, 4, 7};
        }
        if (padrao == null) {
            return new BasicStroke(espessura, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        }
        return new BasicStroke(espessura, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, padrao, 0);
    }

    private static void marcadorSerie(Graphics2D g, int indice, int x, int y, Color cor) {
        int raio = 9;
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3.1f));
        if (indice % 3 == 0) {
            g.fillOval(x - raio, y - raio, 2 * raio, 2 * raio);
            g.setColor(cor);
            g.drawOval(x - raio, y - raio, 2 * raio, 2 * raio);
        } else if (indice % 3 == 1) {
            g.fillRect(x - raio, y - raio, 2 * raio, 2 * raio);
            g.setColor(cor);
            g.drawRect(x - raio, y - raio, 2 * raio, 2 * raio);
        } else {
            Polygon triangulo = new Polygon();
            triangulo.addPoint(x, y - raio);
            triangulo.addPoint(x + raio, y + raio);
            triangulo.addPoint(x - raio, y + raio);
            g.fillPolygon(triangulo);
            g.setColor(cor);
            g.drawPolygon(triangulo);
        }
    }

    // Curvas iguais são agrupadas apenas na figura, nunca nos resultados numéricos.
    private static boolean mesmosValores(double[] primeiro, double[] segundo) {
        if (primeiro == null || segundo == null) {
            return primeiro == null && segundo == null;
        }
        if (primeiro.length != segundo.length) {
            return false;
        }
        for (int i = 0; i < primeiro.length; i++) {
            double a = primeiro[i];
            double b = segundo[i];
            if (!Double.isFinite(a) || !Double.isFinite(b)) {
                if (Double.doubleToLongBits(a) != Double.doubleToLongBits(b)) {
                    return false;
                }
            } else {
                double tolerancia = 1e-12 * Math.max(1e-12, Math.max(Math.abs(a), Math.abs(b)));
                if (Math.abs(a - b) > tolerancia) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean mesmasCurvas(Serie a, Serie b) {
        if (!mesmosValores(a.x, b.x)) {
            return false;
        }
        if (!mesmosValores(a.y, b.y)) {
            return false;
        }
        if (!mesmosValores(a.lo, b.lo)) {
            return false;
        }
        return mesmosValores(a.hi, b.hi);
    }

    private static String nomeBreveSerie(String nome) {
        // O cenário continua completo nos CSVs; a legenda mostra apenas o indicador principal.
        int indicadorR0 = nome.lastIndexOf("(R0=");
        int indicadorRe = nome.lastIndexOf("(Re=");
        int indice = Math.max(indicadorR0, indicadorRe);
        if (indice >= 0) {
            int fim = nome.indexOf(',', indice);
            if (fim > indice) {
                String descricao = nome.substring(0, indice).trim();
                String indicador = nome.substring(indice + 1, fim);
                return descricao + " · " + indicador;
            }
        }
        return nome;
    }

    private static String nomeProtecaoBreve(String nome) {
        if (nome.startsWith("Proteção ")) {
            return nome.substring("Proteção ".length());
        }
        return nome;
    }

    private static String nomeCurvasCoincidentes(String primeiro, String segundo) {
        String bfs = "Menos estações | ";
        String dijkstra = "Menor exposição | ";
        if (primeiro.startsWith(bfs) && segundo.startsWith(dijkstra)) {
            String a = primeiro.substring(bfs.length());
            String b = segundo.substring(dijkstra.length());
            if (a.equals(b)) {
                return "BFS = Dijkstra · " + nomeProtecaoBreve(a);
            }
        }
        if (primeiro.startsWith(dijkstra) && segundo.startsWith(bfs)) {
            String a = primeiro.substring(dijkstra.length());
            String b = segundo.substring(bfs.length());
            if (a.equals(b)) {
                return "BFS = Dijkstra · " + nomeProtecaoBreve(a);
            }
        }
        return primeiro + " = " + segundo;
    }

    // A indicação completa do contexto continua no CSV e nos metadados.
    // No gráfico, rótulos curtos preservam espaço para os valores.
    private static String rotuloEixo(String eixo) {
        if (eixo.equals("Nº de estações percorridas") || eixo.equals("Estações percorridas [ordem no trajeto]")) {
            return "Estações percorridas [ordem no trajeto]";
        }
        if (eixo.startsWith("P acumulada") || eixo.startsWith("Probabilidade acumulada")) {
            return "Probabilidade acumulada de transmissão [0–1]";
        }
        if (eixo.startsWith("Nº de pessoas infecciosas") || eixo.startsWith("Pessoas infecciosas")) {
            return "Pessoas infecciosas [indivíduos simulados]";
        }
        if (eixo.equals("Nº de nós alcançados") || eixo.startsWith("Estações alcançadas")) {
            return "Estações alcançadas [nós da rede]";
        }
        if (eixo.startsWith("Dia")) {
            return "Dia da simulação [dias]";
        }
        return eixo;
    }

    // Quantos pontos de séries cairiam atrás de uma legenda nesta posição?
    // A escolha afeta somente o desenho, nunca os dados da simulação.
    private static int interferenciasLegenda(List<Serie> series, int x, int y, int largura, int altura,
                                              double xMin, double xMax, double yMax,
                                              int esquerda, int inferior, int larguraPlot, int alturaPlot) {
        int total = 0;
        for (Serie serie : series) {
            for (int i = 0; i < serie.x.length; i++) {
                int px = xImagem(serie.x[i], xMin, xMax, esquerda, larguraPlot);
                int py = yImagem(serie.y[i], yMax, inferior, alturaPlot);
                if (px >= x && px <= x + largura && py >= y && py <= y + altura) {
                    total++;
                }
            }
        }
        return total;
    }

    private static int xImagem(double x, double minimo, double maximo, int esquerda, int largura) {
        return esquerda + (int) Math.round((x - minimo) / (maximo - minimo) * largura);
    }

    private static int yImagem(double y, double maximo, int inferior, int altura) {
        return inferior - (int) Math.round(y / maximo * altura);
    }

    public static void graficoLinhas(Path arq, String titulo, String xl, String yl,
                                    List<Serie> series) throws IOException {
        graficoLinhas(arq, titulo, xl, yl, series, null);
    }

    public static void graficoLinhas(Path arq, String titulo, String xl, String yl,
                                    List<Serie> series, List<String> notas) throws IOException {
        if (series.isEmpty()) {
            throw new IOException("Não há séries para desenhar: " + titulo);
        }

        List<Serie> visiveis = new ArrayList<>();
        List<String> nomes = new ArrayList<>();
        for (Serie candidata : series) {
            int igual = -1;
            for (int i = 0; i < visiveis.size(); i++) {
                if (mesmasCurvas(visiveis.get(i), candidata)) {
                    igual = i;
                    break;
                }
            }
            if (igual < 0) {
                visiveis.add(candidata);
                nomes.add(nomeBreveSerie(candidata.nome));
            } else {
                // Ex.: BFS e Dijkstra têm a mesma probabilidade acumulada.
                nomes.set(igual, nomeCurvasCoincidentes(nomes.get(igual),
                        nomeBreveSerie(candidata.nome)));
            }
        }

        // Layout em três regiões exclusivas: curvas (esquerda), legenda (direita)
        // e informações / eixo X (abaixo). Assim nenhum texto cobre valores.
        int qtdNotas = notas == null ? 0 : notas.size();
        int larguraImagem = 2600;
        int alturaImagem = Math.max(qtdNotas > 0 ? 1270 : 1190,
                670 + visiveis.size() * 47 + (qtdNotas > 0 ? 130 : 0));
        int esquerda = 215;
        int superior = 188;
        int margemInferior = qtdNotas > 0 ? Math.max(305, 230 + qtdNotas * 30) : 180;
        int inferior = alturaImagem - margemInferior;
        int alturaGrafico = inferior - superior;
        int direita = 1870;
        int larguraGrafico = direita - esquerda;

        BufferedImage imagem = new BufferedImage(larguraImagem, alturaImagem,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = nova(imagem);
        tituloFigura(g, titulo, larguraImagem);
        int rodapeNotas = inferior + 86;

        double minimoX = Double.POSITIVE_INFINITY;
        double maximoX = Double.NEGATIVE_INFINITY;
        double maximoY = 0;
        for (Serie serie : visiveis) {
            for (int i = 0; i < serie.x.length; i++) {
                if (Double.isFinite(serie.x[i])) {
                    minimoX = Math.min(minimoX, serie.x[i]);
                    maximoX = Math.max(maximoX, serie.x[i]);
                }
                if (Double.isFinite(serie.y[i])) {
                    maximoY = Math.max(maximoY, serie.y[i]);
                }
                if (serie.hi != null && Double.isFinite(serie.hi[i])) {
                    maximoY = Math.max(maximoY, serie.hi[i]);
                }
            }
        }
        if (!Double.isFinite(minimoX)) {
            minimoX = 0;
            maximoX = 1;
        }
        if (maximoX <= minimoX) {
            maximoX = minimoX + 1;
        }
        if (maximoY <= 0) {
            maximoY = 1;
        }

        double passoY = passo(maximoY);
        double tetoY = Math.ceil(maximoY / passoY) * passoY;
        double passoX = passo(maximoX - minimoX);
        String eixoX = xl.toLowerCase(Locale.ROOT);
        if (eixoX.contains("dia") || eixoX.contains("estaç")) {
            passoX = Math.max(1, Math.ceil(passoX));
        }

        // Fundo branco e divisões internas cinzas, com moldura preta completa.
        g.setColor(Color.WHITE);
        g.fillRect(esquerda, superior, larguraGrafico, alturaGrafico);
        g.setFont(FONTE_NUMERO);
        int totalMarcasY = Math.max(1, (int) Math.round(tetoY / passoY));
        for (int i = 0; i <= totalMarcasY; i++) {
            double valor = i * passoY;
            if (valor > tetoY + passoY * 1e-7) {
                break;
            }
            int py = yImagem(valor, tetoY, inferior, alturaGrafico);
            g.setColor(COR_GRADE);
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(esquerda, py, direita, py);
            g.setColor(COR_TEXTO);
            String numero = fmt(valor, passoY);
            g.drawString(numero, esquerda - g.getFontMetrics().stringWidth(numero) - 19, py + 10);
        }
        for (double x = Math.ceil(minimoX / passoX) * passoX;
                x <= maximoX + passoX * 1e-7; x += passoX) {
            int px = xImagem(x, minimoX, maximoX, esquerda, larguraGrafico);
            if (px < esquerda || px > direita) {
                continue;
            }
            g.setColor(COR_GRADE);
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(px, superior, px, inferior);
            g.setColor(COR_TEXTO);
            String numero = fmt(x, passoX);
            centralizar(g, numero, px, inferior + 47);
        }

        Shape recorteOriginal = g.getClip();
        g.clipRect(esquerda, superior, larguraGrafico, alturaGrafico);
        // Intervalo de percentis P5-P95 quando a série contém limites.
        for (Serie serie : visiveis) {
            if (serie.lo == null || serie.hi == null || serie.x.length < 2) {
                continue;
            }
            Path2D faixa = new Path2D.Double();
            for (int i = 0; i < serie.x.length; i++) {
                int px = xImagem(serie.x[i], minimoX, maximoX, esquerda, larguraGrafico);
                int py = yImagem(Math.max(0, serie.hi[i]), tetoY, inferior, alturaGrafico);
                if (i == 0) {
                    faixa.moveTo(px, py);
                } else {
                    faixa.lineTo(px, py);
                }
            }
            for (int i = serie.x.length - 1; i >= 0; i--) {
                int px = xImagem(serie.x[i], minimoX, maximoX, esquerda, larguraGrafico);
                int py = yImagem(Math.max(0, serie.lo[i]), tetoY, inferior, alturaGrafico);
                faixa.lineTo(px, py);
            }
            faixa.closePath();
            Color cor = CORES_CIENTIFICAS[visiveis.indexOf(serie) % CORES_CIENTIFICAS.length];
            g.setColor(new Color(cor.getRed(), cor.getGreen(), cor.getBlue(), 34));
            g.fill(faixa);
        }

        for (int indice = 0; indice < visiveis.size(); indice++) {
            Serie serie = visiveis.get(indice);
            if (serie.x.length == 0) {
                continue;
            }
            Path2D caminho = new Path2D.Double();
            for (int i = 0; i < serie.x.length; i++) {
                int px = xImagem(serie.x[i], minimoX, maximoX, esquerda, larguraGrafico);
                int py = yImagem(serie.y[i], tetoY, inferior, alturaGrafico);
                if (i == 0) {
                    caminho.moveTo(px, py);
                } else {
                    caminho.lineTo(px, py);
                }
            }
            Color cor = CORES_CIENTIFICAS[indice % CORES_CIENTIFICAS.length];
            g.setColor(cor);
            g.setStroke(tracoSerie(indice, 3.6f));
            g.draw(caminho);
            int intervalo = Math.max(1, serie.x.length / 10);
            for (int i = 0; i < serie.x.length; i += intervalo) {
                int px = xImagem(serie.x[i], minimoX, maximoX, esquerda, larguraGrafico);
                int py = yImagem(serie.y[i], tetoY, inferior, alturaGrafico);
                marcadorSerie(g, indice, px, py, cor);
            }
            int ultimo = serie.x.length - 1;
            if (ultimo % intervalo != 0) {
                int px = xImagem(serie.x[ultimo], minimoX, maximoX, esquerda, larguraGrafico);
                int py = yImagem(serie.y[ultimo], tetoY, inferior, alturaGrafico);
                marcadorSerie(g, indice, px, py, cor);
            }
        }
        g.setClip(recorteOriginal);

        g.setColor(COR_BORDA);
        g.setStroke(new BasicStroke(2.2f));
        g.drawRect(esquerda, superior, larguraGrafico, alturaGrafico);

        if (notas != null && !notas.isEmpty()) {
            caixaNotas(g, esquerda + 10, rodapeNotas, larguraGrafico - 20, notas);
        }

        g.setFont(FONTE_EIXO);
        g.setColor(COR_TEXTO);
        centralizar(g, rotuloEixo(xl), esquerda + larguraGrafico / 2, alturaImagem - 46);
        g.rotate(-Math.PI / 2);
        centralizar(g, rotuloEixo(yl), -(superior + alturaGrafico / 2), 61);
        g.rotate(Math.PI / 2);

        // Legenda técnica como no gráfico de referência: dentro do quadrante superior direito.
        // Quando há muitas curvas, a legenda fica na margem para não ocultar resultados.
        g.setFont(FONTE_LEGENDA);
        int alturaLinha = 43;
        int margemLegenda = 20;
        int textoMaximo = 0;
        for (String nome : nomes) {
            textoMaximo = Math.max(textoMaximo, g.getFontMetrics().stringWidth(nome));
        }
        int legendaLargura = Math.min(textoMaximo + 137, larguraImagem - direita - 62);
        int legendaX = direita + 24;
        int legendaY = superior + 18;
        int alturaLegenda = 2 * margemLegenda + visiveis.size() * alturaLinha;
        g.setColor(new Color(255, 255, 255, 225));
        g.fillRoundRect(legendaX, legendaY, legendaLargura, alturaLegenda, 12, 12);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(legendaX, legendaY, legendaLargura, alturaLegenda, 12, 12);
        for (int i = 0; i < visiveis.size(); i++) {
            int ly = legendaY + margemLegenda + 25 + i * alturaLinha;
            Color cor = CORES_CIENTIFICAS[i % CORES_CIENTIFICAS.length];
            g.setColor(cor);
            g.setStroke(tracoSerie(i, 3.0f));
            g.drawLine(legendaX + 16, ly - 7, legendaX + 62, ly - 7);
            marcadorSerie(g, i, legendaX + 39, ly - 7, cor);
            g.setColor(COR_TEXTO);
            g.drawString(abreviar(g, nomes.get(i), legendaLargura - 108), legendaX + 81, ly);
        }

        g.dispose();
        ImageIO.write(imagem, "png", arq.toFile());
    }

    // Uma escala contínua de azuis substitui as cores independentes do ranking.
    // Cor continua entre azul claro (menor valor) e escuro (maior valor).
    private static Color corBarraAzul(double proporcao) {
        Color azulClaro = new Color(146, 200, 240);
        Color azulEscuro = new Color(12, 72, 150);
        int vermelho = (int) Math.round(azulClaro.getRed() + proporcao * (azulEscuro.getRed() - azulClaro.getRed()));
        int verde = (int) Math.round(azulClaro.getGreen() + proporcao * (azulEscuro.getGreen() - azulClaro.getGreen()));
        int azul = (int) Math.round(azulClaro.getBlue() + proporcao * (azulEscuro.getBlue() - azulClaro.getBlue()));
        return new Color(vermelho, verde, azul);
    }

    public static void graficoBarras(Path arq, String titulo, String eixo,
                                     List<String> rotulos, double[] valores) throws IOException {
        graficoBarras(arq, titulo, eixo, rotulos, valores, null);
    }

    public static void graficoBarras(Path arq, String titulo, String eixo,
                                     List<String> rotulos, double[] valores, List<String> notas) throws IOException {
        if (rotulos.size() != valores.length) {
            throw new IOException("As quantidades de rótulos e valores são diferentes.");
        }
        int quantidade = rotulos.size();
        if (quantidade == 0) {
            throw new IOException("Não há valores para desenhar o gráfico de barras.");
        }

        int espaco = 78;
        if (quantidade <= 6) {
            espaco = 108;
        }
        int superior = 194;
        int alturaGrafico = quantidade * espaco;
        int inferior = superior + alturaGrafico;
        int alturaImagem = Math.max(900, inferior + 320);

        double maximo = 0;
        for (double valor : valores) {
            if (Double.isFinite(valor)) {
                maximo = Math.max(maximo, valor);
            }
        }
        if (maximo <= 0) {
            maximo = 1;
        }
        double intervalo = passo(maximo);
        double teto = Math.ceil(maximo / intervalo) * intervalo;
        if (teto <= maximo) {
            teto += intervalo;
        }

        // Medimos os textos antes de criar a imagem para preservar os nomes completos.
        // Esta margem se adapta automaticamente às linhas e estações mais longas.
        BufferedImage amostra = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D medidas = nova(amostra);
        medidas.setFont(FONTE_NUMERO);
        int maiorRotulo = 0;
        for (String rotulo : rotulos) {
            maiorRotulo = Math.max(maiorRotulo, medidas.getFontMetrics().stringWidth(rotulo));
        }
        int esquerda = Math.max(800, maiorRotulo + 130);
        int larguraGrafico = 950;

        // A margem à direita é reservada aos números desenhados fora das barras.
        // Ela evita cortes mesmo se o maior valor terminar perto da moldura.
        Font fonteValores = new Font("SansSerif", Font.PLAIN, 29);
        medidas.setFont(fonteValores);
        int maiorNumero = 0;
        for (double valor : valores) {
            String numero = fmt(valor, intervalo);
            if (eixo.startsWith("%")) {
                numero += "%";
            }
            maiorNumero = Math.max(maiorNumero, medidas.getFontMetrics().stringWidth(numero));
        }
        medidas.dispose();
        int margemDireita = Math.max(190, maiorNumero + 115);
        int larguraImagem = Math.max(2100, esquerda + larguraGrafico + margemDireita);

        BufferedImage imagem = new BufferedImage(larguraImagem, alturaImagem,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = nova(imagem);
        tituloFigura(g, titulo, larguraImagem);
        int rodapeNotas = inferior + 28;

        // Fundo branco com linhas de grade cinza e contorno preto nos quatro lados.
        g.setColor(Color.WHITE);
        g.fillRect(esquerda, superior, larguraGrafico, alturaGrafico);
        g.setFont(FONTE_NUMERO);
        int totalMarcas = Math.max(1, (int) Math.round(teto / intervalo));
        for (int i = 0; i <= totalMarcas; i++) {
            double valor = i * intervalo;
            if (valor > teto + intervalo * 1e-7) {
                break;
            }
            int px = esquerda + (int) Math.round(valor / teto * larguraGrafico);
            g.setColor(COR_GRADE);
            g.setStroke(new BasicStroke(1.3f));
            g.drawLine(px, superior, px, inferior);
            g.setColor(COR_TEXTO);
            centralizar(g, fmt(valor, intervalo), px, inferior + 44);
        }

        for (int i = 0; i < quantidade; i++) {
            int y = superior + i * espaco;
            g.setFont(FONTE_NUMERO);
            g.setColor(COR_TEXTO);
            String nomeCompleto = rotulos.get(i);
            int larguraNome = g.getFontMetrics().stringWidth(nomeCompleto);
            g.drawString(nomeCompleto, esquerda - larguraNome - 30, y + espaco / 2 + 10);

            double valor = valores[i];
            int tamanho = (int) Math.round(Math.max(0, valor) / teto * larguraGrafico);
            double proporcao = Math.max(0, Math.min(1, valor / maximo));
            g.setColor(corBarraAzul(proporcao));
            if (tamanho > 0) {
                g.fillRoundRect(esquerda, y + 12, tamanho, espaco - 24, 10, 10);
            }

            g.setColor(COR_TEXTO);
            g.setFont(fonteValores);
            String exibicao = fmt(valor, intervalo);
            if (eixo.startsWith("%")) {
                exibicao += "%";
            }
            g.drawString(exibicao, esquerda + tamanho + 20, y + espaco / 2 + 10);
        }

        if (notas != null && !notas.isEmpty()) {
            caixaNotas(g, esquerda, inferior + 110, Math.min(larguraGrafico + 110, 1180), notas);
        }

        // A borda é desenhada por último, para permanecer preta e completa.
        g.setColor(COR_BORDA);
        g.setStroke(new BasicStroke(2.2f));
        g.drawRect(esquerda, superior, larguraGrafico, alturaGrafico);
        g.setColor(COR_TEXTO);
        g.setFont(FONTE_EIXO);
        centralizar(g, eixo, esquerda + larguraGrafico / 2, alturaImagem - 58);
        g.dispose();
        ImageIO.write(imagem, "png", arq.toFile());
    }

    private double[][] layout() {
        if (pos != null) return pos;
        int n = nos.size(); double[] x = new double[n], y = new double[n];
        Random rd = new Random(7);
        for (int i = 0; i < n; i++) { double a = 2 * Math.PI * i / n; x[i] = 0.5 + 0.4 * Math.cos(a) + 0.05 * rd.nextGaussian(); y[i] = 0.5 + 0.4 * Math.sin(a) + 0.05 * rd.nextGaussian(); }
        double k = Math.sqrt(1.0 / n), t = 0.1;
        for (int it = 0; it < 400; it++) {
            double[] dx = new double[n], dy = new double[n];


            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
                double ex = x[i] - x[j], ey = y[i] - y[j], d = Math.max(Math.hypot(ex, ey), 1e-3), f = k * k / d;
                dx[i] += ex / d * f; dy[i] += ey / d * f; dx[j] -= ex / d * f; dy[j] -= ey / d * f;
            }


            for (int i = 0; i < n; i++) for (int[] e : adj.get(i)) {
                int j = e[0]; if (j < i) continue;
                double ex = x[i] - x[j], ey = y[i] - y[j], d = Math.max(Math.hypot(ex, ey), 1e-3), f = d * d / k;
                dx[i] -= ex / d * f; dy[i] -= ey / d * f; dx[j] += ex / d * f; dy[j] += ey / d * f;
            }

            for (int i = 0; i < n; i++) {
                dx[i] -= (x[i] - 0.5) * 0.8; dy[i] -= (y[i] - 0.5) * 0.8;
                double len = Math.max(Math.hypot(dx[i], dy[i]), 1e-9), m = Math.min(len, t);
                x[i] += dx[i] / len * m; y[i] += dy[i] / len * m;
            }
            t *= 0.985;
        }
        double x0 = Arrays.stream(x).min().getAsDouble(), x1 = Arrays.stream(x).max().getAsDouble();
        double y0 = Arrays.stream(y).min().getAsDouble(), y1 = Arrays.stream(y).max().getAsDouble();
        pos = new double[n][2];
        for (int i = 0; i < n; i++) { pos[i][0] = (x[i] - x0) / Math.max(x1 - x0, 1e-9); pos[i][1] = (y[i] - y0) / Math.max(y1 - y0, 1e-9); }

        return pos;

    }

    private static Color gradiente(double t) {
        t = Math.max(0, Math.min(1, t));
        int[] a = {49, 130, 189}, b = {254, 224, 144}, c = {215, 48, 39};
        int[] u;
        int[] v;
        double proporcao;
        if (t < 0.5) {
            u = a;
            v = b;
            proporcao = t * 2;
        } else {
            u = b;
            v = c;
            proporcao = (t - 0.5) * 2;
        }
        return new Color((int) (u[0] + (v[0] - u[0]) * proporcao),
                (int) (u[1] + (v[1] - u[1]) * proporcao),
                (int) (u[2] + (v[2] - u[2]) * proporcao));
    }

    /*
     * Mapa esquemático da rede, sem coordenadas geográficas.
     * Cor do nó = valor do indicador; tamanho do nó = demanda mensal.
     */
    public void mapa(Path arq, String titulo, double[] valores, double maximo,
                     String legenda, Collection<Integer> sementes, int mes) throws IOException {
        mapa(arq, titulo, valores, maximo, legenda, sementes, mes, null);
    }

    public void mapa(Path arq, String titulo, double[] valores, double maximo,
                     String legenda, Collection<Integer> sementes, int mes, List<String> notas) throws IOException {
        double[][] coordenadas = layout();
        Rede rede = rede(mes, 0);
        int larguraImagem = 2000;
        int esquerda = 92;
        int superior = 153;
        int larguraMapa = 1390;
        int alturaMapa = 960;
        int direitaMapa = esquerda + larguraMapa;
        int inferiorMapa = superior + alturaMapa;
        int qtdNotas = notas == null ? 0 : notas.size();
        // A caixa fica INTEIRAMENTE depois da borda inferior do mapa.
        int yNotas = inferiorMapa + 44;
        int alturaImagem = Math.max(1340, yNotas + 32 + qtdNotas * 30 + 70);

        BufferedImage imagem = new BufferedImage(larguraImagem, alturaImagem,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = nova(imagem);
        tituloFigura(g, titulo, larguraImagem);

        // Região do mapa com fundo branco, grade discreta e quatro bordas pretas.
        g.setColor(Color.WHITE);
        g.fillRect(esquerda, superior, larguraMapa, alturaMapa);
        g.setColor(COR_GRADE);
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 1; i < 10; i++) {
            int x = esquerda + larguraMapa * i / 10;
            g.drawLine(x, superior, x, inferiorMapa);
        }
        for (int i = 1; i < 8; i++) {
            int y = superior + alturaMapa * i / 8;
            g.drawLine(esquerda, y, direitaMapa, y);
        }

        int total = nos.size();
        int[] cx = new int[total];
        int[] cy = new int[total];
        int[] raios = new int[total];
        for (int i = 0; i < total; i++) {
            // A margem interna impede que as estações toquem a borda.
            cx[i] = esquerda + 54 + (int) Math.round(coordenadas[i][0] * (larguraMapa - 108));
            cy[i] = superior + 54 + (int) Math.round(coordenadas[i][1] * (alturaMapa - 108));
            raios[i] = (int) Math.round(10 + 12 * Math.sqrt(rede.w[i]));
        }

        // Ligações na mesma linha: cinza. Baldeações explícitas: laranja tracejado.
        for (int i = 0; i < total; i++) {
            for (int[] aresta : adj.get(i)) {
                int j = aresta[0];
                if (j <= i) {
                    continue;
                }
                if (aresta[1] == 1) {
                    g.setColor(new Color(216, 113, 37, 190));
                    g.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND, 1f, new float[]{10f, 7f}, 0f));
                } else {
                    g.setColor(new Color(159, 159, 159, 180));
                    g.setStroke(new BasicStroke(1.7f));
                }
                g.drawLine(cx[i], cy[i], cx[j], cy[j]);
            }
        }

        Integer[] ordem = new Integer[total];
        for (int i = 0; i < total; i++) {
            ordem[i] = i;
        }
        Arrays.sort(ordem, (a, b) -> Double.compare(valores[b], valores[a]));

        // A identificação fica na legenda lateral em vez de sobrepor nomes extensos aos nós.
        List<Integer> identificados = new ArrayList<>();
        for (int i = 0; i < Math.min(8, total); i++) {
            identificados.add(ordem[i]);
        }
        if (sementes != null) {
            for (int id : sementes) {
                if (!identificados.contains(id)) {
                    identificados.add(id);
                }
            }
        }
        for (int i = 0; i < total; i++) {
            double proporcao = 0;
            if (maximo > 0) {
                proporcao = valores[i] / maximo;
            }
            g.setColor(gradiente(proporcao));
            g.fillOval(cx[i] - raios[i], cy[i] - raios[i], 2 * raios[i], 2 * raios[i]);

            boolean semente = sementes != null && sementes.contains(i);
            g.setColor(COR_TEXTO);
            if (semente) {
                g.setStroke(new BasicStroke(3.5f));
            } else {
                g.setStroke(new BasicStroke(1.0f));
            }
            g.drawOval(cx[i] - raios[i], cy[i] - raios[i], 2 * raios[i], 2 * raios[i]);
        }

        g.setFont(new Font("SansSerif", Font.BOLD, 19));
        for (int i = 0; i < identificados.size(); i++) {
            int id = identificados.get(i);
            g.setColor(Color.WHITE);
            int nx = cx[id] + raios[id] + 4;
            int ny = cy[id] - raios[id] - 4;
            g.fillRoundRect(nx - 2, ny - 19, 36, 26, 6, 6);
            g.setColor(COR_TEXTO);
            g.drawString(String.valueOf(i + 1), nx + 3, ny);
        }

        g.setColor(COR_BORDA);
        g.setStroke(new BasicStroke(2.4f));
        g.drawRect(esquerda, superior, larguraMapa, alturaMapa);

        int painelX = 1524;
        g.setColor(COR_TEXTO);
        g.setFont(new Font("SansSerif", Font.BOLD, 28));
        g.drawString("Estações destacadas", painelX, superior + 18);
        g.setFont(new Font("SansSerif", Font.PLAIN, 22));
        int passoLinha = 43;
        for (int i = 0; i < identificados.size(); i++) {
            int id = identificados.get(i);
            int py = superior + 63 + i * passoLinha;
            double proporcao = 0;
            if (maximo > 0) {
                proporcao = valores[id] / maximo;
            }
            g.setColor(gradiente(proporcao));
            g.fillOval(painelX, py - 17, 18, 18);
            g.setColor(COR_TEXTO);
            String descricao = (i + 1) + ". " + nos.get(id).estacao + " (" + nos.get(id).linhaChave + ")" + ((sementes != null && sementes.contains(id)) ? " [semente]" : "");
            g.drawString(abreviar(g, descricao, larguraImagem - painelX - 38), painelX + 27, py);
        }

        int legendaY = Math.max(superior + 63 + identificados.size() * passoLinha + 25, 900);
        g.setColor(COR_TEXTO);
        g.setFont(new Font("SansSerif", Font.BOLD, 23));
        List<String> linhasLegenda = quebrarRotulo(g, legenda, larguraImagem - painelX - 28);
        for (int i = 0; i < linhasLegenda.size(); i++) {
            g.drawString(linhasLegenda.get(i), painelX, legendaY + 31 * i);
        }
        int larguraEscala = 385;
        int escalaY = legendaY + 19 + Math.max(0, linhasLegenda.size() - 1) * 31;
        for (int i = 0; i < larguraEscala; i++) {
            g.setColor(gradiente((double) i / Math.max(1, larguraEscala - 1)));
            g.drawLine(painelX + i, escalaY, painelX + i, escalaY + 24);
        }
        g.setColor(COR_BORDA);
        g.setStroke(new BasicStroke(1.6f));
        g.drawRect(painelX, escalaY, larguraEscala, 24);
        g.setFont(new Font("SansSerif", Font.PLAIN, 21));
        g.drawString("0", painelX, escalaY + 54);
        String maximoFormatado = fmt(maximo, passo(Math.max(1, maximo)));
        g.drawString(maximoFormatado,
                painelX + larguraEscala - g.getFontMetrics().stringWidth(maximoFormatado),
                escalaY + 54);

        // Uma legenda curta explica a codificação sem sobrecarregar a figura.
        g.setColor(new Color(155, 155, 155));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(painelX, escalaY + 104, painelX + 45, escalaY + 104);
        g.setColor(COR_TEXTO);
        g.drawString("Trecho da linha", painelX + 57, escalaY + 111);
        g.setColor(new Color(216, 113, 37));
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 1f, new float[]{10f, 7f}, 0f));
        g.drawLine(painelX, escalaY + 148, painelX + 45, escalaY + 148);
        g.setColor(COR_TEXTO);
        g.drawString("Baldeação", painelX + 57, escalaY + 155);
        if (sementes != null && !sementes.isEmpty()) {
            g.setColor(Color.WHITE);
            g.fillOval(painelX + 4, escalaY + 184 - 9, 18, 18);
            g.setColor(COR_TEXTO);
            g.setStroke(new BasicStroke(3.4f));
            g.drawOval(painelX + 4, escalaY + 184 - 9, 18, 18);
            g.drawString("Borda espessa = semente inicial", painelX + 57, escalaY + 191);
        }
        if (notas != null && !notas.isEmpty()) {
            caixaNotas(g, esquerda, yNotas, 1320, notas);
        }

        g.dispose();
        ImageIO.write(imagem, "png", arq.toFile());
    }

    public static double[] seq(int n) { double[] x = new double[n]; for (int i = 0; i < n; i++) x[i] = i; return x; }
}