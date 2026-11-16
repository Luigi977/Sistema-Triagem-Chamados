"""Cenários fictícios de TI. Não lê nem reproduz conversas privadas."""
import csv
from pathlib import Path

# Cada assunto e suas paráfrases ficam inteiramente em uma partição.
THEMES = {
    'treino': [
        'A conexão com a internet está falhando.', 'O aplicativo de scanner não abre.',
        'O acesso ao e-mail apresenta erro.', 'As câmeras não mostram as imagens.',
        'Preciso configurar uma impressora.', 'O computador precisa de formatação.',
        'Não consigo converter imagens para PDF.', 'É necessário redefinir a senha do sistema.',
        'O acesso remoto está indisponível.', 'O programa de planilhas não inicia.',
        'O Wi-Fi está desconectando.', 'Preciso instalar um aplicativo de edição.',
    ],
    'validacao': [
        'O aplicativo de estoque não sincroniza.', 'A pasta compartilhada não está acessível.',
        'O serviço de backup apresenta uma falha.',
    ],
    'teste': [
        'A plataforma de videoconferência deixou de funcionar.',
        'O portal de emissão de notas fiscais está com defeito.',
        'O sistema de ponto não permite registrar o horário.',
    ],
}
SCOPES = {
    'BAIXO': ['Afeta apenas meu computador; o restante da equipe trabalha normalmente.',
              'Sou a única pessoa afetada, em uma estação individual.'],
    'MEDIO': ['Afeta todos da minha equipe em um único setor; outros setores funcionam.',
              'O departamento inteiro está afetado, restrito a esse setor.'],
    'ALTO': ['Todos os setores da empresa foram afetados, sem exceção.',
             'O problema atinge a organização inteira e todas as unidades.'],
}
DEADLINES = {
    'BAIXO': ['Pode esperar até a próxima semana, sem prazo imediato.',
              'É uma melhoria sem pressa, pode aguardar vários dias.'],
    'MEDIO': ['Precisa ser resolvido até amanhã; existe uma alternativa temporária.',
              'Temos um prazo próximo e conseguimos continuar por outro procedimento.'],
    'ALTO': ['O trabalho está parado agora e não existe alternativa; precisamos resolver imediatamente.',
             'É uma interrupção imediata sem contorno; não conseguimos continuar o atendimento.'],
}


def generate(output=None):
    directory = Path(__file__).resolve().parent
    with (directory / 'chamados-sinteticos.csv').open(encoding='utf-8', newline='') as handle:
        rows = list(csv.DictReader(handle))
    for partition, themes in THEMES.items():
        for index, theme in enumerate(themes):
            group = f'contextual-{partition}-{index}'
            for impact, scopes in SCOPES.items():
                for urgency, deadlines in DEADLINES.items():
                    for variant in range(2):
                        rows.append({'id': f'ctx-{partition}-{index}-{impact}-{urgency}-{variant}',
                                     'grupo': group, 'texto': f'{theme} {scopes[variant]} {deadlines[variant]}',
                                     'impacto': impact, 'urgencia': urgency,
                                     'particao': partition, 'origem': 'SINTETICO'})
    target = Path(output) if output else directory / 'chamados-contextuais.csv'
    with target.open('w', encoding='utf-8', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)
    return len(rows)


if __name__ == '__main__':
    print(f'{generate()} exemplos sintéticos gerados')
