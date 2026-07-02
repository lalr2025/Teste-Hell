# ============================================================
# CÉLULA DE RECUPERAÇÃO — FINAL DA FASE 1 PDSI + ENSO
# ============================================================
# Cole esta célula DEPOIS de já terem sido definidas/carregadas no notebook:
#   da_pdsi, LAT_PDSI, LON_PDSI
#   df_enso_mapas
#   preparar_limites_para_mapa
#   plotar_mapa_pdsi_mes
#   gerar_mapas_picos_pdsi
#   DIR_GIF_PDSI, DIR_FRAMES_TMP, DIR_PICOS_PDSI
#
# Se você apagou o fim da célula original, esta célula substitui somente
# a parte final: prepara limites, gera o GIF e gera os mapas dos picos.
# ============================================================

from pathlib import Path
import sys

# ------------------------------------------------------------
# 1. Garantir que o Python encontre o módulo otimizado do repo
# ------------------------------------------------------------
# Se estiver rodando o notebook fora da pasta do repositório, ajuste REPO_DIR.
# Exemplos Windows:
# REPO_DIR = Path(r"D:\caminho\para\Teste-Hell")
# REPO_DIR = Path(r"C:\Users\seu_usuario\Teste-Hell")
try:
    REPO_DIR
except NameError:
    REPO_DIR = Path.cwd()

if str(REPO_DIR) not in sys.path:
    sys.path.append(str(REPO_DIR))

from scripts.pdsi_enso_maps_optimized import (
    preparar_limites_otimizado,
    gerar_gif_pdsi_otimizado,
)

# ------------------------------------------------------------
# 2. Conferir variáveis necessárias antes de iniciar processamento pesado
# ------------------------------------------------------------
variaveis_obrigatorias = [
    "da_pdsi",
    "LAT_PDSI",
    "LON_PDSI",
    "df_enso_mapas",
    "preparar_limites_para_mapa",
    "plotar_mapa_pdsi_mes",
    "gerar_mapas_picos_pdsi",
    "DIR_GIF_PDSI",
    "DIR_FRAMES_TMP",
    "DIR_PICOS_PDSI",
]

faltando = [nome for nome in variaveis_obrigatorias if nome not in globals()]
if faltando:
    raise NameError(
        "Antes de rodar esta célula, rode as células anteriores que definem: "
        + ", ".join(faltando)
    )

# ------------------------------------------------------------
# 3. Recalcular extent principal a partir do PDSI carregado
# ------------------------------------------------------------
extent_principal = [
    float(da_pdsi[LON_PDSI].min()),
    float(da_pdsi[LON_PDSI].max()),
    float(da_pdsi[LAT_PDSI].min()),
    float(da_pdsi[LAT_PDSI].max()),
]

print("Extent principal:", extent_principal)

# ------------------------------------------------------------
# 4. Preparar limites uma única vez, com simplificação geométrica
# ------------------------------------------------------------
limites_mapa = preparar_limites_otimizado(
    preparar_limites_para_mapa,
    extent_principal,
    tolerancia=0.01,
)

# ------------------------------------------------------------
# 5. Configuração rápida de desempenho
# ------------------------------------------------------------
# TESTE_RAPIDO = True gera menos frames para verificar se está tudo certo.
# Depois que validar, troque para False para gerar o GIF mensal completo.
TESTE_RAPIDO = True

if TESTE_RAPIDO:
    PASSO_MESES_GIF = 3   # trimestral: bem mais rápido para validar
    DPI_GIF = 65
    MAX_WORKERS_GIF = 2
else:
    PASSO_MESES_GIF = 1   # mensal completo
    DPI_GIF = 65          # use 80 se quiser mais qualidade
    MAX_WORKERS_GIF = 4

# Duração de cada frame na tela. Aumente para 1.0 ou 1.5 se quiser mais lento.
DURACAO_FRAME_SEGUNDOS = 0.50

print("Configuração do GIF:")
print("  TESTE_RAPIDO:", TESTE_RAPIDO)
print("  PASSO_MESES_GIF:", PASSO_MESES_GIF)
print("  DPI_GIF:", DPI_GIF)
print("  MAX_WORKERS_GIF:", MAX_WORKERS_GIF)
print("  DURACAO_FRAME_SEGUNDOS:", DURACAO_FRAME_SEGUNDOS)

# ------------------------------------------------------------
# 6. Gerar GIF PDSI + ENSO otimizado
# ------------------------------------------------------------
gif_pdsi = gerar_gif_pdsi_otimizado(
    da_pdsi=da_pdsi,
    df_enso=df_enso_mapas,
    limites=limites_mapa,
    plotar_mapa_pdsi_mes=plotar_mapa_pdsi_mes,
    dir_gif_pdsi=DIR_GIF_PDSI,
    dir_frames_tmp=DIR_FRAMES_TMP,
    passo_meses=PASSO_MESES_GIF,
    dpi=DPI_GIF,
    max_workers=MAX_WORKERS_GIF,
    frame_duration=DURACAO_FRAME_SEGUNDOS,
)

print("GIF gerado:", gif_pdsi)

# ------------------------------------------------------------
# 7. Gerar mapas estáticos dos meses de pico dos eventos ENSO
# ------------------------------------------------------------
tabela_picos_pdsi = gerar_mapas_picos_pdsi(
    da_pdsi,
    df_enso_mapas,
    limites_mapa,
    n=5,
)

try:
    display(tabela_picos_pdsi)
except NameError:
    print(tabela_picos_pdsi)

print("\nProdutos gerados:")
print("GIF:", gif_pdsi)
print("Pasta dos mapas de pico:", DIR_PICOS_PDSI)
