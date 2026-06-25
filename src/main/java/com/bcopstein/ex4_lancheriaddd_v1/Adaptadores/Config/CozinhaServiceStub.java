package com.bcopstein.ex4_lancheriaddd_v1.Adaptadores.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ICozinhaService;

// ===================== TEMPORARIO (Pessoa 1) =====================
// Stub no-op de ICozinhaService para que o contexto Spring suba e o UC9 (pagar) funcione ENQUANTO
// a Pessoa 2 nao entrega a simulacao real (CozinhaService @Service + persistencia via Seam #2).
// REMOVER na integracao: quando a Pessoa 2 anotar CozinhaService como @Service, este stub causaria
// dois beans de ICozinhaService (NoUniqueBeanDefinitionException). Ver Seam #1 / Task 11 do plano.
@Service
public class CozinhaServiceStub implements ICozinhaService {
    private static final Logger log = LoggerFactory.getLogger(CozinhaServiceStub.class);

    @Override
    public void chegadaDePedido(Pedido p) {
        log.info("[STUB Cozinha] pedido recebido para preparo: {}", p.getId());
    }

    @Override
    public void pedidoPronto() {
        // no-op (tick interno da simulacao real)
    }
}
