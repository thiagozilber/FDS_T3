package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;

public interface ICozinhaService {
    void chegadaDePedido(Pedido p);
    void pedidoPronto();
}