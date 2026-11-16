import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import "./App.css";
import { Dashboard, type CallFilter, type CallSummary } from "./Dashboard";

type Chamado = {
  id: number;
  resumo: string | null;
  descricao: string | null;
  status: string;
  prioridadeFinal: string | null;
  prioridadeSugerida: string | null;
};
type Resultado = {
  prioridade_recomendada: string;
  prioridade_base: string;
  prioridade_matriz?: string;
  metodo_prioridade?: string;
  confianca_prioridade?: number | null;
  impacto: string;
  urgencia: string;
  origem: string;
  confianca_impacto: number | null;
  confianca_urgencia: number | null;
  modelo_sintetico: boolean;
  baixa_confianca: boolean;
  versao_modelo: string | null;
  versao_matriz: string;
  versao_regras: string;
  regras_aplicadas: { id: string; justificativa: string }[];
};
type Triagem = {
  id: number;
  estado: string;
  resultado: Resultado | null;
  erro: string | null;
  criadaEm: string;
  decididaEm: string | null;
  decisorId: number | null;
  prioridadeFinal: string | null;
  justificativa: string | null;
  feedbackTreinamento?: { estado: string; tentativas: number } | null;
};
type Sessao = {
  token: string;
  tecnico: { nome: string; perfil: string; equipe: string };
};

async function api<T>(
  path: string,
  token: string | null,
  method = "GET",
  body?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  const response = await fetch(`/api${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    signal: signal
      ? AbortSignal.any([signal, AbortSignal.timeout(30000)])
      : AbortSignal.timeout(30000),
  });
  if (!response.ok) {
    if (response.status === 401 && token)
      window.dispatchEvent(new Event("session-expired"));
    const messages: Record<number, string> = {
      400: "Confira os campos informados.",
      401: "Credenciais inválidas ou sessão expirada.",
      403: "Você não tem permissão para este chamado.",
      404: "Registro não encontrado.",
      409: "O chamado ou a sugestão mudou. Atualize antes de decidir.",
      502: "Não foi possível consultar a IA.",
      503: "IA ou modelo indisponível. Informe impacto e urgência ou continue o atendimento manual.",
      504: "Tempo limite da IA. Consulte o histórico antes de tentar novamente.",
    };
    throw new Error(
      messages[response.status] ?? `Falha na operação (${response.status}).`,
    );
  }
  return response.status === 204
    ? (undefined as T)
    : (response.json() as Promise<T>);
}
const message = (e: unknown) =>
  e instanceof Error && e.name === "TimeoutError"
    ? "Tempo limite. Consulte os dados antes de repetir a operação."
    : e instanceof Error
      ? e.message
      : "Falha de comunicação.";
const label = (v: string | null) =>
  v ? v.replaceAll("_", " ") : "Não definida";
const confidence = (v: number | null) =>
  v === null ? "informada" : `${Math.round(v * 100)}%`;

function Review({
  chamado,
  token,
  onChanged,
}: {
  chamado: Chamado;
  token: string;
  onChanged: () => void;
}) {
  const [history, setHistory] = useState<Triagem[]>([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [priority, setPriority] = useState("MEDIA");
  const [reason, setReason] = useState("");
  const latest = history[0];
  const closed = chamado.status === "ENCERRADO";
  const refresh = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      try {
        setHistory(
          await api<Triagem[]>(
            `/chamados/${chamado.id}/triagens-prioridade`,
            token,
            "GET",
            undefined,
            signal,
          ),
        );
      } catch (e) {
        if (!signal?.aborted) setError(message(e));
      } finally {
        if (!signal?.aborted) setLoading(false);
      }
    },
    [chamado.id, token],
  );
  useEffect(() => {
    const controller = new AbortController();
    api<Triagem[]>(
      `/chamados/${chamado.id}/triagens-prioridade`,
      token,
      "GET",
      undefined,
      controller.signal,
    )
      .then((data) => {
        if (!controller.signal.aborted) setHistory(data);
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(message(e));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [chamado.id, token]);
  async function analyze(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const body = {
        ...(data.get("impacto")
          ? { impactoInformado: String(data.get("impacto")) }
          : {}),
        ...(data.get("urgencia")
          ? { urgenciaInformada: String(data.get("urgencia")) }
          : {}),
        contexto: {
          ...(data.get("cargo") ? { cargo: String(data.get("cargo")) } : {}),
          ...(data.get("criticidade")
            ? { criticidadeServico: String(data.get("criticidade")) }
            : {}),
          confirmado: data.get("confirmado") === "on",
        },
      };
      await api(
        `/chamados/${chamado.id}/triagens-prioridade`,
        token,
        "POST",
        body,
      );
      setNotice(
        "Análise registrada. Revise a recomendação antes de aplicá-la.",
      );
      onChanged();
    } catch (e) {
      setError(message(e));
    } finally {
      await refresh();
      setBusy(false);
    }
  }
  async function decide(action: "ACEITAR" | "CORRIGIR") {
    if (!latest) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await api(
        `/chamados/${chamado.id}/triagens-prioridade/${latest.id}/decisao`,
        token,
        "POST",
        {
          acao: action,
          ...(action === "CORRIGIR"
            ? { prioridadeFinal: priority, justificativa: reason }
            : {}),
        },
      );
      setNotice("Decisão salva com autoria e histórico.");
      setReason("");
      onChanged();
    } catch (e) {
      setError(message(e));
    } finally {
      await refresh();
      setBusy(false);
    }
  }
  async function manual(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await api(`/chamados/${chamado.id}/prioridade`, token, "PATCH", {
        prioridadeFinal: String(data.get("prioridadeManual")),
      });
      setNotice(
        "Prioridade manual salva. Sugestões anteriores precisam de nova análise antes de uma decisão.",
      );
      onChanged();
    } catch (e) {
      setError(message(e));
    } finally {
      await refresh();
      setBusy(false);
    }
  }
  return (
    <section className="review card" aria-label="Revisão de prioridade">
      <header className="section-heading">
        <div>
          <span className="eyebrow">TRIAGEM ASSISTIDA</span>
          <h2>Chamado #{chamado.id}</h2>
        </div>
        <span className="badge">{label(chamado.status)}</span>
      </header>
      <h3>{chamado.resumo || "Sem resumo"}</h3>
      <p className="description">{chamado.descricao}</p>
      <p>
        Prioridade final: <strong>{label(chamado.prioridadeFinal)}</strong>
      </p>
      {error && (
        <p className="alert error" role="alert">
          {error}
        </p>
      )}
      {notice && (
        <p className="alert" role="status">
          {notice}
        </p>
      )}
      {!closed && (
        <form onSubmit={(e) => void analyze(e)}>
          <p>
            A análise usa a última mensagem de texto do cliente ou, na ausência
            dela, a descrição/resumo. Informe impacto e urgência para usar a
            matriz diretamente.
          </p>
          <div className="fields">
            {[
              ["impacto", "Impacto"],
              ["urgencia", "Urgência"],
            ].map(([name, title]) => (
              <label key={name}>
                {title}
                <select name={name} aria-label={title} disabled={busy}>
                  <option value="">Estimar pelo modelo</option>
                  {["BAIXO", "MEDIO", "ALTO"].map((v) => (
                    <option key={v}>{v}</option>
                  ))}
                </select>
              </label>
            ))}
          </div>
          <details>
            <summary>Contexto do colaborador e serviço</summary>
            <div className="fields">
              <label>
                Cargo
                <input name="cargo" maxLength={100} disabled={busy} />
              </label>
              <label>
                Criticidade do serviço
                <select
                  name="criticidade"
                  aria-label="Criticidade do serviço"
                  disabled={busy}
                >
                  <option value="">Não informada</option>
                  {["BAIXA", "MEDIA", "ALTA"].map((v) => (
                    <option key={v}>{v}</option>
                  ))}
                </select>
              </label>
            </div>
            <label className="checkbox">
              <input type="checkbox" name="confirmado" disabled={busy} />
              Confirmei a criticidade do serviço.
            </label>
            <p className="muted">
              Serviço com criticidade alta confirmada recebe piso ALTA. O cargo,
              isoladamente, não eleva a prioridade.
            </p>
          </details>
          <button disabled={busy || loading}>
            {busy ? "Processando…" : "Analisar prioridade"}
          </button>
        </form>
      )}
      {!closed && (
        <details>
          <summary>Definir prioridade sem IA</summary>
          <form onSubmit={(e) => void manual(e)}>
            <label>
              Prioridade manual
              <select
                name="prioridadeManual"
                aria-label="Prioridade manual"
                defaultValue="MEDIA"
                disabled={busy}
              >
                {["BAIXA", "MEDIA", "ALTA", "CRITICA"].map((v) => (
                  <option key={v}>{v}</option>
                ))}
              </select>
            </label>
            <button className="secondary" disabled={busy}>
              Aplicar prioridade manual
            </button>
          </form>
        </details>
      )}
      <div className="section-heading">
        <h3>Recomendação e histórico</h3>
        <button
          className="secondary"
          disabled={busy}
          onClick={() => {
            setError("");
            void refresh();
          }}
        >
          Atualizar
        </button>
      </div>
      {loading ? (
        <p role="status">Consultando histórico…</p>
      ) : !history.length ? (
        <p className="muted">Nenhuma análise registrada.</p>
      ) : (
        history.map((t) => (
          <article className="suggestion" key={t.id}>
            <div className="section-heading">
              <strong>
                Análise #{t.id} · {label(t.estado)}
              </strong>
              <small>{new Date(t.criadaEm).toLocaleString("pt-BR")}</small>
            </div>
            {t.erro && (
              <p className="error">
                Análise indisponível ({t.erro}). O atendimento manual permanece
                disponível.
              </p>
            )}
            {t.resultado && (
              <>
                <p className="recommended">
                  {t.resultado.prioridade_recomendada}
                </p>
                <p>
                  Impacto {t.resultado.impacto} · Urgência{" "}
                  {t.resultado.urgencia} · Origem {t.resultado.origem}
                </p>
                {t.resultado.metodo_prioridade === "FEEDBACK_TECNICO" && (
                  <p>
                    Prioridade aprendida com decisões dos técnicos · confiança{" "}
                    {confidence(t.resultado.confianca_prioridade ?? null)}.
                    Pela matriz: {t.resultado.prioridade_matriz}.
                  </p>
                )}
                {t.resultado.modelo_sintetico && (
                  <p className="alert">
                    Modelo treinado com dados sintéticos. Qualidade para uso
                    real ainda não validada.
                  </p>
                )}
                {t.resultado.baixa_confianca && (
                  <p className="alert">
                    Baixa confiança: confira os dados e o contexto.
                  </p>
                )}
                <p className="muted">
                  Confiança estimada: impacto{" "}
                  {confidence(t.resultado.confianca_impacto)} · urgência{" "}
                  {confidence(t.resultado.confianca_urgencia)}. Revisão humana
                  obrigatória.
                </p>
                {t.resultado.regras_aplicadas.map((rule) => (
                  <p key={rule.id}>
                    {rule.justificativa} Prioridade base:{" "}
                    {t.resultado?.prioridade_base}.
                  </p>
                ))}
                <details>
                  <summary>Versões da análise</summary>
                  <p>
                    {t.resultado.versao_modelo ||
                      "Sem modelo; valores informados"}{" "}
                    · {t.resultado.versao_matriz} · {t.resultado.versao_regras}
                  </p>
                </details>
              </>
            )}
            {t.decididaEm && (
              <p>
                Decisão: {t.prioridadeFinal} · Técnico #{t.decisorId} ·{" "}
                {new Date(t.decididaEm).toLocaleString("pt-BR")}
                {t.justificativa && ` · ${t.justificativa}`}
              </p>
            )}
            {t.feedbackTreinamento && (
              <p className="muted">
                {t.feedbackTreinamento.estado === "ENVIADO"
                  ? "Decisão recebida pela IA. O treinamento ocorre em lotes com validação."
                  : "Decisão salva na fila de aprendizado; envio automático pendente."}
              </p>
            )}
            {t.id === latest?.id &&
              t.estado === "AGUARDANDO_REVISAO" &&
              !closed && (
                <div className="decision">
                  <button
                    disabled={busy}
                    onClick={() => void decide("ACEITAR")}
                  >
                    Aceitar recomendação
                  </button>
                  <form
                    onSubmit={(e) => {
                      e.preventDefault();
                      void decide("CORRIGIR");
                    }}
                  >
                    <div className="fields">
                      <label>
                        Prioridade corrigida
                        <select
                          aria-label="Prioridade corrigida"
                          value={priority}
                          onChange={(e) => setPriority(e.target.value)}
                          disabled={busy}
                        >
                          {["BAIXA", "MEDIA", "ALTA", "CRITICA"].map((v) => (
                            <option key={v}>{v}</option>
                          ))}
                        </select>
                      </label>
                      <label>
                        Justificativa
                        <input
                          required
                          value={reason}
                          onChange={(e) => setReason(e.target.value)}
                          maxLength={1000}
                          disabled={busy}
                        />
                      </label>
                    </div>
                    <button
                      className="secondary"
                      disabled={busy || !reason.trim()}
                    >
                      Salvar correção
                    </button>
                  </form>
                </div>
              )}
          </article>
        ))
      )}
    </section>
  );
}

function Workspace({
  session,
  onLogout,
}: {
  session: Sessao;
  onLogout: () => void;
}) {
  const [calls, setCalls] = useState<Chamado[]>([]);
  const [selected, setSelected] = useState<number | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [filter, setFilter] = useState<CallFilter>("TODOS");
  const [summary, setSummary] = useState<CallSummary | null>(null);
  const queuePath = filter === "ATIVOS" ? "/chamados/abertos" : "/chamados";
  const query = `${queuePath}?page=${page}&size=10&sort=dataAbertura,desc${filter === "EM_ATENDIMENTO" || filter === "ENCERRADO" ? `&status=${filter}` : ""}`;
  const fetchData = useCallback(async (signal?: AbortSignal) => {
    const [queue, total, active, attending, closed] = await Promise.all([
      api<{ content: Chamado[]; totalPages: number }>(query, session.token, "GET", undefined, signal),
      api<{ totalElements: number }>("/chamados?size=1", session.token, "GET", undefined, signal),
      api<{ totalElements: number }>("/chamados/abertos?size=1", session.token, "GET", undefined, signal),
      api<{ totalElements: number }>("/chamados?status=EM_ATENDIMENTO&size=1", session.token, "GET", undefined, signal),
      api<{ totalElements: number }>("/chamados?status=ENCERRADO&size=1", session.token, "GET", undefined, signal),
    ]);
    return { queue, summary: { total: total.totalElements, active: active.totalElements, attending: attending.totalElements, closed: closed.totalElements } };
  }, [query, session.token]);
  const load = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setError("");
      try {
        const data = await fetchData(signal);
        if (signal?.aborted) return;
        setCalls(data.queue.content);
        setTotalPages(data.queue.totalPages);
        setSummary(data.summary);
      } catch (e) {
        if (!signal?.aborted) setError(message(e));
      } finally {
        if (!signal?.aborted) setLoading(false);
      }
    },
    [fetchData],
  );
  useEffect(() => {
    const controller = new AbortController();
    fetchData(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          setCalls(data.queue.content);
          setTotalPages(data.queue.totalPages);
          setSummary(data.summary);
          setError("");
        }
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(message(e));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [fetchData]);
  function chooseFilter(next: CallFilter) {
    if (next === filter) return;
    setFilter(next);
    setPage(0);
    setSelected(null);
    setCalls([]);
    setLoading(true);
  }
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setCreating(true);
    setError("");
    try {
      const call = await api<Chamado>("/chamados", session.token, "POST", {
        telefoneCliente: String(data.get("telefone")),
        resumo: String(data.get("resumo")),
        descricao: String(data.get("descricao")),
        equipe: session.tecnico.equipe,
      });
      form.reset();
      setSelected(call.id);
      if (page === 0 && filter === "TODOS") await load();
      else {
        setFilter("TODOS");
        setPage(0);
        setCalls([]);
        setLoading(true);
      }
    } catch (e) {
      setError(message(e));
    } finally {
      setCreating(false);
    }
  }
  const call = calls.find((c) => c.id === selected);
  return (
    <>
      <header className="topbar">
        <a className="brand" href="/">
          T
          <span>
            Triagem TI<small>Prioridade com revisão humana</small>
          </span>
        </a>
        <div>
          <span>{session.tecnico.nome}</span>
          <button className="secondary" onClick={onLogout}>
            Sair
          </button>
        </div>
      </header>
      <main>
        <div className="page-title">
          <span className="eyebrow">CENTRAL DE ATENDIMENTO</span>
          <h1>Dashboard de chamados</h1>
          <p>Acompanhe os atendimentos e organize as próximas decisões da equipe.</p>
        </div>
        {error && (
          <p className="alert error" role="alert">
            {error}
          </p>
        )}
        <Dashboard summary={summary} filter={filter} onFilter={chooseFilter} loading={loading} />
        <div className="workspace">
          <section className="card queue">
            <div className="section-heading">
              <h2>{filter === "ATIVOS" ? "Chamados ativos" : filter === "EM_ATENDIMENTO" ? "Em atendimento" : filter === "ENCERRADO" ? "Já atendidos" : "Todos os chamados"}</h2>
              <button
                className="secondary"
                disabled={loading}
                onClick={() => void load()}
              >
                Atualizar fila
              </button>
            </div>
            {loading && <p role="status">Carregando fila…</p>}
            {!loading && !calls.length && <p>Nenhum chamado nesta página.</p>}
            <div className="call-list">
              {calls.map((c) => (
                <button
                  className={`call ${c.id === selected ? "active" : ""}`}
                  key={c.id}
                  onClick={() => setSelected(c.id)}
                >
                  <small>
                    #{c.id} · {label(c.status)}
                  </small>
                  <strong>{c.resumo || "Sem resumo"}</strong>
                  <span>
                    Final: {label(c.prioridadeFinal)} · Sugerida:{" "}
                    {label(c.prioridadeSugerida)}
                  </span>
                </button>
              ))}
            </div>
            <div className="pagination">
              <button
                className="secondary"
                disabled={page === 0 || loading}
                onClick={() => {
                  setLoading(true);
                  setCalls([]);
                  setSelected(null);
                  setPage(page - 1);
                }}
              >
                Anterior
              </button>
              <span>
                {page + 1} / {Math.max(totalPages, 1)}
              </span>
              <button
                className="secondary"
                disabled={page + 1 >= totalPages || loading}
                onClick={() => {
                  setLoading(true);
                  setCalls([]);
                  setSelected(null);
                  setPage(page + 1);
                }}
              >
                Próxima
              </button>
            </div>
            <details>
              <summary>Abrir chamado manual</summary>
              <form onSubmit={(e) => void create(e)}>
                <label>
                  Telefone do cliente
                  <input
                    name="telefone"
                    required
                    maxLength={30}
                    disabled={creating}
                  />
                </label>
                <label>
                  Resumo
                  <input
                    name="resumo"
                    required
                    maxLength={200}
                    disabled={creating}
                  />
                </label>
                <label>
                  Descrição
                  <textarea
                    name="descricao"
                    required
                    maxLength={10000}
                    disabled={creating}
                  />
                </label>
                <button disabled={creating}>Criar chamado</button>
              </form>
            </details>
          </section>
          {call ? (
            <Review
              key={call.id}
              chamado={call}
              token={session.token}
              onChanged={() => void load()}
            />
          ) : (
            <section className="card placeholder">
              <h2>Selecione um chamado</h2>
              <p>
                A recomendação original e as decisões ficam preservadas no
                histórico.
              </p>
            </section>
          )}
        </div>
      </main>
    </>
  );
}

export default function App() {
  const [session, setSession] = useState<Sessao | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    const expire = () => {
      setSession(null);
      setError("Sessão expirada. Entre novamente.");
    };
    window.addEventListener("session-expired", expire);
    return () => window.removeEventListener("session-expired", expire);
  }, []);
  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setBusy(true);
    setError("");
    try {
      setSession(
        await api<Sessao>("/auth/login", null, "POST", {
          login: String(data.get("login")),
          senha: String(data.get("senha")),
        }),
      );
    } catch (e) {
      setError(message(e));
    } finally {
      setBusy(false);
    }
  }
  async function logout() {
    const token = session?.token;
    setSession(null);
    try {
      if (token) await api("/auth/logout", token, "POST");
    } catch {
      setError(
        "Sessão encerrada localmente. Não foi possível confirmar a revogação no servidor.",
      );
    }
  }
  return session ? (
    <Workspace session={session} onLogout={() => void logout()} />
  ) : (
    <main className="login">
      <section className="login-story">
        <span className="eyebrow">TRIAGEM TI</span>
        <h1>
          Cada chamado.
          <br />
          Uma decisão
          <br />
          bem informada.
        </h1>
        <p>
          Reúna impacto, urgência e contexto para organizar o atendimento com a
          decisão final da equipe.
        </p>
      </section>
      <section className="card login-card">
        <h2>Acesse a central</h2>
        <p>Entre com sua conta de técnico.</p>
        {error && (
          <p className="alert error" role="alert">
            {error}
          </p>
        )}
        <form onSubmit={(e) => void login(e)}>
          <label>
            Login
            <input
              name="login"
              required
              autoComplete="username"
              maxLength={100}
              disabled={busy}
            />
          </label>
          <label>
            Senha
            <input
              name="senha"
              required
              type="password"
              autoComplete="current-password"
              maxLength={72}
              disabled={busy}
            />
          </label>
          <button disabled={busy}>{busy ? "Entrando…" : "Entrar"}</button>
        </form>
        <p className="muted">Ao recarregar a página, entre novamente.</p>
      </section>
    </main>
  );
}
