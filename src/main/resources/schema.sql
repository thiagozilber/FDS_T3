create table if not exists clientes(
  cpf varchar(15) not null primary key,
  nome varchar(100) not null,
  celular varchar(20) not null,
  endereco varchar(255) not null,
  email varchar(255) not null
);

create table if not exists ingredientes (
  id bigint primary key,
  descricao varchar(255) not null
);

create table if not exists itensEstoque(
    id bigint primary key,
    quantidade int,
    ingrediente_id bigint,
    foreign key (ingrediente_id) references ingredientes(id)
);

-- Tabela Receita
create table if not exists receitas (
  id bigint primary key,
  titulo varchar(255) not null
);

-- Tabela de relacionamento entre Receita e Ingrediente
create table if not exists receita_ingrediente (
  receita_id bigint not null,
  ingrediente_id bigint not null,
  primary key (receita_id, ingrediente_id),
  foreign key (receita_id) references receitas(id),
  foreign key (ingrediente_id) references ingredientes(id)
);

-- Tabela de Produtos
create table if not exists produtos (
  id bigint primary key,
  descricao varchar(255) not null,
  preco bigint
);

-- Tabela de relacionamento entre Produto e Receita
create table if not exists produto_receita (
  produto_id bigint not null,
  receita_id bigint not null,
  primary key (produto_id,receita_id),
  foreign key (produto_id) references produtos(id),
  foreign key (receita_id) references receitas(id)
);

-- Tabela de Cardapios
create table if not exists cardapios (
  id bigint primary key,
  titulo varchar(255) not null
);

-- Tabela de relacionamento entre Cardapio e Produto
create table if not exists cardapio_produto (
  cardapio_id bigint not null,
  produto_id bigint not null,
  primary key (cardapio_id,produto_id),
  foreign key (cardapio_id) references cardapios(id),
  foreign key (produto_id) references produtos(id)
);

-- Tabela chave/valor para estado corrente (cardapio corrente, politica de desconto corrente)
create table if not exists configuracao (
  chave varchar(50) not null primary key,
  valor varchar(100) not null
);

-- Ciclo do pedido (P2 / Pessoa 1): pedidos + itens + historico de status
-- Valores monetarios em DOUBLE (reais), espelhando os campos double da entidade Pedido.
create table if not exists pedidos (
  id bigint auto_increment primary key,
  cliente_cpf varchar(15) not null,
  status varchar(20) not null,
  valor double not null,
  impostos double not null,
  desconto double not null,
  valor_cobrado double not null,
  data_hora_pagamento timestamp,
  endereco_entrega varchar(255) not null,
  foreign key (cliente_cpf) references clientes(cpf)
);

create table if not exists itens_pedido (
  pedido_id bigint not null,
  produto_id bigint not null,
  quantidade int not null,
  primary key (pedido_id, produto_id),
  foreign key (pedido_id) references pedidos(id),
  foreign key (produto_id) references produtos(id)
);

create table if not exists historico_status (
  id bigint auto_increment primary key,
  pedido_id bigint not null,
  status varchar(20) not null,
  data_hora timestamp not null,
  foreign key (pedido_id) references pedidos(id)
);
