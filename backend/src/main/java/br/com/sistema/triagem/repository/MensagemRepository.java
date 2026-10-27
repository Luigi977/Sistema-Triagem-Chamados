package br.com.sistema.triagem.repository;

import br.com.sistema.triagem.model.entity.Mensagem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    List<Mensagem> findByChamadoIdOrderByDataEnvioAscIdAsc(Long chamadoId);
}
