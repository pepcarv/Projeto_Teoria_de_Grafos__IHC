import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/*
 * Valida os CSVs e as operações do simulador sem bibliotecas externas.
 * A referência numérica foi calculada a partir dos mesmos CSVs no VigiMetro.
 *
 * Executar:
 *     javac -encoding UTF-8 *.java
 *     java TestesRede
 */
public class TestesRede {

    private static int verificacoes = 0;

    private static void verificar(boolean condicao, String descricao) {
        verificacoes++;
        if (!condicao) {
            throw new AssertionError(descricao);
        }
    }

    private static void aproximado(double real, double esperado, double tolerancia, String descricao) {
        verificar(Math.abs(real - esperado) <= tolerancia,
                descricao + ": esperado " + esperado + ", encontrado " + real);
    }

    private static int no(MalhaMetroferroviaria grafo, String linha, String estacao) {
        String linhaChave = MalhaMetroferroviaria.chaveLinha(linha);
        String estacaoChave = MalhaMetroferroviaria.chaveTexto(estacao);
        List<Integer> ids = grafo.linhas.get(linhaChave);
        if (ids == null) {
            throw new AssertionError("Linha não encontrada: " + linha);
        }

        for (int id : ids) {
            MalhaMetroferroviaria.No atual = grafo.nos.get(id);
            if (atual.chave.equals(estacaoChave)) {
                return id;
            }
        }
        throw new AssertionError("Estação não encontrada: " + linha + " / " + estacao);
    }

    private static boolean vizinhos(MalhaMetroferroviaria grafo, int a, int b) {
        for (int[] aresta : grafo.adj.get(a)) {
            if (aresta[0] == b) {
                return true;
            }
        }
        return false;
    }

    private static void conferirTrajeto(MalhaMetroferroviaria grafo, List<Integer> trajeto,
                                        int origem, int destino) {
        verificar(trajeto != null, "Percurso inexistente.");
        verificar(!trajeto.isEmpty(), "Percurso vazio.");
        verificar(trajeto.get(0) == origem, "Origem incorreta.");
        verificar(trajeto.get(trajeto.size() - 1) == destino, "Destino incorreto.");
        for (int i = 1; i < trajeto.size(); i++) {
            verificar(vizinhos(grafo, trajeto.get(i - 1), trajeto.get(i)),
                    "Percurso contém aresta não existente.");
        }
    }

    public static void main(String[] args) throws Exception {
        Path pasta = Paths.get(".");
        if (args.length > 0) {
            pasta = Paths.get(args[0]);
        }

        MalhaMetroferroviaria grafo = MalhaMetroferroviaria.carregar(
                pasta.resolve("demanda_unificada.csv"),
                pasta.resolve("topologia_extra.csv"),
                pasta.resolve("conexoes.csv"));

        // Rede de referência: estações por linha, trechos e transferências.
        verificar(grafo.nos.size() == 203, "Número de nós do VigiMetro.");
        verificar(grafo.numArestas() == 231, "Número de arestas do VigiMetro.");
        verificar(grafo.numBaldeacoes() == 41, "Número de baldeações explícitas.");
        verificar(grafo.linhas.size() == 13, "Número de linhas.");
        verificar(grafo.linhas.get("L7").size() == 17, "Linha 7 individualizada.");
        verificar(grafo.linhas.get("L10").size() == 15, "Linha 10 individualizada.");
        verificar(grafo.linhas.get("L11").size() == 17, "Linha 11 individualizada.");
        verificar(grafo.linhas.get("L12").size() == 13, "Linha 12 individualizada.");
        verificar(grafo.linhas.get("L13").size() == 3, "Linha 13 individualizada.");

        int lapa7 = no(grafo, "Linha 7", "Lapa");
        int lapa8 = no(grafo, "Linha 8", "Lapa");
        verificar(lapa7 != lapa8, "Lapa 7 e Lapa 8 devem ser nós diferentes.");
        verificar(!vizinhos(grafo, lapa7, lapa8), "Não existe baldeação Lapa 7-Lapa 8.");

        int consolacao = no(grafo, "Linha 2", "Consolação");
        int paulista = no(grafo, "Linha 4", "Paulista");
        verificar(grafo.ehBaldeacao(consolacao, paulista), "Integração Consolação-Paulista.");

        int se1 = no(grafo, "Linha 1", "Sé");
        int se3 = no(grafo, "Linha 3", "Sé");
        verificar(se1 != se3 && grafo.ehBaldeacao(se1, se3), "Integração Sé 1-3.");

        // BFS: busca em largura, sem usar os pesos de demanda.
        List<Integer> bfs = grafo.menorNumeroEstacoes(lapa7, lapa8);
        conferirTrajeto(grafo, bfs, lapa7, lapa8);
        verificar(bfs.size() == 5, "BFS entre as duas estações Lapa passa por Barra Funda.");

        // Dijkstra: menor soma de w_j * (t_est + t_bald), com tempo em minutos.
        List<Integer> dijkstra = grafo.menorExposicao(lapa7, lapa8, 0, 0, 3, 5);
        conferirTrajeto(grafo, dijkstra, lapa7, lapa8);
        verificar(dijkstra.size() == 5, "Dijkstra Lapa 7-Lapa 8.");

        List<Integer> bfsAlternativo = grafo.menorNumeroEstacoes(90, 200);
        List<Integer> dijkstraAlternativo = grafo.menorExposicao(90, 200, 0, 0, 3, 5);
        conferirTrajeto(grafo, bfsAlternativo, 90, 200);
        conferirTrajeto(grafo, dijkstraAlternativo, 90, 200);
        verificar(bfsAlternativo.size() == 27, "BFS mantém menor número de trechos.");
        verificar(dijkstraAlternativo.size() == 31, "Dijkstra pode preferir mais trechos e menor exposição.");

        ModeloEpidemiologico modelo = new ModeloEpidemiologico();
        MalhaMetroferroviaria.Rede mes0 = grafo.rede(0, 0);
        MalhaMetroferroviaria.Rede mes0Caminhada = grafo.rede(0, 1);
        aproximado(mes0.rho, 1.5462162949189113, 1e-9, "Raio espectral Janeiro, contato médio.");
        aproximado(mes0Caminhada.rho, 0.5336085105140533, 1e-9, "Raio espectral Janeiro, caminhada.");

        double beta = grafo.beta(modelo.cenarios.get(1), 0, 0, modelo);
        aproximado(beta, 0.2730680200497854, 1e-9, "Transmissibilidade calibrada.");

        // Risco no percurso: lambda_i = beta * w_i * k * t_i/1440;
        // p_i = OR(1 - exp(-lambda_i)); P = 1 - produto_i(1 - p_i).
        MalhaMetroferroviaria.AvalPercurso avaliado = grafo.avaliarPercurso(
                dijkstra, 0, 0, beta, 1, 3, 5, 0.38);
        verificar(avaliado.total >= 0 && avaliado.total <= 1, "Probabilidade do percurso.");
        for (int i = 1; i < avaliado.acum.length; i++) {
            verificar(avaliado.acum[i] >= avaliado.acum[i - 1], "Risco acumulado não diminui.");
        }

        // Simulação SEIR com 5 execuções e semente fixa; valores de referência em Python.
        MalhaMetroferroviaria.Config config = new MalhaMetroferroviaria.Config();
        config.cenario = modelo.cenarios.get(1);
        config.sementes.add(consolacao);
        config.dias = 10;
        config.execucoes = 5;
        config.rngSeed = 42;
        config.popEscala = 2000;
        config.infectadosIniciais = 5;

        MalhaMetroferroviaria.Resultado resultado = grafo.simular(config, modelo);
        aproximado(resultado.alcFinalMedia, 5.8, 1e-10, "SEIR alcance médio.");
        aproximado(resultado.picoIMedio, 10.2, 1e-10, "SEIR pico médio de infectados.");
        aproximado(resultado.ataqueMedia, 0.00032665771156430433, 1e-12, "SEIR taxa de ataque.");

        // A caminhada aleatória deve usar os pesos direcionados C_ij definidos no VigiMetro.
        config.mobilidade = 1;
        MalhaMetroferroviaria.Resultado resultadoCaminhada = grafo.simular(config, modelo);
        aproximado(resultadoCaminhada.beta, 0.7912584111813982, 1e-9, "Beta, mobilidade 1.");
        aproximado(resultadoCaminhada.alcFinalMedia, 5.8, 1e-10, "SEIR alcance, mobilidade 1.");
        aproximado(resultadoCaminhada.picoIMedio, 8.4, 1e-10, "SEIR pico, mobilidade 1.");
        aproximado(resultadoCaminhada.ataqueMedia, 0.0003602393454634312, 1e-12,
                "SEIR taxa de ataque, mobilidade 1.");

        System.out.println("OK: " + verificacoes + " verificações passaram.");
        System.out.println("Rede: " + grafo.nos.size() + " nós, " + grafo.numArestas()
                + " arestas e " + grafo.numBaldeacoes() + " baldeações.");
        System.out.println("BFS, Dijkstra, beta, raio espectral, risco e SEIR validados.");
    }
}
