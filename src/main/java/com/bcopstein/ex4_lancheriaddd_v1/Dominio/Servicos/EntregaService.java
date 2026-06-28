package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos;

import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;

@Service
public class EntregaService implements IEntregaService {
	private final Queue<Pedido> filaEntrada;
	private Pedido emTransporte;
	private final Queue<Pedido> filaSaida;

	private final ScheduledExecutorService scheduler;
	private final IRegistradorStatus registradorStatus;

	public EntregaService(IRegistradorStatus registradorStatus) {
		this.filaEntrada = new LinkedBlockingQueue<>();
		this.emTransporte = null;
		this.filaSaida = new LinkedBlockingQueue<>();
		this.scheduler = Executors.newSingleThreadScheduledExecutor();
		this.registradorStatus = registradorStatus;
	}

	private synchronized void colocaEmTransporte(Pedido pedido) {
		registradorStatus.registrarTransicao(pedido.getId(), Pedido.Status.TRANSPORTE);
		emTransporte = pedido;
		System.out.println("Pedido em transporte: " + pedido);
		scheduler.schedule(() -> pedidoEntregue(), 5, TimeUnit.SECONDS);
	}

	@Override
	public synchronized void pedidoParaEntrega(Pedido p) {
		filaEntrada.add(p);
		System.out.println("Pedido na fila de entrega: " + p);
		if (emTransporte == null) {
			colocaEmTransporte(filaEntrada.poll());
		}
	}

	@Override
	public synchronized void pedidoEntregue() {
		if (emTransporte == null) {
			return;
		}

		registradorStatus.registrarTransicao(emTransporte.getId(), Pedido.Status.ENTREGUE);
		filaSaida.add(emTransporte);
		System.out.println("Pedido entregue: " + emTransporte);
		emTransporte = null;

		if (!filaEntrada.isEmpty()) {
			Pedido prox = filaEntrada.poll();
			scheduler.schedule(() -> colocaEmTransporte(prox), 1, TimeUnit.SECONDS);
		}
	}
}
