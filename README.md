# Teste-Hell

Relatório georreferenciado.

## Otimização do GIF PDSI + ENSO

A célula original ficou lenta principalmente porque:

1. o GIF era gerado mais de uma vez no final da célula;
2. o `ThreadPoolExecutor` usava muitos workers para uma rotina pesada em Matplotlib/GeoPandas;
3. os limites municipais/estaduais eram desenhados em todos os frames com geometrias grandes;
4. o GIF mensal completo em DPI alto gera centenas de PNGs antes da montagem final.

O arquivo `scripts/pdsi_enso_maps_optimized.py` centraliza funções auxiliares para reduzir esse custo. Para usar no notebook, execute a célula original até carregar `da_pdsi`, `df_enso_mapas`, `extent_principal` e definir `plotar_mapa_pdsi_mes`. Depois substitua o trecho final por:

```python
from scripts.pdsi_enso_maps_optimized import (
    preparar_limites_otimizado,
    gerar_gif_pdsi_otimizado,
)

limites_mapa = preparar_limites_otimizado(
    preparar_limites_para_mapa,
    extent_principal,
    tolerancia=0.01,
)

gif_pdsi = gerar_gif_pdsi_otimizado(
    da_pdsi=da_pdsi,
    df_enso=df_enso_mapas,
    limites=limites_mapa,
    plotar_mapa_pdsi_mes=plotar_mapa_pdsi_mes,
    dir_gif_pdsi=DIR_GIF_PDSI,
    dir_frames_tmp=DIR_FRAMES_TMP,
    passo_meses=1,   # mensal; use 3 para teste rápido trimestral
    dpi=65,          # aumente para 80 se quiser mais qualidade
    max_workers=4,   # evite 32 workers com Matplotlib/GeoPandas
)

tabela_picos_pdsi = gerar_mapas_picos_pdsi(
    da_pdsi,
    df_enso_mapas,
    limites_mapa,
    n=5,
)
```

Para testar rapidamente antes de gerar o produto mensal completo, use `passo_meses=3` ou `passo_meses=12`.

## Célula de recuperação se o final do notebook foi apagado

Se o fim da célula original foi apagado, use `notebook_cells/pdsi_enso_recovery_tail.py` como célula de recuperação. Ela deve ser colada depois das células que já carregaram `da_pdsi`, `df_enso_mapas` e definiram as funções de plotagem. A célula começa em modo `TESTE_RAPIDO = True`, com `passo_meses=3`, para validar o resultado antes do GIF mensal completo.
