# -*- coding: utf-8 -*-
"""Testes offline de regressão contra a implementação Java original.
Execute: python validar.py
Valores esperados coletados pela execução direta do Java, não inventados.
"""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from grafo import Grafo
from epidemiologia import CENARIOS, PROTECOES, percurso, simular, JavaRandom

BASE=Path(__file__).resolve().parent

class TestVigiMetro(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.g=Grafo(BASE)
    def test_topologia_java(self):
        self.assertEqual(len(self.g.nos),143)
        self.assertEqual(len(self.g.arestas),159)
        self.assertEqual(len(self.g.linhas),13)
    def test_raio_espectral_java(self):
        self.assertAlmostEqual(self.g.rede(0,0)['rho'],1.4309059868201213,places=11)
        self.assertAlmostEqual(self.g.beta(CENARIOS[1],0,0),.295073349410271,places=11)
    def test_caminhos_java(self):
        expected=[10,11,12,13,14,66,119,118,117,116,115,114,113,112,111,110]
        self.assertEqual(self.g.bfs(10,110),expected)
        self.assertEqual(self.g.dijkstra(10,110,0,0,3,5),expected)
    def test_percurso_java(self):
        r=percurso(self.g,self.g.bfs(10,110),0,0,CENARIOS[1],PROTECOES[1],1,3,5)
        self.assertAlmostEqual(r['total'],.0007548604067598719,places=12)
        self.assertEqual(len(r['etapas']),16)
    def test_monte_carlo_java(self):
        c={'mes':0,'mobilidade':0,'cenario':1,'dias':14,'execucoes':5,'sementes':[10],
           'infectados':5,'pop_escala':2000,'isolamento':0,'odds_ratio':1,'seed':42}
        r=simular(self.g,c)
        expected_i=[5.,4.4,3.8,3.6,2.8,2.4,2.8,2.4,2.4,2.4,2.2,2.,1.6,2.2,2.4]
        expected_alc=[1,1.4,1.4,1.6,1.8,2.2,2.2,2.2,2.2,2.4,2.4,2.6,3.,3.,3.]
        for a,b in zip(r['I'],expected_i):self.assertAlmostEqual(a,b,places=11)
        for a,b in zip(r['alcance'],expected_alc):self.assertAlmostEqual(a,b,places=11)
        self.assertEqual(len(r['I_por_no']),15)
    def test_monte_carlo_java_cenario_omicron_mobilidade_1(self):
        c={'mes':6,'mobilidade':1,'cenario':3,'dias':8,'execucoes':4,
           'sementes':[65,70],'seed':100,'odds_ratio':.15,
           'isolamento':.45,'pop_escala':1500,'infectados':3}
        r=simular(self.g,c)
        self.assertAlmostEqual(r['rho'],.5365648403727941,places=11)
        self.assertEqual(r['I'],[6,5.75,5.25,4.75,3.75,3.75,3,2.75,2.25])
        self.assertEqual(r['alcance'],[2]*9)

    def test_random_java(self):
        rng=JavaRandom(42)
        self.assertAlmostEqual(rng.random(),.7275636800328681,places=15)
    def test_qr_code_svg(self):
        from servidor import qr_svg
        body=qr_svg('https://vigilmetro.example.trycloudflare.com')
        root=ET.fromstring(body)
        self.assertTrue(root.tag.endswith('svg'))
        self.assertIn('viewBox',root.attrib)
    def test_centralidade(self):
        bt=self.g.betweenness()
        self.assertEqual(len(bt),143)
        self.assertTrue(all(v>=0 for v in bt))
    def test_api_entrada_invalida(self):
        from servidor import api_route,api_sim
        with self.assertRaises(ValueError):api_route({'origem':-1,'destino':5})
        with self.assertRaises(ValueError):api_sim({'sementes':[0],'dias':999})

if __name__=='__main__':unittest.main(verbosity=2)
