# Publicação de teste: Render + Neon

Estado: arquivos preparados; aplicativo ainda não publicado. A conexão com PostgreSQL real e a imagem Docker precisam de validação no primeiro deploy. Não há transferência automática dos dados locais.

## 1. Contas e repositório
Crie contas gratuitas em https://render.com e https://neon.com usando seu GitHub.
Use o repositório de portfólio preparado, contendo apenas código e documentação revisados. Nunca envie dados/, config/, .env, arquivos de banco ou chaves. O pacote contém código e documentação, sem contas pessoais.

## 2. Neon
Crie um projeto Free, de preferência em região próxima à do Render. Em Connect, obtenha host, database, user e password. Guarde os segredos diretamente nas variáveis de ambiente do Render, nunca em mensagens ou no repositório.

FLUXO_DATABASE_URL = jdbc:postgresql://HOST/DATABASE?sslmode=verify-full
FLUXO_DATABASE_USUARIO = usuário informado pelo Neon
FLUXO_DATABASE_SENHA = senha informada pelo Neon

Use a URL JDBC acima, sem usuário/senha na URL. O aplicativo usa as autoridades certificadoras confiáveis do Java e verifica o nome do servidor.

## 3. Render
New > Blueprint > conecte o repositório privado. O render.yaml escolhe explicitamente o plano Free. Confira que não há serviço pago antes de criar.
Preencha as três variáveis do banco e FLUXO_URL_PUBLICA com a URL HTTPS final fornecida pelo Render. A chave FLUXO_LOGIN_CHAVE é gerada pelo Render: mantenha o mesmo valor nos próximos deploys. Não troque essa chave a cada reinício.
Mantenha uma única instância e um único serviço conectado ao banco. A implementação atual serializa operações por processo; não habilite múltiplas réplicas nem serviços simultâneos no mesmo banco.
O perfil cloud impede iniciar sem banco remoto e habilita cookies seguros. Não execute o perfil cloud em HTTP local.

## 4. Validação antes de convidar usuários
Teste cadastro, login, histórico de dois meses, foto, congelar/reativar e excluir usando contas de teste. Reinicie o serviço e confira que os registros continuam disponíveis. Confira também a recuperação do login com cookie persistente.
Para migrar seus registros locais, precisamos fazer uma cópia verificada, importar no banco vazio e comparar os registros; essa migração ainda não foi executada. Não coloque o arquivo do banco no GitHub.
Depois de migrar, configure FLUXO_PROPRIETARIO_ID e FLUXO_PROPRIETARIO_EMAIL no Render com os dados privados da sua conta. Se começar com um cadastro novo na nuvem, seu identificador será outro e precisará ser vinculado antes do uso. O pacote não concede isenção automaticamente ao primeiro cadastrado.

## Limites e pendências
- Render Free pode dormir após inatividade e demora para iniciar novamente.
- Dados ficam no Neon. Backups locais H2 continuam funcionando no modo local, mas não são executados no PostgreSQL; configure exportações e teste restauração fora do servidor antes do uso comercial.
- A rotina atual de e-mail usa SMTP. Render Free bloqueia portas SMTP comuns: integrar envio por HTTPS e validar o domínio/remetente antes de habilitar recuperação de senha pública.
- Cobrança real continua desabilitada. Renovação automática e cartão antes do teste grátis continuam pendentes.
- A publicação é para testes. Completar contato, termos, retenção de dados e revisão de segurança antes de receber assinantes.

Referências: https://render.com/docs/free ; https://render.com/docs/blueprint-spec ; https://jdbc.postgresql.org/documentation/ssl/
