"""
Alunos:
- Pedro Henrique Carvalho Pereira - 10418861
- Mateus Ribeiro Cerqueira - 10443901

Neste código temos as equações e algoritmos de ModeloEpidemiologico.java e 
MalhaMetroferroviaria.java.
Inclui JavaRandom para replicar o mesmo gerador que foi feito no projeto em 
Java (48-bit LCG e nextGaussian).

Referências:
- Java Random (LCG de 48 bits, next(bits), nextDouble, nextGaussian):
  https://docs.oracle.com/javase/8/docs/api/java/util/Random.html

- Linear congruential generator (multiplicador 0x5DEECE66D, módulo 2^48):
  https://en.wikipedia.org/wiki/Linear_congruential_generator

- Método polar de Marsaglia (base do nextGaussian):
  https://en.wikipedia.org/wiki/Marsaglia_polar_method

- Math.round() do Java (floor(x + 0.5)):
  https://docs.oracle.com/javase/8/docs/api/java/lang/Math.html#round(double)

- Modelo SEIR (compartimentos S, E, I, R):
  https://en.wikipedia.org/wiki/Compartmental_models_in_epidemiology#The_SEIR_model

- Número de reprodução básico (R0) e intervalo serial:
  https://en.wikipedia.org/wiki/Basic_reproduction_number

- Geração de variáveis Poisson, algoritmo de Knuth (usado em binom quando média < 15):
  Aulas de Matemática Discreta II
  https://en.wikipedia.org/wiki/Poisson_distribution#Generating_Poisson-distributed_random_variables

- Aproximação Normal da Binomial (usada em binom quando média >= 15):
  https://en.wikipedia.org/wiki/Binomial_distribution#Normal_approximation

- Amostragem binomial por passos de tempo discretos (tau-leaping):
  https://en.wikipedia.org/wiki/Tau-leaping

- Conversão de taxa em probabilidade, p = 1 - exp(-λ) (distribuição exponencial):
  Aulas de Matemática Discreta II
  https://en.wikipedia.org/wiki/Exponential_distribution

- Odds ratio (aplicar_or: p -> odds -> odds * OR -> p):
  https://en.wikipedia.org/wiki/Odds_ratio

- Percentil com interpolação linear (função pct):
  Aulas de Algoritmos Numéricos
  https://numpy.org/doc/stable/reference/generated/numpy.percentile.html

Essas referências foram principalmente para as formulaçõe que usamos no projeto de Grafos, 
mas foram revisadas para este também.
"""
import math
from statistics import mean

CENARIOS = [
    {'nome':'Ancestral (R0 baixo)','r0':2.2,'serial':5.2,'incubacao':5.2,'fonte':'Li 2020; Alene 2021'},
    {'nome':'Agrupado estudos iniciais','r0':2.66,'serial':5.2,'incubacao':6.5,'fonte':'Dhungel 2022; Alene 2021'},
    {'nome':'Delta','r0':5.08,'serial':3.9,'incubacao':5.2,'fonte':'Liu & Rocklöv 2021; Madewell 2023; incubação ancestral estimada'},
    {'nome':'Ômicron (Re)','r0':4.2,'serial':3.2,'incubacao':3.49,'fonte':'Liu 2022; Madewell 2023; Xu 2023 (Re, não R0)'}
]



PROTECOES = [
    {'nome':'Sem proteção','or':1.0,'fonte':'Referência'},
    {'nome':'Proteção intermediária','or':0.38,'fonte':'Li et al. 2021'},
    {'nome':'Proteção elevada','or':0.15,'fonte':'Chu et al. 2020'}
]




DUR_INFECCIOSA = 6.3
FRAC_ASSINT = .351


def jround(v):
    # Math.round() do Java (para NaN/negativos não usados em população).
    return math.floor(v+0.5)


class JavaRandom:
    MULT=0x5DEECE66D
    MASK=(1<<48)-1
    
    
    
    def __init__(self,seed):
        self.seed=(int(seed)^self.MULT)&self.MASK
        self.next_next=None
    
    
    
    
    def next(self,bits):
        self.seed=(self.seed*self.MULT+0xB)&self.MASK
        return self.seed>>(48-bits)

    def random(self):
        return ((self.next(26)<<27)+self.next(27))/float(1<<53)

    def gauss(self):
        if self.next_next is not None:
            v=self.next_next;self.next_next=None;return v


        while True:
            v1=2*self.random()-1;v2=2*self.random()-1
            s=v1*v1+v2*v2

            if 0<s<1:
                multiplier=math.sqrt(-2*math.log(s)/s)
                self.next_next=v2*multiplier
                return v1*multiplier


def binom(n,p,rng):
    """Mesmo método aproximado de MalhaMetroferroviaria.binom, inclusive Poisson e Normal."""
    if n<=0 or p<=0:return 0


    if p>=1:return n


    media=n*p


    if n<=40:
        c=0

        for _ in range(n):
            if rng.random()<p:c+=1
        
        
        return c


    if media<15:
        L=math.exp(-media);pp=1;k=0

        while True:


            k+=1;pp*=rng.random()
            if not pp>L:break
        return min(k-1,n)
    
    
    x=jround(media+math.sqrt(media*(1-p))*rng.gauss())
    return max(0,min(n,x))


def aplicar_or(p,odds):
    if p<=0:return 0
    if p>=1:return 1
    
    o=p/(1-p)*odds
    return o/(1+o)


def percurso(g,caminho,mes,mob,cenario,protecao,k=1.0,t_est=3.0,t_bald=5.0):
    if caminho is None:return None
    rd=g.rede(mes,mob)
    beta=g.beta(cenario,mes,mob)
    sobrevida=1.0;etapas=[]
    
    
    
    for ix,v in enumerate(caminho):
        bal=ix>0 and any(u==caminho[ix-1] and b for u,b in g.adj[v])
        tempo=t_est+(t_bald if bal else 0)
        lmb=beta*rd['w'][v]*k*(tempo/1440.0)
        p=aplicar_or(1-math.exp(-lmb),protecao['or'])
        sobrevida*=(1-p)
        n=g.nos[v]
        etapas.append({'id':v,'estacao':n['estacao'],'linha':n['linha'],'baldeacao':bal,
                       'peso':rd['w'][v],'lambda':lmb,'p':p,'acum':1-sobrevida})
    return {'ids':caminho,'total':etapas[-1]['acum'] if etapas else 0,'etapas':etapas}


def pct(nums,porc):
    a=sorted(nums)
    idx=porc*(len(a)-1);lo=int(math.floor(idx));hi=int(math.ceil(idx))
    
    return a[lo]+(a[hi]-a[lo])*(idx-lo)


def simular(g,cfg):
    """
    Port de simular(): cada run usa JavaRandom(seed+1000003*run).
    Sem dados horários nem previsão real; resultado = população sintética fechada em nós.
    """
    mes=cfg['mes'];mob=cfg['mobilidade'];cenario=CENARIOS[cfg['cenario']]
    rd=g.rede(mes,mob);n=len(g.nos)
    D=cfg['dias'];R=cfg['execucoes'];sementes=cfg['sementes'];inf0=cfg['infectados']
    beta=g.beta(cenario,mes,mob)
    
    
    lat=max(1.0, cenario['serial']-DUR_INFECCIOSA/2)
    pEI=1-math.exp(-1/lat);pIR=1-math.exp(-1/DUR_INFECCIOSA)
    
    
    pos_sint=max(0.0,1-max(0.0,cenario['incubacao']-lat)/DUR_INFECCIOSA)
    m=1-(1-FRAC_ASSINT)*pos_sint*cfg['isolamento']
    
    N=[max(1,jround(w*cfg['pop_escala'])) for w in rd['w']]
    
    
    for j in sementes:N[j]=max(N[j],inf0)
    
    poptot=sum(N)
    S=[];E=[];I=[];Rm=[];A=[]
    
    por_no=[[0.0]*n for _ in range(D+1)]  # soma por nó/dia de I, mesma trajetória SEIR
    
    
    cont=[0]*n;soma_ch=[0.0]*n;picos=[];dia_picos=[];ataques=[]
    
    
    for run in range(R):
        rng=JavaRandom(cfg['seed']+1000003*run)
        s=N[:];e=[0]*n;infect=[0]*n;rr=[0]*n;cheg=[-1]*n
        
        for sd in sementes:
            infect[sd]=inf0;s[sd]=N[sd]-infect[sd];cheg[sd]=0
        sc=[];ec=[];ic=[];rc=[];ac=[]
        
        
        def registrar():
            dia_corrente=len(ic)
            for j in range(n):por_no[dia_corrente][j]+=infect[j]
            sc.append(sum(s));ec.append(sum(e));ic.append(sum(infect));rc.append(sum(rr));ac.append(sum(d>=0 for d in cheg))
        
        
        registrar()
        
        for dia in range(1,D+1):
            ne=[0]*n;ni=[0]*n;nr=[0]*n
        
            for j in range(n):
        
                if s[j]>0:
                    pres=0.0
        
                    for ix,i in enumerate(rd['viz'][j]):
                        if infect[i]>0:
                            pres+=beta*rd['cin'][j][ix]*m*infect[i]
        
        
                    if pres>0:
                        prob=aplicar_or(1-math.exp(-pres/N[j]),cfg['odds_ratio'])
                        ne[j]=binom(s[j],prob,rng)
        
                ni[j]=binom(e[j],pEI,rng)
                nr[j]=binom(infect[j],pIR,rng)
        
            for j in range(n):
                s[j]-=ne[j];e[j]+=ne[j]-ni[j];infect[j]+=ni[j]-nr[j];rr[j]+=nr[j]
        
                if ne[j]>0 and cheg[j]<0:cheg[j]=dia
        
            registrar()
        pk=max(ic);dp=ic.index(pk)
        picos.append(pk);dia_picos.append(dp);ataques.append(1-sc[-1]/poptot)
        
        
        for j in range(n):
            if cheg[j]>=0:
                cont[j]+=1;soma_ch[j]+=cheg[j]
        S.append(sc);E.append(ec);I.append(ic);Rm.append(rc);A.append(ac)
    
    
    def serie(vals):return [mean(run[d] for run in vals) for d in range(D+1)]
    
    
    def bandas(vals):return ([pct([run[d] for run in vals],.05) for d in range(D+1)],
                             [pct([run[d] for run in vals],.95) for d in range(D+1)])
    p5I,p95I=bandas(I);p5A,p95A=bandas(A)
    fin=[row[D] for row in A];mfin=mean(fin)
    
    
    return {
        'dias':D,'execucoes':R,'beta':beta,'rho':rd['rho'],'rnet':beta*DUR_INFECCIOSA*rd['rho'],
        'pop_total':poptot,'S':serie(S),'E':serie(E),'I':serie(I),'R':serie(Rm),'alcance':serie(A),
        'I_p5':p5I,'I_p95':p95I,'alcance_p5':p5A,'alcance_p95':p95A,
        'alc_final_media':mfin,'alc_final_mediana':pct(fin,.5),'alc_final_p5':pct(fin,.05),
        'alc_final_p95':pct(fin,.95),
        'alc_final_dp': math.sqrt(sum((v-mfin)**2 for v in fin)/(R-1)) if R>1 else 0,
        'pico_I_medio':mean(picos),'dia_pico_medio':mean(dia_picos),
        'ataque_media':mean(ataques),'ataque_p5':pct(ataques,.05),'ataque_p95':pct(ataques,.95),
        'prob_alcance':[x/R for x in cont],
        'I_por_no':[[x/R for x in linha] for linha in por_no],  # apenas visualização, sem nova equação
        'chegada_media':[soma_ch[j]/cont[j] if cont[j] else None for j in range(n)],
        'sementes':sementes,'cenario':cenario,'odds_ratio':cfg['odds_ratio'],'mes':mes
    }