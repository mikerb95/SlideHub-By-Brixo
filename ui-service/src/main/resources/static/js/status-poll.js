(function () {
    const POLL_MS = Math.max(1000, Math.min(3000, Number(window.statusConfig?.pollIntervalMs || 2000)));
    const tbody = document.getElementById('checks-body');
    const errorBox = document.getElementById('status-error');
    const pollText = document.getElementById('poll-interval');
    const lastUpdatedText = document.getElementById('last-updated');
    const diagnosticModal = document.getElementById('diagnostic-modal');
    const latencyChartCanvas = document.getElementById('diag-latency-chart');
    const latencyChartEmpty = document.getElementById('diag-chart-empty');
    const latencyChartMeta = document.getElementById('diag-chart-meta');

    let checksCache = [];
    let activeDiagnosticService = null;
    const latencyHistoryByService = new Map();
    const HISTORY_LIMIT = 36;

    pollText.textContent = String(POLL_MS);

    function formatDate(value) {
        if (!value) return '--';
        const d = new Date(value);
        if (Number.isNaN(d.getTime())) return '--';
        return d.toLocaleTimeString();
    }

    function badge(status) {
        const ok = status === 'ok';
        const bg = ok ? 'var(--sh-green)' : 'var(--sh-red)';
        const fg = ok ? 'var(--sh-deep)' : '#fff';
        return `<span style="display:inline-block;padding:2px 8px;border-radius:999px;background:${bg};color:${fg};font-size:11px;font-family:var(--sh-font-mono);font-weight:700;">${status.toUpperCase()}</span>`;
    }

    function renderRows(checks) {
        if (!checks || checks.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="py-4 text-[var(--sh-text-3)] font-mono text-xs">Sin datos</td></tr>';
            return;
        }

        updateLatencyHistory(checks);
        checksCache = checks;

        tbody.innerHTML = checks.map((check) => {
            const latency = check.latencyMs === null || check.latencyMs === undefined ? '--' : check.latencyMs;
            const detail = (check.detail || '--').replace(/</g, '&lt;').replace(/>/g, '&gt;');
            return `
                <tr style="border-bottom:1px solid var(--sh-border);">
                    <td class="py-2 text-[var(--sh-text-2)] font-mono text-xs">${check.name}</td>
                    <td class="py-2">${badge(check.status)}</td>
                    <td class="py-2 text-[var(--sh-text-2)] font-mono text-xs">${latency}</td>
                    <td class="py-2 text-[var(--sh-text-3)] font-mono text-xs">${formatDate(check.lastCheckedAt)}</td>
                    <td class="py-2 text-[var(--sh-text-3)] font-mono text-xs">
                        <div>${detail}</div>
                        <button type="button"
                            data-service-name="${escapeHtmlAttr(check.name)}"
                            class="sh-btn sh-btn-ghost text-[0.65rem] py-0.5 px-2 mt-1"
                            style="border-color: var(--sh-border-subtle);"
                        >
                            <i class="fa-solid fa-circle-info"></i> Diagnóstico
                        </button>
                    </td>
                </tr>
            `;
        }).join('');

        if (activeDiagnosticService && diagnosticModal && !diagnosticModal.classList.contains('hidden')) {
            const current = checksCache.find((item) => item.name === activeDiagnosticService);
            if (current) {
                openDiagnostic(current);
            }
        }
    }

    function updateLatencyHistory(checks) {
        checks.forEach((check) => {
            const serviceName = check.name || '--';
            const latency = Number(check.latencyMs);
            const validLatency = Number.isFinite(latency) ? latency : null;
            const timestamp = check.lastCheckedAt ? new Date(check.lastCheckedAt).getTime() : Date.now();

            const current = latencyHistoryByService.get(serviceName) || [];
            current.push({
                latencyMs: validLatency,
                status: check.status || '--',
                time: Number.isFinite(timestamp) ? timestamp : Date.now()
            });

            if (current.length > HISTORY_LIMIT) {
                current.splice(0, current.length - HISTORY_LIMIT);
            }

            latencyHistoryByService.set(serviceName, current);
        });
    }

    function resizeCanvasToDisplaySize(canvas) {
        if (!canvas) return;
        const ratio = window.devicePixelRatio || 1;
        const displayWidth = Math.max(1, Math.floor(canvas.clientWidth));
        const displayHeight = Math.max(1, Math.floor(canvas.clientHeight));
        const width = Math.floor(displayWidth * ratio);
        const height = Math.floor(displayHeight * ratio);

        if (canvas.width !== width || canvas.height !== height) {
            canvas.width = width;
            canvas.height = height;
        }
    }

    function drawLatencyChart(serviceName) {
        if (!latencyChartCanvas) {
            return;
        }

        resizeCanvasToDisplaySize(latencyChartCanvas);

        const ctx = latencyChartCanvas.getContext('2d');
        if (!ctx) {
            return;
        }

        const history = latencyHistoryByService.get(serviceName) || [];
        const points = history.filter((entry) => Number.isFinite(entry.latencyMs));

        if (latencyChartEmpty) {
            latencyChartEmpty.style.display = points.length >= 2 ? 'none' : 'flex';
        }

        if (latencyChartMeta) {
            if (points.length === 0) {
                latencyChartMeta.textContent = 'Sin muestras';
            } else {
                const max = Math.max(...points.map((p) => p.latencyMs));
                const min = Math.min(...points.map((p) => p.latencyMs));
                latencyChartMeta.textContent = `min ${Math.round(min)}ms · max ${Math.round(max)}ms · ${points.length} muestras`;
            }
        }

        const width = latencyChartCanvas.width;
        const height = latencyChartCanvas.height;
        const ratio = window.devicePixelRatio || 1;

        ctx.setTransform(1, 0, 0, 1, 0, 0);
        ctx.clearRect(0, 0, width, height);
        ctx.scale(ratio, ratio);

        const drawWidth = width / ratio;
        const drawHeight = height / ratio;
        const padding = { top: 16, right: 10, bottom: 22, left: 36 };
        const chartWidth = Math.max(1, drawWidth - padding.left - padding.right);
        const chartHeight = Math.max(1, drawHeight - padding.top - padding.bottom);

        ctx.strokeStyle = 'rgba(110, 118, 129, 0.22)';
        ctx.lineWidth = 1;
        for (let i = 0; i <= 4; i += 1) {
            const y = padding.top + (chartHeight / 4) * i;
            ctx.beginPath();
            ctx.moveTo(padding.left, y);
            ctx.lineTo(padding.left + chartWidth, y);
            ctx.stroke();
        }

        if (points.length < 2) {
            return;
        }

        const maxLatency = Math.max(...points.map((p) => p.latencyMs), 1);
        const minLatency = Math.min(...points.map((p) => p.latencyMs), 0);
        const latencyRange = Math.max(1, maxLatency - minLatency);

        const xy = points.map((entry, index) => {
            const x = padding.left + (chartWidth * index) / Math.max(1, points.length - 1);
            const normalized = (entry.latencyMs - minLatency) / latencyRange;
            const y = padding.top + chartHeight - normalized * chartHeight;
            return { x, y, latencyMs: entry.latencyMs };
        });

        const areaGradient = ctx.createLinearGradient(0, padding.top, 0, padding.top + chartHeight);
        areaGradient.addColorStop(0, 'rgba(88, 166, 255, 0.28)');
        areaGradient.addColorStop(1, 'rgba(88, 166, 255, 0.02)');

        ctx.beginPath();
        ctx.moveTo(xy[0].x, padding.top + chartHeight);
        xy.forEach((point) => ctx.lineTo(point.x, point.y));
        ctx.lineTo(xy[xy.length - 1].x, padding.top + chartHeight);
        ctx.closePath();
        ctx.fillStyle = areaGradient;
        ctx.fill();

        ctx.beginPath();
        ctx.moveTo(xy[0].x, xy[0].y);
        for (let i = 1; i < xy.length; i += 1) {
            ctx.lineTo(xy[i].x, xy[i].y);
        }
        ctx.strokeStyle = 'rgba(88, 166, 255, 0.95)';
        ctx.lineWidth = 2;
        ctx.stroke();

        const lastPoint = xy[xy.length - 1];
        ctx.beginPath();
        ctx.arc(lastPoint.x, lastPoint.y, 3.5, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(88, 166, 255, 1)';
        ctx.fill();

        ctx.fillStyle = 'rgba(201, 209, 217, 0.85)';
        ctx.font = '11px var(--sh-font-mono)';
        ctx.fillText(`${Math.round(maxLatency)}ms`, 4, padding.top + 4);
        ctx.fillText(`${Math.round(minLatency)}ms`, 4, padding.top + chartHeight);
    }

    function escapeHtmlAttr(value) {
        return String(value || '')
            .replace(/&/g, '&amp;')
            .replace(/"/g, '&quot;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    function parseConnectionProps(check) {
        const detail = check.detail || '';

        if (detail.startsWith('GET ')) {
            const target = detail.split(' -> ')[0].replace('GET ', '').trim();
            const host = target.includes('/') ? target.split('/')[0] : target;
            const path = target.includes('/') ? target.substring(target.indexOf('/')) : '/';
            return {
                protocol: 'HTTP',
                target,
                host,
                path,
                env: expectedEnvByService(check.name)
            };
        }

        if (detail.startsWith('TCP ')) {
            const target = detail.split(' -> ')[0].replace('TCP ', '').trim();
            const [host, port] = target.split(':');
            return {
                protocol: 'TCP',
                target,
                host: host || '--',
                port: port || '--',
                env: expectedEnvByService(check.name)
            };
        }

        return {
            protocol: '--',
            target: '--',
            host: '--',
            env: expectedEnvByService(check.name)
        };
    }

    function expectedEnvByService(serviceName) {
        const map = {
            'state-service': 'STATE_SERVICE_URL',
            'ai-service': 'AI_SERVICE_URL',
            gateway: 'GATEWAY_URL',
            render: 'RENDER_SERVICE_URL',
            redis: 'REDIS_HOST / REDIS_PORT',
            mongodb: 'MONGODB_URI',
            postgres: 'DATABASE_URL',
            'aws-s3': 'AWS_S3_BUCKET / AWS_REGION'
        };
        return map[serviceName] || '--';
    }

    function deriveDiagnosis(check) {
        const detail = (check.detail || '').toLowerCase();
        const causes = [];
        const fixes = [];
        let severity = 'info';
        let impact = 'Sin impacto operativo detectado.';

        if (detail.includes('not configured')) {
            causes.push('La variable de entorno requerida no está definida en este servicio.');
            fixes.push(`Configura ${expectedEnvByService(check.name)} y reinicia el servicio.`);
            severity = 'warn';
            impact = 'Monitoreo incompleto: este check no puede validar conectividad real.';
            return { causes, fixes, severity, impact };
        }

        if (detail.includes('http 429')) {
            causes.push('Rate limit del proveedor externo o servicio temporalmente saturado.');
            fixes.push('Reintentar con backoff (ya implementado) y validar cuota del proveedor.');
            fixes.push('Usar token/API key con mayor cuota si aplica.');
            severity = 'warn';
            impact = 'Degradación parcial: operaciones con IA o API externa pueden fallar intermitentemente.';
        }

        if (detail.includes('connectexception')) {
            causes.push('El host está caído, dormido o la URL/puerto no es alcanzable.');
            fixes.push('Verifica URL/puerto y que el servicio esté levantado.');
            fixes.push('En Render free tier, confirma keep-alive y tráfico reciente.');
            severity = 'error';
            impact = 'Impacto alto: dependencia no alcanzable; funcionalidades relacionadas están caídas.';
        }

        if (detail.includes('unknownhostexception')) {
            causes.push('Hostname inválido o DNS no resolvible desde el contenedor.');
            fixes.push('Corrige el hostname en variables de entorno y vuelve a desplegar.');
            severity = 'error';
            impact = 'Impacto alto: resolución DNS fallida; no hay conexión posible al servicio.';
        }

        if (detail.includes('timedout') || detail.includes('timeout')) {
            causes.push('Tiempo de respuesta excedido por latencia de red o servicio lento.');
            fixes.push('Aumenta timeout de health-check o reduce carga del servicio destino.');
            if (severity !== 'error') {
                severity = 'warn';
            }
            impact = 'Impacto medio: lentitud o fallos por timeout en operaciones dependientes.';
        }

        if (detail.includes('http 5')) {
            causes.push('El servicio respondió error interno (5xx).');
            fixes.push('Revisar logs del servicio destino para stacktrace y dependencia fallida.');
            severity = 'error';
            impact = 'Impacto alto: el servicio destino está fallando internamente.';
        }

        if (check.status === 'ok') {
            causes.push('Conectividad y respuesta correctas para el objetivo.');
            fixes.push('Sin acción requerida. Monitorear tendencia de latencia.');
            severity = 'info';
            impact = 'Sin impacto actual. Servicio operativo.';

            if (check.latencyMs !== null && check.latencyMs !== undefined && check.latencyMs > 1200) {
                severity = 'warn';
                impact = 'Servicio disponible pero con latencia alta; podría afectar UX.';
                causes.push('Latencia superior al umbral recomendado (>1200 ms).');
                fixes.push('Revisar región, red y carga del servicio para bajar latencia.');
            }
        }

        if (causes.length === 0) {
            causes.push('Fallo no clasificado automáticamente.');
            fixes.push('Inspeccionar logs del servicio y validar variables de entorno asociadas.');
            severity = check.status === 'ok' ? 'info' : 'warn';
            impact = check.status === 'ok'
                ? 'Sin impacto observable por ahora.'
                : 'Impacto indeterminado hasta revisar logs detallados.';
        }

        if (check.status !== 'ok' && severity === 'info') {
            severity = 'warn';
            impact = 'Servicio en estado DOWN; revisar causa raíz en logs.';
        }

        return { causes, fixes, severity, impact };
    }

    function rowItem(label, value) {
        return `<div class="diag-item"><strong>${label}</strong><span>${escapeHtml(value)}</span></div>`;
    }

    function escapeHtml(value) {
        return String(value || '--')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    function openDiagnostic(check) {
        if (!diagnosticModal) {
            return;
        }

        activeDiagnosticService = check.name || null;

        const title = document.getElementById('diag-title');
        const connection = document.getElementById('diag-connection');
        const latency = document.getElementById('diag-latency');
        const causes = document.getElementById('diag-causes');
        const fixes = document.getElementById('diag-fixes');
        const severityBadge = document.getElementById('diag-severity');
        const impactText = document.getElementById('diag-impact');

        const props = parseConnectionProps(check);
        const analysis = deriveDiagnosis(check);

        title.textContent = check.name;

        connection.innerHTML = [
            rowItem('Protocol', props.protocol),
            rowItem('Target', props.target || '--'),
            rowItem('Host', props.host || '--'),
            rowItem('Path/Port', props.path || props.port || '--'),
            rowItem('Config', props.env || '--')
        ].join('');

        latency.innerHTML = [
            rowItem('Status', (check.status || '--').toUpperCase()),
            rowItem('Latency (ms)', check.latencyMs === null || check.latencyMs === undefined ? '--' : String(check.latencyMs)),
            rowItem('Last Check', formatDate(check.lastCheckedAt)),
            rowItem('Detail', check.detail || '--')
        ].join('');

        const severityClass = analysis.severity === 'error'
            ? 'diag-severity diag-severity-error'
            : analysis.severity === 'warn'
                ? 'diag-severity diag-severity-warn'
                : 'diag-severity diag-severity-info';

        if (severityBadge) {
            severityBadge.className = severityClass;
            severityBadge.textContent = analysis.severity.toUpperCase();
        }

        if (impactText) {
            impactText.textContent = analysis.impact;
        }

        causes.innerHTML = analysis.causes.map((c) => `<li>${escapeHtml(c)}</li>`).join('');
        fixes.innerHTML = analysis.fixes.map((f) => `<li>${escapeHtml(f)}</li>`).join('');

        drawLatencyChart(check.name);

        diagnosticModal.classList.remove('hidden');
    }

    function closeDiagnostic() {
        if (diagnosticModal) {
            diagnosticModal.classList.add('hidden');
        }
        activeDiagnosticService = null;
    }

    tbody.addEventListener('click', (event) => {
        const btn = event.target.closest('button[data-service-name]');
        if (!btn) {
            return;
        }

        const serviceName = btn.getAttribute('data-service-name');
        const check = checksCache.find((item) => item.name === serviceName);
        if (check) {
            openDiagnostic(check);
        }
    });

    window.closeStatusDiagnostic = closeDiagnostic;

    function sanitizeText(value) {
        return String(value || '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    function snippet(value, max = 160) {
        const clean = sanitizeText(value);
        return clean.length > max ? clean.slice(0, max) + '…' : clean;
    }

    async function loadChecks() {
        try {
            errorBox.classList.add('hidden');
            const response = await fetch('/status/api/checks', { cache: 'no-store' });
            const contentType = response.headers.get('content-type') || 'unknown';
            const responseText = await response.text();

            if (!response.ok) {
                throw new Error(`HTTP ${response.status} · content-type=${contentType} · body=${snippet(responseText)}`);
            }

            if (!contentType.toLowerCase().includes('application/json')) {
                throw new Error(`Expected JSON but got ${contentType} · body=${snippet(responseText)}`);
            }

            let payload;
            try {
                payload = JSON.parse(responseText);
            } catch (parseError) {
                throw new Error(`Invalid JSON (${parseError.message}) · body=${snippet(responseText)}`);
            }

            renderRows(payload.checks || []);
            lastUpdatedText.textContent = formatDate(payload.generatedAt);
        } catch (err) {
            errorBox.textContent = 'Error cargando verificaciones: ' + (err.message || 'desconocido');
            errorBox.classList.remove('hidden');
        }
    }

    loadChecks();
    setInterval(loadChecks, POLL_MS);
})();
