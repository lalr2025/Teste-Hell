"""Funções otimizadas para mapas TerraClimate PDSI + ENSO.

Use este módulo para substituir o trecho lento/duplicado da célula original.
Ele evita regenerar o GIF duas vezes, reduz custo de geometria e permite
controlar resolução, passo temporal e quantidade de threads.
"""

from __future__ import annotations

import concurrent.futures
import multiprocessing
import shutil
from pathlib import Path

import imageio.v2 as imageio
import matplotlib.pyplot as plt
import pandas as pd


# Configurações rápidas para reduzir tempo de processamento.
# Ajuste conforme a qualidade desejada.
PDSI_GIF_FRAME_DURATION = 0.50
PDSI_ANIMACAO_PASSO_MESES = 1  # 1 = mensal; 3 = trimestral; 12 = anual
PDSI_DPI_GIF = 65             # menor que 80 acelera e reduz GIF
PDSI_MAX_WORKERS = max(1, min(4, multiprocessing.cpu_count() - 1))


def fase_e_intensidade_txt(categoria: str) -> tuple[str, str]:
    """Converte categoria ENSO interna para fase e intensidade em português."""
    categoria = str(categoria).strip()

    if categoria == "Neutro":
        return "Neutro", "Neutro"

    substituicoes = (
        ("El Nino", "El Niño"),
        ("La Nina", "La Niña"),
        ("El Niño", "El Niño"),
        ("La Niña", "La Niña"),
    )
    for prefixo, fase in substituicoes:
        if categoria.startswith(prefixo):
            return fase, categoria.replace(prefixo, "").strip()

    return categoria, ""


def simplificar_gdf_para_plot(gdf, tolerancia=0.01):
    """Simplifica geometrias mantendo colunas e CRS para acelerar o desenho."""
    if gdf is None or gdf.empty:
        return gdf

    gdf = gdf.copy()
    gdf["geometry"] = gdf.geometry.simplify(tolerancia, preserve_topology=True)
    return gdf


def preparar_limites_otimizado(preparar_limites_para_mapa, extent, tolerancia=0.01):
    """Reaproveita a função existente e simplifica os limites uma única vez."""
    limites = preparar_limites_para_mapa(extent)
    limites["uf_plot"] = simplificar_gdf_para_plot(limites.get("uf_plot"), tolerancia)
    limites["mun_plot"] = simplificar_gdf_para_plot(limites.get("mun_plot"), tolerancia)
    limites["guaxupe"] = simplificar_gdf_para_plot(limites.get("guaxupe"), tolerancia / 4)
    limites["guaxupe_buffer"] = simplificar_gdf_para_plot(limites.get("guaxupe_buffer"), tolerancia / 4)
    return limites


def datas_animacao_pdsi_rapida(da_pdsi, data_inicial=None, data_final=None, passo_meses=PDSI_ANIMACAO_PASSO_MESES):
    """Seleciona datas do GIF sem repetir meses e com opção de pular frames."""
    tempos = pd.DatetimeIndex(pd.to_datetime(da_pdsi["time"].values)).to_period("M").to_timestamp()
    datas = pd.Series(tempos).drop_duplicates().sort_values()

    if data_inicial is not None:
        datas = datas[datas >= pd.Timestamp(data_inicial)]
    if data_final is not None:
        datas = datas[datas <= pd.Timestamp(data_final)]
    if passo_meses and int(passo_meses) > 1:
        datas = datas.iloc[:: int(passo_meses)]

    return list(datas)


def gerar_gif_pdsi_otimizado(
    *,
    da_pdsi,
    df_enso,
    limites,
    plotar_mapa_pdsi_mes,
    dir_gif_pdsi: Path,
    dir_frames_tmp: Path,
    dpi: int = PDSI_DPI_GIF,
    frame_duration: float = PDSI_GIF_FRAME_DURATION,
    passo_meses: int = PDSI_ANIMACAO_PASSO_MESES,
    data_inicial=None,
    data_final=None,
    max_workers: int = PDSI_MAX_WORKERS,
):
    """Gera GIF PDSI de forma mais rápida e sem duplicar processamento.

    Principais correções em relação à célula original:
    - chama a geração do GIF uma única vez;
    - limita paralelismo para evitar travar Matplotlib/GeoPandas;
    - usa DPI menor no GIF, mantendo mapas estáticos em DPI maior se desejar;
    - monta o GIF depois que todos os frames existem, em ordem cronológica.
    """
    datas = datas_animacao_pdsi_rapida(da_pdsi, data_inicial, data_final, passo_meses)
    if not datas:
        raise ValueError("Nenhuma data selecionada para animação PDSI.")

    dir_gif_pdsi = Path(dir_gif_pdsi)
    dir_frames_tmp = Path(dir_frames_tmp)
    dir_gif_pdsi.mkdir(parents=True, exist_ok=True)

    data_ini = pd.Timestamp(datas[0]).strftime("%Y%m")
    data_fim = pd.Timestamp(datas[-1]).strftime("%Y%m")
    out_gif = dir_gif_pdsi / f"TerraClimate_PDSI_Guaxupe500km_ENSO_borda_{data_ini}_{data_fim}.gif"

    if dir_frames_tmp.exists():
        shutil.rmtree(dir_frames_tmp)
    dir_frames_tmp.mkdir(parents=True, exist_ok=True)

    args_list = [(i, data) for i, data in enumerate(datas, start=1)]
    max_workers = max(1, min(int(max_workers), len(args_list)))

    print(f"Gerando {len(datas)} frames PDSI com {max_workers} worker(s), dpi={dpi}, passo={passo_meses} mês(es)...")
    plt.ioff()

    def renderizar_frame(item):
        i, data = item
        frame = dir_frames_tmp / f"frame_pdsi_{i:04d}_{pd.Timestamp(data):%Y%m}.png"
        plotar_mapa_pdsi_mes(
            da_pdsi=da_pdsi,
            data=data,
            df_enso=df_enso,
            limites=limites,
            out_path=frame,
            dpi=dpi,
            fechar=True,
        )
        return frame

    try:
        if max_workers == 1:
            for item in args_list:
                renderizar_frame(item)
        else:
            with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
                for count, _ in enumerate(executor.map(renderizar_frame, args_list), start=1):
                    if count == 1 or count % 25 == 0 or count == len(args_list):
                        print(f"  {count}/{len(args_list)} frames gerados...")
    finally:
        plt.ion()

    print("Montando GIF final...")
    with imageio.get_writer(out_gif, mode="I", duration=float(frame_duration), loop=0) as writer:
        for i, data in args_list:
            frame = dir_frames_tmp / f"frame_pdsi_{i:04d}_{pd.Timestamp(data):%Y%m}.png"
            writer.append_data(imageio.imread(frame))

    shutil.rmtree(dir_frames_tmp, ignore_errors=True)
    print("GIF concluído:", out_gif)
    return out_gif


# Exemplo de uso no fim da célula original, substituindo as chamadas duplicadas:
# limites_mapa = preparar_limites_otimizado(preparar_limites_para_mapa, extent_principal, tolerancia=0.01)
# gif_pdsi = gerar_gif_pdsi_otimizado(
#     da_pdsi=da_pdsi,
#     df_enso=df_enso_mapas,
#     limites=limites_mapa,
#     plotar_mapa_pdsi_mes=plotar_mapa_pdsi_mes,
#     dir_gif_pdsi=DIR_GIF_PDSI,
#     dir_frames_tmp=DIR_FRAMES_TMP,
#     passo_meses=1,      # use 3 para testar rápido
#     dpi=65,
#     max_workers=4,
# )
# tabela_picos_pdsi = gerar_mapas_picos_pdsi(da_pdsi, df_enso_mapas, limites_mapa, n=5)
