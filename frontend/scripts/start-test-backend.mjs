import { readFileSync, existsSync } from 'node:fs'
import { resolve, delimiter } from 'node:path'
import { spawn } from 'node:child_process'
const backend = resolve('../backend')
const classpathFile = resolve(backend, 'target/test-classpath.txt')
if (!existsSync(classpathFile) || !existsSync(resolve(backend, 'target/classes')))
  throw new Error(
    'Compile o backend e gere target/test-classpath.txt conforme README.md antes do teste integrado.',
  )
if (!process.env.FRONTEND_TEST_PASSWORD)
  throw new Error('Inicie pelo comando npm run test:e2e:live para gerar credenciais efêmeras.')
const child = spawn(
  'java',
  [
    '-cp',
    [resolve(backend, 'target/classes'), readFileSync(classpathFile, 'utf8').trim()].join(
      delimiter,
    ),
    'br.com.sistema.triagem.TriagemChamadosApplication',
    '--server.port=18085',
    '--server.address=127.0.0.1',
    '--spring.config.import=',
    '--spring.datasource.url=jdbc:h2:mem:frontend_homologacao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1',
    '--spring.datasource.driver-class-name=org.h2.Driver',
    '--spring.datasource.username=sa',
    '--spring.datasource.password=',
    '--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect',
    '--spring.jpa.show-sql=false',
    '--spring.jpa.hibernate.ddl-auto=validate',
    '--evolution.enabled=false',
    '--evolution.worker-enabled=false',
    '--evolution.managed=false',
  ],
  {
    windowsHide: true,
    stdio: 'inherit',
    env: {
      ...process.env,
      TRIAGEM_ADMIN_LOGIN: 'frontend-laboratorio',
      TRIAGEM_ADMIN_PASSWORD: process.env.FRONTEND_TEST_PASSWORD,
    },
  },
)
child.on('error', () => {
  console.error('Não foi possível iniciar Java 21+.')
  process.exitCode = 1
})
child.on('exit', (code) => {
  process.exitCode = code ?? 0
})
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill())
