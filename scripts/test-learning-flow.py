"""Regressão local: Java/H2 + FastAPI + Newman, sem banco ou WhatsApp operacional.

Pré-requisito: mvn test e dependency:build-classpath com saída
backend/target/learning-classpath.txt (escopo test).
Execute com ia/.venv/Scripts/python.exe scripts/test-learning-flow.py.
"""
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import sqlite3
import subprocess
import sys
import tempfile
import time
import httpx

ROOT = Path(__file__).resolve().parents[1]


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def wait_for(url, process):
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError('Serviço de teste encerrou durante a inicialização; consulte os logs locais')
        try:
            if httpx.get(url, timeout=1).status_code == 200:
                return
        except httpx.HTTPError:
            pass
        time.sleep(0.3)
    raise RuntimeError('Tempo limite na inicialização do ambiente isolado')


def main():
    results = ROOT / 'ia/reports'
    results.mkdir(exist_ok=True)
    destination = Path(tempfile.mkdtemp(prefix='learning-flow-', dir=results))
    classpath_file = ROOT / 'backend/target/learning-classpath.txt'
    if not classpath_file.exists():
        raise RuntimeError('Gere backend/target/learning-classpath.txt conforme a docstring')
    classpath = str(ROOT / 'backend/target/classes') + os.pathsep + classpath_file.read_text().strip()
    ia_port, backend_port = free_port(), free_port()
    ia_url, backend_url = f'http://127.0.0.1:{ia_port}', f'http://127.0.0.1:{backend_port}'
    token, password = secrets.token_hex(32), secrets.token_urlsafe(24)
    env = dict(os.environ, IA_FEEDBACK_TOKEN=token, IA_FEEDBACK_DB=str(destination / 'feedback.sqlite3'),
               IA_LEARNING_ENABLED='false', IA_LEARNING_DATASET='examples/chamados-contextuais.csv',
               IA_MODEL_PATH=str(destination / 'missing-model.joblib'), SPRING_CONFIG_IMPORT='',
               SPRING_DATASOURCE_URL='jdbc:h2:mem:learning_flow;DB_CLOSE_DELAY=-1', SPRING_DATASOURCE_USERNAME='sa',
               SPRING_DATASOURCE_PASSWORD='', SPRING_JPA_SHOW_SQL='false', SPRING_JPA_OPEN_IN_VIEW='false',
               SPRING_FLYWAY_BASELINE_ON_MIGRATE='false', EVOLUTION_ENABLED='false', EVOLUTION_WORKER_ENABLED='false',
               IA_ENABLED='true', IA_AUTO_ENABLED='false', IA_API_URL=ia_url, IA_FEEDBACK_DELAY_MS='1000',
               TRIAGEM_ADMIN_LOGIN='admin-feedback-test', TRIAGEM_ADMIN_PASSWORD=password,
               SERVER_PORT=str(backend_port), SERVER_ADDRESS='127.0.0.1')
    processes, handles = [], []
    flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
    try:
        for name, command, cwd in [
            ('python', [sys.executable, '-m', 'uvicorn', 'src.main:app', '--host', '127.0.0.1', '--port', str(ia_port), '--log-level', 'warning'], ROOT / 'ia'),
            ('java', [shutil.which('java'), '-cp', classpath, 'br.com.sistema.triagem.TriagemChamadosApplication'], ROOT / 'backend')]:
            log = (destination / f'{name}.log').open('w', encoding='utf-8')
            handles.append(log)
            processes.append(subprocess.Popen(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT, creationflags=flags))
        wait_for(ia_url + '/health', processes[0])
        wait_for(backend_url + '/health', processes[1])
        values = {'baseUrl': backend_url, 'iaBaseUrl': ia_url, 'adminLogin': 'admin-feedback-test',
                  'adminPassword': password, 'iaFeedbackToken': token, 'iaTestsEnabled': 'true',
                  'iaLearningTestsEnabled': 'true', 'evolutionTestsEnabled': 'false', 'whatsappConnectionTestsEnabled': 'false'}
        environment = destination / 'environment.private.json'
        environment.write_text(json.dumps({'name': 'isolated-learning-test', 'values': [
            {'key': k, 'value': v, 'enabled': True} for k, v in values.items()]}), encoding='utf-8')
        command = ['npx.cmd' if os.name == 'nt' else 'npx', '--yes', 'newman@6.2.1', 'run',
                   str(ROOT / 'docs/postman/triagem-chamados.postman_collection.json'), '-e', str(environment),
                   '--reporters', 'cli,json', '--reporter-json-export', str(destination / 'newman.private.json')]
        if os.name == 'nt':
            command = ['cmd.exe', '/d', '/c'] + command
        with (destination / 'newman.log').open('w', encoding='utf-8') as log:
            completed = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, creationflags=flags)
        report = json.loads((destination / 'newman.private.json').read_text(encoding='utf-8'))
        if completed.returncode or report['run']['failures']:
            raise RuntimeError(f'Falhas Newman: {len(report["run"]["failures"])}; relatórios locais em {destination}')
        with httpx.Client(base_url=backend_url, timeout=10) as client:
            login = client.post('/api/auth/login', json={'login': values['adminLogin'], 'senha': password})
            login.raise_for_status()
            client.headers['Authorization'] = 'Bearer ' + login.json()['token']
            created = client.post('/api/chamados', json={'telefoneCliente': '119' + str(time.time_ns())[-8:],
                'descricao': 'Impressora do setor com falha e alternativa confirmada no teste', 'equipe': 'N1'})
            created.raise_for_status()
            call = created.json()['id']
            path = f'/api/chamados/{call}/triagens-prioridade'
            def decide(action):
                analysis = client.post(path, json={'impactoInformado': 'BAIXO', 'urgenciaInformada': 'BAIXO'})
                analysis.raise_for_status()
                data = {'acao': action}
                if action == 'CORRIGIR':
                    data.update(prioridadeFinal='MEDIA', justificativa='Impacto setorial confirmado pelo técnico de teste')
                result = client.post(path + f'/{analysis.json()["id"]}/decisao', json=data)
                result.raise_for_status()
                return result.json()['feedbackTreinamento']['eventoId']
            accepted, corrected = decide('ACEITAR'), decide('CORRIGIR')
            client.patch(f'/api/chamados/{call}/prioridade', json={'prioridadeFinal': 'ALTA'}).raise_for_status()
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                with sqlite3.connect(destination / 'feedback.sqlite3') as db:
                    records = db.execute('SELECT grupo FROM feedback WHERE evento_id=?', (accepted,)).fetchall()
                    rows = db.execute('SELECT prioridade FROM feedback WHERE grupo=? ORDER BY decidida,seq', records[0]).fetchall() if records else []
                if [r[0] for r in rows] == ['BAIXA', 'MEDIA', 'ALTA']:
                    break
                time.sleep(0.3)
            else:
                raise RuntimeError('A fila não entregou aceite, correção e alteração manual à IA')
            history = client.get(path).json()
            assert all(t['feedbackTreinamento']['estado'] == 'ENVIADO' for t in history)
        summary = {'newman': report['run']['stats'], 'fluxo_real_http': 'ACEITAR + CORRIGIR + PRIORIDADE_MANUAL recebidos',
                   'treinamento': 'Não executado neste smoke; coberto nos testes Python em arquivos temporários',
                   'banco': 'H2 isolado e SQLite temporário', 'whatsapp': 'Desativado'}
        (destination / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps(summary, ensure_ascii=False, indent=2))
    finally:
        for process in reversed(processes):
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
        for handle in handles:
            handle.close()
        # Credenciais e tokens efêmeros não ficam nos artefatos finais.
        for name in ('environment.private.json', 'newman.private.json'):
            file = destination / name
            if file.exists():
                file.unlink()


if __name__ == '__main__':
    main()
