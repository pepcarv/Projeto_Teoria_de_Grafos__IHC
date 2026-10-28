"""Validação da topologia operacional (2026), preservação de demandas e algoritmos.
O benchmark numérico do Java original NÃO é válido para outro grafo.
"""
import csv
import hashlib
import unittest
import xml.etree.ElementTree as ET
from collections import deque
from pathlib import Path
from grafo import Grafo, chave
from epidemiologia import CENARIOS, PROTECOES, percurso, simular, JavaRandom

BASE=Path(__file__).resolve().parent

def no(g,linha,estacao):
    return next(n['id'] for n in g.nos if n['linha_chave']=='L'+str(linha) and n['chave']==chave(estacao))

def liga(g,i,j):
    return next((b for v,b in g.adj[i] if v==j),None)

class TestVigiMetro(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.g=Grafo(BASE)

    def test_rede_topologica_2026(self):
        g=self.g
        self.assertEqual((len(g.nos),len(g.arestas),len(g.linhas)),(203,231,13))
        self.assertEqual(sum(1 for _,_,b in g.arestas if b),41)
        self.assertEqual(len(g.avisos),0)
        self.assertFalse(any(n['agregado'] for n in g.nos))

    def test_demanda_original_integra(self):
        velho=(BASE/'referencia_original_2026_10_28'/'demanda_unificada.csv').read_bytes()
        novo=(BASE/'demanda_unificada.csv').read_bytes()
        self.assertEqual(hashlib.sha256(velho).hexdigest(),hashlib.sha256(novo).hexdigest())

    def test_conexoes_explicitas(self):
        g=self.g
        with (BASE/'conexoes.csv').open(encoding='utf-8',newline='') as f:
            rec=list(csv.DictReader(f,delimiter=';'))
        self.assertEqual(len(rec),41)
        for e in rec:
            a=no(g,int(e['linhaA'].split()[-1]),e['estacaoA'])
            b=no(g,int(e['linhaB'].split()[-1]),e['estacaoB'])
            self.assertTrue(liga(g,a,b),str(e))
        transferencias={(min(a,b),max(a,b)) for a,b,t in g.arestas if t}
        declaradas={
            (min(no(g,int(e['linhaA'].split()[-1]),e['estacaoA']),no(g,int(e['linhaB'].split()[-1]),e['estacaoB'])),
             max(no(g,int(e['linhaA'].split()[-1]),e['estacaoA']),no(g,int(e['linhaB'].split()[-1]),e['estacaoB']))) for e in rec
        }
        self.assertEqual(transferencias,declaradas)

    def test_situacoes_importantes(self):
        g=self.g
        for la,ea,lb,eb in [(2,'Consolação',4,'Paulista'),(1,'Sé',3,'Sé'),
                             (1,'Ana Rosa',2,'Ana Rosa'),(1,'Paraíso',2,'Paraíso'),
                             (4,'Pinheiros',9,'Pinheiros'),(5,'Santo Amaro',9,'Santo Amaro'),
                             (11,'Calmon Viana',12,'Calmon Viana'),
                             (12,'Engenheiro Goulart',13,'Engenheiro Goulart'),
                             (8,'Presidente Altino',9,'Presidente Altino'),
                             (8,'Osasco',9,'Osasco')]:
            a,b=no(g,la,ea),no(g,lb,eb)
            self.assertNotEqual(a,b)
            self.assertTrue(liga(g,a,b))
        # Duas Lapas distintas: não há transferência interna entre elas.
        self.assertIsNone(liga(g,no(g,7,'Lapa'),no(g,8,'Lapa')))
        # Em 2026, L7 não chega à Luz (não criar transferência antiga).
        self.assertFalse(any(n['linha_chave']=='L7' and n['chave']=='luz' for n in g.nos))

    def test_rede_conexa_e_linha_sequencial(self):
        g=self.g
        vistos={0};fila=deque([0])
        while fila:
            a=fila.popleft()
            for b,_ in g.adj[a]:
                if b not in vistos: vistos.add(b);fila.append(b)
        self.assertEqual(len(vistos),len(g.nos))
        for ids in g.linhas.values():
            for a,b in zip(ids,ids[1:]): self.assertIs(liga(g,a,b),False)

    def test_bfs_dijkstra_e_percurso(self):
        g=self.g;a=no(g,1,'Jabaquara');b=no(g,13,'Aeroporto-Guarulhos')
        bfs=g.bfs(a,b); dij=g.dijkstra(a,b,0,0,3,5)
        for path in [bfs,dij]:
            self.assertEqual(path[0],a);self.assertEqual(path[-1],b)
            self.assertTrue(all(liga(g,i,j) is not None for i,j in zip(path,path[1:])))
        r=percurso(g,bfs,0,0,CENARIOS[1],PROTECOES[1],1,3,5)
        self.assertGreaterEqual(r['total'],0)
        self.assertLessEqual(r['total'],1)

    def test_monte_carlo_rodabilidade(self):
        cfg={'mes':6,'mobilidade':1,'cenario':3,'dias':4,'execucoes':3,
             'sementes':[no(self.g,1,'Sé')],'seed':100,'odds_ratio':.15,
             'isolamento':.45,'pop_escala':1500,'infectados':3}
        r=simular(self.g,cfg)
        self.assertEqual(len(r['I']),5)
        self.assertEqual(len(r['I_por_no']),5)
        self.assertTrue(all(len(dia)==203 for dia in r['I_por_no']))

    def test_random_java_e_qr(self):
        self.assertAlmostEqual(JavaRandom(42).random(),.7275636800328681,places=15)
        from servidor import qr_svg,BOOT
        body=qr_svg('https://vigimetro.example.trycloudflare.com')
        root=ET.fromstring(body)
        self.assertTrue(root.tag.endswith('svg'))
        self.assertEqual(len(BOOT['nos']),203)
        self.assertEqual(len(BOOT['arestas']),231)
        self.assertIn('viewBox',root.attrib)

if __name__=='__main__':unittest.main(verbosity=2)
