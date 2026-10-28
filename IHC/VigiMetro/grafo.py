# -*- coding: utf-8 -*-
"""Port direto da construção do grafo, BFS, Dijkstra e betweenness de
MalhaMetroferroviaria.java. CSVs são as únicas fontes de dados de demanda/topologia.
"""
import csv
import math
import re
import unicodedata
from collections import OrderedDict, deque
from pathlib import Path

MESES = ('Janeiro','Fevereiro','Março','Abril','Maio','Junho','Julho','Agosto','Setembro','Outubro','Novembro','Dezembro')
AGREGADO_DIVISOR = 15  # ESTACOES_PADRAO_AGREGADO do Java


def chave(s):
    s = ''.join(c for c in unicodedata.normalize('NFD', str(s)) if unicodedata.category(c) != 'Mn')
    return re.sub(r'[^a-z0-9]', '', s.lower())


def chave_linha(s):
    m = re.search(r'\d+', str(s))
    return 'L' + str(int(m.group())) if m else chave(s)


def numero(s):
    s = str(s).strip().replace(' ', '')
    if not s:
        return math.nan
    if ',' in s and '.' in s:
        s = s.replace('.', '').replace(',', '.')
    elif ',' in s:
        s = s.replace(',', '.')
    try:
        return float(s)
    except ValueError:
        return math.nan


def csv_ler(p):
    with open(p, encoding='utf-8-sig', newline='') as arq:
        head = arq.readline()
        sep = max([';', '\t', ','], key=lambda c: (head.count(c), [';', '\t', ','].index(c)*-1))
        arq.seek(0)
        return list(csv.DictReader(arq, delimiter=sep))


class Grafo:
    def __init__(self, pasta):
        pasta = Path(pasta)
        self.nos, self.adj, self.linhas, self.avisos = [], [], OrderedDict(), []
        self._cache, self._bet = {}, None
        self._carregar(pasta / 'demanda_unificada.csv', pasta / 'conexoes.csv', pasta / 'topologia_extra.csv')

    def _carregar(self, csv_path, conex_path, topo_path):
        estacoes = OrderedDict()
        totais = {}
        nomes_linha, nomes_estacao = {}, {}
        for f in csv_ler(csv_path):
            m = next((i for i, nome in enumerate(MESES) if chave(nome)==chave(f.get('mes',''))), -1)
            val = numero(f.get('passageiros', ''))
            if m < 0 or math.isnan(val):
                self.avisos.append('Registro com mês ou demanda inválidos ignorado')
                continue
            if 'milhar' in chave(f.get('unidade', '')):
                val *= 1000
            lk = chave_linha(f.get('linha',''))
            nomes_linha.setdefault(lk, f.get('linha',''))
            est = f.get('estacao', '')
            est = re.sub(r'\s+\d+$', '', est)
            est = re.sub(r'[*¹²³⁴]+$', '', est).strip()
            if chave(est) == 'total':
                totais.setdefault(lk, [math.nan]*12)[m] = val
            else:
                ek = chave(est)
                estacoes.setdefault(lk, OrderedDict()).setdefault(ek, [math.nan]*12)[m] = val
                nomes_estacao.setdefault((lk,ek), est)
        topo = OrderedDict()
        if topo_path.is_file():
            for f in csv_ler(topo_path):
                l, e = f.get('linha'), f.get('estacao')
                if l and e:topo.setdefault(chave_linha(l), []).append(e)
        for lk in nomes_linha:
            ids = []
            est = estacoes.get(lk)
            if est:
                ordem = list(est)
                if lk in topo:
                    ordem = [chave(n) for n in topo[lk] if chave(n) in est]
                    ordem.extend(ek for ek in est if ek not in ordem)
                for ek in ordem:
                    ids.append(self._no(lk, nomes_linha[lk], nomes_estacao[(lk,ek)], ek, False, False, est[ek][:]))
            elif lk in totais:
                if lk in topo and topo[lk]:
                    for n in topo[lk]:
                        ids.append(self._no(lk, nomes_linha[lk], n, chave(n), False, True, [v/len(topo[lk]) for v in totais[lk]]))
                else:
                    ids.append(self._no(lk, nomes_linha[lk], '(linha agregada)', 'agregado', True, False,
                                        [v/AGREGADO_DIVISOR for v in totais[lk]]))
                    self.avisos.append(nomes_linha[lk] + ': linha agregada em nó único; demanda = total/15.')
            if ids:self.linhas[lk] = ids
        medias_linha = {}
        for n in self.nos:
            valores = [x for x in n['demanda'] if math.isfinite(x) and x>0]
            n['media'] = sum(valores)/len(valores) if valores else math.nan
            if valores:medias_linha.setdefault(n['linha_chave'],[]).append(n['media'])
        faltantes = 0
        for n in self.nos:
            for i,d in enumerate(n['demanda']):
                if not math.isfinite(d) or d<=0:
                    v = n['media'] if math.isfinite(n['media']) else (sum(medias_linha.get(n['linha_chave'],[0])) / max(1,len(medias_linha.get(n['linha_chave'],[]))))
                    n['demanda'][i] = v
                    faltantes += 1
        if faltantes:self.avisos.append('%d valores de demanda ausentes/zerados preenchidos com média (mesma regra Java).'%faltantes)
        arestas = set()
        def add(a,b,bald):
            if a == b or (min(a,b),max(a,b)) in arestas:return
            arestas.add((min(a,b),max(a,b)))
            self.adj[a].append((b, bool(bald)))
            self.adj[b].append((a, bool(bald)))
        for ids in self.linhas.values():
            for a,b in zip(ids,ids[1:]):add(a,b,False)
        agrupados = OrderedDict()
        for n in self.nos:
            if not n['agregado']:agrupados.setdefault(n['chave'],[]).append(n['id'])
        for grupo in agrupados.values():
            for i,a in enumerate(grupo):
                for b in grupo[i+1:]:
                    if self.nos[a]['linha_chave'] != self.nos[b]['linha_chave']:add(a,b,True)
        if conex_path.is_file():
            for f in csv_ler(conex_path):
                lk1,lk2 = chave_linha(f.get('linhaA','')),chave_linha(f.get('linhaB',''))
                a,b=self._achar(lk1,chave(f.get('estacaoA',''))),self._achar(lk2,chave(f.get('estacaoB','')))
                if a is None or b is None:self.avisos.append('Conexão ignorada por nó não encontrado: '+str(f))
                else:add(a,b,True)
        self.arestas = [(i,j,b) for i,li in enumerate(self.adj) for j,b in li if j>i]

    def _no(self,lk,linha,estacao,ek,agregado,estimado,demanda):
        i=len(self.nos)
        self.nos.append({'id':i,'linha_chave':lk,'linha':linha,'estacao':estacao,'chave':ek,
                         'agregado':agregado,'estimado':estimado,'demanda':demanda})
        self.adj.append([])
        return i

    def _achar(self,lk,ek):
        ids=self.linhas.get(lk,[])
        if len(ids)==1 and self.nos[ids[0]]['agregado']:return ids[0]
        return next((i for i in ids if self.nos[i]['chave']==ek),None)

    def rede(self,mes,mobilidade):
        key=(mes,mobilidade)
        if key in self._cache:return self._cache[key]
        mx=max(n['demanda'][mes] for n in self.nos)
        w=[n['demanda'][mes]/mx if mx>0 else 0 for n in self.nos]
        viz,c=[],[]
        for i,adj in enumerate(self.adj):
            nv=[v for v,b in adj]
            soma=sum((w[i]+w[j])/2 for j in nv)
            viz.append(nv)
            c.append([((w[i]+w[j])/2 if mobilidade==0 else w[i]*((w[i]+w[j])/2)/soma if soma>0 else 0) for j in nv])
        cin=[]
        for j in range(len(w)):
            ci=[]
            for i in viz[j]:
                ci.append(c[i][viz[i].index(j)])
            cin.append(ci)
        x=[1/math.sqrt(len(w))]*len(w)
        lam=0
        for _ in range(5000):
            y=[x[i]+sum(coeff*x[j] for j,coeff in zip(viz[i],c[i])) for i in range(len(w))]
            norma=math.sqrt(sum(val*val for val in y))
            x=[v/norma for v in y]
            if abs(norma-lam)<1e-12:
                lam=norma
                break
            lam=norma
        out={'w':w,'viz':viz,'c':c,'cin':cin,'rho':max(lam-1,1e-9)}
        self._cache[key]=out
        return out

    def beta(self,cenario,mes,mob):
        return cenario['r0']/(6.3*self.rede(mes,mob)['rho'])

    def bfs(self,o,d):
        prev=[-2]*len(self.nos);prev[o]=-1
        q=deque([o])
        while q:
            v=q.popleft()
            if v==d:break
            for j,b in self.adj[v]:
                if prev[j]==-2:prev[j]=v;q.append(j)
        return self._rec(prev,o,d)

    def dijkstra(self,o,d,mes,mob,t_est,t_bald):
        w=self.rede(mes,mob)['w'];n=len(w)
        dist=[math.inf]*n;prev=[-2]*n;dist[o]=0;prev[o]=-1
        # Mesmo relaxamento que o Java: custo de entrada no sucessor j.
        # A iteração usa menor índice em empates; o Java usa HashSet (ordem não contratual).
        abertos=set(range(n))
        while abertos:
            r=min(abertos,key=lambda i:(dist[i],i))
            if not math.isfinite(dist[r]):break
            abertos.remove(r)
            for j,b in self.adj[r]:
                if j not in abertos:continue
                custo=dist[r]+w[j]*(t_est+(t_bald if b else 0))
                if custo<dist[j]:dist[j]=custo;prev[j]=r
        return self._rec(prev,o,d)

    @staticmethod
    def _rec(prev,o,d):
        if prev[d]==-2:return None
        arr=[];v=d
        while v!=-1:arr.append(v);v=prev[v]
        return arr[::-1]

    def betweenness(self):
        if self._bet is not None:return self._bet
        n=len(self.nos);cb=[0.0]*n
        for s in range(n):
            stack=[];pred=[[] for _ in range(n)];sigma=[0.0]*n;dist=[-1]*n
            sigma[s]=1;dist[s]=0;q=deque([s])
            while q:
                v=q.popleft();stack.append(v)
                for w,b in self.adj[v]:
                    if dist[w]<0:dist[w]=dist[v]+1;q.append(w)
                    if dist[w]==dist[v]+1:sigma[w]+=sigma[v];pred[w].append(v)
            delta=[0.0]*n
            for w in reversed(stack):
                for v in pred[w]:delta[v]+=sigma[v]/sigma[w]*(1+delta[w])
                if w!=s:cb[w]+=delta[w]
        self._bet=[v/2 for v in cb]
        return self._bet

# Coordenadas ESQUEMÁTICAS originais do protótipo HTML enviado, sem valor geográfico.
POSICOES_VISUAIS = {'aacdservidor': [0.804, 0.51], 'adolfopinheiro': [0.858, 0.773], 'altodaboavista': [0.871, 0.734], 'altodoipiranga': [0.643, 0.405], 'amadorbueno': [0.0, 0.99], 'ambuita': [0.025, 0.973], 'anarosa': [0.704, 0.477], 'anhangabau': [0.553, 0.524], 'antoniojoao': [0.253, 0.81], 'armenia': [0.427, 0.484], 'arturalvim': [0.444, 0.427], 'autodromo': [0.908, 0.9], 'barrafunda': [0.429, 0.545], 'barueri': [0.227, 0.829], 'belem': [0.492, 0.401], 'berrini': [0.681, 0.843], 'borbagato': [0.876, 0.692], 'bressermooca': [0.523, 0.409], 'brigadeiro': [0.673, 0.541], 'brooklin': [0.874, 0.651], 'brunocovasmendesvilanatal': [0.979, 0.968], 'bras': [0.526, 0.45], 'butanta': [0.553, 0.787], 'camilohaddad': [0.567, 0.246], 'campobelo': [0.865, 0.611], 'campolimpo': [0.821, 0.964], 'capaoredondo': [0.825, 1.0], 'carandiru': [0.359, 0.456], 'carapicuiba': [0.305, 0.772], 'carraoassaiatacadista': [0.464, 0.401], 'ceasa': [0.47, 0.705], 'chacaraklabin': [0.709, 0.443], 'cidadejardim': [0.622, 0.803], 'cidadeuniversitaria': [0.529, 0.749], 'clinicas': [0.635, 0.61], 'comandantesampaio': [0.386, 0.711], 'conceicao': [0.837, 0.35], 'consolacao': [0.615, 0.585], 'corinthiansitaquera': [0.472, 0.451], 'domingosdemorais': [0.454, 0.588], 'engenheirocardoso': [0.1, 0.92], 'eucaliptos': [0.849, 0.573], 'farialima': [0.572, 0.715], 'fazendadajuta': [0.557, 0.068], 'fradiquecoutinho': [0.575, 0.675], 'generalmiguelcosta': [0.331, 0.752], 'giovannigronchi': [0.819, 0.887], 'grajau': [0.956, 0.944], 'granjajulieta': [0.75, 0.853], 'guilherminaesperanca': [0.404, 0.364], 'hebraicareboucas': [0.595, 0.777], 'higienopolismackenzie': [0.552, 0.567], 'hospitalsaopaulo': [0.777, 0.482], 'imperatrizleopoldina': [0.448, 0.629], 'itapevi': [0.075, 0.938], 'jabaquara': [0.861, 0.328], 'jandira': [0.151, 0.885], 'japaoliberdade': [0.594, 0.484], 'jardimbelval': [0.202, 0.848], 'jardimcolonial': [0.552, 0.0], 'jardimplanalto': [0.561, 0.138], 'jardimsilveira': [0.176, 0.866], 'jardimsaopauloayrtonsenna': [0.302, 0.416], 'joaodias': [0.785, 0.849], 'julioprestes': [0.398, 0.548], 'jurubatuba': [0.882, 0.88], 'linha10total': [0.537, 0.434], 'linha11total': [0.503, 0.464], 'linha12total': [0.511, 0.435], 'linha13total': [0.512, 0.396], 'linha7total': [0.486, 0.503], 'lapa': [0.461, 0.543], 'largotreze': [0.839, 0.809], 'luz': [0.504, 0.499], 'marechaldeodoro': [0.487, 0.558], 'moema': [0.828, 0.54], 'morumbi': [0.715, 0.852], 'oratorio': [0.569, 0.321], 'osasco': [0.414, 0.691], 'oscarfreire': [0.575, 0.635], 'palmeirasbarrafunda': [0.474, 0.523], 'paradainglesa': [0.276, 0.395], 'paraiso': [0.682, 0.504], 'patriarcavilare': [0.419, 0.398], 'paulista': [0.58, 0.593], 'pedroii': [0.554, 0.46], 'penhalojasbesni': [0.451, 0.365], 'pinheiros': [0.564, 0.753], 'portuguesatiete': [0.391, 0.472], 'pracadaarvore': [0.767, 0.422], 'presidentealtino': [0.446, 0.672], 'primaverainterlagos': [0.933, 0.922], 'quitauna': [0.358, 0.732], 'republica': [0.527, 0.534], 'sacoma': [0.605, 0.403], 'sagradocoracao': [0.126, 0.903], 'santacecilia': [0.513, 0.559], 'santacruz': [0.747, 0.452], 'santarita': [0.05, 0.955], 'santaterezinha': [0.279, 0.791], 'santana': [0.33, 0.437], 'santoamaro': [0.821, 0.847], 'santosimigrantes': [0.678, 0.418], 'santuarionsdefatimasumare': [0.66, 0.63], 'sapopemba': [0.559, 0.103], 'saudeultrafarma': [0.79, 0.396], 'socorro': [0.852, 0.862], 'saobento': [0.53, 0.498], 'saojoaquim': [0.625, 0.477], 'saojudas': [0.813, 0.372], 'saolucas': [0.568, 0.283], 'saomateus': [0.554, 0.034], 'saopaulomorumbi': [0.542, 0.822], 'se': [0.562, 0.492], 'tamanduatei': [0.568, 0.404], 'tatuape': [0.486, 0.434], 'tiradentes': [0.464, 0.493], 'trianonmasp': [0.646, 0.565], 'tucuruvi': [0.252, 0.374], 'varginha': [1.0, 0.992], 'vergueiro': [0.655, 0.487], 'vilamadalena': [0.687, 0.645], 'vilamariana': [0.736, 0.478], 'vilamatilde': [0.426, 0.342], 'vilaolimpia': [0.65, 0.826], 'vilaprudente': [0.568, 0.361], 'vilasonia': [0.533, 0.857], 'vilatolstoi': [0.565, 0.209], 'vilauniao': [0.563, 0.173], 'viladasbelezas': [0.819, 0.926], 'villalobosjaguare': [0.496, 0.733]}
