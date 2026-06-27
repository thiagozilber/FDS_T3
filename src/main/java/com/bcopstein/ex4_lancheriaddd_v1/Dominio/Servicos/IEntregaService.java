package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;

public interface IEntregaService {
	void pedidoParaEntrega(Pedido p);
	void pedidoEntregue();
}
