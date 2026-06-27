package com.bcopstein.ex4_lancheriaddd_v1.Aplicacao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests.ItemPedidoRequest;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Requests.SubmeterPedidoRequest;
import com.bcopstein.ex4_lancheriaddd_v1.Aplicacao.Responses.SubmeterPedidoResponse;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados.ProdutosRepository;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Cliente;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.ItemPedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Pedido;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Produto;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoEstoque;
import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Servicos.ServicoPedido;

@Component
public class SubmeterPedidoParaAprovacaoUC {
    private final ServicoPedido servicoPedido;
    private final ServicoEstoque servicoEstoque;
    private final ProdutosRepository produtosRepository;

    @Autowired
    public SubmeterPedidoParaAprovacaoUC(ServicoPedido servicoPedido, ServicoEstoque servicoEstoque,
                                         ProdutosRepository produtosRepository) {
        this.servicoPedido = servicoPedido;
        this.servicoEstoque = servicoEstoque;
        this.produtosRepository = produtosRepository;
    }

    public SubmeterPedidoResponse run(SubmeterPedidoRequest req) {
        if (req == null || req.itens() == null || req.itens().isEmpty()) {
            throw new IllegalArgumentException("Pedido sem itens");
        }
        // Agrega quantidades por produto (evita itens duplicados -> violacao de PK em itens_pedido).
        Map<Long, Integer> quantidadePorProduto = new LinkedHashMap<>();
        for (ItemPedidoRequest ir : req.itens()) {
            if (ir.quantidade() <= 0) {
                throw new IllegalArgumentException("Quantidade invalida para o produto " + ir.produtoId());
            }
            quantidadePorProduto.merge(ir.produtoId(), ir.quantidade(), Integer::sum);
        }

        List<ItemPedido> itens = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : quantidadePorProduto.entrySet()) {
            Produto produto = produtosRepository.recuperaProdutoPorid(e.getKey());
            if (produto == null) {
                throw new IllegalArgumentException("Produto inexistente: " + e.getKey());
            }
            itens.add(new ItemPedido(produto, e.getValue()));
        }

        Cliente cliente = new Cliente(req.clienteCpf(), null, null, null, null, null);
        Pedido pedido = servicoPedido.submeter(cliente, req.enderecoEntrega(), itens);

        List<String> indisponiveis = List.of();
        if (pedido.getStatus() == Pedido.Status.RECUSADO) {
            indisponiveis = servicoEstoque.itensIndisponiveis(itens).stream()
                .map(Produto::getDescricao).sorted().toList();
        }
        return new SubmeterPedidoResponse(pedido.getId(), pedido.getStatus().name(),
            pedido.getValor(), pedido.getDesconto(), pedido.getImpostos(),
            pedido.getValorCobrado(), indisponiveis);
    }
}
