export type CallFilter = "TODOS" | "ATIVOS" | "EM_ATENDIMENTO" | "ENCERRADO";
export type CallSummary = {
  total: number;
  active: number;
  attending: number;
  closed: number;
};

const metrics: {
  key: keyof CallSummary;
  filter: CallFilter;
  title: string;
  description: string;
  icon: string;
}[] = [
  { key: "total", filter: "TODOS", title: "Total de chamados", description: "Todos os registros", icon: "≡" },
  { key: "active", filter: "ATIVOS", title: "Ativos", description: "Abertos, em atendimento ou aguardando", icon: "◷" },
  { key: "attending", filter: "EM_ATENDIMENTO", title: "Em atendimento", description: "Atendimentos em andamento", icon: "↗" },
  { key: "closed", filter: "ENCERRADO", title: "Já atendidos", description: "Chamados encerrados", icon: "✓" },
];

export function Dashboard({ summary, filter, onFilter, loading }: {
  summary: CallSummary | null;
  filter: CallFilter;
  onFilter: (filter: CallFilter) => void;
  loading: boolean;
}) {
  const percentage = summary && summary.total > 0
    ? Math.round(summary.closed / summary.total * 100) : 0;
  return (
    <section className="dashboard" aria-label="Visão geral dos chamados" aria-busy={loading}>
      <div className="metric-grid">
        {metrics.map((metric) => (
          <button
            key={metric.key}
            className={`metric-card metric-${metric.key}`}
            aria-pressed={filter === metric.filter}
            disabled={loading}
            onClick={() => onFilter(metric.filter)}
          >
            <span className="metric-heading">{metric.title}<span className="metric-icon" aria-hidden="true">{metric.icon}</span></span>
            <strong>{summary ? summary[metric.key].toLocaleString("pt-BR") : "—"}</strong>
            <span className="metric-description">{metric.description}</span>
            <span className="metric-action">{filter === metric.filter ? "Exibindo na fila abaixo" : "Ver chamados →"}</span>
          </button>
        ))}
      </div>
      <div className="dashboard-progress card">
        <div>
          <h2>Panorama dos atendimentos</h2>
          <p>Os chamados em atendimento fazem parte dos ativos. Indicadores de todo o histórico.</p>
        </div>
        <div className="completion">
          <span><strong>{summary ? `${percentage}%` : "—"}</strong> encerrados</span>
          <div className="completion-track" role="progressbar" aria-label="Percentual de chamados encerrados" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percentage}>
            <div style={{ width: `${percentage}%` }} />
          </div>
          <small>{summary ? `${summary.closed.toLocaleString("pt-BR")} de ${summary.total.toLocaleString("pt-BR")} chamados` : "Aguardando dados"}</small>
        </div>
      </div>
    </section>
  );
}
