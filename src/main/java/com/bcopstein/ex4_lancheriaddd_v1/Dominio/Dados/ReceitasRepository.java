package com.bcopstein.ex4_lancheriaddd_v1.Dominio.Dados;

import com.bcopstein.ex4_lancheriaddd_v1.Dominio.Entidades.Receita;

public interface ReceitasRepository {
    Receita recuperaReceita(long id);
    
}
