# VigiMetro

Alunos:
- Mateus Ribeiro Cerqueira - 10443901
- Pedro Henrique Carvalho Pereira - 10418861



## Como iniciar

Precisa de Python 3.10 ou mais recente para habilitar o acesso público por Cloudflare, instale o Cloudflared no terminal do Windows:

```bash
winget install --id Cloudflare.cloudflared --exact
```

Depois, na pasta do projeto dois cliques no **`iniciar.bat`**. O navegador vai abrir o site automaticamente.

**Sem Cloudflared:** abra a pasta no VS Code e execute no terminal:

```bash
python servidor.py --sem-tunel
```

Acesse **http://localhost:8000** no navegador.

## Organização do repositório

O projeto foi organizado em uma pasta principal: 
- `index.html` contém a interface em html do site; 
- `servidor.py` executa o servidor local e gerencia as requisições; 
- `grafo.py` implementa a leitura da malha e os algoritmos de caminhos e foi baseado no projeto de Teoria dos Grafos que também consta no repositório do projeto. O projeto base foi feito em java, mas adaptamos para python para ficar mais facil de controlar grafo/modelo e o servidor (centralizando em uma linguagem), deixando a parte visual em html; 
- `epidemiologia.py` contém o modelo de simulação; 
- `conexoes.csv`, `topologia_extra.csv` e `demanda_unificada.csv` armazenam os dados da rede e da demanda; 
- `validar.py` executa testes; 
- `iniciar.bat` e `iniciar.sh` iniciam o projeto no Windows e em sistemas com Bash. 
- Os arquivos `auditoria_conexoes_2026.csv`, complementam a atualização da malha metroferroviária, enquanto `qr_vendor.zip` fornece o recurso local de QR Code.

## Relatório de Uso de IA

Neste projeto, utilizamos Inteligencia Artificil como principal ajudante na implementação frontend em html. 

Para o index.html foi utilizado o Lovable + Figma para montar a interface do protótipo e, seguindo as heurísticas estudadas, fomos atualizando a interface através de prompts e manualmente.