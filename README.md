<p align="center"><img src="src/main/resources/static/icons/fluxo-verde-192.png" width="96" alt="Carteira Fluxo"></p>

# Fluxo — Organização financeira pessoal

Aplicação web para organizar renda, despesas e metas, com histórico mensal e interface adaptada a celulares. Projeto em desenvolvimento para portfólio.

## Funcionalidades

- Cadastro, login, recuperação de senha e registro de aceite dos termos.
- Organização do orçamento por necessidades, desejos e investimentos.
- Histórico mensal, receitas extras e registro de gastos diários.
- Registro manual de faturas de cartões e marcação de despesas pagas.
- Perfil com foto, tema claro/escuro e navegação responsiva.
- Congelamento, reativação e exclusão da conta com confirmação.
- Estrutura de aplicativo web instalável (PWA).

## Tecnologias

Java 17, Spring Boot 3, Spring Security, JDBC, H2 no ambiente local e suporte a PostgreSQL na nuvem. Interface em HTML, CSS e JavaScript. Testes com JUnit e MockMvc. Docker e configuração para Render.

## Executar localmente

Requisitos: Java 17 ou superior e Maven 3.9.

```sh
mvn test
mvn spring-boot:run
```

Abra http://localhost:8080. O modo local cria o banco H2 em `dados/`. Essa pasta é privada e não deve ser adicionada ao Git.

Para gerar o aplicativo:

```sh
mvn package
java -jar target/fluxo-1.0.0.jar
```

## Arquitetura

O navegador acessa uma API Spring Boot. A autenticação usa Spring Security e senhas com BCrypt; as operações de alteração usam proteção CSRF. Os registros financeiros são separados por conta e mês. O modo de nuvem exige banco remoto e chave de login persistente.

## Estado do projeto

O projeto ainda não é um serviço comercial lançado. A configuração Render + Neon está preparada, mas o primeiro deploy e a conexão real com PostgreSQL precisam ser validados. A cobrança automática, o cartão obrigatório antes do teste grátis e o envio de e-mails por API continuam pendentes. O checkout mensal existente não representa uma assinatura recorrente completa.

A política de retenção, os backups remotos e os dados de atendimento dos termos precisam ser concluídos antes da operação comercial. As versões Android e iOS nativas ainda não foram criadas.

## Publicação e privacidade

Veja [PUBLICAR.md](PUBLICAR.md) para a configuração de teste. Configure credenciais apenas no ambiente da hospedagem. Este repositório não deve conter bancos de usuários, arquivos `.env`, chaves ou configurações privadas. Os dados usados pelos testes são fictícios.

O GitHub armazena o código; o servidor Java precisa de hospedagem própria. GitHub Pages não executa o aplicativo completo.
