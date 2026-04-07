# Render Log Automation

Este proyecto incluye un analizador local para los logs de Render.

## Qué hace

- Lee los nombres de servicios desde [render.yaml](../render.yaml).
- Resuelve los servicios contra la API de Render.
- Descarga logs del rango de tiempo pedido.
- Detecta patrones de error comunes.
- Agrupa fallas repetidas para evitar scroll manual interminable.
- Genera un JSON en `target/render-logs/`.

## Requisitos

- `RENDER_API_KEY`: API key de Render.
- `RENDER_OWNER_ID`: ID del workspace/owner de Render.

## Uso

Solo deploys fallidos de la última hora:

```bash
python3 scripts/render-log-analyzer.py --minutes 60 --deploy-only
```

Build logs + errores probables:

```bash
python3 scripts/render-log-analyzer.py --minutes 60 --log-type build
```

Logs generales:

```bash
python3 scripts/render-log-analyzer.py --minutes 60
```

Opcionalmente puedes filtrar servicios:

```bash
python3 scripts/render-log-analyzer.py --services slidehub-ui slidehub-ai
```

## Salida

El script imprime:

- número de logs analizados por servicio,
- deploys fallidos detectados,
- cantidad de errores probables,
- firmas agrupadas de error,
- un artefacto JSON reproducible.

## Notas

- El analizador usa heurísticas; no sustituye la observabilidad completa.
- Si Render devuelve errores de permisos o rate limit, revisa las variables de entorno o reduce la ventana temporal.
