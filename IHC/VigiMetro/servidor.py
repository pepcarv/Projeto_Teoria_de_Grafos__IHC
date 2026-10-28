# -*- coding: utf-8 -*-
"""VigiMetro: servidor HTTP local (somente biblioteca padrão).
Iniciar por iniciar.bat/.sh; cloudflared é opcional para link temporário público.
"""
import argparse
import gzip
import html
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import webbrowser
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse

ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'qr_vendor.zip'))
from grafo import Grafo, MESES, POSICOES_VISUAIS, POSICOES_POR_LINHA, chave
from epidemiologia import CENARIOS,PROTECOES,percurso,simular

G=Grafo(ROOT)
PUBLIC_URL=None
TUNNEL_ERROR=None
TUNNEL=None
START=time.time()
PORT=8000
LOCK=threading.Lock()
SEM=threading.BoundedSemaphore(2)
CACHE={}
STATS={'visitas':0,'rotas':0,'simulacoes':0}

LIMITACOES=[
  'Modelo exploratório: não é previsão real de infecções, surtos ou segurança de viagens.',
  'Probabilidade do trajeto é condicional à presença de k infectados em cada estação.',
  'Demanda mensal média de dias úteis, sem informação sobre horários ou fluxos origem-destino.',
  'Parte dos dados provém de anos diferentes; nas linhas 7/10/11/12/13 as demandas por estação são estimadas dividindo os totais.',
  'Mobilidade e tempos de exposição são hipóteses do modelo Java.',
  'População SEIR é sintética: N_i = w_i × popEscala; SAR domiciliar não é aplicada ao metrô.',
  'A topologia usa estações físicas de 2026 e baldeações verificadas; as demandas repartidas igualmente nas linhas CPTM são aproximações.'
]


def valor(v,tipo,mi,ma,nome):
    try:
        x=tipo(v)
        if not mi<=x<=ma or (isinstance(x,float) and (x!=x)):
            raise ValueError()
        return x
    except (TypeError,ValueError,OverflowError):
        raise ValueError('%s deve estar entre %s e %s'%(nome,mi,ma))


def local_ip():
    try:
        s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
        s.connect(('1.1.1.1',80));ip=s.getsockname()[0];s.close()
        return ip
    except OSError:
        try:return socket.gethostbyname(socket.gethostname())
        except OSError:return '127.0.0.1'


def api_bootstrap():
    bt=G.betweenness()
    counts={}
    for n in G.nos:
        k=chave(n['estacao']);counts[k]=counts.get(k,0)+1
    vistos={}
    nodes=[]
    for n in G.nos:
        k=chave(n['estacao']);x,y=POSICOES_POR_LINHA.get((n['linha_chave'],k), POSICOES_VISUAIS.get(k,(.5,.5)))
        if n['agregado']:
            vs=[POSICOES_VISUAIS[chave(G.nos[j]['estacao'])] for j,b in G.adj[n['id']] if chave(G.nos[j]['estacao']) in POSICOES_VISUAIS]
            if vs:x=sum(a for a,b in vs)/len(vs);y=sum(b for a,b in vs)/len(vs)
        elif counts[k]>1:
            idx=vistos.get(k,0);vistos[k]=idx+1
            x+=(idx-(counts[k]-1)/2)*.009
            y+=(idx-(counts[k]-1)/2)*.009
        nodes.append({'id':n['id'],'nome':n['estacao'],'linha':n['linha'],'l':n['linha_chave'],
                      'agregado':n['agregado'],'estimado':n['estimado'],'demanda':n['demanda'],
                      'grau':len(G.adj[n['id']]),'betweenness':bt[n['id']],
                      'x':max(0.01,min(.99,x)), 'y':max(.01,min(.99,y))})
    return {'nos':nodes,'arestas':G.arestas,'meses':MESES,'cenarios':CENARIOS,
            'protecoes':PROTECOES,'avisos':G.avisos,'limitacoes':LIMITACOES,
            'total_nos':len(G.nos),'total_arestas':len(G.arestas),'total_linhas':len(G.linhas),
            'fontes':['ModeloEpidemiologico.java','MalhaMetroferroviaria.java','Main.java',
                      'Projeto TG - Relatório Pt 2.pdf','relatorio_covid_malha_metroferroviaria.pdf']}
BOOT=api_bootstrap()


def args_comuns(p):
    return {
       'mes':valor(p.get('mes',0),int,0,11,'Mês'),
       'cenario':valor(p.get('cenario',1),int,0,3,'Cenário'),
       'protecao':valor(p.get('protecao',0),int,0,2,'Proteção'),
       'mobilidade':valor(p.get('mobilidade',0),int,0,1,'Mobilidade'),
    }


def api_route(p):
    cfg=args_comuns(p)
    o=valor(p.get('origem'),int,0,len(G.nos)-1,'Origem');d=valor(p.get('destino'),int,0,len(G.nos)-1,'Destino')
    k=valor(p.get('k',1),float,.001,1e4,'Infectados k')
    t_est=valor(p.get('t_est',3),float,.1,120,'Tempo por estação')
    t_bald=valor(p.get('t_bald',5),float,0,60,'Tempo de baldeação')
    c=CENARIOS[cfg['cenario']];pr=PROTECOES[cfg['protecao']]
    ca=G.bfs(o,d);cb=G.dijkstra(o,d,cfg['mes'],cfg['mobilidade'],t_est,t_bald)
    b=G.beta(c,cfg['mes'],cfg['mobilidade'])
    comp=[]
    for prot in PROTECOES:
        comp.append({'nome':prot['nome'],'or':prot['or'],
                     'bfs':percurso(G,ca,cfg['mes'],cfg['mobilidade'],c,prot,k,t_est,t_bald),
                     'dijkstra':percurso(G,cb,cfg['mes'],cfg['mobilidade'],c,prot,k,t_est,t_bald)})
    with LOCK:STATS['rotas']+=1
    return {'comparacoes_protecao':comp,'origem':o,'destino':d,'mes':cfg['mes'],'cenario':c,'protecao':pr,
            'beta':b,'rho':G.rede(cfg['mes'],cfg['mobilidade'])['rho'],
            'bfs':percurso(G,ca,cfg['mes'],cfg['mobilidade'],c,pr,k,t_est,t_bald),
            'dijkstra':percurso(G,cb,cfg['mes'],cfg['mobilidade'],c,pr,k,t_est,t_bald),
            'hipotese':'P(condicional): k indivíduos infecciosos presentes em CADA estação do percurso; não corresponde ao risco individual real.',
            'k':k,'t_est':t_est,'t_bald':t_bald}


def cfg_sim(p):
    c=args_comuns(p)
    sementes=p.get('sementes',[0])
    if not isinstance(sementes,list) or not 1<=len(sementes)<=8:
        raise ValueError('Selecione de 1 a 8 estações-semente')
    sementes=[valor(v,int,0,len(G.nos)-1,'Semente') for v in sementes]
    if len(set(sementes))!=len(sementes):raise ValueError('Sementes repetidas')
    c.update({'sementes':sementes,
      'dias':valor(p.get('dias',60),int,1,120,'Dias (limite público)'),
      'execucoes':valor(p.get('execucoes',30),int,1,100,'Execuções (limite público)'),
      'seed':valor(p.get('seed',42),int,0,10**12,'Semente aleatória'),
      'isolamento':valor(p.get('isolamento',0),float,0,1,'Isolamento'),
      'infectados':valor(p.get('infectados',5),int,1,100000,'Infectados iniciais'),
      'pop_escala':valor(p.get('pop_escala',2000),int,50,200000,'Escala populacional')})
    c['odds_ratio']=PROTECOES[c.pop('protecao')]['or']
    return c


def api_sim(p):
    cfg=cfg_sim(p)
    key=json.dumps(cfg,sort_keys=True,separators=(',',':'))
    with LOCK:
        if key in CACHE:
            return CACHE[key]
    if not SEM.acquire(timeout=8):raise RuntimeError('Simulador ocupado: tente novamente')
    try:
        with LOCK:
            if key in CACHE:return CACHE[key]
        res=simular(G,cfg)
        res['limitacoes']=LIMITACOES
        with LOCK:
            if len(CACHE)>16:CACHE.clear()
            CACHE[key]=res;STATS['simulacoes']+=1
        return res
    finally:SEM.release()


def api_compare(p):
    cfg=cfg_sim(p)
    tipo=p.get('tipo','protecoes')
    if tipo not in ('protecoes','cenarios','meses'):raise ValueError('Comparação inválida')
    # Mesmas famílias de comparação E2/E4/E5 de Main.java.
    variantes=([('Sem proteção',1.),('Intermediária',.38),('Elevada',.15)] if tipo=='protecoes'
               else [(c['nome'],i) for i,c in enumerate(CENARIOS)] if tipo=='cenarios'
               else [(nome,i) for i,nome in enumerate(MESES)])
    out=[]
    for nome,v in variantes:
        cs=cfg.copy()
        if tipo=='protecoes':cs['odds_ratio']=v
        elif tipo=='cenarios':cs['cenario']=v
        else:cs['mes']=v
        # Mesmo algoritmo SEIR e RNG; envia só séries para preservar tráfego.
        res=api_sim({'mes':cs['mes'],'cenario':cs['cenario'],
                     'protecao':next(i for i,x in enumerate(PROTECOES) if x['or']==cs['odds_ratio']),
                     'mobilidade':cs['mobilidade'],'sementes':cs['sementes'],'dias':cs['dias'],
                     'execucoes':cs['execucoes'],'seed':cs['seed'],'isolamento':cs['isolamento'],
                     'infectados':cs['infectados'],'pop_escala':cs['pop_escala']})
        out.append({'nome':nome,'I':res['I'],'alcance':res['alcance'],
                    'alc_final_media':res['alc_final_media'],'ataque_media':res['ataque_media']})
    return {'tipo':tipo,'series':out,'dias':cfg['dias'],'nota':'Mesmo modelo SEIR, variando somente o parâmetro indicado.'}


def ranking(mes,mob):
    rd=G.rede(mes,mob);bt=G.betweenness()
    def top(vals):
        return [{'id':i,'valor':vals[i]} for i in sorted(range(len(vals)),key=lambda i:(-vals[i],i))[:12]]
    return {'demanda':top([n['demanda'][mes] for n in G.nos]),
            'grau':top([len(a) for a in G.adj]),'betweenness':top(bt),'rho':rd['rho']}


def qr_svg(url):
    import qrcode
    qr=qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M,border=4,box_size=1)
    qr.add_data(url,optimize=0);qr.make(fit=True)
    matriz=qr.get_matrix();n=len(matriz)
    ret=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" shape-rendering="crispEdges">'%(n,n),
         '<rect width="100%" height="100%" fill="white"/>', '<path fill="#10172c" d="']
    for y,linha in enumerate(matriz):
        for x,v in enumerate(linha):
            if v:ret.append('M%d %dh1v1h-1z'%(x,y))
    ret.append('"/></svg>')
    return ''.join(ret).encode('utf-8')


class Handler(BaseHTTPRequestHandler):
    protocol_version='HTTP/1.1'
    def log_message(self,fmt,*args):
        if not self.path.startswith('/api/config') and not self.path.startswith('/api/status'):
            print('HTTP',self.address_string(),fmt%args,flush=True)
    def resp(self,code,content,ctype='application/json; charset=utf-8'):
        if isinstance(content,(dict,list)):
            content=json.dumps(content,ensure_ascii=False,allow_nan=False,separators=(',',':')).encode('utf-8')
        elif isinstance(content,str):content=content.encode('utf-8')
        use_gzip=len(content)>800 and 'gzip' in self.headers.get('Accept-Encoding','')
        if use_gzip:content=gzip.compress(content,compresslevel=5)
        self.send_response(code)
        self.send_header('Content-Type',ctype)
        self.send_header('Content-Length',str(len(content)))
        self.send_header('Cache-Control','no-store' if self.path.startswith('/api') else 'max-age=60')
        self.send_header('X-Content-Type-Options','nosniff')
        self.send_header('X-Frame-Options','DENY')
        self.send_header('Referrer-Policy','no-referrer')
        self.send_header('Content-Security-Policy',"default-src 'self' 'unsafe-inline' data:; connect-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'")
        if use_gzip:self.send_header('Content-Encoding','gzip')
        self.end_headers()
        try:self.wfile.write(content)
        except (BrokenPipeError,ConnectionResetError):pass
    def do_GET(self):
        global STATS
        path=urlparse(self.path).path
        try:
            if path in ('/','/index.html'):
                if path=='/':
                    with LOCK:STATS['visitas']+=1
                return self.resp(200,(ROOT/'index.html').read_bytes(),'text/html; charset=utf-8')
            if path=='/api/bootstrap':return self.resp(200,BOOT)
            if path=='/api/config':
                return self.resp(200,{'local':f'http://localhost:{PORT}','rede':f'http://{local_ip()}:{PORT}',
                                      'publico':PUBLIC_URL,'erro_tunel':TUNNEL_ERROR,'ativo':True})
            if path=='/api/status':return self.resp(200,{'ativo':True,'segundos':round(time.time()-START),'estatisticas':STATS})
            if path=='/api/ranking':
                from urllib.parse import parse_qs
                qs=parse_qs(urlparse(self.path).query)
                return self.resp(200,ranking(valor(qs.get('mes',['0'])[0],int,0,11,'Mês'),valor(qs.get('mob',['0'])[0],int,0,1,'Mobilidade')))
            if path=='/api/qr.svg':
                from urllib.parse import parse_qs
                qs=parse_qs(urlparse(self.path).query);tipo=qs.get('tipo',['publico'])[0]
                url= PUBLIC_URL if tipo=='publico' else f'http://{local_ip()}:{PORT}' if tipo=='rede' else f'http://localhost:{PORT}' if tipo=='local' else None
                if not url:raise ValueError('Link ainda indisponível')
                return self.resp(200,qr_svg(url),'image/svg+xml')
            if path=='/favicon.ico':return self.resp(204,b'','image/x-icon')
            self.resp(404,{'erro':'Página não encontrada'})
        except ValueError as e:self.resp(400,{'erro':str(e)})
        except Exception as e:
            print('Falha GET:',repr(e),flush=True)
            self.resp(500,{'erro':'Falha interna do servidor'})
    def do_POST(self):
        try:
            n=int(self.headers.get('Content-Length','0'))
            if not 1<=n<=16384:raise ValueError('Requisição muito grande ou vazia')
            if 'application/json' not in self.headers.get('Content-Type',''):
                raise ValueError('Content-Type deve ser application/json')
            p=json.loads(self.rfile.read(n).decode('utf-8'))
            if not isinstance(p,dict):raise ValueError('Parâmetros devem ser um objeto JSON')
            path=urlparse(self.path).path
            if path=='/api/route':return self.resp(200,api_route(p))
            if path=='/api/sim':return self.resp(200,api_sim(p))
            if path=='/api/compare':return self.resp(200,api_compare(p))
            return self.resp(404,{'erro':'Operação não encontrada'})
        except (ValueError,TypeError,KeyError,json.JSONDecodeError) as e:self.resp(400,{'erro':str(e)})
        except RuntimeError as e:self.resp(503,{'erro':str(e)})
        except Exception as e:
            print('Falha POST:',repr(e),flush=True)
            self.resp(500,{'erro':'Falha interna do servidor'})


def iniciar_tunel(port,solicitado):
    global TUNNEL,TUNNEL_ERROR,PUBLIC_URL
    if not solicitado:
        TUNNEL_ERROR='Acesso externo desativado (--sem-tunel)';return
    exe=shutil.which('cloudflared')
    if not exe:
        for p in [ROOT/'cloudflared.exe',ROOT/'cloudflared',Path(os.environ.get('LOCALAPPDATA',''))/'Microsoft/WinGet/Links/cloudflared.exe']:
            if p.is_file():exe=str(p);break
    if not exe:
        TUNNEL_ERROR='cloudflared não encontrado. No Windows instale: winget install --id Cloudflare.cloudflared'
        print('\n[LINK PÚBLICO] '+TUNNEL_ERROR,flush=True)
        return
    try:
        TUNNEL=subprocess.Popen([exe,'tunnel','--url',f'http://localhost:{port}'],
             stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
             text=True,encoding='utf-8',errors='replace',bufsize=1)
        def ouvir():
            global TUNNEL_ERROR,PUBLIC_URL
            for linha in TUNNEL.stdout:
                m=re.search(r'https://[a-z0-9-]+\.trycloudflare\.com',linha)
                if m and not PUBLIC_URL:
                    PUBLIC_URL=m.group(0)
                    print('\n'+'='*62,flush=True)
                    print(' LINK PÚBLICO (4G / 5G / OUTRO WI-FI):',PUBLIC_URL,flush=True)
                    print(' QR CODE NA ABA "SITE"',flush=True)
                    print('='*62+'\n',flush=True)
                if any(x in linha.lower() for x in ('error','failed','unable')):
                    TUNNEL_ERROR=linha.strip()[:250]
                    print('[cloudflared]',linha.strip(),flush=True)
            if not PUBLIC_URL:TUNNEL_ERROR='Túnel encerrado sem gerar link público. Veja o terminal.'
        threading.Thread(target=ouvir,daemon=True).start()
    except OSError as e:
        TUNNEL_ERROR='Não foi possível executar cloudflared: '+str(e)
        print(TUNNEL_ERROR,flush=True)


def main():
    global PORT
    parser=argparse.ArgumentParser()
    parser.add_argument('--porta',type=int,default=8000)
    parser.add_argument('--sem-tunel',action='store_true')
    parser.add_argument('--sem-navegador',action='store_true')
    opts=parser.parse_args()
    if not 1024<=opts.porta<=65535:parser.error('porta fora da faixa 1024–65535')
    PORT=opts.porta
    try:serv=ThreadingHTTPServer(('0.0.0.0',PORT),Handler)
    except OSError as e:
        print('ERRO ao abrir porta:',e,flush=True)
        sys.exit(1)
    serv.daemon_threads=True
    print('\n'+'='*62,flush=True)
    print('VIGIMETRO - SERVIDOR INICIADO',flush=True)
    print(f'Rede carregada: {len(G.nos)} nós, {len(G.arestas)} arestas, {len(G.linhas)} linhas',flush=True)
    print('NESTE COMPUTADOR: http://localhost:%d'%PORT,flush=True)
    print('NO MESMO WI-FI: http://%s:%d'%(local_ip(),PORT),flush=True)
    print('EXTERNO (5G): aguardando geração do link...',flush=True)
    print('='*62+'\n',flush=True)
    threading.Thread(target=iniciar_tunel,args=(PORT,not opts.sem_tunel),daemon=True).start()
    if not opts.sem_navegador:
        try:webbrowser.open('http://localhost:%d'%PORT,new=2)
        except Exception as e:print('Não foi possível abrir o navegador:',e,flush=True)
    try:serv.serve_forever(poll_interval=.25)
    except KeyboardInterrupt:pass
    finally:
        serv.shutdown();serv.server_close()
        if TUNNEL is not None:
            TUNNEL.terminate()
            try:TUNNEL.wait(timeout=4)
            except subprocess.TimeoutExpired:TUNNEL.kill()
        print('VigiMetro encerrado.',flush=True)


if __name__=='__main__':main()
