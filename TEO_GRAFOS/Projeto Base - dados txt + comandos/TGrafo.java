/*
---------------------------------------------------------------------------
Projeto da Disciplina de Teoria dos Grafos - Parte 2
Arquivo: TGrafo.java
Conteudo: Estrutura de Grafo implementada como Matriz de Adjacencia,
          estendida a partir da classe apresentada em aula para suportar
          rotulo e peso de vertice, peso de aresta, grafos orientados ou
          nao, insercao/remocao de vertices e arestas, leitura e gravacao
          do arquivo grafo.txt, exibicao do grafo e analise de conexidade
          (incluindo o algoritmo FCONEX para componentes fortemente conexas).

autor Pedro Henrique Carvalho Pereira - 10418861
autor Mateus Ribeiro Cerqueira - 10443901    
---------------------------------------------------------------------------
 */

package GrafoMatriz;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;



// definicao  Matriz de Adjacencia para armazenar um grafo
// com rotulo e peso de vertice, e peso de aresta baseada na classe TGrafo
public class TGrafo {

	// privados - importantt
	private static final int CAPACIDADE = 400; // numero maximo de vertices suportado

	private int tipoGrafo; // 0 a 7, conforme especificacao do arquivo grafo.txt

	// uso geral - futuras melhorias 
	private boolean orientado; // true se o grafo for dir
	private boolean temPesoV; // true se o tipo do grafo usa peso no vertice
	private boolean temPesoA; // true se o tipo do grafo usa peso na aresta

	private int n; // qtd de vertices ativos no momento
	private int m; // qdt de arestas no momento
	private int maiorIndice; // maior indice de vertice usado - percorre vet

	private boolean ativo[]; // ativo[i] = true se o vertice i existe no grafo
	private String rotulo[]; // rotulo[i] = nome/apelido do vertice i
	private double pesoV[]; // pesoV[i] = peso do vertice i 

	private boolean existeA[][];// existeA[i][j] = true se existe aresta de i para j
	private double pesoA[][]; // pesoA[i][j] = peso da aresta de i para j 


	// Construtor
	public TGrafo(int tipoGrafo) {
		this.tipoGrafo = tipoGrafo;
		atualizaFlagsTipo();

		this.n = 0;
		this.m = 0;
		this.maiorIndice = -1;

		this.ativo = new boolean[CAPACIDADE];
		this.rotulo = new String[CAPACIDADE];
		this.pesoV = new double[CAPACIDADE];


		this.existeA = new boolean[CAPACIDADE][CAPACIDADE];
		this.pesoA = new double[CAPACIDADE][CAPACIDADE];


		
		// inicia as estruturas vazias
		for (int i = 0; i < CAPACIDADE; i++) {
		
			ativo[i] = false;
		
			rotulo[i] = "";
		
			pesoV[i] = 0.0;
		
			for (int j = 0; j < CAPACIDADE; j++) {
		
				existeA[i][j] = false;
				pesoA[i][j] = 0.0;
		
			}
		}
	}

	// calcula as flags orientado/temPesoV/temPesoA a partir do tipoGrafo 
	private void atualizaFlagsTipo() {
		this.orientado = (tipoGrafo >= 4);
		int t = tipoGrafo % 4; // 0,1,2,3 -> mesma logica de peso para orientado e nao orientado
		this.temPesoV = (t == 1 || t == 3);
		this.temPesoA = (t == 2 || t == 3);
	}

	// Getters simples
	public int getTipoGrafo() { return tipoGrafo; }
	public boolean isOrientado() { return orientado; }
	public boolean isTemPesoV() { return temPesoV; }
	public boolean isTemPesoA() { return temPesoA; }
	public int getN() { return n; }
	public int getM() { return m; }
	public int getCapacidade() { return CAPACIDADE; }
	public int getMaiorIndice() { return maiorIndice; }
	public boolean estaAtivo(int i) { return (i >= 0 && i <= maiorIndice && ativo[i]); }
	public String getRotulo(int i) { return rotulo[i]; }
	public double getPesoVertice(int i) { return pesoV[i]; }
	public boolean existeAresta(int v, int w) { return existeA[v][w]; }
	public double getPesoAresta(int v, int w) { return pesoA[v][w]; }

	public void setTipoGrafo(int tipoGrafo) {
		this.tipoGrafo = tipoGrafo;
		atualizaFlagsTipo();
	}

	// Insercao / Remocao de Vertices

	// insere um novo vertice com rotulo e peso, retorna o indice usado (-1 se nao houver espaco)
	public int insereVertice(String rotulo, double peso) {
		// procura primeiro um "buraco" deixado por remocao anterior
		int idx = -1;
		for (int i = 0; i <= maiorIndice; i++) {
			if (!ativo[i]) {
				idx = i;
				break;
			}
		}
		// se nao achou buraco, usa a proxima posicao livre
		if (idx == -1) {
			if (maiorIndice + 1 >= CAPACIDADE) {
				return -1; // grafo cheio
			}
			idx = maiorIndice + 1;
			maiorIndice = idx;
		}

		ativo[idx] = true;
		this.rotulo[idx] = rotulo;
		this.pesoV[idx] = peso;
		n++;
		return idx;
	}

	// remove um vertice e todas as arestas ligadas a ele
	public boolean removeVertice(int v) {
		if (!estaAtivo(v)) {
			return false;
		}
		// remove todas as arestas de/para o vertice v
		for (int j = 0; j <= maiorIndice; j++) {
			if (existeA[v][j]) {
				existeA[v][j] = false;
				pesoA[v][j] = 0.0;
				m--;
			}
			if (existeA[j][v]) {
				existeA[j][v] = false;
				pesoA[j][v] = 0.0;
				m--;
			}
		}
		ativo[v] = false;
		rotulo[v] = "";
		pesoV[v] = 0.0;
		n--;
		return true;
	}

	// Insercao / Remocao de Arestas

	// insere uma aresta v->w (e tambem w->v se o grafo nao for orientado)
	public boolean insereAresta(int v, int w, double peso) {
		if (!estaAtivo(v) || !estaAtivo(w)) {
			return false;
		}
		if (!existeA[v][w]) {
			existeA[v][w] = true;
			pesoA[v][w] = peso;
			m++;
		} else {
			pesoA[v][w] = peso; // ja existia: apenas atualiza o peso
		}
		if (!orientado && v != w) {
			if (!existeA[w][v]) {
				existeA[w][v] = true;
				pesoA[w][v] = peso;
				m++;
			} else {
				pesoA[w][v] = peso;
			}
		}
		return true;
	}

	// remove a aresta v->w (e tambem w->v se o grafo nao for orientado)
	public boolean removeAresta(int v, int w) {
		if (!estaAtivo(v) || !estaAtivo(w)) {
			return false;
		}
		boolean removeu = false;
		if (existeA[v][w]) {
			existeA[v][w] = false;
			pesoA[v][w] = 0.0;
			m--;
			removeu = true;
		}
		if (!orientado && v != w && existeA[w][v]) {
			existeA[w][v] = false;
			pesoA[w][v] = 0.0;
			m--;
			removeu = true;
		}
		return removeu;
	}

	// Leitura do arquivo grafo.txt

	public void lerArquivo(String caminho) throws IOException {
		// le explicitamente como UTF-8 (em vez de "new FileReader(caminho)",
		// que usa o charset padrao da plataforma/terminal) - isso evita que
		// nomes de estacao acentuados (ex.: "Conceicao", "Sao Paulo") sejam
		// lidos incorretamente em maquinas com locale/console diferente
		// https://docs.oracle.com/javase/8/docs/api/java/io/InputStreamReader.html
		// https://stackoverflow.com/questions/4123433/how-to-read-files-that-uses-different-charset-in-java
		BufferedReader br = new BufferedReader(
				new InputStreamReader(new FileInputStream(caminho), StandardCharsets.UTF_8));

		// linha 1: tipo do grafo
		int tipoLido = Integer.parseInt(br.readLine().trim());
		this.tipoGrafo = tipoLido;
		atualizaFlagsTipo();

		// reinicia as estruturas
		this.n = 0;
		this.m = 0;
		this.maiorIndice = -1;
		for (int i = 0; i < CAPACIDADE; i++) {
			ativo[i] = false;
			rotulo[i] = "";
			pesoV[i] = 0.0;
			for (int j = 0; j < CAPACIDADE; j++) {
				existeA[i][j] = false;
				pesoA[i][j] = 0.0;
			}
		}

		// linha 2: quantidade de vertices
		int qtdV = Integer.parseInt(br.readLine().trim());

		for (int k = 0; k < qtdV; k++) {
			String linha = br.readLine();



			// primeiro token = indice do vertice
			// usamos indexOf(' ') + substring em vez de um split
			// o split("\\s+") quebrava o rotulo em varios pedacos 
			// Pegamos so o primeiro espaco e tratamos o resto 
			// https://docs.oracle.com/javase/8/docs/api/java/lang/String.html#indexOf-int-
			// https://www.w3schools.com/java/ref_string_substring.asp
			int primeiroEspaco = linha.indexOf(' ');
			int idx;
			String resto;
			if (primeiroEspaco == -1) {
				idx = Integer.parseInt(linha.trim());
				resto = "";
			} else {
				idx = Integer.parseInt(linha.substring(0, primeiroEspaco).trim());
				resto = linha.substring(primeiroEspaco + 1);
			}

			String[] partesEntreAspas = extraiPartesEntreAspas(resto);
			String rot = (partesEntreAspas.length >= 1) ? partesEntreAspas[0] : ("v" + idx);
			double peso = 0.0;
			if (temPesoV && partesEntreAspas.length >= 2) {
				peso = Double.parseDouble(partesEntreAspas[1]);
			}

			ativo[idx] = true;
			rotulo[idx] = rot;
			pesoV[idx] = peso;
			if (idx > maiorIndice) {
				maiorIndice = idx;
			}
			n++;
		}

		// linha seguinte: quantidade de arestas
		int qtdA = Integer.parseInt(br.readLine().trim());

		for (int k = 0; k < qtdA; k++) {
			String linha = br.readLine();
			// split("\\s+") para  arquivos gravados com mais
			// de um espaco entre os campos v, w e peso
			// https://www.geeksforgeeks.org/split-string-java-examples/
			// https://docs.oracle.com/javase/8/docs/api/java/lang/String.html#split-java.lang.String-
			String[] campos = linha.trim().split("\\s+");
			
			
			
			
			int v = Integer.parseInt(campos[0]);
			int w = Integer.parseInt(campos[1]);
			double peso = 0.0;
			if (temPesoA && campos.length >= 3) {
				peso = Double.parseDouble(campos[2]);
			}
			
			
			
			// insere diretamente na matriz
			if (!existeA[v][w]) {
				existeA[v][w] = true;
				pesoA[v][w] = peso;
				m++;
			} else {
				pesoA[v][w] = peso;
			}
		}

		br.close();
	}

	// extrai os textos que estao entre aspas duplas em uma string, na ordem 
	// https://docs.oracle.com/javase/8/docs/api/java/lang/String.html#charAt-int-
	// https://www.w3schools.com/java/ref_string_indexof.asp
	private String[] extraiPartesEntreAspas(String texto) {
		java.util.ArrayList<String> partes = new java.util.ArrayList<String>();
		int i = 0;
		
		
		while (i < texto.length()) {
			if (texto.charAt(i) == '"') {
				int fim = texto.indexOf('"', i + 1);
				if (fim == -1) {
					break;
				}
				partes.add(texto.substring(i + 1, fim));
				i = fim + 1;
			} else {
				i++;
			}
		}
		String[] r = new String[partes.size()];
		for (int k = 0; k < partes.size(); k++) {
			r[k] = partes.get(k);
		}
		return r;
	}

	// Gravacao do arquivo grafo.txt

	public void gravarArquivo(String caminho) throws IOException {
		// grava explicitamente como UTF-8 
		// https://docs.oracle.com/javase/8/docs/api/java/io/OutputStreamWriter.html
		PrintWriter pw = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(caminho), StandardCharsets.UTF_8));

		pw.println(tipoGrafo);
		pw.println(n);

		for (int i = 0; i <= maiorIndice; i++) {
			if (!ativo[i]) {
				continue;
			}
			if (temPesoV) {
				pw.println(i + " \"" + rotulo[i] + "\" \"" + pesoV[i] + "\"");
			} else {
				pw.println(i + " \"" + rotulo[i] + "\"");
			}
		}

		
		
		
		pw.println(m);

		for (int i = 0; i <= maiorIndice; i++) {
			if (!ativo[i]) {
				continue;
			}
			for (int j = 0; j <= maiorIndice; j++) {
				if (!ativo[j] || !existeA[i][j]) {
					continue;
				}
				// grafo nao orientado: grava so uma vez (i < j) para nao duplicar no arquivo
				if (!orientado && i > j) {
					continue;
				}
				if (temPesoA) {
					pw.println(i + " " + j + " " + pesoA[i][j]);
				} else {
					pw.println(i + " " + j);
				}
			}
		}

		pw.close();
	}

	// Exibicao

	// mostra o conteudo do arquivo de forma legivel (tipo, vertices, arestas)
	public void mostrarConteudo() {
		System.out.println("Tipo do grafo: " + tipoGrafo + " (" + descreveTipo() + ")");
		System.out.println("Quantidade de vertices (n): " + n);
		System.out.println("Quantidade de arestas (m): " + m);

		System.out.println("\n--- Vertices ---");
		for (int i = 0; i <= maiorIndice; i++) {
			if (!ativo[i]) {
				continue;
			}
			if (temPesoV) {
				System.out.println(i + " - \"" + rotulo[i] + "\" (peso: " + pesoV[i] + ")");
			} else {
				System.out.println(i + " - \"" + rotulo[i] + "\"");
			}
		}

		System.out.println("\n--- Arestas ---");
		for (int i = 0; i <= maiorIndice; i++) {
			if (!ativo[i]) {
				continue;
			}
			for (int j = 0; j <= maiorIndice; j++) {
				if (!ativo[j] || !existeA[i][j]) {
					continue;
				}
				if (!orientado && i > j) {
					continue;
				}
				String seta = orientado ? " -> " : " -- ";
				if (temPesoA) {
					System.out.println(rotulo[i] + "(" + i + ")" + seta + rotulo[j] + "(" + j + ") | peso: " + pesoA[i][j]);
				} else {
					System.out.println(rotulo[i] + "(" + i + ")" + seta + rotulo[j] + "(" + j + ")");
				}
			}
		}
	}

	// mostra o grafo como matriz de adjacencia
	public void mostrarGrafo() {
		System.out.println("Matriz de Adjacencia (" + (orientado ? "orientado" : "nao orientado") + "):\n");

		// relembrar formatações
		// https://www.w3schools.com/java/java_string_format.asp
		// https://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html
		System.out.print("        ");
		for (int j = 0; j <= maiorIndice; j++) {
			if (!ativo[j]) {
				continue;
			}
			System.out.printf("%6d", j);
		}
		System.out.println();

		for (int i = 0; i <= maiorIndice; i++) {
			if (!ativo[i]) {
				continue;
			}
			System.out.printf("%6d :", i);
			for (int j = 0; j <= maiorIndice; j++) {
				if (!ativo[j]) {
					continue;
				}
				if (existeA[i][j]) {
					if (temPesoA) {
						System.out.printf("%6.1f", pesoA[i][j]);
					} else {
						System.out.printf("%6d", 1);
					}
				} else {
					System.out.printf("%6d", 0);
				}
			}
			System.out.println();
		}
	}

	private String descreveTipo() {
		switch (tipoGrafo) {
			case 0: return "nao orientado, sem peso";
			case 1: return "nao orientado, com peso no vertice";
			case 2: return "nao orientado, com peso na aresta";
			case 3: return "nao orientado, com peso em vertices e arestas";
			case 4: return "orientado, sem peso";
			case 5: return "orientado, com peso no vertice";
			case 6: return "orientado, com peso na aresta";
			case 7: return "orientado, com peso em vertices e arestas";
			default: return "desconhecido";
		}
	}

	// Conexidade

	// faz uma busca em profundidade (DFS) a partir de origem, usando a
	// matriz de adjacencia na direcao normal (i -> j), marcando visitado
	private void dfs(int origem, boolean visitado[]) {
		visitado[origem] = true;
		for (int j = 0; j <= maiorIndice; j++) {
			if (ativo[j] && existeA[origem][j] && !visitado[j]) {
				dfs(j, visitado);
			}
		}
	}






	// DFS na direcao inversa (usa existeA[j][origem] em vez de existeA[origem][j])
	private void dfsInversa(int origem, boolean visitado[]) {
		visitado[origem] = true;
		for (int j = 0; j <= maiorIndice; j++) {
			if (ativo[j] && existeA[j][origem] && !visitado[j]) {
				dfsInversa(j, visitado);
			}
		}
	}

	// DFS que ignora a direcao das arestas (usa o grafo subjacente nao orientado)
	private void dfsSubjacente(int origem, boolean visitado[]) {
		visitado[origem] = true;
		for (int j = 0; j <= maiorIndice; j++) {
			if (ativo[j] && !visitado[j] && (existeA[origem][j] || existeA[j][origem])) {
				dfsSubjacente(j, visitado);
			}
		}
	}

	// retorna o indice do primeiro vertice ativo, ou -1 se o grafo estiver vazio
	private int primeiroVerticeAtivo() {
		for (int i = 0; i <= maiorIndice; i++) {
			if (ativo[i]) {
				return i;
			}
		}
		return -1;
	}

	// analisa e imprime a conexidade do grafo e tambem apresenta o grafo reduzido (FCONEX)
	public void apresentarConexidade() {
		int origem = primeiroVerticeAtivo();
		if (origem == -1) {
			System.out.println("O grafo esta vazio.");
			return;
		}

		if (!orientado) {
			boolean visitado[] = new boolean[CAPACIDADE];
			dfsSubjacente(origem, visitado);
			boolean conexo = true;
			for (int i = 0; i <= maiorIndice; i++) {
				if (ativo[i] && !visitado[i]) {
					conexo = false;
					break;
				}
			}
			if (conexo) {
				System.out.println("Grafo NAO ORIENTADO: CONEXO (todos os vertices sao alcancaveis entre si).");
			} else {
				System.out.println("Grafo NAO ORIENTADO: DESCONEXO (existem vertices isolados de outras partes do grafo).");
			}
			return;
		}

		// grafo orientado: classifica C3, C2, C1 ou C0

		// matriz de alcancabilidade direta: alcanca[i][j] = true se j e alcancavel a partir de i
		boolean alcanca[][] = new boolean[CAPACIDADE][CAPACIDADE];
		for (int i = 0; i <= maiorIndice; i++) {
			if (!ativo[i]) {
				continue;
			}
			boolean visitado[] = new boolean[CAPACIDADE];
			dfs(i, visitado);
			alcanca[i] = visitado;
		}

		// C3: fortemente conexo -> todo par (i,j) alcanca[i][j] e alcanca[j][i]
		boolean fortementeConexo = true;
		for (int i = 0; i <= maiorIndice && fortementeConexo; i++) {
			if (!ativo[i]) {
				continue;
			}
			for (int j = 0; j <= maiorIndice; j++) {
				if (!ativo[j] || i == j) {
					continue;
				}
				if (!alcanca[i][j]) {
					fortementeConexo = false;
					break;
				}
			}
		}

		if (fortementeConexo) {
			System.out.println("Grafo ORIENTADO: categoria C3 (fortemente conexo).");
			apresentarGrafoReduzido();
			return;
		}

		// C2: unilateralmente conexo -> para todo par (i,j), i alcanca j OU j alcanca i
		boolean unilateralmenteConexo = true;
		for (int i = 0; i <= maiorIndice && unilateralmenteConexo; i++) {
			if (!ativo[i]) {
				continue;
			}
			for (int j = i + 1; j <= maiorIndice; j++) {
				if (!ativo[j]) {
					continue;
				}
				if (!alcanca[i][j] && !alcanca[j][i]) {
					unilateralmenteConexo = false;
					break;
				}
			}
		}

		if (unilateralmenteConexo) {
			System.out.println("Grafo ORIENTADO: categoria C2.");
			apresentarGrafoReduzido();
			return;
		}

		// C1: fracamente conexo -> o grafo subjacente (ignorando a direcao) e conexo
		boolean visitadoSub[] = new boolean[CAPACIDADE];
		dfsSubjacente(origem, visitadoSub);
		boolean subjacenteConexo = true;
		for (int i = 0; i <= maiorIndice; i++) {
			if (ativo[i] && !visitadoSub[i]) {
				subjacenteConexo = false;
				break;
			}
		}

		if (subjacenteConexo) {
			System.out.println("Grafo ORIENTADO: categoria C1.");
		} else {
			System.out.println("Grafo ORIENTADO: categoria C0 (desconexo).");
		}
		apresentarGrafoReduzido();
	}

	// Algoritmo FCONEX (Kosaraju):
	//
	// implementacao do algoritmo de Kosaraju (2 passagens de DFS):
	// 1a DFS no grafo original, empilhando os vertices por ordem de termino;
	// 2a DFS no grafo transposto (arestas invertidas), na ordem inversa da
	// pilha, onde cada nova arvore de busca corresponde a uma componente
	// lembrar exercicio da pilha - nõa é urgente, pois é simples
	// fortemente conexa


	// https://www.geeksforgeeks.org/dsa/strongly-connected-components/
	// https://en.wikipedia.org/wiki/Kosaraju%27s_algorithm

	private int comp[];       // comp[i] = indice da componente fortemente conexa do vertice i
	private int ordemFinal[]; // ordem de termino da 1a DFS
	private int topoOrdem;    // topo da pilha ordemFinal

	private void dfs1(int v, boolean visitado[]) {
		visitado[v] = true;
		for (int w = 0; w <= maiorIndice; w++) {
			if (ativo[w] && existeA[v][w] && !visitado[w]) {
				dfs1(w, visitado);
			}
		}
		ordemFinal[topoOrdem] = v;
		topoOrdem++;
	}

	private void dfs2(int v, int idComp, boolean visitado[]) {
		visitado[v] = true;
		comp[v] = idComp;
		for (int w = 0; w <= maiorIndice; w++) {
			if (ativo[w] && existeA[w][v] && !visitado[w]) { // usa arestas invertidas
				dfs2(w, idComp, visitado);
			}
		}
	}

	// calcula as componentes fortemente conexas 
	// e apresenta o grafo reduzido correspondente
	private void apresentarGrafoReduzido() {
		comp = new int[CAPACIDADE];
		ordemFinal = new int[CAPACIDADE];
		topoOrdem = 0;
		for (int i = 0; i < CAPACIDADE; i++) {
			comp[i] = -1;
		}

		// 1a etapa: DFS normal, guardando a ordem de termino
		boolean visitado1[] = new boolean[CAPACIDADE];
		for (int i = 0; i <= maiorIndice; i++) {
			if (ativo[i] && !visitado1[i]) {
				dfs1(i, visitado1);
			}
		}

		// 2a etapa: DFS no grafo com as arestas invertidas, na ordem inversa
		// de termino da 1a etapa
		boolean visitado2[] = new boolean[CAPACIDADE];
		int idComp = 0;
		for (int k = topoOrdem - 1; k >= 0; k--) {
			int v = ordemFinal[k];
			if (!visitado2[v]) {
				dfs2(v, idComp, visitado2);
				idComp++;
			}
		}

		int qtdComponentes = idComp;

		System.out.println("\n Componentes Fortemente Conexas (FCONEX) ");
		System.out.println("Quantidade de componentes: " + qtdComponentes);
		for (int c = 0; c < qtdComponentes; c++) {
			System.out.print("Componente " + c + ": { ");
			for (int i = 0; i <= maiorIndice; i++) {
				if (ativo[i] && comp[i] == c) {
					System.out.print(rotulo[i] + "(" + i + ") ");
				}
			}
			System.out.println("}");
		}

		// arestas do grafo reduzido: uma aresta entre a componente A e a
		// componente B se existir ao menos uma aresta original de um
		// vertice de A para um vertice de B, com A != B 
		boolean existeArestaReduzida[][] = new boolean[qtdComponentes][qtdComponentes];
		for (int i = 0; i <= maiorIndice; i++) {
			if (!ativo[i]) {
				continue;
			}
			for (int j = 0; j <= maiorIndice; j++) {
				if (!ativo[j] || !existeA[i][j]) {
					continue;
				}
				int ci = comp[i];
				int cj = comp[j];
				if (ci != cj) {
					existeArestaReduzida[ci][cj] = true;
				}
			}
		}



		// nosso grafo a gente sabe que é C3 - testar para mais C2/C1/C0

		//passou correto
		System.out.println("\n Grafo Reduzido>");
		int arestasReduzidas = 0;
		for (int a = 0; a < qtdComponentes; a++) {
			for (int b = 0; b < qtdComponentes; b++) {
				if (existeArestaReduzida[a][b]) {
					System.out.println("Componente " + a + " -> Componente " + b);
					arestasReduzidas++;
				}
			}
		}
		if (arestasReduzidas == 0) {
			System.out.println("nenhuma aresta entre componentes distintas");
		}
	}
}