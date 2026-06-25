package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.Pagamento;

import org.springframework.stereotype.Service;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.IPagamentoService;

// Implementacao fake do pagamento: sempre aprova (spec). Fica atras da interface IPagamentoService
// para poder ser trocada por uma integracao real depois.
@Service
public class PagamentoFake implements IPagamentoService {
    @Override
    public boolean processarPagamento(Pedido pedido) {
        return true;
    }
}
