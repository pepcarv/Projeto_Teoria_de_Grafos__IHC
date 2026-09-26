/*
---------------------------------------------------------------------------
Projeto da Disciplina de Teoria dos Grafos - Parte 2
Arquivo: TesteGrafoMatriz.java
Conteudo: Aplicacao com menu de opcoes (usando Scanner) para ler, gravar,
          consultar e editar o grafo da malha metroferroviaria de Sao
          Paulo, armazenado nos arquivos grafo_<mes>.txt. A classe usa a
          estrutura TGrafo (matriz de adjacencia) para representar o grafo.

     
autor Pedro Henrique Carvalho Pereira - 10418861
autor Mateus Ribeiro Cerqueira - 10443901
---------------------------------------------------------------------------
 */

package GrafoMatriz;

import java.util.Scanner;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;


public class TesteGrafoMatriz {

	// inicializa em null
	private static TGrafo g = null;

	// nome do ultimo arquivo lido/gravado (já que existem grafos para cada mês)
	private static String ultimoArquivo = "";

	private static Scanner teclado = new Scanner(System.in);

	public static void main(String[] args) {

		// fontes usadas para o ajuste de encoding do console 
		// como nomes de estacao estao acentuados (ex.: "Conceicao")
		// forcamos aqui o UTF-8 explicitamente para evitar esse problema
		// https://docs.oracle.com/javase/8/docs/api/java/io/PrintStream.html
		// https://stackoverflow.com/questions/4569123/system-out-println-not-printing-utf-8-characters-in-windows-console
		System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

		System.out.println("");
		System.out.println(" SIMULADOR MALHA METROFERROVIARIA DE SP");
		System.out.println(" (Grafo de estacoes ponderado por demanda de passageiros)");
		System.out.println("");

		int opcao;
		do {
			mostrarMenu();

			opcao = lerOpcao();
			switch (opcao) {
				case 1: opcaoLerArquivo(); break;
				case 2: opcaoGravarArquivo(); break;
				case 3: opcaoInserirVertice(); break;
				case 4: opcaoInserirAresta(); break;
				case 5: opcaoRemoverVertice(); break;
				case 6: opcaoRemoverAresta(); break;
				case 7: opcaoMostrarConteudo(); break;
				case 8: opcaoMostrarGrafo(); break;
				case 9: opcaoConexidade(); break;

				case 0: System.out.println("fim"); break;
				default: System.out.println("Opcao invalida...");
			}

		} while (opcao != 0);

		teclado.close();
	}

	// Menu 

	private static void mostrarMenu() {
		System.out.println("\n");
		System.out.println("a) 1 - Ler dados do arquivo grafo.txt");
		System.out.println("b) 2 - Gravar dados no arquivo grafo.txt");
		System.out.println("c) 3 - Inserir vertice");
		System.out.println("d) 4 - Inserir aresta");
		System.out.println("e) 5 - Remover vertice");
		System.out.println("f) 6 - Remover aresta");
		System.out.println("g) 7 - Mostrar conteudo do arquivo");
		System.out.println("h) 8 - Mostrar grafo (matriz de adjacencia)");
		System.out.println("i) 9 - Apresentar conexidade do grafo e o reduzido");
		System.out.println("j) 0 - Encerrar a aplicacao");
		System.out.println("");
		System.out.print("Escolha uma opcao: ");
	}

	// le a opcao do menu
	private static int lerOpcao() {
		// fontes usadas na formatacao - funcao trim principalmente para espacos
		// implementado apenas para descomplicar o projeto final
		// https://www.w3schools.com/java/ref_scanner_nextline.asp
		// https://stackoverflow.com/questions/4442702/using-a-simple-scanner-reading-a-string-with-spaces-how-come-trim-method-is
		String linha = teclado.nextLine().trim();
		try {
			// parseInt lanca NumberFormatException se a string nao for um int valid
			// https://docs.oracle.com/javase/8/docs/api/java/lang/Integer.html#parseInt-java.lang.String-
			return Integer.parseInt(linha);
		} catch (NumberFormatException e) {
			return -1;
		}
	}



	// --------------------  PARTE 2 - Implementações --------------------

	// -------------------- a) Ler dados do arquivo --------------------

	private static void opcaoLerArquivo() {
		System.out.println("\n ATENCAO: os arquivos ficam no formato grafo_<mes>.txt");
		System.out.println("(ex.: grafo_janeiro.txt) ou, se houver apenas um arquivo\ngenerico, pode ser simplesmente grafo.txt.");
		System.out.print("Digite o nome do arquivo a ser lido: ");
		String nomeArquivo = teclado.nextLine().trim();

		try {
			TGrafo novo = new TGrafo(0); // o tipo real sera sobrescrito pela leitura
			novo.lerArquivo(nomeArquivo);
			g = novo;
			ultimoArquivo = nomeArquivo;
			System.out.println("Arquivo \"" + nomeArquivo + "\" lido com sucesso!");
			System.out.println("Vertices: " + g.getN() + " | Arestas: " + g.getM());
		} catch (IOException e) {
			System.out.println("Erro ao ler o arquivo \"" + nomeArquivo + "\": " + e.getMessage());
		} catch (Exception e) {
			System.out.println("Erro ao interpretar o conteudo do arquivo: " + e.getMessage());
		}
	}

	// b) Gravar dados no arquivo

	private static void opcaoGravarArquivo() {
		if (!verificaGrafoCarregado()) {
			return;
		}


		String sugestao = ultimoArquivo.isEmpty() ? "grafo.txt" : ultimoArquivo;
		System.out.print("Digite o nome do arquivo para gravacao [" + sugestao + "]: ");
		String nomeArquivo = teclado.nextLine().trim();
		
		if (nomeArquivo.isEmpty()) {
			nomeArquivo = sugestao;
		}

		try {
			g.gravarArquivo(nomeArquivo);
			ultimoArquivo = nomeArquivo;
			System.out.println("Grafo gravado com sucesso em \"" + nomeArquivo + "\".");
		} catch (IOException e) {
			System.out.println("Erro ao gravar o arquivo: " + e.getMessage());
		}
	}

	// -------------------- c) Inserir vertice --------------------

	private static void opcaoInserirVertice() {
		if (!verificaGrafoCarregado()) {
			return;
		}
		System.out.print("Rotulo (nome) do novo vertice: ");
		String rotulo = teclado.nextLine().trim();

		double peso = 0.0;
		if (g.isTemPesoV()) {
			System.out.print("Peso do vertice: ");
			peso = lerDouble();
		}

		int idx = g.insereVertice(rotulo, peso);
		if (idx == -1) {
			System.out.println("Nao foi possivel inserir: capacidade maxima do grafo atingida.");
		} else {
			System.out.println("Vertice inserido com sucesso! Indice: " + idx);
		}
	}

	// -------------------- d) Inserir aresta --------------------

	private static void opcaoInserirAresta() {
		if (!verificaGrafoCarregado()) {
			return;
		}
		System.out.print("Indice do vertice de origem (v): ");
		int v = lerInt();
		System.out.print("Indice do vertice de destino (w): ");
		int w = lerInt();

		if (!g.estaAtivo(v) || !g.estaAtivo(w)) {
			System.out.println("Um dos vertices informados nao existe/nao esta ativo.");
			return;
		}

		double peso = 0.0;
		if (g.isTemPesoA()) {
			System.out.print("Peso da aresta: ");
			peso = lerDouble();
		}

		boolean ok = g.insereAresta(v, w, peso);
		// terminário direto funicona - #euAmoJava
		System.out.println(ok ? "Aresta inserida com sucesso!" : "Nao foi possivel inserir a aresta.");
	}

	// -------------------- e) Remover vertice --------------------

	private static void opcaoRemoverVertice() {
		if (!verificaGrafoCarregado()) {
			return;
		}
		System.out.print("Indice do vertice a remover: ");
		int v = lerInt();

		boolean ok = g.removeVertice(v);
		if (ok) {
			System.out.println("Vertice removido (e suas arestas) com sucesso!");
		} else {
			System.out.println("Vertice invalido ou ja inativo.");
		}
	}

	// -------------------- f) Remover aresta --------------------

	private static void opcaoRemoverAresta() {
		if (!verificaGrafoCarregado()) {
			return;
		}
		System.out.print("Indice do vertice de origem (v): ");
		int v = lerInt();
		System.out.print("Indice do vertice de destino (w): ");
		int w = lerInt();

		boolean ok = g.removeAresta(v, w);
		System.out.println(ok ? "Aresta removida com sucesso!" : "Aresta nao encontrada.");
	}

	// -------------------- g) Mostrar conteudo --------------------

	private static void opcaoMostrarConteudo() {
		if (!verificaGrafoCarregado()) {
			return;
		}
		g.mostrarConteudo();
	}

	// -------------------- h) Mostrar grafo (matriz) --------------------

	private static void opcaoMostrarGrafo() {
		if (!verificaGrafoCarregado()) {
			return;
		}
		g.mostrarGrafo();
	}

	// -------------------- i) Conexidade e grafo reduzido --------------------

	private static void opcaoConexidade() {
		if (!verificaGrafoCarregado()) {
			return;
		}
		g.apresentarConexidade();
	}





	// metodos auxiliares para verificação e leitura
	private static boolean verificaGrafoCarregado() {
		
		
		if (g == null) {
			System.out.println("Nenhum grafo carregado ainda. Use a opcao 1 para ler um arquivo primeiro.");
			return false;
		}
		return true;
	}



	private static int lerInt() {
		// laco de releitura ate entrada valida
		// evita exceções
		// https://www.baeldung.com/java-validate-input-is-number
		while (true) {

			String linha = teclado.nextLine().trim();


			try {
				return Integer.parseInt(linha);
			} catch (NumberFormatException e) {
				System.out.print("De novo, digite um numero inteiro: ");
			}
		}
	}



	private static double lerDouble() {
		while (true) {
			// troca "," por "." antes do parse
			// https://stackoverflow.com/questions/2437799/does-java-double-parsedouble-take-into-account-locale
			String linha = teclado.nextLine().trim().replace(",", ".");
			try {
				return Double.parseDouble(linha);
			} catch (NumberFormatException e) {
				System.out.print("De novo, digite digite um numero (ex.: 12.5): ");
			}
		}
	}
}
