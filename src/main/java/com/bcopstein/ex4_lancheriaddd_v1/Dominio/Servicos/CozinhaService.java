package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;

@Service
public class CozinhaService implements ICozinhaService {
    private final Queue<Pedido> filaEntrada;
    private Pedido emPreparacao;
    private final Queue<Pedido> filaSaida;

    private final ScheduledExecutorService scheduler;
    private final IRegistradorStatus registradorStatus;
    private final IEntregaService entregaService;

    public CozinhaService(IRegistradorStatus registradorStatus, IEntregaService entregaService) {
        filaEntrada = new LinkedBlockingQueue<>();
        emPreparacao = null;
        filaSaida = new LinkedBlockingQueue<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        this.registradorStatus = registradorStatus;
        this.entregaService = entregaService;
    }

    private synchronized void colocaEmPreparacao(Pedido pedido){
        registradorStatus.registrarTransicao(pedido.getId(), Pedido.Status.PREPARACAO);
        emPreparacao = pedido;
        System.out.println("Pedido em preparacao: "+pedido);
        // Agenda pedidoPronto para ser chamado em 5 segundos
        scheduler.schedule(() -> pedidoPronto(), 5, TimeUnit.SECONDS);
    }

    @Override
    public synchronized void chegadaDePedido(Pedido p) {
        filaEntrada.add(p);
        System.out.println("Pedido na fila de entrada: "+p);
        if (emPreparacao == null) {
            colocaEmPreparacao(filaEntrada.poll());
        }
    }

    @Override
    public synchronized void pedidoPronto() {
        if (emPreparacao == null) {
            return;
        }

        Pedido pronto = emPreparacao;
        registradorStatus.registrarTransicao(pronto.getId(), Pedido.Status.PRONTO);
        filaSaida.add(pronto);
        System.out.println("Pedido na fila de saida: "+pronto);
        entregaService.pedidoParaEntrega(pronto);
        emPreparacao = null;
        // Se tem pedidos na fila, programa a preparação para daqui a 1 segundo
        if (!filaEntrada.isEmpty()){
            Pedido prox = filaEntrada.poll();
            scheduler.schedule(() -> colocaEmPreparacao(prox), 1, TimeUnit.SECONDS);
        }
    }
}
